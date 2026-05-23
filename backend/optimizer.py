import itertools
import math
from sqlalchemy.orm import Session
from database import Store, Product

# Driving speed assumption in Toronto/Ontario suburb (km/h)
AVG_DRIVING_SPEED_KMH = 40.0
# Base shopping time overhead per store visited (hours)
SHOPPING_OVERHEAD_HR = 0.50 

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
    
    return R * c

def calculate_trip_costs(
    stores_visited: list,
    gas_price: float,
    mileage: float,
    time_value: float,
    user_lat: float = 43.3333,
    user_lon: float = -79.8833
):
    """
    Computes total travel distance, fuel cost, and time cost for visiting a sequence of stores.
    Calculates the exact shortest 2D path (TSP) starting at home, visiting all stores, and returning home.
    """
    if not stores_visited:
        return 0.0, 0.0, 0.0, 0.0

    best_distance = float('inf')
    
    # Check all permutations to find the shortest TSP path
    for perm in itertools.permutations(stores_visited):
        current_distance = 0.0
        current_lat, current_lon = user_lat, user_lon
        
        for store in perm:
            store_lat = store.latitude if store.latitude is not None else user_lat
            store_lon = store.longitude if store.longitude is not None else user_lon
            current_distance += haversine_distance(current_lat, current_lon, store_lat, store_lon)
            current_lat, current_lon = store_lat, store_lon
            
        current_distance += haversine_distance(current_lat, current_lon, user_lat, user_lon)
        
        if current_distance < best_distance:
            best_distance = current_distance

    total_distance_km = round(best_distance, 2)

    # Fuel Cost = (L / 100km) * total_km * price_per_L
    fuel_cost = (mileage / 100.0) * total_distance_km * gas_price
    
    # Time spent: Driving time + Shopping overhead per store
    driving_time_hr = total_distance_km / AVG_DRIVING_SPEED_KMH
    shopping_time_hr = len(stores_visited) * SHOPPING_OVERHEAD_HR
    total_time_hr = driving_time_hr + shopping_time_hr
    
    # Time Cost = hours * user_hourly_rate
    time_cost = total_time_hr * time_value
    
    return round(total_distance_km, 1), round(fuel_cost, 2), round(total_time_hr, 2), round(time_cost, 2)

