#!/usr/bin/env python3
"""Issue/renew a short-lived IP certificate using ACME v2 and system cryptography.
No global packages, cron entries, or /etc certificate files are created.
"""
import base64,datetime,hashlib,ipaddress,json,os,re,time,urllib.request,urllib.error,subprocess,socket,ssl,threading
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
  challenge=next(c for c in auth['challenges'] if c['type']=='tls-alpn-01')
  proof=hashlib.sha256((challenge['token']+'.'+thumb).encode()).digest()
  tlskey=serialization.load_pem_private_key((R/'secrets/tls/server.key').read_bytes(),password=None)
  now=datetime.datetime.now(datetime.timezone.utc)
  subject=x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,IP)])
  leaf=x509.CertificateBuilder().subject_name(subject).issuer_name(subject).public_key(tlskey.public_key()).serial_number(x509.random_serial_number()).not_valid_before(now-datetime.timedelta(minutes=1)).not_valid_after(now+datetime.timedelta(days=1))
  leaf=leaf.add_extension(x509.SubjectAlternativeName([x509.IPAddress(ipaddress.ip_address(IP))]),critical=True)
  leaf=leaf.add_extension(x509.UnrecognizedExtension(x509.ObjectIdentifier('1.3.6.1.5.5.7.1.31'),bytes([4,32])+proof),critical=True).sign(tlskey,hashes.SHA256())
  temporary=R/'secrets/tls-alpn.crt';atomic(temporary,leaf.public_bytes(serialization.Encoding.PEM))
  context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);context.load_cert_chain(str(temporary),str(R/'secrets/tls/server.key'));context.set_alpn_protocols(['acme-tls/1'])
  subprocess.run([str(R/'ops/compose.sh'),'stop','edge'],check=True)
  listener=socket.socket();listener.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1);stop=threading.Event();thread=None
  def accept():
   while not stop.is_set():
    try:
     client,_=listener.accept();client.settimeout(3)
     try:context.wrap_socket(client,server_side=True).close()
     except (ssl.SSLError,OSError) as error:
      print('TLS validation handshake:',type(error).__name__,flush=True);client.close()
    except socket.timeout:pass
    except OSError:break
  try:
   listener.bind(('0.0.0.0',443));listener.listen(20);listener.settimeout(1)
   thread=threading.Thread(target=accept,daemon=True);thread.start()
   post(challenge['url'],{})
   for attempt in range(60):
    auth=json.loads(post(auth_url)[0])
    if auth['status']=='valid':break
    if auth['status']=='invalid':raise RuntimeError('IP TLS validation failed: '+json.dumps([c.get('error') for c in auth.get('challenges',[]) if c.get('error')]))
    time.sleep(2)
   else:raise RuntimeError('IP TLS validation timed out')
  finally:
   stop.set();listener.close()
   if thread:thread.join(timeout=4)
   temporary.unlink(missing_ok=True)
   subprocess.run([str(R/'ops/compose.sh'),'up','-d','--no-deps','edge'],check=True)
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
