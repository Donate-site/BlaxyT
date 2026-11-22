from pathlib import Path
import os,tempfile,time,hashlib,sys
root=Path(__file__).resolve().parent
os.environ["SHOP_DB"]=str(Path(tempfile.mkdtemp(prefix="owner-auth-check-",dir=root))/"shop.sqlite3")
import shop,identity
shop.prepare();api=shop.LINKS;now=int(time.time())
owner=api.OWNER_UID;other="123456789123";target="987654321123";secret="S"*40;code="blaxy-"+"a"*16
def fixture(uid,c,h,verified=1,tg=None):
 with shop.database() as db:
  db.execute("INSERT INTO link_sessions(code,uid,name,secret_hash,created,verified) VALUES(?,?,?,?,?,?)",(c,uid,"fixture",hashlib.sha256(h.encode()).hexdigest(),now,verified))
  db.execute("INSERT INTO identities(uid,code,name,bio_present,checked,next_check,telegram) VALUES(?,?,?,1,?,?,?)",(uid,c,"fixture",now,now+600,tg))
fixture(owner,code,secret,tg=api.OWNER_TELEGRAM)
fixture(other,"blaxy-"+"b"*16,"O"*40)
identity.read_profile=lambda name:(target,name,"")
checks=0
def denied(call,status=403):
 global checks
 try:call()
 except identity.LinkError as e:assert e.status==status,(e.status,status)
 else:raise AssertionError("Unauthorized action succeeded")
 checks+=1
assert api.admin_status(owner,code,secret)["owner"];checks+=1
denied(lambda:api.admin_status(owner,code,"X"*40))
denied(lambda:api.admin_status(other,"blaxy-"+"b"*16,"O"*40))
with shop.database() as db:db.execute("INSERT INTO grants VALUES(?, 'developer',0,0,?)",(other,now))
denied(lambda:api.admin_status(other,"blaxy-"+"b"*16,"O"*40))
with shop.database() as db:db.execute("UPDATE identities SET telegram=NULL WHERE uid=?",(owner,))
denied(lambda:api.admin_status(owner,code,secret))
with shop.database() as db:db.execute("UPDATE identities SET telegram=? WHERE uid=?",(api.OWNER_TELEGRAM,owner))
with shop.database() as db:db.execute("UPDATE link_sessions SET verified=0 WHERE uid=?",(owner,))
denied(lambda:api.admin_status(owner,code,secret))
with shop.database() as db:db.execute("UPDATE link_sessions SET verified=1 WHERE uid=?",(owner,))
for badge in api.ADMIN_BADGES:
 resolved=api.admin_resolve(owner,code,secret,"recipient")
 denied(lambda:api.admin_grant(other,"blaxy-"+"b"*16,"O"*40,resolved["token"],badge,"grant"))
 result=api.admin_grant(owner,code,secret,resolved["token"],badge,"grant")
 assert result["ok"];checks+=1
 denied(lambda:api.admin_grant(owner,code,secret,resolved["token"],badge,"grant"),409)
with shop.database() as db:
 assert db.execute("SELECT count(*) FROM grants WHERE uid=? AND revoked=0",(target,)).fetchone()[0]==len(api.ADMIN_BADGES);checks+=1
 assert db.execute("SELECT count(*) FROM admin_audit").fetchone()[0]==len(api.ADMIN_BADGES);checks+=1
 assert db.execute("SELECT count(*) FROM payments").fetchone()[0]==0;checks+=1
resolved=api.admin_resolve(owner,code,secret,"recipient")
api.admin_grant(owner,code,secret,resolved["token"],"ambassador","revoke")
with shop.database() as db:
 assert db.execute("SELECT revoked FROM grants WHERE uid=? AND badge='ambassador'",(target,)).fetchone()[0]==1;checks+=1
resolved=api.admin_resolve(owner,code,secret,"recipient")
with shop.database() as db:db.execute("UPDATE admin_targets SET expires=0")
denied(lambda:api.admin_grant(owner,code,secret,resolved["token"],"developer","grant"),409)
denied(lambda:api.admin_grant(owner,code,secret,"X"*40,"owner","grant"),400)
with shop.database() as db:db.execute("UPDATE identities SET bio_present=0 WHERE uid=?",(owner,))
denied(lambda:api.admin_status(owner,code,secret))
print(str(checks)+" owner authorization, grant, revoke and replay checks passed on isolated database.")
