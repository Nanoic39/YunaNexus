#!/usr/bin/env python3
"""Apply publication schema and exact anonymous route to the project gateway only."""
import json,os,subprocess
from pathlib import Path
R=Path('/YunaNexus/platform')
if not os.path.ismount('/YunaNexus'):raise SystemExit('Data disk is not mounted')
os.umask(0o077)
statement=(R/'ops/20261017-public-files.sql').read_text()
subprocess.run([str(R/'ops/compose.sh'),'exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'],input=statement,text=True,check=True)
p=R/'config/gateway.yaml';config=json.loads(p.read_text())
gateway=config.setdefault('yunanexus',{}).setdefault('gateway',{})
paths=gateway.setdefault('permit-paths',[])
if '/api/v1/file/public/assets/*' not in paths:paths.append('/api/v1/file/public/assets/*')
tmp=p.with_suffix('.publication.tmp');tmp.write_text(json.dumps(config,ensure_ascii=False,indent=2)+'\n');tmp.chmod(0o644);tmp.replace(p)
print('Publication schema and anonymous asset route configured')
