#!/usr/bin/env python3
"""Validate a fresh bind mount, then recreate the project edge after atomic config replacement."""
from pathlib import Path
import hashlib,os,subprocess
R=Path('/YunaNexus/platform')
if not os.path.ismount('/YunaNexus'):raise SystemExit('Data disk is not mounted')
compose=str(R/'ops/compose.sh')
subprocess.run([compose,'run','--rm','--no-deps','--entrypoint','nginx','edge','-t'],check=True)
subprocess.run([compose,'up','-d','--no-deps','--force-recreate','edge'],check=True)
expected=hashlib.sha256((R/'config/nginx.conf').read_bytes()).hexdigest()
actual=subprocess.check_output([compose,'exec','-T','edge','sha256sum','/etc/nginx/nginx.conf'],text=True).split()[0]
if actual!=expected:raise SystemExit('Live edge configuration does not match the host file')
subprocess.run([compose,'exec','-T','edge','nginx','-t'],check=True)
print('Fresh edge configuration validated and live bind-mount SHA256 matched')
