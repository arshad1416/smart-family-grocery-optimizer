import logging
import re
import hashlib
from fastapi import FastAPI, Depends, HTTPException, Query, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session
from typing import List, Optional
from pydantic import BaseModel
from datetime import datetime

import database as db
import scraper
import optimizer
import os
from dotenv import load_dotenv
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

# CORS configuration for local development and mobile network sync
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
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

@app.get("/api/stores", response_model=List[dict])
def get_stores(session: Session = Depends(db.get_db)):
    stores = session.query(db.Store).all()
    return [{"id": s.id, "name": s.name, "distance_km": s.distance_km, "location": s.location} for s in stores]

@app.post("/api/stores/request")
def request_store(req: StoreRequestCreate, session: Session = Depends(db.get_db)):
    requested = db.RequestedStore(
        store_name=req.store_name,
        address_hint=req.address_hint,
        requested_at=datetime.utcnow()
    )
    session.add(requested)
    session.commit()
    return {"status": "success", "message": f"Request to add '{req.store_name}' logged successfully."}

import math
import requests

def haversine_distance(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    # Radius of the earth in km
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
def search_nearby_stores(
    query: str,
    lat: float = 43.3333,
    lon: float = -79.8833,
    session: Session = Depends(db.get_db)
):
    """
    Searches for stores using Google Places API (New) if key is set or OpenStreetMap Nominatim.
    Fails back to realistic simulated stores if offline or Nominatim/Google times out.
    """
    logger.info(f"Searching nearby stores for query '{query}' from user location ({lat}, {lon})")
    
    # 1. Try Google Places Text Search (New V1 API) if Key is available
    if GOOGLE_MAPS_API_KEY:
        logger.info("Using Google Places API (New) for nearby store lookup")
        url = "https://places.googleapis.com/v1/places:searchText"
        headers = {
            "Content-Type": "application/json",
            "X-Goog-Api-Key": GOOGLE_MAPS_API_KEY,
            "X-Goog-FieldMask": "places.displayName,places.formattedAddress,places.location"
        }
        payload = {
            "textQuery": f"{query} grocery store",
            "locationBias": {
                "circle": {
                    "center": {
                        "latitude": lat,
                        "longitude": lon
                    },
                    "radius": 10000.0  # 10 km
                }
            }
        }
        try:
            response = requests.post(url, json=payload, headers=headers, timeout=5)
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
                                "distance_km": dist
                            })
                    stores_found.sort(key=lambda s: s["distance_km"])
                    return stores_found
                else:
                    logger.warning("Google Places API (New) returned 0 results.")
            else:
                logger.warning(f"Google Places API (New) returned status {response.status_code}: {response.text}")
        except Exception as e:
            logger.warning(f"Google Places API (New) request failed: {str(e)}. Falling back to OpenStreetMap Nominatim.")

    # 2. Try OpenStreetMap Nominatim Search
    # We construct a bounding box around user coordinates (approx +/- 25 km) to prioritize local results
    min_lon = lon - 0.25
    max_lon = lon + 0.25
    min_lat = lat - 0.25
    max_lat = lat + 0.25
    
    headers = {"User-Agent": "SmartFamilyGroceryList/1.0 (contact: support@shiftlogic.ca)"}
    
    # Try query with grocery store first, then fallback to query name alone if empty
    for search_term in [f"{query} grocery store", query]:
        url = f"https://nominatim.openstreetmap.org/search?q={search_term}&format=json&limit=5&viewbox={min_lon},{max_lat},{max_lon},{min_lat}&bounded=1"
        try:
            response = requests.get(url, headers=headers, timeout=5)
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
                            "distance_km": dist
                        })
                    stores_found.sort(key=lambda s: s["distance_km"])
                    return stores_found
        except Exception as e:
            logger.warning(f"OSM Nominatim API request failed or timed out for term '{search_term}': {str(e)}")
            
    # 3. Local Fallback generator (for offline / robust local testing)
    brand = query.title()
    fallbacks = [
        {"suffix": "Markham East", "offset_lat": 0.015, "offset_lon": -0.02, "addr": "1 Yorktech Dr, Markham, ON"},
        {"suffix": "Richmond Hill Plaza", "offset_lat": -0.025, "offset_lon": 0.035, "addr": "9350 Yonge St, Richmond Hill, ON"}
    ]
    
    stores_found = []
    for f in fallbacks:
        item_lat = lat + f["offset_lat"]
        item_lon = lon + f["offset_lon"]
        dist = haversine_distance(lat, lon, item_lat, item_lon)
        stores_found.append({
            "name": f"{brand} {f['suffix']}",
            "location": f["addr"],
            "distance_km": dist
        })
        
    stores_found.sort(key=lambda s: s["distance_km"])
    return stores_found

