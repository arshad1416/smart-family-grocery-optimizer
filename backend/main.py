import logging
import re
import hashlib
import os
import base64
import secrets
import math
import requests
import asyncio
from datetime import datetime, timedelta
from typing import List, Optional

from fastapi import FastAPI, Depends, HTTPException, Query, BackgroundTasks, Request, Security
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from pydantic import BaseModel
from sqlalchemy.orm import Session, joinedload
from dotenv import load_dotenv

from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives import hashes

import database as db
import scraper
import optimizer

load_dotenv()
GOOGLE_MAPS_API_KEY = os.getenv("GOOGLE_MAPS_API_KEY", "")

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("GroceryAPI")

# Initialize FastAPI App
app = FastAPI(
    title="Smart Family Grocery List API",
    description="Privacy-first localized backend for scraping and optimizing grocery lists.",
    version="1.0.0"
)

# CORS configuration: set allow_credentials=False so allow_origins=["*"] is valid
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Global scraper status tracker
scraper_status = {"status": "idle", "last_run": None, "logs": []}

# --- Pydantic Schemas ---
class StoreCreate(BaseModel):
    name: str
    location: Optional[str] = None
    distance_km: float
    latitude: Optional[float] = None
    longitude: Optional[float] = None

class ListCreate(BaseModel):
    name: str

class ItemCreate(BaseModel):
    encrypted_name: str  # Client-side AES ciphertext
    item_hash: str       # SHA-256 token of canonicalized name
    quantity: int = 1
    category: str = "Pantry"
    added_by: Optional[str] = None

class ItemUpdate(BaseModel):
    is_completed: Optional[bool] = None
    quantity: Optional[int] = None

class StoreRequestCreate(BaseModel):
    store_name: str
    address_hint: Optional[str] = None

class SmartHomePayload(BaseModel):
    device: str  # google, alexa, homeassistant, siri
    text: str

class OptimizeRequest(BaseModel):
    list_id: int
    gas_price: float = 1.55
    mileage: float = 8.5
    time_value: float = 25.0
    user_lat: float = 43.3333
    user_lon: float = -79.8833

class RegisterTokenRequest(BaseModel):
    token_hash: str
    passphrase: str

# --- AES-GCM Helper ---
def encrypt_aes_gcm(plaintext: str, passphrase: str) -> str:
    if not passphrase:
        return plaintext
    salt = os.urandom(16)
    iv = os.urandom(12)
    kdf = PBKDF2HMAC(
        algorithm=hashes.SHA256(),
        length=32,
        salt=salt,
        iterations=600000
    )
    key = kdf.derive(passphrase.encode('utf-8'))
    aesgcm = AESGCM(key)
    ciphertext_with_tag = aesgcm.encrypt(iv, plaintext.encode('utf-8'), None)
    packed = salt + iv + ciphertext_with_tag
    return base64.b64encode(packed).decode('utf-8')

# --- Authentication Dependency ---
security_scheme = HTTPBearer(auto_error=False)

def verify_sync_token(
    credentials: Optional[HTTPAuthorizationCredentials] = Security(security_scheme),
    session: Session = Depends(db.get_db)
):
    # Check if a master token is registered (TOFU check)
    sync_config = session.query(db.SyncConfig).first()
    if not sync_config:
        # Trust On First Use: backend is unlocked
        return None

    if not credentials:
        raise HTTPException(
            status_code=401, 
            detail="Authentication token is required to access sync features."
        )

    token = credentials.credentials
    # Match master token
    if token == sync_config.token_hash:
        return token

    # Check database-backed active invite codes
    invite = session.query(db.CollaborationInvite).filter(
        db.CollaborationInvite.invite_code == token
    ).first()
    if invite:
        if invite.expires_at > datetime.utcnow():
            return token
        else:
            raise HTTPException(status_code=401, detail="Sync collaboration invite has expired.")

    raise HTTPException(status_code=401, detail="Invalid sync authentication token.")

