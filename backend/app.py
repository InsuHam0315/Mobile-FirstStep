"""FirstStep API: local MySQL-backed identity and isolated progress."""
import base64, binascii, hashlib, hmac, io, json, math, os, re, secrets, time
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from pathlib import Path
from threading import Lock

from dotenv import load_dotenv
from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from google.auth.transport import requests as google_requests
from google.oauth2 import id_token as google_id_token
from PIL import Image
from argon2 import PasswordHasher
from argon2.exceptions import VerifyMismatchError, VerificationError, InvalidHashError
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import (BigInteger, Boolean, Column, DateTime, ForeignKey, Integer, String, UniqueConstraint, create_engine, select, text)
from sqlalchemy.dialects.mysql import MEDIUMBLOB, insert as mysql_insert
from sqlalchemy.orm import declarative_base, sessionmaker
from sqlalchemy.exc import SQLAlchemyError

load_dotenv(Path(__file__).with_name('.env'))
DATABASE_URL = os.environ.get('DATABASE_URL', '')
if not DATABASE_URL.startswith('mysql+pymysql://'):
    raise RuntimeError('DATABASE_URL must use mysql+pymysql')
GOOGLE_CLIENT_ID = os.environ.get('GOOGLE_WEB_CLIENT_ID', '').strip()
DEV_ENABLED = os.environ.get('DEVELOPMENT_LOGIN_ENABLED', '').lower() == 'true'
engine = create_engine(DATABASE_URL, pool_pre_ping=True, pool_recycle=1800, hide_parameters=True)
Session = sessionmaker(bind=engine, expire_on_commit=False)
Base = declarative_base()

def utcnow(): return datetime.utcnow()

class User(Base):
    __tablename__ = 'users'
    id = Column(Integer, primary_key=True)
    provider = Column(String(20), nullable=False)
    identity = Column(String(255), nullable=False)
    nickname = Column(String(80), nullable=False)
    email = Column(String(255), nullable=True)
    password_hash = Column(String(255), nullable=True)
    __table_args__ = (UniqueConstraint('provider', 'identity'),)

class Token(Base):
    __tablename__ = 'tokens'
    digest = Column(String(64), primary_key=True)
    user_id = Column(Integer, ForeignKey('users.id', ondelete='CASCADE'), nullable=False)
    expires_at = Column(DateTime, nullable=False)

class Challenge(Base):
    __tablename__ = 'google_challenges'
    id = Column(String(64), primary_key=True)
    nonce_hash = Column(String(64), nullable=False)
    expires_at = Column(DateTime, nullable=False)
    used = Column(Boolean, nullable=False, default=False)

class ModeState(Base):
    __tablename__ = 'mode_states'
    user_id = Column(Integer, ForeignKey('users.id', ondelete='CASCADE'), primary_key=True)
    mode = Column(String(5), primary_key=True)
    generation = Column(Integer, nullable=False, default=0)

class MissionRow(Base):
    __tablename__ = 'missions'
    id = Column(String(32), primary_key=True)
    mission_type = Column(String(32), nullable=False)
    position = Column(Integer, nullable=False)
    coordinate_verified = Column(Boolean, nullable=False)

class Completion(Base):
    __tablename__ = 'completions'
    id = Column(Integer, primary_key=True)
    user_id = Column(Integer, ForeignKey('users.id', ondelete='CASCADE'), nullable=False)
    mode = Column(String(5), nullable=False)
    mission_id = Column(String(32), ForeignKey('missions.id'), nullable=False)
    saved_at = Column(BigInteger, nullable=False)
    discovery_method = Column(String(12), nullable=False)
    completion_method = Column(String(20), nullable=False)
    photo = Column(MEDIUMBLOB, nullable=True)
    __table_args__ = (UniqueConstraint('user_id','mode','mission_id'),)

def verified_coordinates(mission):
    latitude=mission.get('latitude'); longitude=mission.get('longitude')
    return (mission.get('coordinateVerified') is True and type(latitude) in (int,float)
            and type(longitude) in (int,float) and math.isfinite(latitude) and math.isfinite(longitude)
            and -90<=latitude<=90 and -180<=longitude<=180)

@asynccontextmanager
async def lifespan(app):
    Base.metadata.create_all(engine)
    with Session.begin() as db:
        for mission in CATALOG:
            stmt=mysql_insert(MissionRow).values(id=mission['id'],mission_type=mission['type'],position=mission['order'],coordinate_verified=verified_coordinates(mission))
            db.execute(stmt.on_duplicate_key_update(mission_type=stmt.inserted.mission_type,position=stmt.inserted.position,coordinate_verified=stmt.inserted.coordinate_verified))
    yield
    engine.dispose()
app = FastAPI(lifespan=lifespan)
@app.exception_handler(RequestValidationError)
async def invalid_request(request, exc):
    return JSONResponse({'detail':'Invalid request'},status_code=422)

