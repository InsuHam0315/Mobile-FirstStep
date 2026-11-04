"""Integration tests require a dedicated MySQL database via DATABASE_URL; no SQLite fallback."""
import base64, io, os, secrets, time
import pytest
from PIL import Image
from fastapi.testclient import TestClient

if not os.getenv('DATABASE_URL'):
    pytest.skip('Set DATABASE_URL to a dedicated MySQL test database', allow_module_level=True)
os.environ['DEVELOPMENT_LOGIN_ENABLED']='true'
os.environ['GOOGLE_WEB_CLIENT_ID']='test-client.apps.googleusercontent.com'
import app as server

created_users=[]

@pytest.fixture(scope='module')
def client():
    with TestClient(server.app) as c:
        yield c
    with server.Session.begin() as db:
        for user_id in created_users:
            user=db.get(server.User,user_id)
            if user: db.delete(user)

def register(client):
    name='test_'+secrets.token_hex(8)
    r=client.post('/v1/auth/development/register',json={'username':name,'password':'long-passphrase-123','nickname':'테스터'})
    assert r.status_code==200
    created_users.append(r.json()['user']['id'])
    return r.json(), name

def auth(user): return {'Authorization':'Bearer '+user['access_token']}
def photo():
    out=io.BytesIO(); Image.new('RGB',(8,8),'pink').save(out,'JPEG'); return base64.b64encode(out.getvalue()).decode()
def completion(mid,method='PHOTO',pic=None):
    return {'mission_id':mid,'saved_at':1700000000000,'discovery_method':'CARD','completion_method':method,'photo_base64':pic}

def test_health_config_login_logout(client):
    assert client.get('/health').json()=={'status':'ok','database':'mysql'}
    user,name=register(client)
    assert client.post('/v1/auth/development/login',json={'username':name,'password':'wrong password'}).status_code==401
    assert client.get('/v1/me',headers=auth(user)).json()['id']==user['user']['id']
    assert client.post('/v1/auth/logout',headers=auth(user)).status_code==200
    assert client.get('/v1/me',headers=auth(user)).status_code==401

def test_sync_generation_order_photo_and_isolation(client):
    user,_=register(client); other,_=register(client); h=auth(user)
    assert client.get('/v1/progress/DEMO',headers=h).json()=={'generation':0,'completions':[]}
    assert client.post('/v1/progress/DEMO/sync',headers=h,json={'generation':0,'completions':[completion('M02','HOLD')]}).status_code==400
    assert client.post('/v1/progress/DEMO/sync',headers=h,json={'generation':0,'completions':[completion('M01','PHOTO','dGVzdA==')]}).status_code==400
    assert client.post('/v1/progress/DEMO/sync',headers=h,json={'generation':0,'completions':[completion('M01','PHOTO')]}).status_code==400
    assert client.post('/v1/progress/DEMO/sync',headers=h,json={'generation':0,'completions':[completion('M01','DEMO_PHOTO')]}).status_code==400
    first=completion('M01','PHOTO',photo()); second=completion('M02','HOLD')
    r=client.post('/v1/progress/DEMO/sync',headers=h,json={'generation':0,'completions':[second,first]}); assert r.status_code==200
    assert len(r.json()['completions'])==2
    assert r.json()['completions'][0]['photo_base64']==first['photo_base64']
    assert client.get('/v1/progress/DEMO',headers=auth(other)).json()['completions']==[]
    assert client.get('/v1/progress/FIELD',headers=h).json()['completions']==[]
    assert client.post('/v1/progress/FIELD/sync',headers=h,json={'generation':0,'completions':[first]}).status_code==400
    assert client.delete('/v1/progress/DEMO?generation=0',headers=h).json()=={'generation':1,'completions':[]}
    assert client.post('/v1/progress/DEMO/sync',headers=h,json={'generation':0,'completions':[first]}).status_code==409
    assert client.delete('/v1/progress/DEMO?generation=0',headers=h).status_code==409
    assert client.post('/v1/progress/DEMO/sync',headers=h,json={'generation':1,'completions':[completion('M01','DEMO_PHOTO',photo())]}).status_code==200