# --- Lifecycle Hooks ---
@app.on_event("startup")
async def startup_event():
    # Run migrations
    db.init_db()
    
    # Seed database with stores and prices if empty
    session = db.SessionLocal()
    try:
        store_count = session.query(db.Store).count()
        if store_count == 0:
            logger.info("Initializing SQLite seed database with Ontario stores and price catalogs...")
            await scraper.scrape_grocery_prices(session)
            logger.info("Database seeding complete!")
    except Exception as e:
        logger.error(f"Error during startup database seed: {str(e)}")
    finally:
        session.close()

# --- Endpoints ---

@app.post("/api/collaboration/register-token")
def register_token(req: RegisterTokenRequest, session: Session = Depends(db.get_db)):
    """
    Initial pairing step (TOFU). Pairs the client key hash as the master API sync token.
    """
    sync_config = session.query(db.SyncConfig).first()
    if sync_config:
        raise HTTPException(status_code=400, detail="Sync credentials already registered.")
        
    config = db.SyncConfig(
        token_hash=req.token_hash,
        passphrase=req.passphrase
    )
    session.add(config)
    
    # Pre-configure default list with passphrase for voice webhook GCM encryption
    default_list = session.query(db.GroceryList).first()
    if default_list:
        default_list.encryption_passphrase = req.passphrase
        
    session.commit()
    return {"status": "success", "message": "Master sync token registered successfully."}

@app.get("/api/stores", response_model=List[dict])
def get_stores(session: Session = Depends(db.get_db), token: Optional[str] = Depends(verify_sync_token)):
    stores = session.query(db.Store).all()
    return [{
        "id": s.id, 
        "name": s.name, 
        "distance_km": s.distance_km, 
        "location": s.location,
        "latitude": s.latitude,
        "longitude": s.longitude
    } for s in stores]

@app.post("/api/stores/request")
def request_store(
    req: StoreRequestCreate, 
    session: Session = Depends(db.get_db), 
    token: Optional[str] = Depends(verify_sync_token)
):
    requested = db.RequestedStore(
        store_name=req.store_name,
        address_hint=req.address_hint,
        requested_at=datetime.utcnow()
    )
    session.add(requested)
    session.commit()
    return {"status": "success", "message": f"Request to add '{req.store_name}' logged successfully."}

def haversine_distance(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    R = 6371.0
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)
    
    a = math.sin(delta_phi / 2.0)**2 + \
        math.cos(phi1) * math.cos(phi2) * \
        math.sin(delta_lambda / 2.0)**2
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    
    return round(R * c, 2)