@app.exception_handler(SQLAlchemyError)
async def database_error(request, exc):
    return JSONResponse({'detail':'Database unavailable'},status_code=503)

class BoundedGoogleRequest(google_requests.Request):
    def __call__(self, url, method='GET', body=None, headers=None, timeout=10, **kwargs):
        return super().__call__(url,method=method,body=body,headers=headers,timeout=10,**kwargs)

MAX_BODY=12*1024*1024
class BodyLimitMiddleware:
    def __init__(self, app): self.app=app
    async def __call__(self, scope, receive, send):
        if scope['type'] != 'http': return await self.app(scope,receive,send)
        headers=dict(scope.get('headers',[])); length=headers.get(b'content-length')
        if length:
            try:
                if int(length)<0 or int(length)>MAX_BODY: raise ValueError()
            except ValueError:
                return await JSONResponse({'detail':'Request too large'},status_code=413)(scope,receive,send)
        chunks=[]; size=0
        while True:
            event=await receive()
            if event['type']=='http.disconnect': return
            if event['type']!='http.request': continue
            chunk=event.get('body',b''); size+=len(chunk)
            if size>MAX_BODY:
                return await JSONResponse({'detail':'Request too large'},status_code=413)(scope,receive,send)
            chunks.append(chunk)
            if not event.get('more_body',False): break
        body=b''.join(chunks); sent=False
        async def replay():
            nonlocal sent
            if not sent:
                sent=True; return {'type':'http.request','body':body,'more_body':False}
            return await receive()
        await self.app(scope,replay,send)
app.add_middleware(BodyLimitMiddleware)

class Input(BaseModel):
    model_config=ConfigDict(extra='forbid', strict=True)
class Register(Input):
    username: str = Field(min_length=3,max_length=64)
    password: str = Field(min_length=10,max_length=128)
    nickname: str = Field(min_length=1,max_length=40)
class Login(Input):
    username: str = Field(min_length=1,max_length=64)
    password: str = Field(min_length=1,max_length=128)
class GoogleLogin(Input):
    id_token: str = Field(min_length=1,max_length=20000)
    challenge_id: str = Field(min_length=1,max_length=64)
class CompletionInput(Input):
    mission_id: str = Field(min_length=1,max_length=32)
    saved_at: int = Field(ge=0,le=4102444800000)
    discovery_method: str
    completion_method: str
    photo_base64: str|None = Field(default=None,max_length=2800000)
class SyncInput(Input):
    generation: int = Field(ge=0,le=2147483646)
    completions: list[CompletionInput] = Field(max_length=5)

_rate={}; _rate_lock=Lock()
def auth_limit(request: Request):
    key=request.client.host if request.client else 'unknown'; now=time.monotonic()
    with _rate_lock:
        # Keep bounded by removing stale entries; this is per-process, not distributed.
        for k in list(_rate):
            if not _rate[k] or now-_rate[k][-1]>60: del _rate[k]
        if key not in _rate and len(_rate)>=10000: raise HTTPException(429,'Too many attempts')
        hits=_rate.setdefault(key,[]); hits[:]=[v for v in hits if now-v<60]
        if len(hits)>=20: raise HTTPException(429,'Too many attempts')
        hits.append(now)

password_hasher=PasswordHasher()
def hash_password(password): return password_hasher.hash(password)
def check_password(password, encoded):
    try: return password_hasher.verify(encoded,password)
    except (VerifyMismatchError,VerificationError,InvalidHashError,TypeError): return False
DUMMY_PASSWORD=hash_password('placeholder-password')
def digest(value): return hashlib.sha256(value.encode()).hexdigest()
def profile(user):
    result={'id':user.id,'nickname':user.nickname,'provider':user.provider}
    if user.email: result['email']=user.email
    return result

def issue(session,user):
    token=secrets.token_urlsafe(32)
    session.add(Token(digest=digest(token),user_id=user.id,expires_at=utcnow()+timedelta(days=30)))
    session.commit()
    return {'access_token':token,'user':profile(user)}

def authenticated(authorization: str|None=Header(default=None)):
    if not authorization or not authorization.startswith('Bearer '): raise HTTPException(401,'Authentication required')
    token=authorization[7:]
    if not token or len(token)>256: raise HTTPException(401,'Authentication required')
    with Session() as db:
        row=db.get(Token,digest(token))
        if not row or row.expires_at<=utcnow(): raise HTTPException(401,'Authentication required')
        user=db.get(User,row.user_id)
        if not user: raise HTTPException(401,'Authentication required')
        return (user.id,digest(token))

def require_dev():
    if not DEV_ENABLED: raise HTTPException(404,'Not available')
