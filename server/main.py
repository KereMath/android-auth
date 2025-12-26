from fastapi import FastAPI, HTTPException, Depends
from pydantic import BaseModel
from sqlalchemy import create_engine, Column, Integer, String, Boolean
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker, Session
import pyotp
import os

# --- Database ---
DATABASE_URL = os.getenv("DATABASE_URL", "postgresql://user:password@localhost:11001/authdb")

engine = create_engine(DATABASE_URL)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

class User(Base):
    __tablename__ = "users"
    id = Column(Integer, primary_key=True, index=True)
    email = Column(String, unique=True, index=True)
    totp_secret = Column(String, nullable=True)
    is_verified = Column(Boolean, default=False)

def init_db():
    Base.metadata.create_all(bind=engine)

# --- FastAPI ---
app = FastAPI()

# Dependency
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

@app.on_event("startup")
def on_startup():
    # Wait for DB? For simplicity, we assume it's up or we retry. 
    # Docker entrypoint usually handles valid wait logic or restarts.
    try:
        init_db()
    except Exception as e:
        print(f"DB not ready yet: {e}")

# --- Models ---
class AuthRequest(BaseModel):
    id_token: str  # Google ID Token (mocked for now or real)
    email: str      # In real flow, we extract this from token

class VerifyRequest(BaseModel):
    email: str
    code: str

# --- Endpoints ---

@app.get("/")
def read_root():
    return {"status": "running"}

@app.post("/auth/google")
def google_auth(req: AuthRequest, db: Session = Depends(get_db)):
    # TODO: Verify google ID token here using google-auth
    # For now, we trust the email sent for the prototype logic flow
    email = req.email.lower()
    
    # Mock check: only accept specific email
    if "keremrgur@gmail.com" not in email:
        raise HTTPException(status_code=403, detail="Unauthorized email")

    user = db.query(User).filter(User.email == email).first()
    if not user:
        user = User(email=email)
        db.add(user)
        db.commit()
        db.refresh(user)
    
    
    # Logic Change: Always require TOTP verification on login
    # Even if they verified before, this is a new session
    # We return is_verified=False so the client asks for code
    # We return has_secret=True so the client knows to only ask for code (not setup)
    
    return {
        "status": "success",
        "email": user.email,
        "is_verified": False, # Force check
        "has_secret": user.totp_secret is not None
    }

@app.post("/totp/generate")
def generate_totp(email: str, db: Session = Depends(get_db)):
    user = db.query(User).filter(User.email == email).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    
    if user.totp_secret:
        # User already has a secret, return it? Or regenerate?
        # Let's keep it for now.
        secret = user.totp_secret
    else:
        secret = pyotp.random_base32()
        user.totp_secret = secret
        db.commit()
    
    uri = pyotp.totp.TOTP(secret).provisioning_uri(name=email, issuer_name="MyApp")
    
    return {
        "secret": secret,
        "otpauth_url": uri
    }

@app.post("/totp/verify")
def verify_totp(req: VerifyRequest, db: Session = Depends(get_db)):
    user = db.query(User).filter(User.email == req.email).first()
    if not user or not user.totp_secret:
        raise HTTPException(status_code=400, detail="User setup incomplete")
    
    totp = pyotp.TOTP(user.totp_secret)
    if totp.verify(req.code):
        user.is_verified = True
        db.commit()
        return {"status": "verified"}
    else:
        raise HTTPException(status_code=401, detail="Invalid code")
