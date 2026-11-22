"""Exercise APK streaming and range/error paths on an isolated loopback HTTP server."""
import hashlib,json,tempfile,threading,urllib.request,urllib.error
from pathlib import Path
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from app_updates import AppUpdates
root=Path(tempfile.mkdtemp(prefix='blaxyt-updates-'));service=AppUpdates(root);checks=0
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if not service.handle(self):self.send_error(404)
    def log_message(self,*args):pass
server=ThreadingHTTPServer(('127.0.0.1',0),Handler);threading.Thread(target=server.serve_forever,daemon=True).start()
def get(path,range=None):
    request=urllib.request.Request('http://127.0.0.1:'+str(server.server_port)+path,headers={'Range':range} if range else {})
    try:
        with urllib.request.urlopen(request,timeout=5) as r:return r.status,r.read(),r.headers
    except urllib.error.HTTPError as r:return r.code,r.read(),r.headers
def check(b):
    global checks
    assert b;checks+=1
try:
    check(get('/v1/update')[0]==503)
    raw=bytes(range(256))*8192;digest=hashlib.sha256(raw).hexdigest();name=digest+'.apk'
    (root/name).write_bytes(raw)
    manifest={'schema':1,'sha256':digest,'bytes':len(raw)};(root/'latest.json').write_text(json.dumps(manifest))
    status,body,headers=get('/v1/update');check(status==200 and json.loads(body)==manifest);check(headers['Cache-Control']=='no-store')
    status,body,headers=get('/v1/update/'+name);check(status==200 and body==raw);check(int(headers['Content-Length'])==len(raw))
    check(headers['Content-Type']=='application/vnd.android.package-archive')
    status,body,headers=get('/v1/update/'+name,'bytes=100-199');check(status==206 and body==raw[100:200]);check(headers['Content-Range']==f'bytes 100-199/{len(raw)}')
    check(get('/v1/update/'+name,'bytes=2097100-')[1]==raw[2097100:])
    for invalid in ('bytes=20-10','bytes=9999999-','bytes=-5','bytes=0-3,5-8','bytes=0-9999999'):
        check(get('/v1/update/'+name,invalid)[0]==416)
    for invalid in ('../../shop.sqlite3','latest.json',name+'?x=1','x.apk'):
        check(get('/v1/update/'+invalid)[0]==404)
    for i in range(3):service.slots.acquire()
    check(get('/v1/update/'+name)[0]==503)
    for i in range(3):service.slots.release()
    check(get('/v1/update/'+name,'bytes=0-0')[0]==206)
    manifest['bytes']+=1;(root/'latest.json').write_text(json.dumps(manifest));check(get('/v1/update')[0]==503)
    print(str(checks)+' isolated update HTTP checks passed; no production changes.')
finally:server.shutdown();server.server_close()
