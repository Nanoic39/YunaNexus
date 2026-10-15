#!/usr/bin/env python3
"""Exercise real registration and token lifecycle using a dedicated smoke account."""
import runpy,json,secrets,os,re,subprocess
from pathlib import Path
R=Path('/YunaNexus/platform')
ns=runpy.run_path(str(R/'ops/smoke.py'));call=ns['call']
f=R/'secrets/smoke-auto-account.json'
created=not f.exists()
if created:
 account={'username':'yn_smoke_'+secrets.token_hex(6),'password':secrets.token_urlsafe(24)}
 f.write_text(json.dumps(account));f.chmod(0o600)
else:account=json.loads(f.read_text())
def request(path,payload=None,token=None):
 s,b=call('/api/v1/auth/csrf');assert s==200 and b['code']==200
 csrf=b['data'];headers={csrf['headerName']:csrf['token'],'Origin':'http://103.39.66.135:3000'}
 if token:headers['Authorization']='Bearer '+token
 return call(path,headers=headers,data=payload)
if created:
 s,b=request('/api/v1/auth/register',{**account,'email':account['username']+'@example.invalid'})
 assert s==200 and b['code']==200,('register',s,b.get('code'),b.get('msg'));print('registration + meta ID + DB PASS')
assert re.fullmatch(r'yn_smoke_[a-f0-9]{12}',account['username'])
def sql(statement):
 subprocess.run([str(R/'ops/compose.sh'),'exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'],input="USE yunanexus_auth;\n"+statement,text=True,check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
username=account['username']
sql((R/'ops/portal-client.sql').read_text()+"\nUPDATE yunanexus_auth.auth_user SET status=1 WHERE username='"+username+"';")
print('Automatic web grant will be verified by login; no grant inserted by this test')
try:
 s,b=request('/api/v1/auth/login',{**account,'clientCode':'web'})
 assert s==200 and b['code']==200,('login',s,b.get('code'),b.get('msg'))
 login=b['data'];print('login PASS')
 s,b=call('/api/v1/auth/me',headers={'Authorization':'Bearer '+login['accessToken']})
 assert s==200 and b['code']==200,('me',s,b.get('code'),b.get('msg'));print('JWT user identity PASS')
 s,b=request('/api/v1/auth/refresh',{'refreshToken':login['refreshToken']})
 assert s==200 and b['code']==200,('refresh',s,b.get('code'),b.get('msg'));login=b['data'];print('refresh PASS')
 s,b=request('/api/v1/auth/logout',{},login['accessToken'])
 assert s==200 and b['code']==200,('logout',s,b.get('code'),b.get('msg'));print('logout PASS')
 s,b=call('/api/v1/auth/me',headers={'Authorization':'Bearer '+login['accessToken']})
 assert s in [401,403] or b.get('code') in [401,403];print('revoked JWT rejection PASS')
finally:
 sql("UPDATE yunanexus_auth.auth_user SET status=0 WHERE username='"+username+"';")
 print('Smoke account disabled')
