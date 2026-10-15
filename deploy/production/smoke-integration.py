#!/usr/bin/env python3
"""Verify production admin/ordinary boundaries and PKCE without logging credentials."""
import base64
import hashlib
import http.cookiejar
import json
import re
import secrets
import subprocess
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path('/YunaNexus/platform')
BASE = 'http://103.39.66.135:3000'
CALLBACK = 'http://103.39.66.135:3001/sso/callback'
ADMIN_PATHS = ['/api/v1/auth/admin/users', '/api/v1/permission/admin/roles',
               '/api/v1/notice/portal/admin/site']


def browser():
    return urllib.request.build_opener(urllib.request.ProxyHandler({}),
        urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))


def call(client, path, data=None, token=None, csrf=False):
    headers = {'Accept': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if csrf:
        status, result = call(client, '/api/v1/auth/csrf')
        assert status == 200 and result['code'] == 200, 'CSRF bootstrap failed'
        headers[result['data']['headerName']] = result['data']['token']
        headers['Origin'] = BASE
    if data is not None:
        headers['Content-Type'] = 'application/json'
    request = urllib.request.Request(BASE + path, headers=headers,
        data=json.dumps(data).encode() if data is not None else None)
    try:
        with client.open(request, timeout=20) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


def success(pair, label):
    status, body = pair
    assert status == 200 and body.get('code') == 200, (label, status, body.get('code'))
    return body['data']


def sql(statement):
    subprocess.run([str(ROOT/'ops/compose.sh'), 'exec', '-T', 'mysql', 'sh', '-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'], input=statement,
        text=True, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


admin = json.loads((ROOT/'secrets/admin-bootstrap.json').read_text())
client = browser()
login = success(call(client, '/api/v1/auth/login', {**admin, 'clientCode': 'web'}, csrf=True), 'admin login')
token = login['accessToken']
for path in ADMIN_PATHS:
    success(call(client, path, token=token), path)
print('Unified admin Bearer access across auth/permission/portal PASS', flush=True)

verifier = secrets.token_urlsafe(32)
state = secrets.token_urlsafe(32)
challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=')
authorized = success(call(client, '/api/v1/auth/sso/authorize', {
    'clientCode': 'web', 'redirectUri': CALLBACK, 'state': state,
    'codeChallenge': challenge, 'codeChallengeMethod': 'S256'}, token, True), 'authorize')
url = urllib.parse.urlsplit(authorized['redirectUrl'])
params = urllib.parse.parse_qs(url.query)
assert url.scheme + '://' + url.netloc + url.path == CALLBACK and params['state'] == [state]
exchange = {'clientCode': 'web', 'redirectUri': CALLBACK, 'code': params['code'][0],
            'state': state, 'codeVerifier': verifier}
clean = urllib.request.build_opener(urllib.request.ProxyHandler({}))
status, body = call(clean, '/api/v1/auth/sso/token', {**exchange, 'codeVerifier': secrets.token_urlsafe(32)})
assert status != 200 or body['code'] != 200, 'Wrong PKCE verifier accepted'
console = success(call(clean, '/api/v1/auth/sso/token', exchange), 'token exchange')
status, body = call(clean, '/api/v1/auth/sso/token', exchange)
assert status != 200 or body['code'] != 200, 'Code replay accepted'
success(call(client, '/api/v1/auth/logout', {}, console['accessToken'], True), 'console logout')
success(call(client, '/api/v1/auth/me', token=token), 'portal independent session')
for path in ADMIN_PATHS:
    status, _ = call(client, path, token=console['accessToken'])
    assert status in (401, 403), 'Revoked console token accepted'
print('PKCE wrong verifier / replay rejected; independent sessions and revocation PASS', flush=True)

captcha = success(call(client, '/api/v1/auth/captcha?username=yn_integration_probe'), 'captcha')
assert not captcha.get('code') and captcha['imageDataUrl'].startswith('data:image/png;base64,')
assert base64.b64decode(captcha['imageDataUrl'].split(',', 1)[1]).startswith(b'\x89PNG\r\n\x1a\n')
print('Production CAPTCHA image without plaintext code PASS', flush=True)

fixture = json.loads((ROOT/'secrets/smoke-auto-account.json').read_text())
username = fixture['username']
assert re.fullmatch(r'yn_smoke_[a-f0-9]{12}', username)
sql("UPDATE yunanexus_auth.auth_user SET status=1 WHERE username='" + username + "';")
ordinary = browser()
try:
    user = success(call(ordinary, '/api/v1/auth/login', {**fixture, 'clientCode': 'web'}, csrf=True), 'ordinary login')
    for path in ADMIN_PATHS:
        status, _ = call(ordinary, path, token=user['accessToken'])
        assert status == 403, ('Ordinary admin rejection', path, status)
    success(call(ordinary, '/api/v1/notice/inbox', token=user['accessToken']), 'own inbox')
    status, body = call(ordinary, '/api/v1/notice/inbox/9223372036854775806', token=user['accessToken'])
    assert status == 200 and body['code'] == 60103, 'Inbox ownership/not-found boundary failed'
    success(call(ordinary, '/api/v1/auth/logout', {}, user['accessToken'], True), 'ordinary logout')
    print('Ordinary account denied all admin areas; own inbox permitted PASS', flush=True)
finally:
    sql("UPDATE yunanexus_auth.auth_user SET status=0 WHERE username='" + username + "';")
    print('Dedicated smoke account disabled', flush=True)

success(call(client, '/api/v1/auth/logout', {}, token, True), 'admin smoke logout')
print('Production integration checks PASS', flush=True)
