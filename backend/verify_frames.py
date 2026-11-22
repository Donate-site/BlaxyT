"""Exercise real frame storage, authority and source refresh without production writes."""
import copy, hashlib, json, os, tempfile, time
from pathlib import Path
os.environ['SHOP_DB']=str(Path(tempfile.mkdtemp(prefix='blaxyt-frames-'))/'shop.sqlite3')
import shop
from avatar_frames import FREE,CREATOR,EXCLUSIVE
from identity import LinkError
shop.prepare();shop.prepare();service=shop.FRAMES;checks=0
def check(value):
    global checks
    assert value;checks+=1
def denied(call):
    try:call()
    except (LinkError,ValueError):
        global checks
        checks+=1
    else:raise AssertionError('Invalid frame operation accepted')
def feed():
    with shop.database() as db:return service.feed(db)
def session(uid,verified=True):
    code='blaxy-'+hashlib.sha256(uid.encode()).hexdigest()[:16];holder='private-'+uid+'x'*40;now=int(time.time())
    with shop.database() as db:
        db.execute('INSERT INTO link_sessions(code,uid,name,secret_hash,created,verified) VALUES(?,?,?,?,?,?)',
                   (code,uid,'fixture',hashlib.sha256(holder.encode()).hexdigest(),now,int(verified)))
        if verified:db.execute('INSERT INTO identities(uid,code,name,bio_present,checked,next_check) VALUES(?,?,?,1,?,?)',(uid,code,'fixture',now,now+600))
    return dict(uid=uid,code=code,holder=holder)
a=session('123456789');b=session('987654321');owner=session(CREATOR);unverified=session('111222333',False)
check(len(feed()['frames'])==12)
check(feed()['worn'][CREATOR]==EXCLUSIVE)
for frame in FREE:
    check(service.choose(**a,frame=frame)['ok'])
    check(feed()['worn'][a['uid']]==frame)
with shop.database() as db:
    check(db.execute('SELECT count(*) FROM payments').fetchone()[0]==0)
    check(db.execute('SELECT count(*) FROM grants').fetchone()[0]==0)
denied(lambda:service.choose(**b,frame=EXCLUSIVE))
denied(lambda:service.choose(**unverified,frame=FREE[0]))
denied(lambda:service.choose(**dict(a,holder='bad'*20),frame=FREE[0]))
denied(lambda:service.choose(**dict(a,uid=b['uid']),frame=FREE[0]))
denied(lambda:service.choose(**a,frame='margyt:tilcayo'))
check(service.choose(**owner,frame=EXCLUSIVE)['ok'])
check(service.choose(**owner,frame='')['ok'] and feed()['worn'][CREATOR]=='')
check(service.choose(**a,frame='')['ok'] and feed()['worn'][a['uid']]=='')
shop.IMPORTS.image=lambda url:'import/'+hashlib.sha256(url.encode()).hexdigest()+'.png'
source={'frames':[{'id':'tilcayo','image':'frame_tilcayo.png?v=a1','scale':2.05,'title_ru':'Тилкайо'},
                  {'id':'supporter','image':'frame_supporter.png','shapes':{'cookie':'frame_cookie.png'}}],
        'worn':{b['uid']:'tilcayo'},'badges':[{'id':'supporter','users':[a['uid']]}]}
service.ingest(source)
check(feed()['worn'][b['uid']]=='margyt:tilcayo')
check(feed()['frame_holders'][a['uid']]==['margyt:supporter'])
check(service.choose(**b,frame='margyt:tilcayo')['ok'])
denied(lambda:service.choose(**a,frame='margyt:tilcayo'))
check(service.choose(**a,frame='margyt:supporter')['ok'])
check(feed()['worn'][a['uid']]=='margyt:supporter')
check(service.choose(**b,frame=FREE[1])['ok'])
service.ingest(source);check(feed()['worn'][b['uid']]==FREE[1])
check(any('Источник: MargyT' in f.get('text','') for f in feed()['frames']))
snapshot=feed();service.ingest({});check(feed()==snapshot)
for bad in ('https://evil/a.png','../../secret.png','frame.png?evil=yes'):
    broken=copy.deepcopy(source);broken['frames'][0]['image']=bad
    denied(lambda:service.ingest(broken));check(feed()==snapshot)
broken=copy.deepcopy(source);broken['frames'][0]['scale']=float('nan')
denied(lambda:service.ingest(broken));check(feed()==snapshot)
source['worn']={};source['badges']=[];service.ingest(source)
denied(lambda:service.choose(**b,frame='margyt:tilcayo'))
check(a['uid'] not in feed()['worn'])
check(feed()['worn'][b['uid']]==FREE[1])
with shop.database() as db:db.execute('UPDATE identities SET bio_present=0 WHERE uid=?',(b['uid'],))
denied(lambda:service.choose(**b,frame=FREE[0]))
check(b['uid'] not in feed()['worn'])
print(json.dumps({'frame_checks':checks,'production_modified':False}))