def username_valid(name): return bool(re.fullmatch(r'[A-Za-z0-9_.-]{3,64}',name))

@app.get('/health')
def health():
    with engine.connect() as c:
        if c.execute(text('SELECT 1')).scalar_one()!=1: raise HTTPException(503,'Database unavailable')
    return {'status':'ok','database':'mysql'}
@app.get('/v1/config')
def config(): return {'google_client_id':GOOGLE_CLIENT_ID,'google_enabled':bool(GOOGLE_CLIENT_ID),'development_login_enabled':DEV_ENABLED,'school_sso_enabled':False}
@app.post('/v1/auth/development/register',dependencies=[Depends(auth_limit)])
def register(data:Register):
    require_dev(); name=data.username.lower(); nickname=data.nickname.strip()
    if not username_valid(name) or not nickname: raise HTTPException(400,'Invalid registration')
    with Session() as db:
        try:
            user=User(provider='DEVELOPMENT',identity=name,nickname=nickname,password_hash=hash_password(data.password)); db.add(user); db.flush()
            return issue(db,user)
        except Exception as e:
            from sqlalchemy.exc import IntegrityError
            db.rollback()
            if isinstance(e,IntegrityError): raise HTTPException(409,'Account unavailable')
            raise
@app.post('/v1/auth/development/login',dependencies=[Depends(auth_limit)])
def login(data:Login):
    require_dev()
    with Session() as db:
        user=db.scalar(select(User).where(User.provider=='DEVELOPMENT',User.identity==data.username.lower()))
        valid=check_password(data.password,user.password_hash if user else DUMMY_PASSWORD)
        if not user or not valid: raise HTTPException(401,'Invalid credentials')
        return issue(db,user)
@app.post('/v1/auth/google/challenge',dependencies=[Depends(auth_limit)])
def challenge():
    if not GOOGLE_CLIENT_ID: raise HTTPException(404,'Not available')
    ident=secrets.token_urlsafe(32); nonce=secrets.token_urlsafe(32)
    with Session.begin() as db: db.add(Challenge(id=ident,nonce_hash=digest(nonce),expires_at=utcnow()+timedelta(minutes=5),used=False))
    return {'challenge_id':ident,'nonce':nonce}
@app.post('/v1/auth/google',dependencies=[Depends(auth_limit)])
def google_login(data:GoogleLogin):
    if not GOOGLE_CLIENT_ID: raise HTTPException(404,'Not available')
    try:
        claims=google_id_token.verify_oauth2_token(data.id_token,BoundedGoogleRequest(),GOOGLE_CLIENT_ID)
        if not isinstance(claims.get('sub'),str) or len(claims['sub'])>255 or not isinstance(claims.get('email'),str) or len(claims['email'])>255 or not isinstance(claims.get('nonce'),str) or len(claims['nonce'])>256: raise ValueError('claims')
        if not isinstance(claims.get('exp'),int) or claims['exp']<=int(time.time()): raise ValueError('expired')
        if claims.get('iss') not in ('accounts.google.com','https://accounts.google.com') or claims.get('aud') != GOOGLE_CLIENT_ID or claims.get('email_verified') is not True or not claims.get('sub') or not claims.get('nonce') or not claims.get('email'): raise ValueError('claims')
    except Exception: raise HTTPException(401,'Invalid identity token')
    with Session() as db:
        challenge=db.scalar(select(Challenge).where(Challenge.id==data.challenge_id).with_for_update())
        if not challenge or challenge.used or challenge.expires_at<=utcnow() or not hmac.compare_digest(challenge.nonce_hash,digest(str(claims['nonce']))): raise HTTPException(401,'Invalid identity token')
        challenge.used=True
        # A unique provider/subject and upsert prevent concurrent account duplication.
        sub=claims['sub']; email=claims['email']
        nickname=(str(claims.get('name') or email.split('@')[0]).strip() or '사용자')[:80]
        stmt=mysql_insert(User).values(provider='GOOGLE',identity=sub,email=email,nickname=nickname)
        db.execute(stmt.on_duplicate_key_update(identity=stmt.inserted.identity))
        user=db.scalar(select(User).where(User.provider=='GOOGLE',User.identity==sub))
        return issue(db,user)
@app.get('/v1/me')
def me(auth=Depends(authenticated)):
    with Session() as db: return profile(db.get(User,auth[0]))
@app.post('/v1/auth/logout')
def logout(auth=Depends(authenticated)):
    with Session.begin() as db:
        token=db.get(Token,auth[1])
        if token: db.delete(token)
    return {'status':'ok'}