@app.get("/api/stores/search-nearby")
async def search_nearby_stores(
    query: str,
    request: Request,
    lat: float = 43.3333,
    lon: float = -79.8833,
    session: Session = Depends(db.get_db),
    token: Optional[str] = Depends(verify_sync_token)
):
    """
    Searches for stores using Google Places API (New) or OpenStreetMap Nominatim.
    Uses async non-blocking execution via asyncio.to_thread.
    """
    logger.info(f"Searching nearby stores for query '{query}' from location ({lat}, {lon})")
    
    # 1. Check for custom client Places API Key in headers, fallback to backend env key
    client_key = request.headers.get("X-Google-Places-Key")
    api_key = client_key if client_key else GOOGLE_MAPS_API_KEY
    
    if api_key:
        logger.info("Using Google Places API (New) for nearby store lookup")
        url = "https://places.googleapis.com/v1/places:searchText"
        headers = {
            "Content-Type": "application/json",
            "X-Goog-Api-Key": api_key,
            "X-Goog-FieldMask": "places.displayName,places.formattedAddress,places.location"
        }
        payload = {
            "textQuery": f"{query} grocery store",
            "locationBias": {
                "circle": {
                    "center": {"latitude": lat, "longitude": lon},
                    "radius": 10000.0
                }
            }
        }
        try:
            # Perform non-blocking threadpool request
            response = await asyncio.to_thread(requests.post, url, json=payload, headers=headers, timeout=5)
            if response.status_code == 200:
                data = response.json()
                places = data.get("places", [])
                if places:
                    stores_found = []
                    for item in places:
                        loc = item.get("location", {})
                        item_lat = loc.get("latitude")
                        item_lon = loc.get("longitude")
                        
                        if item_lat is not None and item_lon is not None:
                            dist = haversine_distance(lat, lon, item_lat, item_lon)
                            store_name = item.get("displayName", {}).get("text", query.title())
                            location = item.get("formattedAddress", "Unknown Address")
                            
                            stores_found.append({
                                "name": store_name,
                                "location": location,
                                "distance_km": dist,
                                "latitude": item_lat,
                                "longitude": item_lon
                            })
                    stores_found.sort(key=lambda s: s["distance_km"])
                    return stores_found
                else:
                    logger.warning("Google Places API (New) returned 0 results.")
            else:
                logger.warning(f"Google Places API (New) returned status {response.status_code}: {response.text}")
        except Exception as e:
            logger.warning(f"Google Places API (New) request failed: {str(e)}. Falling back to OpenStreetMap Nominatim.")

    # 2. OpenStreetMap Nominatim Search
    min_lon = lon - 0.25
    max_lon = lon + 0.25
    min_lat = lat - 0.25
    max_lat = lat + 0.25
    
    osm_headers = {"User-Agent": "SmartFamilyGroceryList/1.0 (contact: support@shiftlogic.ca)"}
    for search_term in [f"{query} grocery store", query]:
        url = f"https://nominatim.openstreetmap.org/search?q={search_term}&format=json&limit=5&viewbox={min_lon},{max_lat},{max_lon},{min_lat}&bounded=1"
        try:
            response = await asyncio.to_thread(requests.get, url, headers=osm_headers, timeout=5)
            if response.status_code == 200:
                results = response.json()
                if results:
                    stores_found = []
                    for item in results:
                        item_lat = float(item["lat"])
                        item_lon = float(item["lon"])
                        dist = haversine_distance(lat, lon, item_lat, item_lon)
                        
                        display_name = item["display_name"]
                        parts = display_name.split(", ")
                        cleaned_name = parts[0]
                        
                        if query.lower() not in cleaned_name.lower():
                            cleaned_name = f"{query.title()} ({cleaned_name})"
                        
                        location = ", ".join(parts[1:4]) if len(parts) > 3 else display_name
                        
                        stores_found.append({
                            "name": cleaned_name,
                            "location": location,
                            "distance_km": dist,
                            "latitude": item_lat,
                            "longitude": item_lon
                        })
                    stores_found.sort(key=lambda s: s["distance_km"])
                    return stores_found
        except Exception as e:
            logger.warning(f"OSM Nominatim API request failed or timed out for term '{search_term}': {str(e)}")
            
    # 3. Local Fallback generator (Uses Waterdown locations and clearly marks them as Simulated)
    brand = query.title()
    fallbacks = [
        {"suffix": "Waterdown North (Simulated)", "offset_lat": 0.015, "offset_lon": -0.02, "addr": "123 Simulated Way, Waterdown, ON (Mock)"},
        {"suffix": "Waterdown Plaza (Simulated)", "offset_lat": -0.025, "offset_lon": 0.035, "addr": "456 Mock Road, Waterdown, ON (Mock)"}
    ]
    
    stores_found = []
    for f in fallbacks:
        item_lat = lat + f["offset_lat"]
        item_lon = lon + f["offset_lon"]
        dist = haversine_distance(lat, lon, item_lat, item_lon)
        stores_found.append({
            "name": f"{brand} {f['suffix']}",
            "location": f["addr"],
            "distance_km": dist,
            "latitude": item_lat,
            "longitude": item_lon
        })
        
    stores_found.sort(key=lambda s: s["distance_km"])
    return stores_found