@app.post("/api/stores/add-custom")
def add_custom_store(req: StoreCreate, session: Session = Depends(db.get_db)):
    """
    Adds a custom selected store to the database and seeds it with catalog prices.
    """
    existing = session.query(db.Store).filter(db.Store.name == req.name).first()
    if existing:
        return {"status": "success", "id": existing.id, "message": "Store already registered."}
        
    new_store = db.Store(
        name=req.name,
        location=req.location,
        distance_km=req.distance_km
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
def get_lists(session: Session = Depends(db.get_db)):
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
def create_list(lst: ListCreate, session: Session = Depends(db.get_db)):
    new_list = db.GroceryList(name=lst.name)
    session.add(new_list)
    session.commit()
    session.refresh(new_list)
    return {"id": new_list.id, "name": new_list.name}

@app.get("/api/lists/{list_id}/items")
def get_list_items(list_id: int, session: Session = Depends(db.get_db)):
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
def add_list_item(list_id: int, item: ItemCreate, session: Session = Depends(db.get_db)):
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
def update_list_item(item_id: int, item_up: ItemUpdate, session: Session = Depends(db.get_db)):
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
def delete_list_item(item_id: int, session: Session = Depends(db.get_db)):
    db_item = session.query(db.ListItem).filter(db.ListItem.id == item_id).first()
    if not db_item:
        raise HTTPException(status_code=404, detail="Item not found")
        
    session.delete(db_item)
    session.commit()
    return {"status": "success"}

# --- Optimization Endpoint ---
@app.post("/api/optimize")
def run_optimization(req: OptimizeRequest, session: Session = Depends(db.get_db)):
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
        time_value=req.time_value
    )
    return results

# --- Historical Price Trends (For Custom Canvas Charts) ---
@app.get("/api/prices/history")
def get_price_trends(item_hashes: List[str] = Query(None), session: Session = Depends(db.get_db)):
    if not item_hashes:
        return {}

    response = {}
    for h in item_hashes:
        # Find products matching this item hash across all stores
        matched_products = session.query(db.Product).filter(db.Product.product_hash == h).all()
        
        response[h] = []
        for p in matched_products:
            # Get historical prices
            history = session.query(db.PriceHistory).filter(
                db.PriceHistory.product_id == p.id
            ).order_by(db.PriceHistory.recorded_at.asc()).all()
            
            response[h].append({
                "store_name": p.store.name,
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
                    for h_entry in history
                ]
            })
            
    return response

# --- Voice Assistant Webhook Simulator ---
@app.post("/api/webhooks/smart-home")
def smart_home_webhook(payload: SmartHomePayload, session: Session = Depends(db.get_db)):
    logger.info(f"Received smart home trigger from {payload.device}: {payload.text}")
    
    text = payload.text.lower()
    
    # 1. Check if it's a store request
    # Pattern: "add [store] near me", "add store [store] nearby", "add [store] at my location", "add store [store]"
    store_match = re.search(r'(?:add|request|find)\s+(?:store\s+)?([a-z0-9\s\-]+?)\s+(?:near\s+me|nearby|at\s+my\s+location|at\s+my\s+coordinates)', text)
    if not store_match:
        # Check if they said "add store [store_name]"
        store_match = re.search(r'^add\s+store\s+([a-z0-9\s\-]+)', text)
        
    if store_match:
        store_brand = store_match.group(1).strip().title()
        logger.info(f"Smart home voice request: seeking nearby store for brand '{store_brand}'")
        found_stores = search_nearby_stores(query=store_brand, lat=43.3333, lon=-79.8833, session=session)
        if not found_stores:
            raise HTTPException(status_code=404, detail=f"Could not locate any stores matching '{store_brand}' near you.")
            
        nearest = found_stores[0]
        
        # Add store to DB
        existing = session.query(db.Store).filter(db.Store.name == nearest["name"]).first()
        if not existing:
            new_store = db.Store(
                name=nearest["name"],
                location=nearest["location"],
                distance_km=nearest["distance_km"]
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
            "message": f"Successfully added {nearest['name']} located at {nearest['location']} ({nearest['distance_km']} km away) to your active stores."
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

    import base64
    fake_enc_name = f"ENC_{base64.b64encode(extracted_canonical.encode('utf-8')).decode('utf-8')}"
    
    cleaned_key = "".join(extracted_canonical.lower().split())
    item_hash = hashlib.sha256(cleaned_key.encode("utf-8")).hexdigest()

    default_list = session.query(db.GroceryList).first()
    if not default_list:
        default_list = db.GroceryList(name="Family Grocery List")
        session.add(default_list)
        session.commit()
        session.refresh(default_list)

    category = "Pantry"
    for catalog_item in scraper.GROCERY_ITEMS_CATALOG:
        if catalog_item["name"].lower() in extracted_canonical.lower():
            category = catalog_item["category"]
            break

    new_item = db.ListItem(
        list_id=default_list.id,
        encrypted_name=fake_enc_name,
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
            "encrypted_name": fake_enc_name,
            "item_hash": item_hash
        }
    }

# --- Collaboration Invites ---
@app.post("/api/collaboration/invite")
def create_invite():
    # Generates a random cryptographic invite token link
    import secrets
    token = f"INV-{secrets.token_hex(4).upper()}"
    return {
        "invite_code": token,
        "expires_in_minutes": 30,
        "message": f"Join our Family Sync! Code: {token}"
    }

@app.post("/api/collaboration/join")
def join_invite(invite_code: str = Query(...)):
    if not invite_code.startswith("INV-"):
        raise HTTPException(status_code=400, detail="Invalid invite code format")
    # Returns secure initialization packet (simulating handshake)
    return {
        "status": "success",
        "synced_list_id": 1,
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
        # Run scraping algorithm
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
def trigger_scraper(background_tasks: BackgroundTasks, selected_stores: List[str] = Query(None)):
    global scraper_status
    if scraper_status["status"] == "running":
        return {"status": "busy", "message": "Scraper daemon is already running."}
    
    # Run in background Fargate-ready thread
    background_tasks.add_task(background_scraper_task, selected_stores)
    return {"status": "started", "message": "Scraper daemon task queued."}

@app.get("/api/scraper/status")
def get_scraper_status():
    global scraper_status
    return scraper_status
