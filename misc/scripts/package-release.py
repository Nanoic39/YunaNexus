#!/usr/bin/env python3
"""Package vetted build outputs; no source tree or local configuration is uploaded."""
from pathlib import Path
import argparse,tarfile,zipfile,hashlib,json,subprocess
p=argparse.ArgumentParser();p.add_argument('kind',choices=['backend','frontend']);p.add_argument('--root',default='.');p.add_argument('--out',default='release');a=p.parse_args()
r=Path(a.root).resolve();out=Path(a.out).resolve();out.mkdir(parents=True,exist_ok=True)
archive=out/(a.kind+'.tar.gz')
with tarfile.open(archive,'w:gz',compresslevel=1) as tar:
 if a.kind=='backend':
  jars=list(r.glob('yunanexus-*/target/yunanexus-*.jar'));jars=[j for j in jars if not j.name.endswith('-sources.jar')]
  if len(jars)!=9:raise SystemExit('Expected exactly nine executable service jars')
  for jar in jars:
   with zipfile.ZipFile(jar) as z:
    if any('application-local' in n or n.endswith(('.pem','.key')) for n in z.namelist()):raise SystemExit('Private/local resource in artifact')
   tar.add(jar,arcname=jar.parent.parent.name+'.jar')
 else:
  for app in ['admin','user']:
   dist=r/'apps'/app/'dist'
   if not (dist/'index.html').is_file():raise SystemExit('Missing frontend build')
   for f in sorted(dist.rglob('*')):
    if f.is_file() and f.name!='mockServiceWorker.js':tar.add(f,arcname=app+'/'+f.relative_to(dist).as_posix())
sha=hashlib.file_digest(archive.open('rb'),'sha256').hexdigest()
(out/'SHA256SUMS').write_text(sha+'  '+archive.name+'\n',encoding='utf8')
print(archive.name,archive.stat().st_size,'bytes',sha)