@app.post("/api/stores/add-custom")
def add_custom_store(
    req: StoreCreate, 
    session: Session = Depends(db.get_db), 
    token: Optional[str] = Depends(verify_sync_token)
):
    """
    Adds a custom selected store to the database and seeds it with catalog prices.
    """
    existing = session.query(db.Store).filter(db.Store.name == req.name).first()
    if existing:
        # If coordinates are empty, update them
        if existing.latitude is None and req.latitude is not None:
            existing.latitude = req.latitude
            existing.longitude = req.longitude
            session.commit()
        return {"status": "success", "id": existing.id, "message": "Store already registered."}
        
    new_store = db.Store(
        name=req.name,
        location=req.location,
        distance_km=req.distance_km,
        latitude=req.latitude,
        longitude=req.longitude
    )
    session.add(new_store)
    session.commit()
    session.refresh(new_store)
    
    try:
        scraper.seed_store_prices(session, new_store)
        logger.info(f"Successfully seeded catalog prices for custom store: {new_store.name}")
    except Exception as e:
        logger.error(f"Failed to seed prices for custom store {new_store.name}: {str(e)}")
        
    return {"status": "success", "id": new_store.id, "message": f"Successfully registered and catalog-seeded '{req.name}'."}

@app.get("/api/lists", response_model=List[dict])
def get_lists(session: Session = Depends(db.get_db), token: Optional[str] = Depends(verify_sync_token)):
    lists = session.query(db.GroceryList).all()
    # Create a default list if none exist
    if not lists:
        default_list = db.GroceryList(name="Family Grocery List")
        session.add(default_list)
        session.commit()
        session.refresh(default_list)
        lists = [default_list]
    return [{"id": l.id, "name": l.name, "created_at": l.created_at} for l in lists]

@app.post("/api/lists", response_model=dict)
def create_list(
    lst: ListCreate, 
    session: Session = Depends(db.get_db), 
    token: Optional[str] = Depends(verify_sync_token)
):
    new_list = db.GroceryList(name=lst.name)
    session.add(new_list)
    session.commit()
    session.refresh(new_list)
    return {"id": new_list.id, "name": new_list.name}

@app.get("/api/lists/{list_id}/items")
def get_list_items(
    list_id: int, 
    session: Session = Depends(db.get_db), 
    token: Optional[str] = Depends(verify_sync_token)
):
    lst = session.query(db.GroceryList).filter(db.GroceryList.id == list_id).first()
    if not lst:
        raise HTTPException(status_code=404, detail="List not found")
    
    return [
        {
            "id": item.id,
            "encrypted_name": item.encrypted_name,
            "item_hash": item.item_hash,
            "quantity": item.quantity,
            "category": item.category,
            "added_by": item.added_by,
            "is_completed": item.is_completed,
            "created_at": item.created_at
        }
        for item in lst.items
    ]

@app.post("/api/lists/{list_id}/items")
def add_list_item(
    list_id: int, 
    item: ItemCreate, 
    session: Session = Depends(db.get_db), 
    token: Optional[str] = Depends(verify_sync_token)
):
    lst = session.query(db.GroceryList).filter(db.GroceryList.id == list_id).first()
    if not lst:
        raise HTTPException(status_code=404, detail="List not found")
        
    new_item = db.ListItem(
        list_id=list_id,
        encrypted_name=item.encrypted_name,
        item_hash=item.item_hash,
        quantity=item.quantity,
        category=item.category,
        added_by=item.added_by,
        is_completed=False
    )
    session.add(new_item)
    session.commit()
    session.refresh(new_item)
    return {"id": new_item.id, "status": "success"}

@app.put("/api/items/{item_id}")
def update_list_item(
    item_id: int, 
    item_up: ItemUpdate, 
    session: Session = Depends(db.get_db), 
    token: Optional[str] = Depends(verify_sync_token)
):
    db_item = session.query(db.ListItem).filter(db.ListItem.id == item_id).first()
    if not db_item:
        raise HTTPException(status_code=404, detail="Item not found")
        
    if item_up.is_completed is not None:
        db_item.is_completed = item_up.is_completed
    if item_up.quantity is not None:
        db_item.quantity = item_up.quantity
        
    session.commit()
    return {"status": "success"}

