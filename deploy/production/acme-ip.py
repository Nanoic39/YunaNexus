#!/usr/bin/env python3
"""Issue/renew a short-lived IP certificate using ACME v2 and system cryptography.
No global packages, cron entries, or /etc certificate files are created.
"""
import base64,datetime,hashlib,ipaddress,json,os,re,time,urllib.request,urllib.error,subprocess
from pathlib import Path
from cryptography import x509
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import rsa,padding
from cryptography.x509.oid import NameOID
R=Path('/YunaNexus/platform');IP='103.39.66.135';DIRECTORY='https://acme-v02.api.letsencrypt.org/directory'
os.umask(0o077)
def b64(value):return base64.urlsafe_b64encode(value).rstrip(b'=').decode()
def raw(url,data=None,headers=None,method=None):
 request=urllib.request.Request(url,data=data,headers=headers or {'User-Agent':'YunaNexus-ACME'},method=method)
 return urllib.request.urlopen(request,timeout=45)
def atomic(path,value,mode=0o600,group=0):
 temporary=path.with_name(path.name+'.new');temporary.write_bytes(value);os.chmod(temporary,mode);os.chown(temporary,0,group);os.replace(temporary,path)
def main():
 certpath=R/'secrets/tls/server.crt'
 if certpath.exists():
  cert=x509.load_pem_x509_certificate(certpath.read_bytes())
  remaining=cert.not_valid_after-datetime.datetime.utcnow()
  if cert.issuer!=cert.subject and remaining>datetime.timedelta(days=2):print('Certificate not due');return
 account=R/'secrets/acme-account.pem'
 if not account.exists():
  key=rsa.generate_private_key(public_exponent=65537,key_size=3072)
  atomic(account,key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
 key=serialization.load_pem_private_key(account.read_bytes(),password=None);numbers=key.public_key().public_numbers()
 encodeint=lambda n:b64(n.to_bytes((n.bit_length()+7)//8,'big'))
 jwk={'e':encodeint(numbers.e),'kty':'RSA','n':encodeint(numbers.n)}
 thumb=b64(hashlib.sha256(json.dumps(jwk,separators=(',',':'),sort_keys=True).encode()).digest())
 directory=json.load(raw(DIRECTORY));nonce=None;kid=None
 state=R/'data/acme';state.mkdir(parents=True,exist_ok=True)
 if (state/'account.json').exists():kid=json.loads((state/'account.json').read_text())['kid']
 def post(url,payload=None,new=False):
  nonlocal nonce,kid
  for attempt in range(3):
   if not nonce:
    with raw(directory['newNonce'],method='HEAD') as response:nonce=response.headers['Replay-Nonce']
   protected={'alg':'RS256','nonce':nonce,'url':url}
   protected['jwk' if new else 'kid']=jwk if new else kid
   head=b64(json.dumps(protected,separators=(',',':')).encode());body=b64(json.dumps(payload).encode()) if payload is not None else ''
   signature=b64(key.sign((head+'.'+body).encode(),padding.PKCS1v15(),hashes.SHA256()))
   try:
    response=raw(url,json.dumps({'protected':head,'payload':body,'signature':signature}).encode(),{'Content-Type':'application/jose+json','User-Agent':'YunaNexus-ACME'})
    nonce=response.headers.get('Replay-Nonce');data=response.read();return data,response.headers
   except urllib.error.HTTPError as error:
    nonce=error.headers.get('Replay-Nonce');details=error.read()
    if b'badNonce' in details:continue
    raise RuntimeError('ACME request failed '+str(error.code)+' '+details.decode()[:300])
  raise RuntimeError('ACME nonce retry exhausted')
 if not kid:
  data,headers=post(directory['newAccount'],{'termsOfServiceAgreed':True},new=True);kid=headers['Location']
  atomic(state/'account.json',json.dumps({'kid':kid}).encode())
 data,headers=post(directory['newOrder'],{'identifiers':[{'type':'ip','value':IP}],'profile':'shortlived'});order=json.loads(data);order_url=headers['Location']
 for auth_url in order['authorizations']:
  auth=json.loads(post(auth_url)[0])
  if auth['status']=='valid':continue
  challenge=next(c for c in auth['challenges'] if c['type']=='http-01');token=challenge['token']
  if not re.fullmatch('[A-Za-z0-9_-]+',token):raise ValueError('Invalid challenge token')
  folder=R/'data/acme-webroot/.well-known/acme-challenge';folder.mkdir(parents=True,exist_ok=True)
  for parent in [folder,folder.parent,folder.parent.parent]:parent.chmod(0o755)
  path=folder/token;path.write_text(token+'.'+thumb);path.chmod(0o644)
  try:
   post(challenge['url'],{})
   for attempt in range(60):
    auth=json.loads(post(auth_url)[0])
    if auth['status']=='valid':break
    if auth['status']=='invalid':raise RuntimeError('IP HTTP challenge was rejected')
    time.sleep(2)
   else:raise RuntimeError('IP HTTP challenge timed out')
  finally:path.unlink(missing_ok=True)
 tlskey=serialization.load_pem_private_key((R/'secrets/tls/server.key').read_bytes(),password=None)
 csr=x509.CertificateSigningRequestBuilder().subject_name(x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,IP)]))
 csr=csr.add_extension(x509.SubjectAlternativeName([x509.IPAddress(ipaddress.ip_address(IP))]),critical=False).sign(tlskey,hashes.SHA256())
 post(order['finalize'],{'csr':b64(csr.public_bytes(serialization.Encoding.DER))})
 for attempt in range(60):
  order=json.loads(post(order_url)[0])
  if order['status']=='valid':break
  if order['status']=='invalid':raise RuntimeError('Certificate order invalid')
  time.sleep(2)
 else:raise RuntimeError('Certificate order timed out')
 chain=post(order['certificate'])[0];parsed=x509.load_pem_x509_certificate(chain)
 if parsed.not_valid_after<=datetime.datetime.utcnow():raise RuntimeError('Expired certificate response')
 atomic(certpath,chain,0o640,101)
 subprocess.run([str(R/'ops/compose.sh'),'exec','-T','edge','nginx','-s','reload'],check=True)
 print('IP certificate issued; expires',parsed.not_valid_after.isoformat())
if __name__=='__main__':main()
