#!/usr/bin/env python3
"""Bound project logs and caches; never touch another service's directories."""
import os,logging,subprocess,time
from pathlib import Path
R=Path('/YunaNexus/platform').resolve()
def maintain():
 for path in (R/'logs').rglob('*'):
  if path.is_file() and not path.is_symlink() and path.resolve().is_relative_to(R/'logs'):
   if path.stat().st_size>10*1024**2:
    # Truncate in place so running processes continue to write the same inode.
    with path.open('r+b') as output:output.truncate(0)
 for path in (R/'cache').glob('*.tar.gz'):
  if path.is_file() and not path.is_symlink() and time.time()-path.stat().st_mtime>86400:path.unlink()
 if (R/'data/acme/account.json').is_file():
  subprocess.run(['bash',str(R/'ops/renew-certificate.sh')],timeout=180,capture_output=True)
if __name__=='__main__':
 while True:
  try:maintain()
  except Exception:pass
  time.sleep(21600)
