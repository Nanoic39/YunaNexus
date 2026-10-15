#!/usr/bin/env python3
"""Production smoke checks; print assertions, never tokens or credentials."""
from pathlib import Path
import ssl,json,urllib.request,urllib.error,http.cookiejar
R=Path('/YunaNexus/platform')
ctx=ssl.create_default_context(cafile=str(R/'secrets/tls/server.crt'))
cookies=http.cookiejar.CookieJar()
opener=urllib.request.build_opener(urllib.request.HTTPSHandler(context=ctx),urllib.request.HTTPCookieProcessor(cookies),urllib.request.ProxyHandler({}))
def call(path,port=3000,headers=None,data=None):
 req=urllib.request.Request('http://103.39.66.135:'+str(port)+path,headers=headers or {},data=json.dumps(data).encode() if data is not None else None)
 if data is not None:req.add_header('Content-Type','application/json')
 try:
  with opener.open(req,timeout=20) as r:status=r.status;body=r.read()
 except urllib.error.HTTPError as e:status=e.code;body=e.read()
 try:body=json.loads(body)
 except Exception:body=None
 return status,body
for port in [3000,3001]:
 status,_=call('/',port);assert status==200;print('frontend',port,'PASS')
s,b=call('/api/v1/status');assert s==200 and b['code']==200;print('status summary',json.dumps(b['data']))
for name in ['auth','svc-auth','yunanexus-auth']:
 s,b=call('/api/v1/status/'+name);assert s==200 and b['code']==200;print('status alias',name,'PASS')
for path in ['/actuator/health','/internal/permission/check','/api/v1/notice/portal/admin/site']:
 s,_=call(path);assert s in [401,403,404],(path,s);print('public restriction',path,'PASS')
s,b=call('/api/v1/notice/portal/content');assert s==200 and b['code']==200;print('published portal content PASS')
s,b=call('/api/v1/auth/csrf');assert s==200 and b['code']==200;print('csrf PASS')
s,b=call('/api/v1/notice/portal/admin/site',3001);assert s in [401,403];print('admin anonymous rejection PASS')
key=json.loads((R/'secrets/credentials.json').read_text())['admin']
s,b=call('/api/v1/notice/portal/admin/site',3001,{'X-Admin-Token':key});assert s==200 and b['code']==200;print('admin credential validation PASS')
