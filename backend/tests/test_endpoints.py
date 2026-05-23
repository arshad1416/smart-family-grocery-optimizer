import pytest
from fastapi.testclient import TestClient
import sqlalchemy
from sqlalchemy.orm import sessionmaker

from main import app
import database as db
import scraper
import optimizer

client = TestClient(app)

# Use in-memory SQLite database for testing
TEST_DATABASE_URL = "sqlite:///./test_grocery.db"
engine = sqlalchemy.create_engine(TEST_DATABASE_URL, connect_args={"check_same_thread": False})
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

# Override get_db dependency
def override_get_db():
    try:
        database = TestingSessionLocal()
        yield database
    finally:
        database.close()

app.dependency_overrides[db.get_db] = override_get_db

@pytest.fixture(scope="module", autouse=True)
def setup_test_db():
    # Setup test tables
    db.Base.metadata.create_all(bind=engine)
    
    # Pre-populate stores & products
    session = TestingSessionLocal()
    
    # 1. Create stores
    store_walmart = db.Store(name="Walmart Supercentre", location="Test Location A", distance_km=2.5)
    store_nofrills = db.Store(name="No Frills", location="Test Location B", distance_km=1.5)
    session.add_all([store_walmart, store_nofrills])
    session.commit()
    
    # 2. Add products
    # Milk in Walmart is cheaper ($8.20) vs No Frills ($8.50)
    # Eggs in No Frills cheaper ($4.50) vs Walmart ($4.90)
    milk_hash = scraper.get_product_hash("Organic Milk 3.25%")
    eggs_hash = scraper.get_product_hash("Large Eggs Brown")
    
    p_milk_wm = db.Product(
        store_id=store_walmart.id, product_name="Organic Milk 3.25%", product_hash=milk_hash,
        brand="Organic Meadow", price=8.20, category="Dairy", weight="4L"
    )
    p_milk_nf = db.Product(
        store_id=store_nofrills.id, product_name="Organic Milk 3.25%", product_hash=milk_hash,
        brand="Organic Meadow", price=8.50, category="Dairy", weight="4L"
    )
    
    p_eggs_wm = db.Product(
        store_id=store_walmart.id, product_name="Large Eggs Brown", product_hash=eggs_hash,
        brand="Burnbrae", price=4.90, category="Dairy", weight="12-pack"
    )
    p_eggs_nf = db.Product(
        store_id=store_nofrills.id, product_name="Large Eggs Brown", product_hash=eggs_hash,
        brand="Burnbrae", price=4.50, category="Dairy", weight="12-pack"
    )
    
    session.add_all([p_milk_wm, p_milk_nf, p_eggs_wm, p_eggs_nf])
    session.commit()
    
    # Create default list
    default_list = db.GroceryList(name="Family Grocery List")
    session.add(default_list)
    session.commit()
    
    session.close()
    
    yield
    
    # Tear down
    db.Base.metadata.drop_all(bind=engine)
    import os
    if os.path.exists("./test_grocery.db"):
        os.remove("./test_grocery.db")

def test_get_stores():
    response = client.get("/api/stores")
    assert response.status_code == 200
    data = response.json()
    assert len(data) >= 2
    assert any(s["name"] == "Walmart Supercentre" for s in data)

def test_get_lists():
    response = client.get("/api/lists")
    assert response.status_code == 200
    assert len(response.json()) == 1

def test_add_list_item():
    # Milk
    milk_hash = scraper.get_product_hash("Organic Milk 3.25%")
    response = client.post(
        "/api/lists/1/items",
        json={
            "encrypted_name": "ENC_MILK_BLOB",
            "item_hash": milk_hash,
            "quantity": 1,
            "category": "Dairy",
            "added_by": "TestUser"
        }
    )
    assert response.status_code == 200
    assert response.json()["status"] == "success"

    # Eggs
    eggs_hash = scraper.get_product_hash("Large Eggs Brown")
    response_eggs = client.post(
        "/api/lists/1/items",
        json={
            "encrypted_name": "ENC_EGGS_BLOB",
            "item_hash": eggs_hash,
            "quantity": 2,
            "category": "Dairy",
            "added_by": "TestUser"
        }
    )
    assert response_eggs.status_code == 200

    # Get items
    response_items = client.get("/api/lists/1/items")
    assert response_items.status_code == 200
    items = response_items.json()
    assert len(items) == 2
    assert any(i["encrypted_name"] == "ENC_MILK_BLOB" for i in items)