CATALOG=json.loads((Path(__file__).resolve().parent.parent/'app/src/main/assets/missions.json').read_text(encoding='utf-8-sig'))
CATALOG=sorted(CATALOG,key=lambda m:m['order'])
MISSIONS={m['id']:m for m in CATALOG}
ALLOWED={'PHOTO_QUIZ':{'PHOTO','DEMO_PHOTO'},'LIGHT_QUIZ':{'LIGHT','HOLD'},'VOICE_QUIZ':{'SPEECH','TYPED'},'DIRECTION':{'COMPASS','HOLD'},'ACCELERATION_QUIZ':{'ACCELEROMETER','HOLD'}}
def require_mode(mode):
    if mode not in ('DEMO','FIELD'): raise HTTPException(404,'Unknown mode')
    return mode

def lock_mode(db,user_id,mode):
    # Serialize all mode writes via the owning account; covers creation races too.
    db.scalar(select(User).where(User.id==user_id).with_for_update())
    db.execute(mysql_insert(ModeState).values(user_id=user_id,mode=mode,generation=0).on_duplicate_key_update(mode=mode))
    return db.scalar(select(ModeState).where(ModeState.user_id==user_id,ModeState.mode==mode).with_for_update())

def snapshot(db,state):
    rows=db.scalars(select(Completion).where(Completion.user_id==state.user_id,Completion.mode==state.mode).order_by(Completion.id)).all()
    return {'generation':state.generation,'completions':[{'mission_id':r.mission_id,'saved_at':r.saved_at,'discovery_method':r.discovery_method,'completion_method':r.completion_method,'photo_base64':base64.b64encode(r.photo).decode() if r.photo else None} for r in rows]}

def decode_photo(value):
    if value is None: return None
    try:
        raw=base64.b64decode(value,validate=True)
        if not raw or len(raw)>2*1024*1024 or not raw.startswith(b'\xff\xd8\xff') or not raw.endswith(b'\xff\xd9'): raise ValueError()
        with Image.open(io.BytesIO(raw)) as image:
            if image.format!='JPEG' or image.width*image.height>30000000: raise ValueError()
            image.verify()
        with Image.open(io.BytesIO(raw)) as image:
            image.load()
        return raw
    except (ValueError,binascii.Error,OSError,Image.DecompressionBombError): raise HTTPException(400,'Invalid photo')

@app.get('/v1/progress/{mode}')
def progress(mode:str,auth=Depends(authenticated)):
    mode=require_mode(mode)
    with Session.begin() as db: return snapshot(db,lock_mode(db,auth[0],mode))
@app.post('/v1/progress/{mode}/sync')
def sync(mode:str,data:SyncInput,auth=Depends(authenticated)):
    mode=require_mode(mode)
    # Validate all payloads before any write.
    items={}
    for item in data.completions:
        mission=MISSIONS.get(item.mission_id)
        if not mission or item.mission_id in items or item.discovery_method not in ('AR','CARD') or item.completion_method not in ALLOWED.get(mission['type'],set()): raise HTTPException(400,'Invalid completion')
        if item.completion_method=='DEMO_PHOTO' and mode!='DEMO': raise HTTPException(400,'Invalid completion')
        if mode=='FIELD' and not verified_coordinates(mission): raise HTTPException(400,'Unverified field mission')
        photo=decode_photo(item.photo_base64)
        if photo is not None and item.completion_method not in ('PHOTO','DEMO_PHOTO'): raise HTTPException(400,'Invalid photo')
        items[item.mission_id]=(item,photo)
    with Session.begin() as db:
        state=lock_mode(db,auth[0],mode)
        if state.generation!=data.generation: raise HTTPException(409,'Generation mismatch')
        existing={r.mission_id for r in db.scalars(select(Completion).where(Completion.user_id==auth[0],Completion.mode==mode)).all()}
        for mission in CATALOG:
            mid=mission['id']
            if mid in items and mid not in existing:
                if any(prior['id'] not in existing for prior in CATALOG if prior['order']<mission['order']): raise HTTPException(400,'Previous mission required')
                item,photo=items[mid]
                if item.completion_method in ('PHOTO','DEMO_PHOTO') and photo is None: raise HTTPException(400,'Photo required')
                db.add(Completion(user_id=auth[0],mode=mode,mission_id=mid,saved_at=item.saved_at,discovery_method=item.discovery_method,completion_method=item.completion_method,photo=photo)); existing.add(mid)
        db.flush(); return snapshot(db,state)
@app.delete('/v1/progress/{mode}')
def reset(mode:str,generation:int,auth=Depends(authenticated)):
    mode=require_mode(mode)
    with Session.begin() as db:
        state=lock_mode(db,auth[0],mode)
        if generation!=state.generation: raise HTTPException(409,'Generation mismatch')
        if state.generation>=2147483646: raise HTTPException(409,'Generation exhausted')
        from sqlalchemy import delete
        db.execute(delete(Completion).where(Completion.user_id==auth[0],Completion.mode==mode))
        state.generation+=1; db.flush(); return snapshot(db,state)