def test_google_nonce_one_use(client,monkeypatch):
    monkeypatch.setattr(server,'auth_limit',lambda request: None)
    challenge=client.post('/v1/auth/google/challenge').json()
    monkeypatch.setattr(server.google_id_token,'verify_oauth2_token',lambda *a,**kw:{'iss':'https://accounts.google.com','aud':server.GOOGLE_CLIENT_ID,'email_verified':True,'exp':int(time.time())+300,'email':'test@example.com','sub':'test-'+secrets.token_hex(10),'nonce':challenge['nonce']})
    payload={'id_token':'mocked-only-in-test','challenge_id':challenge['challenge_id']}
    response=client.post('/v1/auth/google',json=payload)
    assert response.status_code==200
    created_users.append(response.json()['user']['id'])
    assert client.post('/v1/auth/google',json=payload).status_code==401

def test_first_write_wins_and_user_scoped_reset(client):
    user,_=register(client); h=auth(user)
    original=completion('M01','DEMO_PHOTO',photo()); original['saved_at']=1700000000000
    newer=completion('M01','DEMO_PHOTO'); newer['saved_at']=1800000000000
    assert client.post('/v1/progress/DEMO/sync',headers=h,json={'generation':0,'completions':[original]}).status_code==200
    response=client.post('/v1/progress/DEMO/sync',headers=h,json={'generation':0,'completions':[newer]})
    assert response.status_code==200 and response.json()['completions'][0]['saved_at']==original['saved_at']
    assert response.json()['completions'][0]['photo_base64']==original['photo_base64']
    assert client.post('/v1/progress/DEMO/sync',headers=h,json={'generation':0,'completions':[completion('M02','LEGACY')]}).status_code==400


def test_concurrent_reset_and_sync_cannot_restore_stale_progress(client):
    from concurrent.futures import ThreadPoolExecutor
    user,_=register(client); h=auth(user)
    first=completion('M01','DEMO_PHOTO',photo())
    def sync(): return client.post('/v1/progress/DEMO/sync',headers=h,json={'generation':0,'completions':[first]})
    def reset(): return client.delete('/v1/progress/DEMO?generation=0',headers=h)
    with ThreadPoolExecutor(max_workers=2) as pool:
        pending=[pool.submit(sync),pool.submit(reset)]
        results=[p.result() for p in pending]
    assert results[0].status_code in (200,409) and results[1].status_code==200
    assert client.get('/v1/progress/DEMO',headers=h).json()=={'generation':1,'completions':[]}

@pytest.mark.parametrize('bad', [
    {'exp':1}, {'aud':'wrong-client'}, {'iss':'https://evil.example'},
    {'nonce':'wrong-nonce'}, {'email_verified':False}, {'sub':'x'*256}, {'email':'a'*256}
])
def test_google_rejects_invalid_claims(client,monkeypatch,bad):
    server._rate.clear()
    challenge=client.post('/v1/auth/google/challenge').json()
    claims={'iss':'https://accounts.google.com','aud':server.GOOGLE_CLIENT_ID,'email_verified':True,'exp':int(time.time())+300,'email':'test@example.com','sub':'bad-test','nonce':challenge['nonce']}
    claims.update(bad)
    monkeypatch.setattr(server.google_id_token,'verify_oauth2_token',lambda *a,**kw:claims)
    assert client.post('/v1/auth/google',json={'id_token':'mocked-only-in-test','challenge_id':challenge['challenge_id']}).status_code==401


def test_jpeg_validation_rejects_bad_or_excessive_dimensions():
    from fastapi import HTTPException
    with pytest.raises(HTTPException): server.decode_photo(base64.b64encode(b'\xff\xd8\xffgarbage').decode())
    raw=base64.b64decode(photo())
    with pytest.raises(HTTPException): server.decode_photo(base64.b64encode(raw[:25]).decode())
    with pytest.raises(HTTPException): server.decode_photo(base64.b64encode(raw[:-2]).decode())
    out=io.BytesIO(); Image.new('RGB',(6000,6000),'pink').save(out,'JPEG')
    with pytest.raises(HTTPException): server.decode_photo(base64.b64encode(out.getvalue()).decode())