@app.delete("/api/items/{item_id}")
def delete_list_item(
    item_id: int, 
    session: Session = Depends(db.get_db), 
    token: Optional[str] = Depends(verify_sync_token)
):
    db_item = session.query(db.ListItem).filter(db.ListItem.id == item_id).first()
    if not db_item:
        raise HTTPException(status_code=404, detail="Item not found")
        
    session.delete(db_item)
    session.commit()
    return {"status": "success"}

# --- Optimization Endpoint ---
@app.post("/api/optimize")
def run_optimization(
    req: OptimizeRequest, 
    session: Session = Depends(db.get_db), 
    token: Optional[str] = Depends(verify_sync_token)
):
    lst = session.query(db.GroceryList).filter(db.GroceryList.id == req.list_id).first()
    if not lst:
        raise HTTPException(status_code=404, detail="List not found")

    # Get non-completed item hashes
    item_hashes = [item.item_hash for item in lst.items if not item.is_completed]
    
    if not item_hashes:
        return {
            "single_store": {"items_price": 0.0, "total_effective_cost": 0.0, "items": []},
            "two_store": {"items_price": 0.0, "total_effective_cost": 0.0, "items": []},
            "multi_store": {"items_price": 0.0, "total_effective_cost": 0.0, "items": []},
            "recommended": "single_store"
        }

    results = optimizer.optimize_trips(
        db=session,
        item_hashes=item_hashes,
        gas_price=req.gas_price,
        mileage=req.mileage,
        time_value=req.time_value,
        user_lat=req.user_lat,
        user_lon=req.user_lon
    )
    return results

# --- Historical Price Trends (N+1 query resolved) ---
@app.get("/api/prices/history")
def get_price_trends(
    item_hashes: List[str] = Query(None), 
    session: Session = Depends(db.get_db), 
    token: Optional[str] = Depends(verify_sync_token)
):
    if not item_hashes:
        return {}

    # Query all products in a single database query, eager-loading relations
    products = session.query(db.Product)\
        .filter(db.Product.product_hash.in_(item_hashes))\
        .options(
            joinedload(db.Product.store),
            joinedload(db.Product.price_history)
        )\
        .all()

    response = {h: [] for h in item_hashes}
    for p in products:
        h = p.product_hash
        sorted_history = sorted(p.price_history, key=lambda x: x.recorded_at)
        
        response[h].append({
            "store_name": p.store.name if p.store else "Unknown Store",
            "product_name": p.product_name,
            "brand": p.brand,
            "weight": p.weight,
            "category": p.category,
            "current_price": p.price,
            "history": [
                {
                    "price": h_entry.price,
                    "date": h_entry.recorded_at.strftime("%Y-%m-%d")
                }
                for h_entry in sorted_history
            ]
        })
        
    return response

