#!/usr/bin/env python3
"""Apply the production port and mount migration without exposing credentials."""
from pathlib import Path
import json,os,shutil,subprocess
R=Path('/YunaNexus/platform')
d=R/'secrets/redis';d.mkdir(exist_ok=True);d.chmod(0o755)
if not (d/'redis.conf').exists():shutil.copyfile(R/'secrets/redis.conf',d/'redis.conf')
os.chown(d/'redis.conf',999,1000);(d/'redis.conf').chmod(0o600)
p=R/'config/auth.yaml';c=json.loads(p.read_text());a=c['yunanexus']['auth']['security']
a['public-origin']='https://103.39.66.135:3000'
a['cors-allowed-origins']=['https://103.39.66.135:3000','https://103.39.66.135:3001']
p.write_text(json.dumps(c,indent=2)+'\n')
for name in ['file','audit']:
 p=R/('secrets/'+name+'.env');s=p.read_text();p.write_text(s.replace('/app/data','/data'))
shutil.copyfile(R/'ops/nginx.conf',R/'config/nginx.conf')
(R/'config/nginx.conf').chmod(0o644)
subprocess.run([str(R/'ops/compose.sh'),'up','-d','--no-deps','redis','edge'],check=True)
print('Ports migrated to HTTPS 3000 / 3001')