def test_chunked_body_guard():
    import asyncio
    response=[]; events=iter([{'type':'http.request','body':b'a'*(7*1024*1024),'more_body':True},{'type':'http.request','body':b'b'*(6*1024*1024),'more_body':False}])
    async def receive(): return next(events)
    async def send(event): response.append(event)
    async def downstream(scope,receive,send): raise AssertionError('oversized request reached app')
    scope={'type':'http','headers':[],'method':'POST','path':'/'}
    asyncio.run(server.BodyLimitMiddleware(downstream)(scope,receive,send))
    assert response[0]['status']==413

def test_token_expiry_and_auth_rate_limit(client):
    from datetime import timedelta
    user,_=register(client)
    with server.Session.begin() as db:
        token=db.get(server.Token,server.digest(user['access_token']))
        token.expires_at=server.utcnow()-timedelta(seconds=1)
    assert client.get('/v1/me',headers=auth(user)).status_code==401
    server._rate.clear()
    for _ in range(20):
        assert client.post('/v1/auth/development/login',json={'username':'missing-user','password':'incorrect'}).status_code==401
    assert client.post('/v1/auth/development/login',json={'username':'missing-user','password':'incorrect'}).status_code==429
    server._rate.clear()


def test_google_expired_and_concurrent_nonce(client,monkeypatch):
    from concurrent.futures import ThreadPoolExecutor
    from datetime import timedelta
    server._rate.clear()
    first=client.post('/v1/auth/google/challenge').json()
    with server.Session.begin() as db:
        db.get(server.Challenge,first['challenge_id']).expires_at=server.utcnow()-timedelta(seconds=1)
    current={'nonce':first['nonce']}
    subject='test-'+secrets.token_hex(10)
    def verify(*args,**kwargs):
        return {'iss':'https://accounts.google.com','aud':server.GOOGLE_CLIENT_ID,'email_verified':True,'exp':int(time.time())+300,'email':'test@example.com','sub':subject,'nonce':current['nonce']}
    monkeypatch.setattr(server.google_id_token,'verify_oauth2_token',verify)
    assert client.post('/v1/auth/google',json={'id_token':'mock','challenge_id':first['challenge_id']}).status_code==401
    challenge=client.post('/v1/auth/google/challenge').json(); current['nonce']=challenge['nonce']
    def attempt(): return client.post('/v1/auth/google',json={'id_token':'mock','challenge_id':challenge['challenge_id']})
    with ThreadPoolExecutor(max_workers=2) as pool:
        responses=[r.result() for r in [pool.submit(attempt),pool.submit(attempt)]]
    assert sorted(r.status_code for r in responses)==[200,401]
    created_users.append(next(r.json()['user']['id'] for r in responses if r.status_code==200))


@pytest.mark.parametrize('latitude,longitude',[(float('nan'),0),(0,float('inf')),(91,0),(0,-181),(True,0)])
def test_field_invalid_coordinates_rejected(client,monkeypatch,latitude,longitude):
    server._rate.clear(); user,_=register(client)
    invalid=dict(server.MISSIONS['M01'],coordinateVerified=True,latitude=latitude,longitude=longitude)
    monkeypatch.setitem(server.MISSIONS,'M01',invalid)
    response=client.post('/v1/progress/FIELD/sync',headers=auth(user),json={'generation':0,'completions':[completion('M01','PHOTO',photo())]})
    assert response.status_code==400


def test_validation_errors_never_echo_secrets(client):
    server._rate.clear()
    secret='sensitive-password-marker'
    response=client.post('/v1/auth/development/register',json={'username':'x','password':secret,'nickname':'Name'})
    assert response.status_code==422 and response.json()=={'detail':'Invalid request'} and secret not in response.text
    token='sensitive-google-token-marker'+'x'*20001
    response=client.post('/v1/auth/google',json={'id_token':token,'challenge_id':'id'})
    assert response.status_code==422 and response.json()=={'detail':'Invalid request'} and 'sensitive-google-token-marker' not in response.text
    response=client.post('/v1/auth/development/login',json={'username':'validuser','password':secret,'extra':secret})
    assert response.status_code==422 and response.json()=={'detail':'Invalid request'} and secret not in response.text