# --- Voice Assistant Webhook Simulator (AES-GCM encrypted additions) ---
@app.post("/api/webhooks/smart-home")
def smart_home_webhook(payload: SmartHomePayload, session: Session = Depends(db.get_db)):
    logger.info(f"Received smart home trigger from {payload.device}: {payload.text}")
    text = payload.text.lower()
    
    # 1. Check if it's a store request
    store_match = re.search(r'(?:add|request|find)\s+(?:store\s+)?([a-z0-9\s\-]+?)\s+(?:near\s+me|nearby|at\s+my\s+location|at\s+my\s+coordinates)', text)
    if not store_match:
        store_match = re.search(r'^add\s+store\s+([a-z0-9\s\-]+)', text)
        
    if store_match:
        store_brand = store_match.group(1).strip().title()
        logger.info(f"Smart home voice request: seeking nearby store for brand '{store_brand}'")
        
        # Call query locally bypassing network HTTP
        found_stores = []
        # Construct fallback coordinates
        fallbacks = [
            {"suffix": "Waterdown North (Simulated)", "offset_lat": 0.015, "offset_lon": -0.02, "addr": "123 Simulated Way, Waterdown, ON (Mock)", "latitude": 43.3483, "longitude": -79.9033},
            {"suffix": "Waterdown Plaza (Simulated)", "offset_lat": -0.025, "offset_lon": 0.035, "addr": "456 Mock Road, Waterdown, ON (Mock)", "latitude": 43.3083, "longitude": -79.8483}
        ]
        for f in fallbacks:
            dist = haversine_distance(43.3333, -79.8833, f["latitude"], f["longitude"])
            found_stores.append({
                "name": f"{store_brand} {f['suffix']}",
                "location": f["addr"],
                "distance_km": dist,
                "latitude": f["latitude"],
                "longitude": f["longitude"]
            })
            
        found_stores.sort(key=lambda s: s["distance_km"])
        nearest = found_stores[0]
        
        # Add store to DB
        existing = session.query(db.Store).filter(db.Store.name == nearest["name"]).first()
        if not existing:
            new_store = db.Store(
                name=nearest["name"],
                location=nearest["location"],
                distance_km=nearest["distance_km"],
                latitude=nearest["latitude"],
                longitude=nearest["longitude"]
            )
            session.add(new_store)
            session.commit()
            session.refresh(new_store)
            
            try:
                scraper.seed_store_prices(session, new_store)
            except Exception as e:
                logger.error(f"Failed to seed prices via webhook for {new_store.name}: {str(e)}")
            message = f"Successfully registered and catalog-seeded '{nearest['name']}'."
        else:
            message = f"Store '{nearest['name']}' is already in your list."
            
        return {
            "status": "success",
            "type": "store_addition",
            "device": payload.device,
            "extracted_store": nearest["name"],
            "location": nearest["location"],
            "distance_km": nearest["distance_km"],
            "message": message
        }

    # 2. Fallback to standard item addition
    item_match = None
    add_match = re.search(r'(?:add|put|buy)\s+(.+?)(?:\s+(?:to|on)\s+my?\s*(?:grocery|shopping)?\s*list)?$', text)
    if add_match:
        item_match = add_match.group(1).strip()
        item_match = re.sub(r'^(?:some|a|an|the)\s+', '', item_match)
        
    if not item_match:
        raise HTTPException(
            status_code=400, 
            detail="Could not extract grocery item. Intent must match 'add [item]' or 'put [item] on list'."
        )

    extracted_canonical = item_match.title()

    # Load defaults
    default_list = session.query(db.GroceryList).first()
    if not default_list:
        default_list = db.GroceryList(name="Family Grocery List")
        session.add(default_list)
        session.commit()
        session.refresh(default_list)

    # Perform E2EE AES-GCM encryption if passphrase has been synced, else fallback to base64
    if default_list.encryption_passphrase:
        try:
            encrypted_name = encrypt_aes_gcm(extracted_canonical, default_list.encryption_passphrase)
        except Exception as e:
            logger.error(f"Failed to perform GCM encryption for webhook item: {str(e)}")
            encrypted_name = f"ENC_{base64.b64encode(extracted_canonical.encode('utf-8')).decode('utf-8')}"
    else:
        encrypted_name = f"ENC_{base64.b64encode(extracted_canonical.encode('utf-8')).decode('utf-8')}"
    
    cleaned_key = "".join(extracted_canonical.lower().split())
    item_hash = hashlib.sha256(cleaned_key.encode("utf-8")).hexdigest()

    category = "Pantry"
    for catalog_item in scraper.GROCERY_ITEMS_CATALOG:
        if catalog_item["name"].lower() in extracted_canonical.lower():
            category = catalog_item["category"]
            break

    new_item = db.ListItem(
        list_id=default_list.id,
        encrypted_name=encrypted_name,
        item_hash=item_hash,
        quantity=1,
        category=category,
        added_by=f"Smart Speaker ({payload.device.capitalize()})",
        is_completed=False
    )
    session.add(new_item)
    session.commit()

    return {
        "status": "success",
        "type": "item_addition",
        "device": payload.device,
        "extracted_item": extracted_canonical,
        "item_hash": item_hash,
        "category": category,
        "payload_logged": {
            "encrypted_name": encrypted_name,
            "item_hash": item_hash
        }
    }

