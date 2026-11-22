"""Isolated auth, attachment identity, quota, replay, framing and HTTP checks."""
import base64
import concurrent.futures
import hashlib
import http.client
import json
import os
from pathlib import Path
import tempfile
import threading
import time

os.environ['SHOP_DB']=str(Path(tempfile.mkdtemp(prefix='blaxyt-voice-check-'))/'shop.sqlite3')
import shop
from identity import LinkError
from voice import MAX_AUDIO, audio_duration
shop.prepare();shop.prepare();v=shop.VOICES;checks=0

def check(value):
    global checks
    assert value
    checks+=1

def denied(call,status):
    try:call()
    except LinkError as e:check(e.status==status)
    else:raise AssertionError('Unauthorized or malformed request accepted')

def session(uid,verified=True):
    now=int(time.time());code='blaxy-'+uid.zfill(16);holder='private-'+uid+'x'*40
    with shop.database() as db:
        db.execute('INSERT INTO link_sessions(code,uid,name,secret_hash,created,verified) VALUES(?,?,?,?,?,?)',
            (code,uid,'test',hashlib.sha256(holder.encode()).hexdigest(),now,int(verified)))
        if verified:db.execute('INSERT INTO identities(uid,code,name,bio_present,checked,next_check) VALUES(?,?,?,1,?,?)',(uid,code,'test',now,now+600))
    return dict(uid=uid,code=code,holder=holder)

# Framing fixture, not a claim that a device microphone or decoder has been exercised.
def aac(frames=24):
    size=107
    header=bytes((255,241,(1<<6)|(6<<2),1<<6,(size>>3)&255,((size&7)<<5)|31,252))
    return (header+b'\0'*100)*frames

a=session('123456789');b=session('987654321');unverified=session('111222333',False)
data=aac();encoded=base64.b64encode(data).decode();video='7234567890123456789'
payload=dict(a,video=video,request_id='1'*32,audio=encoded)
check(1000<=audio_duration(data)<=1100)
for invalid in (b'',data[:-1],b'http://localhost/a',data+b'bad',aac(2000),data.replace(b'\xff\xf1',b'\xff\xf3',1)):
    denied(lambda raw=invalid:audio_duration(raw),413 if len(invalid)<100 else 400)
denied(lambda:v.handle('upload',dict(payload,holder='fake'*20)),403)
denied(lambda:v.handle('upload',dict(payload,**unverified)),403)
for value in ('../../secret','0','https://x'):
    denied(lambda value=value:v.handle('upload',dict(payload,video=value)),400)
denied(lambda:v.handle('upload',dict(payload,audio='!invalid')),400)
first=v.handle('upload',payload);key=first['id'];check(len(key)==32)
check('[blaxy-voice:'+key+']' in first['text']);check('@BlaxyT' in first['text'])
check(v.handle('upload',payload)['id']==key)
denied(lambda:v.handle('upload',dict(payload,video='999999999')),409)
denied(lambda:v.handle('upload',dict(payload,audio=base64.b64encode(aac(25)).decode())),409)
read=dict(b,id=key,author=a['uid'],video=video)
check(base64.b64decode(v.handle('read',read)['audio'])==data)
check(v.handle('read',read)['sha256']==hashlib.sha256(data).hexdigest())
denied(lambda:v.handle('read',dict(read,author=b['uid'])),403)
denied(lambda:v.handle('read',dict(read,video='111111111')),403)
denied(lambda:v.handle('read',dict(read,holder='wrong'*10)),403)
denied(lambda:v.handle('delete',dict(b,id=key)),403)
check(len(v.handle('list',a)['items'])==1);check(v.handle('list',b)['items']==[])
concurrent_payload=dict(payload,request_id='2'*32)
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    results=list(pool.map(lambda _:v.handle('upload',concurrent_payload)['id'],range(4)))
check(len(set(results))==1);check(len(v.handle('list',a)['items'])==2)
check(v.handle('delete',dict(a,id=key))['deleted'])
denied(lambda:v.handle('read',read),410);denied(lambda:v.handle('upload',payload),410)
with v.storage() as db:check(db.execute('SELECT audio FROM voices WHERE id=?',(key,)).fetchone()[0] is None)
with shop.database() as db:db.execute('UPDATE identities SET bio_present=0 WHERE uid=?',(a['uid'],))
denied(lambda:v.handle('list',a),403)
with shop.database() as db:db.execute('UPDATE identities SET bio_present=1,checked=1 WHERE uid=?',(a['uid'],))
denied(lambda:v.handle('list',a),403)
with shop.database() as db:db.execute('UPDATE identities SET checked=? WHERE uid=?',(int(time.time()),a['uid']))
for i in range(18):v.handle('upload',dict(payload,request_id=format(i+50,'032x')))
denied(lambda:v.handle('upload',dict(payload,request_id='f'*32)),429)
with shop.database() as db:
    check(db.execute('SELECT count(*) FROM grants').fetchone()[0]==0)
    check(db.execute('SELECT count(*) FROM payments').fetchone()[0]==0)
server=shop.LimitedHTTP(('127.0.0.1',0),shop.Feed)
threading.Thread(target=server.serve_forever,daemon=True).start()
def request(path,body,method='POST'):
    c=http.client.HTTPConnection('127.0.0.1',server.server_port,timeout=5)
    c.request(method,path,json.dumps(body),{'Content-Type':'application/json'})
    r=c.getresponse();raw=r.read();status=r.status;c.close();return status,raw
check(request('/v1/voice/list',a)[0]==200)
check(request('/v1/voice/read',read)[0]==410)
check(request('/v1/voice/list',{})[0]==403)
check(request('/v1/voice/list',a,'GET')[0]==404)
check(request('/v1/voice/upload',dict(payload,audio='x'*1500000))[0]==413)
check(request('/v1/link/status',dict(a,padding='x'*9000))[0]==413)
check(request('/v1/voice/unknown',a)[0]==404)
server.shutdown();server.server_close()
print(str(checks)+' voice authorization, identity, replay, quota and HTTP checks passed.')
