#!/usr/bin/env python3
"""Read-only GitHub release polling: no server credential is stored in GitHub."""
import argparse,hashlib,json,logging,logging.handlers,re,subprocess,time,urllib.request
from pathlib import Path
R=Path('/YunaNexus/platform');log=logging.getLogger('yunanexus-cd');log.setLevel(logging.INFO)
handler=logging.handlers.RotatingFileHandler(R/'logs/cd.log',maxBytes=2*1024**2,backupCount=3);log.addHandler(handler)
def read(url):
 req=urllib.request.Request(url,headers={'User-Agent':'YunaNexus-CD','Accept':'application/vnd.github+json'})
 return urllib.request.urlopen(req,timeout=30)
def check(kind,repo):
 with read('https://api.github.com/repos/Nanoic39/'+repo+'/releases?per_page=10') as response:releases=json.load(response)
 candidates=[r for r in releases if not r['draft'] and re.fullmatch('deploy-'+kind+'-[a-f0-9]{40}',r['tag_name'])]
 if not candidates:return
 release=max(candidates,key=lambda r:r['published_at']);revision=release['tag_name'].rsplit('-',1)[1]
 state=R/'runtime'/(kind+'-release.json')
 if state.exists() and json.loads(state.read_text()).get('revision')==revision:return
 assets={a['name']:a for a in release['assets']};name=kind+'.tar.gz'
 if name not in assets or 'SHA256SUMS' not in assets:raise ValueError('Incomplete release')
 if assets[name]['size']>1200*1024**2:raise ValueError('Compressed artifact too large')
 with read(assets['SHA256SUMS']['browser_download_url']) as response:checks=response.read(4096).decode()
 expected=next((line.split()[0] for line in checks.splitlines() if line.split()[-1]==name),None)
 if not expected or not re.fullmatch('[a-f0-9]{64}',expected):raise ValueError('Missing checksum')
 path=R/'cache'/(kind+'-'+revision+'.tar.gz');digest=hashlib.sha256()
 with read(assets[name]['browser_download_url']) as response,path.open('wb') as output:
  size=0
  while chunk:=response.read(1024*1024):
   size+=len(chunk)
   if size>1200*1024**2:raise ValueError('Download too large')
   output.write(chunk);digest.update(chunk)
 if digest.hexdigest()!=expected:path.unlink(missing_ok=True);raise ValueError('Checksum mismatch')
 subprocess.run(['python3',str(R/'ops/deploy-release.py'),kind,str(path),revision],check=True,timeout=1800)
 path.unlink(missing_ok=True);log.info('%s deployment complete %s',kind,revision)
def cycle():
 for kind,repo in [('backend','YunaNexus'),('frontend','YunaNexusClient')]:
  try:check(kind,repo)
  except Exception as error:log.error('%s deployment failed: %s',kind,type(error).__name__)
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--once',action='store_true');args=parser.parse_args()
 while True:
  cycle()
  if args.once:break
  time.sleep(300)