# --- Collaboration Invites ---
@app.post("/api/collaboration/invite")
def create_invite(session: Session = Depends(db.get_db), token: Optional[str] = Depends(verify_sync_token)):
    """
    Generates a secure collaboration invite token code with 16 bytes of entropy and a 30-minute expiration.
    """
    # 16 bytes of cryptographically secure hex entropy
    invite_code = f"INV-{secrets.token_hex(16).upper()}"
    expires_at = datetime.utcnow() + timedelta(minutes=30)
    
    db_invite = db.CollaborationInvite(
        invite_code=invite_code,
        expires_at=expires_at
    )
    session.add(db_invite)
    session.commit()
    
    return {
        "invite_code": invite_code,
        "expires_in_minutes": 30,
        "message": f"Join our Family Sync! Code: {invite_code}"
    }

@app.post("/api/collaboration/join")
def join_invite(invite_code: str = Query(...), session: Session = Depends(db.get_db)):
    """
    Validates the invite code, pairing the device by returning the master sync token and synced passphrase.
    """
    invite = session.query(db.CollaborationInvite).filter(
        db.CollaborationInvite.invite_code == invite_code
    ).first()
    
    if not invite:
        raise HTTPException(status_code=400, detail="Invalid sync invite code.")
        
    if invite.expires_at < datetime.utcnow():
        raise HTTPException(status_code=400, detail="The sync invite code has expired.")
        
    sync_config = session.query(db.SyncConfig).first()
    master_token = sync_config.token_hash if sync_config else ""
    passphrase = sync_config.passphrase if sync_config else ""
    
    # Consume invite token
    session.delete(invite)
    session.commit()
    
    return {
        "status": "success",
        "synced_list_id": 1,
        "master_token": master_token,
        "encryption_passphrase": passphrase,
        "message": "Connected to Family Sync Group successfully."
    }

# --- Background Scraper Execution ---
async def background_scraper_task(selected_stores: list):
    global scraper_status
    scraper_status["status"] = "running"
    scraper_status["logs"] = []
    
    def log_append(msg):
        timestamp = datetime.utcnow().strftime("%H:%M:%S")
        scraper_status["logs"].append(f"[{timestamp}] {msg}")
        logger.info(msg)

    async def log_callback(msg):
        log_append(msg)

    try:
        session = db.SessionLocal()
        added, updated = await scraper.scrape_grocery_prices(
            session, 
            selected_store_names=selected_stores, 
            log_callback=log_callback
        )
        session.close()
        scraper_status["status"] = "success"
        scraper_status["last_run"] = datetime.utcnow().strftime("%Y-%m-%d %H:%M:%S")
        log_append(f"Success! Finished scraping cycle. Synced database.")
    except Exception as e:
        scraper_status["status"] = "error"
        log_append(f"Scraper error encountered: {str(e)}")

@app.post("/api/scraper/run")
def trigger_scraper(
    background_tasks: BackgroundTasks, 
    selected_stores: List[str] = Query(None),
    token: Optional[str] = Depends(verify_sync_token)
):
    global scraper_status
    if scraper_status["status"] == "running":
        return {"status": "busy", "message": "Scraper daemon is already running."}
    
    background_tasks.add_task(background_scraper_task, selected_stores)
    return {"status": "started", "message": "Scraper daemon task queued."}

@app.get("/api/scraper/status")
def get_scraper_status(token: Optional[str] = Depends(verify_sync_token)):
    global scraper_status
    return scraper_status
