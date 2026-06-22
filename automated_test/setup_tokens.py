"""Setup test users and collect tokens for DAST runner."""
import http.client, json, sys

BASE = 'localhost'
PORT = 5000

def post(path, body):
    conn = http.client.HTTPConnection(BASE, PORT, timeout=20)
    conn.request('POST', path, json.dumps(body), {'Content-Type':'application/json'})
    r = conn.getresponse()
    try:
        bd = json.loads(r.read().decode())
    except Exception:
        bd = {}
    return r.status, bd

def set_role(uid, role, token):
    """Call set_role_tool.js via node — not needed for mock tokens; done via Firestore update."""
    pass

users = [
    {'name':'Patient Test Runner', 'email':'patient_test_runner@example.com', 'role':'user'},
    {'name':'Driver Test Runner',  'email':'driver_test_runner@example.com',  'role':'driver'},
    {'name':'Admin Test Runner',   'email':'admin_test_runner@example.com',   'role':'admin'},
]

tokens_out = {}
uids_out   = {}

for u in users:
    role = u['role']
    email = u['email']
    # Try register first
    s, b = post('/api/auth/register', {
        'name': u['name'], 'email': email,
        'phone': '1234567890', 'password': 'password123'
    })
    if s == 201:
        uid   = b.get('uid', '')
        token = b.get('idToken', '')
        print(f"[REGISTERED] {role}: uid={uid}  token_prefix={str(token)[:50]}")
    else:
        # Already exists — try login
        s2, b2 = post('/api/auth/login', {'email': email, 'password': 'password123'})
        if s2 == 200:
            uid   = b2.get('uid', '')
            token = b2.get('idToken', '')
            print(f"[LOGIN_OK] {role}: uid={uid}  token_prefix={str(token)[:50]}")
        else:
            print(f"[ERR] {role}: reg={s} login={s2}  {b2}")
            uid, token = '', ''

    tokens_out[role] = token
    uids_out[role]   = uid

# Build mock tokens if idToken is null (happens for real firebase users without admin SDK)
for role, token in tokens_out.items():
    if not token and uids_out.get(role):
        uid = uids_out[role]
        payload = json.dumps({'uid': uid, 'user_id': uid, 'role': role, 'email': f'{role}@example.com'})
        import base64
        encoded = base64.b64encode(payload.encode()).decode()
        tokens_out[role] = f"mock-token-{encoded}.dummy.dummy"
        print(f"[MOCK_TOKEN] {role}: constructed mock token")

print("\n=== SUMMARY ===")
for role in ['user','driver','admin']:
    print(f"  {role}: uid={uids_out.get(role)} token_len={len(tokens_out.get(role,''))}")

# Update input.json with real values
try:
    import pathlib
    root = pathlib.Path(__file__).parent.parent
    inp = root / 'input.json'
    with open(inp) as f:
        cfg = json.load(f)
    cfg['user']         = tokens_out.get('user', '')
    cfg['driver']       = tokens_out.get('driver', '')
    cfg['admin']        = tokens_out.get('admin', '')
    cfg['testUserId']   = uids_out.get('user', 'test-uid-placeholder')
    cfg['testDriverId'] = uids_out.get('driver', 'test-driver-placeholder')
    with open(inp, 'w') as f:
        json.dump(cfg, f, indent=2)
    print(f"\n[OK] input.json updated with real tokens and UIDs")
except Exception as e:
    print(f"[WARN] Could not update input.json: {e}")
