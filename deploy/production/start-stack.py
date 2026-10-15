#!/usr/bin/env python3
"""Start an already provisioned production stack; no global service registration."""
import subprocess,time,json,urllib.request,urllib.parse,os
from pathlib import Path
R=Path('/YunaNexus/platform')
os.environ.update(DOCKER_HOST='unix://'+str(R/'runtime/docker.sock'),DOCKER_CONFIG=str(R/'runtime/docker-client'),HOME=str(R/'runtime/home'),TMPDIR=str(R/'tmp'))
def compose(*args):return subprocess.check_output([str(R/'ops/compose.sh'),*args],text=True).strip()
def ip(service):
 cid=compose('ps','-q',service)
 return subprocess.check_output(['docker','inspect','--format','{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}',cid],text=True).strip()
def health(service,port,attempts=90):
 for i in range(attempts):
  try:
   with urllib.request.urlopen('http://'+ip(service)+':'+str(port)+'/actuator/health',timeout=3) as res:
    if json.load(res).get('status')=='UP':print(service,'healthy',flush=True);return
  except Exception:pass
  time.sleep(2)
 raise RuntimeError(service+' did not become healthy')
subprocess.run([str(R/'ops/engine.sh')],check=True)
compose('up','-d','mysql','redis','nacos','namesrv','broker')
pwd=json.loads((R/'secrets/credentials.json').read_text())['nacos']
for i in range(90):
 try:
  url='http://'+ip('nacos')+':8848/nacos/v3/auth/user/admin'
  try:urllib.request.urlopen(urllib.request.Request(url,data=urllib.parse.urlencode({'password':pwd}).encode()),timeout=3).close()
  except urllib.error.HTTPError:pass
  login=urllib.request.urlopen(urllib.request.Request('http://'+ip('nacos')+':8848/nacos/v3/auth/user/login',data=urllib.parse.urlencode({'username':'nacos','password':pwd}).encode()),timeout=3)
  if 'accessToken' in json.load(login):break
 except Exception:pass
 time.sleep(2)
else:raise RuntimeError('Nacos authenticated bootstrap failed')
for name,port in [('meta',8001),('permission',8301),('user',8201),('file',8401),('notice',8501),('auth',8101),('audit',8601),('analytics',8701),('gateway',8081)]:
 compose('up','-d','--no-deps',name);health(name,port)
compose('up','-d','--no-deps','edge')
print('Stack started; HTTPS user=:3000 console=:3001',flush=True)