def optimize_trips(
    db: Session,
    item_hashes: list,
    gas_price: float = 1.55,      # CAD per Liter
    mileage: float = 8.5,          # L/100km
    time_value: float = 25.0,      # CAD per hour
    user_lat: float = 43.3333,
    user_lon: float = -79.8833
):
    """
    Calculates optimal store trips based on three options.
    Returns:
    - Single-Store Convenience
    - Two-Store Balance
    - Absolute Cheapest (Multi-Store)
    """
    if not item_hashes:
        return {"single_store": {}, "two_store": {}, "multi_store": {}}

    # Fetch all stores
    all_stores = db.query(Store).all()
    if not all_stores:
        return {}

    # Query all matches for these item hashes
    products = db.query(Product).filter(Product.product_hash.in_(item_hashes)).all()
    
    # Group products by hash
    products_by_hash = {}
    for p in products:
        if p.product_hash not in products_by_hash:
            products_by_hash[p.product_hash] = []
        products_by_hash[p.product_hash].append(p)

    # 1. OPTION A: SINGLE STORE CONVENIENCE
    single_store_options = []
    for store in all_stores:
        items_bought = []
        unmatched_items = 0
        total_items_price = 0.0

        for h in item_hashes:
            matched_products = products_by_hash.get(h, [])
            store_product = next((p for p in matched_products if p.store_id == store.id), None)
            if store_product:
                total_items_price += store_product.price
                items_bought.append({
                    "item_hash": h,
                    "product_name": store_product.product_name,
                    "price": store_product.price,
                    "store_name": store.name
                })
            else:
                unmatched_items += 1

        dist, fuel, time_hr, time_val_cost = calculate_trip_costs([store], gas_price, mileage, time_value, user_lat, user_lon)
        total_cost = total_items_price + fuel + time_val_cost

        single_store_options.append({
            "store_id": store.id,
            "store_name": store.name,
            "items_price": round(total_items_price, 2),
            "distance_km": dist,
            "fuel_cost": fuel,
            "travel_time_hr": time_hr,
            "time_value_cost": time_val_cost,
            "total_effective_cost": round(total_cost, 2),
            "items": items_bought,
            "unmatched_count": unmatched_items
        })

    best_single_store = sorted(single_store_options, key=lambda x: (x["unmatched_count"], x["total_effective_cost"]))[0]

    # 2. OPTION B: TWO-STORE BALANCE
    two_store_options = []
    for store_pair in itertools.combinations(all_stores, 2):
        store1, store2 = store_pair
        items_bought = []
        total_items_price = 0.0
        unmatched_items = 0

        for h in item_hashes:
            matched_products = products_by_hash.get(h, [])
            p1 = next((p for p in matched_products if p.store_id == store1.id), None)
            p2 = next((p for p in matched_products if p.store_id == store2.id), None)

            if p1 and p2:
                cheaper = p1 if p1.price <= p2.price else p2
                total_items_price += cheaper.price
                items_bought.append({
                    "item_hash": h,
                    "product_name": cheaper.product_name,
                    "price": cheaper.price,
                    "store_name": cheaper.store.name
                })
            elif p1:
                total_items_price += p1.price
                items_bought.append({
                    "item_hash": h,
                    "product_name": p1.product_name,
                    "price": p1.price,
                    "store_name": store1.name
                })
            elif p2:
                total_items_price += p2.price
                items_bought.append({
                    "item_hash": h,
                    "product_name": p2.product_name,
                    "price": p2.price,
                    "store_name": store2.name
                })
            else:
                unmatched_items += 1

        dist, fuel, time_hr, time_val_cost = calculate_trip_costs(list(store_pair), gas_price, mileage, time_value, user_lat, user_lon)
        total_cost = total_items_price + fuel + time_val_cost

        two_store_options.append({
            "stores": [s.name for s in store_pair],
            "items_price": round(total_items_price, 2),
            "distance_km": dist,
            "fuel_cost": fuel,
            "travel_time_hr": time_hr,
            "time_value_cost": time_val_cost,
            "total_effective_cost": round(total_cost, 2),
            "items": items_bought,
            "unmatched_count": unmatched_items
        })

    best_two_store = sorted(two_store_options, key=lambda x: (x["unmatched_count"], x["total_effective_cost"]))[0] if two_store_options else best_single_store

    # 3. OPTION C: ABSOLUTE CHEAPEST (MULTI-STORE)
    cheapest_items = []
    stores_to_visit_ids = set()
    total_cheapest_price = 0.0
    unmatched_cheapest = 0

    for h in item_hashes:
        matched_products = products_by_hash.get(h, [])
        if matched_products:
            cheapest_product = min(matched_products, key=lambda p: p.price)
            total_cheapest_price += cheapest_product.price
            stores_to_visit_ids.add(cheapest_product.store_id)
            cheapest_items.append({
                "item_hash": h,
                "product_name": cheapest_product.product_name,
                "price": cheapest_product.price,
                "store_name": cheapest_product.store.name
            })
        else:
            unmatched_cheapest += 1

    visited_stores = [s for s in all_stores if s.id in stores_to_visit_ids]
    dist, fuel, time_hr, time_val_cost = calculate_trip_costs(visited_stores, gas_price, mileage, time_value, user_lat, user_lon)
    total_cost = total_cheapest_price + fuel + time_val_cost

    best_multi_store = {
        "stores": [s.name for s in visited_stores],
        "items_price": round(total_cheapest_price, 2),
        "distance_km": dist,
        "fuel_cost": fuel,
        "travel_time_hr": time_hr,
        "time_value_cost": time_val_cost,
        "total_effective_cost": round(total_cost, 2),
        "items": cheapest_items,
        "unmatched_count": unmatched_cheapest
    }

    # Decide on Recommended Option
    recommendation = "single_store"
    min_effective_cost = best_single_store["total_effective_cost"]
    
    if best_two_store["total_effective_cost"] < min_effective_cost:
        recommendation = "two_store"
        min_effective_cost = best_two_store["total_effective_cost"]
    
    if best_multi_store["total_effective_cost"] < min_effective_cost:
        recommendation = "multi_store"

    return {
        "single_store": best_single_store,
        "two_store": best_two_store,
        "multi_store": best_multi_store,
        "recommended": recommendation
    }
