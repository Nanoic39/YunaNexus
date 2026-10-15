#!/usr/bin/env python3
"""Apply additive personal-workspace schema and gateway settings inside the data disk."""
import json,os,subprocess
from pathlib import Path
R=Path('/YunaNexus/platform')
if not os.path.ismount('/YunaNexus'):raise SystemExit('Data disk is not mounted')
os.umask(0o077)
statement=(R/'ops/20261015-personal-workspace.sql').read_text()
subprocess.run([str(R/'ops/compose.sh'),'exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'],input=statement,text=True,check=True)
p=R/'config/gateway.yaml'
config=json.loads(p.read_text());gateway=config.setdefault('yunanexus',{}).setdefault('gateway',{})
gateway['api-key-verification-url']='http://auth:8100/internal/auth/api-key/verify'
paths=gateway.setdefault('permission',{}).setdefault('permit-paths',[])
for item in ['/api/v1/file/me','/api/v1/file/me/**','/api/v1/auth/account/**']:
 if item not in paths:paths.append(item)
p.write_text(json.dumps(config,ensure_ascii=False,indent=2)+'\n');p.chmod(0o644)
print('Personal workspace schema and private verification endpoint configured')
