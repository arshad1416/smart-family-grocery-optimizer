import os
from datetime import datetime
from sqlalchemy import create_engine, Column, Integer, String, Float, Boolean, DateTime, ForeignKey
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker, relationship

# Determine Database URL (local SQLite default, can override with Postgres URL on AWS)
DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./grocery.db")

# Create engine
# SQLite requires check_same_thread=False for FastAPI multithreading
connect_args = {"check_same_thread": False} if DATABASE_URL.startswith("sqlite") else {}
engine = create_engine(DATABASE_URL, connect_args=connect_args)

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    email = Column(String, unique=True, index=True, nullable=False)
    name = Column(String, nullable=True)
    role = Column(String, default="editor")  # owner, editor, viewer
    created_at = Column(DateTime, default=datetime.utcnow)

class Store(Base):
    __tablename__ = "stores"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String, unique=True, index=True, nullable=False)
    location = Column(String, nullable=True)
    distance_km = Column(Float, default=0.0)  # Simulating distance from home
    latitude = Column(Float, nullable=True)
    longitude = Column(Float, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)

    products = relationship("Product", back_populates="store")

class Product(Base):
    __tablename__ = "products"

    id = Column(Integer, primary_key=True, index=True)
    store_id = Column(Integer, ForeignKey("stores.id"), nullable=False)
    product_name = Column(String, nullable=False)  # Raw name for local scraping
    product_hash = Column(String, index=True, nullable=False)  # SHA-256 for E2EE matching
    brand = Column(String, nullable=True)
    price = Column(Float, nullable=False)
    category = Column(String, nullable=True)  # Produce, Dairy, Pantry, etc.
    weight = Column(String, nullable=True)     # e.g. "454g", "1L"
    last_updated = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    store = relationship("Store", back_populates="products")
    price_history = relationship("PriceHistory", back_populates="product", cascade="all, delete-orphan")

class PriceHistory(Base):
    __tablename__ = "price_history"

    id = Column(Integer, primary_key=True, index=True)
    product_id = Column(Integer, ForeignKey("products.id"), nullable=False)
    price = Column(Float, nullable=False)
    recorded_at = Column(DateTime, default=datetime.utcnow)

    product = relationship("Product", back_populates="price_history")

class GroceryList(Base):
    __tablename__ = "lists"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String, nullable=False)
    encryption_passphrase = Column(String, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    items = relationship("ListItem", back_populates="grocery_list", cascade="all, delete-orphan")

class ListItem(Base):
    __tablename__ = "list_items"

    id = Column(Integer, primary_key=True, index=True)
    list_id = Column(Integer, ForeignKey("lists.id"), nullable=False)
    
    # Client-side End-to-End Encrypted (E2EE) item name (AES-GCM Base64 string)
    encrypted_name = Column(String, nullable=False)
    
    # SHA-256 hash of standardized name (e.g. sha256("organicbananas")) for zero-knowledge matching
    item_hash = Column(String, index=True, nullable=False)
    
    quantity = Column(Integer, default=1)
    category = Column(String, default="Pantry")  # Locally decrypted category metadata
    added_by = Column(String, nullable=True)      # Encrypted user name / identifier
    is_completed = Column(Boolean, default=False)
    created_at = Column(DateTime, default=datetime.utcnow)

    grocery_list = relationship("GroceryList", back_populates="items")

class RequestedStore(Base):
    __tablename__ = "requested_stores"

    id = Column(Integer, primary_key=True, index=True)
    store_name = Column(String, nullable=False)
    address_hint = Column(String, nullable=True)
    requested_at = Column(DateTime, default=datetime.utcnow)

class SyncConfig(Base):
    __tablename__ = "sync_config"

    id = Column(Integer, primary_key=True, index=True)
    token_hash = Column(String, unique=True, index=True, nullable=False)
    passphrase = Column(String, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)

class CollaborationInvite(Base):
    __tablename__ = "collaboration_invites"

    id = Column(Integer, primary_key=True, index=True)
    invite_code = Column(String, unique=True, index=True, nullable=False)
    expires_at = Column(DateTime, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow)

# Initialize database
def init_db():
    Base.metadata.create_all(bind=engine)

# Dependency to get db session
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
