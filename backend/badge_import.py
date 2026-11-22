"""Opt-in badge copies, verified against public sources by stable TikTok UID.

Imported marks are presentation data only, never local roles or authority.
Only the verified account holder can request their own transfer.
"""
import concurrent.futures
import hashlib
import json
import re
import threading
import time
import urllib.parse
import urllib.request
from pathlib import Path
from identity import LinkError


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise LinkError('Источник перенаправил запрос. Перенос временно недоступен.',503)


class BadgeImports:
    def __init__(self,database,links,images):
        self.database,self.links,self.images=database,links,Path(images)
        self.lock=threading.Lock()
        self.cached_at=0
        self.cached=None
        self.slots=threading.BoundedSemaphore(2)
        self.builtin_images={}

    def prepare(self):
        with self.database() as db:
            db.executescript('''
            CREATE TABLE IF NOT EXISTS imported_badges(
              uid TEXT NOT NULL, id TEXT NOT NULL, source TEXT NOT NULL,
              title TEXT NOT NULL, description TEXT NOT NULL, colour TEXT NOT NULL,
              image TEXT NOT NULL, imported INTEGER NOT NULL, PRIMARY KEY(uid,id));
            CREATE TABLE IF NOT EXISTS import_attempts(
              uid TEXT NOT NULL, source TEXT NOT NULL, attempted INTEGER NOT NULL,
              PRIMARY KEY(uid,source));
            ''')
        self.images.mkdir(parents=True,exist_ok=True)
        seed=Path(__file__).with_name('you_catalogue.json')
        if seed.is_file():self.builtin_images=json.loads(seed.read_text(encoding='utf-8')).get('builtin_images',{})

    @staticmethod
    def fetch(url,maximum=2_000_000,timeout=8):
        parts=urllib.parse.urlsplit(url)
        source_host=parts.hostname in ('margy.narez.xyz','ttyou.app','tiktokyou.ru')
        source_mi=parts.hostname=='fuckramochka.github.io' and (parts.path=='/tiktokmi/api/badges.json' or parts.path.startswith('/tiktokmi/api/icon/')) and '..' not in parts.path
        source_cdn=parts.hostname=='cdn.jsdelivr.net' and parts.path.startswith('/gh/yzewe/tiktokyou-static@main/')
        if parts.scheme!='https' or not (source_host or source_cdn or source_mi) or parts.username or parts.password or parts.port not in (None,443):
            raise LinkError('Неподдерживаемый адрес значка. Перенос не выполнен.',503)
        request=urllib.request.Request(url,headers={'User-Agent':'blaxyT badge compatibility/1.0','Accept':'application/json,image/png,image/webp,image/jpeg'})
        try:
            with urllib.request.build_opener(NoRedirect).open(request,timeout=timeout) as response:
                raw=response.read(maximum+1)
            if len(raw)>maximum:raise ValueError('Too large')
            return raw
        except LinkError:raise
        except Exception:raise LinkError('Источник значков временно недоступен. Сохранённые значки остаются.',503) from None

    def margyt(self,uid):
        with self.lock:
            if self.cached is None or time.time()-self.cached_at>300:
                data=json.loads(self.fetch('https://margy.narez.xyz/api/badges',8_000_000))
                if not isinstance(data,dict) or not isinstance(data.get('badges'),list):raise ValueError('Invalid catalogue')
                self.cached=data['badges'];self.cached_at=time.time()
            rows=list(self.cached)
        result=[]
        for item in rows:
            if not isinstance(item,dict) or uid not in item.get('users',[]):continue
            key=item.get('id','')
            if not isinstance(key,str) or not re.fullmatch(r'[A-Za-z0-9_-]{1,64}',key):raise ValueError('Invalid badge')
            image=item.get('image','')
            if not isinstance(image,str) or len(image)>256 or not re.fullmatch(r'[A-Za-z0-9_.-]+(?:\?v=[A-Za-z0-9_-]+)?',image):raise ValueError('Invalid image')
            result.append({'key':key,'title':item.get('title_ru') or item.get('title') or key,
                'description':item.get('text_ru') or item.get('text') or '',
                'colour':item.get('colour',''),'url':'https://margy.narez.xyz/api/icon/'+image})
        return result

    def you(self,uid):
        data=json.loads(self.fetch('https://ttyou.app/api/v1/web/tiktok?q='+urllib.parse.quote(uid)))
        if not isinstance(data,dict) or str(data.get('uid'))!=uid:raise ValueError('UID mismatch')
        if not isinstance(data.get('taken'),bool):raise ValueError('Ownership status missing')
        if data.get('taken') is not True:return []
        badge=data.get('badge')
        if not isinstance(badge,dict):raise ValueError('Badge missing')
        custom=badge.get('custom') is True
        text=(badge.get('text') or '').replace('${1}','Этот пользователь')
        return [{'key':uid if custom else 'supporter','title':(text or 'Значок саппортера') if custom else 'Значок саппортера',
            'description':text or 'Этот человек поддержал разработку TikTok You',
            'colour':'app' if badge.get('tint')=='app' else '',
            'url':urllib.parse.urljoin('https://ttyou.app/',badge['icon']) if custom and badge.get('icon') else 'builtin:you'}]

    def image(self,url):
        if url=='builtin:you':return self.builtin_images.get('supporter','you_supporter')
        raw=self.fetch(url,timeout=5)
        if raw.startswith(b'\x89PNG\r\n\x1a\n'):ext='png'
        elif raw.startswith(b'\xff\xd8\xff'):ext='jpg'
        elif raw[:4]==b'RIFF' and raw[8:12]==b'WEBP':ext='webp'
        else:raise LinkError('Формат картинки значка пока не поддерживается.',503)
        name=hashlib.sha256(raw).hexdigest()+'.'+ext
        path=self.images/name
        if not path.exists():
            temporary=path.with_suffix(path.suffix+'.tmp-'+str(threading.get_ident()))
            temporary.write_bytes(raw);temporary.replace(path)
        return 'import/'+name

    def authorize(self,db,uid,code,holder):
        session=self.links.auth(db,uid,code,holder)
        row=db.execute('SELECT code FROM identities WHERE uid=?',(uid,)).fetchone()
        if not session['verified'] or not row or row['code']!=code or not self.links.visible(db,uid):
            raise LinkError('Сначала подтвердите свой код blaxy-… в описании профиля.',403)

    def transfer(self,uid,code,holder,source):
        if not self.slots.acquire(blocking=False):raise LinkError('Сейчас выполняется другой перенос. Попробуйте чуть позже.',429)
        try:return self._transfer(uid,code,holder,source)
        finally:self.slots.release()

    def _transfer(self,uid,code,holder,source):
        if source not in ('margyt','you'):raise LinkError('Выберите источник значков.')
        now=int(time.time())
        with self.database() as db:
            db.execute('BEGIN IMMEDIATE')
            self.authorize(db,uid,code,holder)
            last=db.execute('SELECT attempted FROM import_attempts WHERE uid=? AND source=?',(uid,source)).fetchone()
            if last and now-last['attempted']<60:raise LinkError('Повторить перенос можно через минуту.',429)
            db.execute('INSERT INTO import_attempts VALUES(?,?,?) ON CONFLICT(uid,source) DO UPDATE SET attempted=excluded.attempted',(uid,source,now))
        try:
            rows=self.margyt(uid) if source=='margyt' else self.you(uid)
            if len(rows)>32:raise ValueError('Too many badges')
            for r in rows:
                if not isinstance(r['title'],str) or not isinstance(r['description'],str) or not isinstance(r['colour'],str):raise ValueError('Invalid metadata')
                r['title']=r['title'][:160];r['description']=r['description'][:1200]
                if r['colour']!='app' and not re.fullmatch(r'#?[0-9a-fA-F]{6}|',r['colour']):r['colour']=''
            with concurrent.futures.ThreadPoolExecutor(max_workers=8) as workers:
                pictures=list(workers.map(lambda row:self.image(row['url']),rows))
        except LinkError:raise
        except Exception:raise LinkError('Не удалось подтвердить значки в источнике. Старые значки не изменены.',503) from None
        # Commit only after every image and ownership proof passed. No local roles are granted.
        with self.database() as db:
            db.execute('BEGIN IMMEDIATE')
            self.authorize(db,uid,code,holder)
            if rows:db.execute('DELETE FROM imported_badges WHERE uid=? AND source=?',(uid,source))
            for r,image in zip(rows,pictures):
                meta=json.dumps([source,r['key']],ensure_ascii=False)
                bid='import:'+source+':'+hashlib.sha256(meta.encode()).hexdigest()[:24]
                db.execute('INSERT INTO imported_badges VALUES(?,?,?,?,?,?,?,?) '
                    'ON CONFLICT(uid,id) DO UPDATE SET imported=excluded.imported',
                    (uid,bid,source,r['title'],r['description'],r['colour'],image,now))
        return {'ok':True,'count':len(rows),'source':source,'notice':
            ('Перенесено значков: '+str(len(rows))+'. Названия, картинки и источник сохранены.') if rows else
            'Источник не подтвердил значков для этого ID TikTok. Уже сохранённые значки остаются.'}

    def feed(self,db):
        catalogue={};users={}
        for row in db.execute('SELECT * FROM imported_badges ORDER BY imported,id'):
            if not self.links.visible(db,row['uid']):continue
            catalogue[row['id']]={key:row[key] for key in ('id','source','title','description','colour','image')}
            users.setdefault(row['uid'],[]).append(row['id'])
        return list(catalogue.values()),users

    def read_image(self,name):
        if not re.fullmatch(r'[a-f0-9]{64}\.(png|jpg|webp)',name):return None
        path=self.images/name
        return path.read_bytes() if path.is_file() else None
