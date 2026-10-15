#!/usr/bin/env python3
"""Install validated release archives. All writes are bounded to the data disk."""
import argparse,fcntl,hashlib,json,os,re,shutil,subprocess,tarfile,time,urllib.request
from pathlib import Path
R=Path('/YunaNexus/platform');NAMES=['meta','auth','gateway','user','permission','file','notice','audit','analytics']
PORTS=dict(zip(NAMES,[8001,8101,8081,8201,8301,8401,8501,8601,8701]))
os.environ.update(DOCKER_HOST='unix://'+str(R/'runtime/docker.sock'),DOCKER_CONFIG=str(R/'runtime/docker-client'),HOME=str(R/'runtime/home'),TMPDIR=str(R/'tmp'))
def compose(*args):return subprocess.check_output([str(R/'ops/compose.sh'),*args],text=True).strip()
def switch(parent,name,target):
 tmp=parent/(name+'.next');tmp.unlink(missing_ok=True);tmp.symlink_to(target);os.replace(tmp,parent/name)
def healthy(service):
 for attempt in range(75):
  try:
   cid=compose('ps','-q',service);ip=subprocess.check_output(['docker','inspect','--format','{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}',cid],text=True).strip()
   with urllib.request.urlopen('http://'+ip+':'+str(PORTS[service])+'/actuator/health',timeout=3) as response:
    if json.load(response).get('status')=='UP':return
  except Exception:pass
  time.sleep(2)
 raise RuntimeError(service+' health check failed')
def deploy(kind,archive,revision):
 if not os.path.ismount('/YunaNexus'):raise RuntimeError('Data disk is not mounted')
 if not re.fullmatch('[a-f0-9]{40}',revision):raise ValueError('Invalid revision')
 if shutil.disk_usage(R).free<4*1024**3:raise RuntimeError('Less than 4 GiB disk reserve; deployment refused')
 parent=R/'releases'/kind;target=parent/revision
 if target.exists():raise RuntimeError('Release already installed; do not overwrite immutable release')
 target.mkdir(mode=0o755)
 try:
  with tarfile.open(archive,'r:*') as tar:
   members=tar.getmembers();seen=set();total=0
   if len(members)>10000:raise ValueError('Too many archive entries')
   for member in members:
    path=Path(member.name)
    if not member.isfile() or path.is_absolute() or '..' in path.parts or member.name in seen:raise ValueError('Unsafe archive entry')
    if kind=='backend' and member.name not in ['yunanexus-'+n+'.jar' for n in NAMES]:raise ValueError('Unexpected backend file')
    if kind=='frontend' and (len(path.parts)<2 or path.parts[0] not in ['admin','user'] or any(p.startswith('.') for p in path.parts)):raise ValueError('Unexpected frontend file')
    total+=member.size
    if total>2*1024**3:raise ValueError('Archive exceeds expanded size limit')
    seen.add(member.name)
    destination=target/path;destination.parent.mkdir(parents=True,exist_ok=True)
    with tar.extractfile(member) as source,destination.open('xb') as sink:shutil.copyfileobj(source,sink)
    destination.chmod(0o644)
  if kind=='backend' and len(seen)!=9:raise ValueError('Nine service jars required')
  if kind=='frontend' and not all((target/app/'index.html').is_file() for app in ['admin','user']):raise ValueError('Both frontend applications are required')
 except Exception:
  shutil.rmtree(target);raise
 previous=os.readlink(parent/'current') if (parent/'current').is_symlink() else None
 switch(parent,'current',revision)
 try:
  if kind=='backend':
   for service in ['meta','permission','user','file','notice','auth','audit','analytics','gateway']:
    compose('up','-d','--no-deps','--force-recreate',service);healthy(service)
  else:compose('up','-d','--no-deps','edge')
 except Exception:
  if previous:
   switch(parent,'current',previous)
   if kind=='backend':compose('up','-d','--no-deps','--force-recreate',*NAMES)
  raise
 if previous:switch(parent,'previous',previous)
 (R/'runtime'/(kind+'-release.json')).write_text(json.dumps({'revision':revision,'time':time.time()}))
 # Retain current and previous; never delete data, secrets, or unknown directory names.
 keep={revision,previous}
 for old in parent.iterdir():
  if old.name not in keep and old.is_dir() and not old.is_symlink() and re.fullmatch('[a-f0-9]{40}',old.name):shutil.rmtree(old)
 print(kind,'deployed',revision,flush=True)
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('kind',choices=['backend','frontend']);p.add_argument('archive');p.add_argument('revision');a=p.parse_args()
 with (R/'runtime/deploy.lock').open('w') as lock:
  fcntl.flock(lock,fcntl.LOCK_EX);deploy(a.kind,a.archive,a.revision)