def test_smart_home_webhook():
    # Say Siri voice input: "Hey Siri, add Organic Milk 3.25% to grocery list"
    response = client.post(
        "/api/webhooks/smart-home",
        json={
            "device": "siri",
            "text": "add Organic Milk 3.25%"
        }
    )
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "success"
    assert data["extracted_item"] == "Organic Milk 3.25%"
    assert data["device"] == "siri"

def test_trip_optimization():
    # Run optimization on our items (Milk and Eggs added in previous tests)
    # Gas Price: 1.50, Mileage: 10.0, Time Value: 20.0
    response = client.post(
        "/api/optimize",
        json={
            "list_id": 1,
            "gas_price": 1.50,
            "mileage": 10.0,
            "time_value": 20.0
        }
    )
    assert response.status_code == 200
    data = response.json()
    
    # Must contain single, two, and multi-store optimization trees
    assert "single_store" in data
    assert "two_store" in data
    assert "multi_store" in data
    assert "recommended" in data
    
    # Milk is cheaper at Walmart ($8.20), Eggs cheaper at No Frills ($4.50)
    # Verify multi-store splits properly
    multi_items = data["multi_store"]["items"]
    assert len(multi_items) == 3
    milk_entry = next(i for i in multi_items if "Milk" in i["product_name"])
    eggs_entry = next(i for i in multi_items if "Eggs" in i["product_name"])
    
    assert milk_entry["store_name"] == "Walmart Supercentre"
    assert eggs_entry["store_name"] == "No Frills"

def test_collaboration_invite():
    response = client.post("/api/collaboration/invite")
    assert response.status_code == 200
    data = response.json()
    assert "invite_code" in data
    assert data["invite_code"].startswith("INV-")

def test_search_nearby_stores():
    response = client.get("/api/stores/search-nearby?query=Costco&lat=43.85&lon=-79.33")
    assert response.status_code == 200
    data = response.json()
    assert len(data) >= 2
    assert any("Costco" in s["name"] for s in data)

def test_add_custom_store():
    response = client.post(
        "/api/stores/add-custom",
        json={
            "name": "Costco Markham East Test",
            "location": "1 Yorktech Dr, Markham, ON",
            "distance_km": 2.31
        }
    )
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "success"
    
    # Query stores to verify
    response_stores = client.get("/api/stores")
    assert response_stores.status_code == 200
    stores = response_stores.json()
    assert any(s["name"] == "Costco Markham East Test" for s in stores)

def test_smart_home_webhook_store_addition():
    response = client.post(
        "/api/webhooks/smart-home",
        json={
            "device": "google-home",
            "text": "add Costco near me"
        }
    )
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "success"
    assert data["type"] == "store_addition"
    assert "Costco" in data["extracted_store"]

from unittest.mock import patch

def test_google_places_api_search_mocking():
    mock_response_data = {
        "places": [
            {
                "displayName": {
                    "text": "Google Places Costco Test"
                },
                "formattedAddress": "100 Google Way, Toronto, ON",
                "location": {
                    "latitude": 43.85,
                    "longitude": -79.33
                }
            }
        ]
    }
    
    with patch("main.GOOGLE_MAPS_API_KEY", "MOCK_KEY"), \
         patch("requests.post") as mock_post:
        
        mock_post.return_value.status_code = 200
        mock_post.return_value.json.return_value = mock_response_data
        
        response = client.get("/api/stores/search-nearby?query=Costco&lat=43.85&lon=-79.33")
        
        assert response.status_code == 200
        data = response.json()
        assert len(data) == 1
        assert data[0]["name"] == "Google Places Costco Test"
        assert data[0]["location"] == "100 Google Way, Toronto, ON"
        assert data[0]["distance_km"] == 0.0
