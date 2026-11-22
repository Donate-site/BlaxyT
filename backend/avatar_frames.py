"""Free BlaxyT frames and source-owned MargyT frames, keyed only by TikTok UID."""
import json
import math
import re
import time
from identity import LinkError, UID

CREATOR = '7662448315568931873'
SKINS = ('classic','glossy','kris','susie','ralsei','noelle','roaring','cosmic','frost','cyber','gold')
TITLES = ('Плюшевый','Глянцевый','Крис','Сьюзи','Ралзей','Ноэль','Рокочущий','Космический','Ледяной','Кибер','Золотой')
FREE = tuple('blaxy:kyksik:'+skin for skin in SKINS)
EXCLUSIVE = 'blaxy:creator'


def builtin():
    rows = [{'id':key,'image':'builtin:'+skin,'scale':1.48,'dy':0,
             'title':'Кыксик · '+title,'text':'Бесплатная рамка BlaxyT','shapes':{}}
            for key,skin,title in zip(FREE,SKINS,TITLES)]
    return rows + [{'id':EXCLUSIVE,'image':'builtin:creator','scale':1.48,'dy':0,
                    'title':'Заряд создателя','text':'Личная рамка @androidtexhhh','shapes':{}}]


class AvatarFrames:
    def __init__(self,database,identity,imports):
        self.database,self.identity,self.imports=database,identity,imports
        self.images={}

    def prepare(self):
        with self.database() as db:
            db.executescript('''
              CREATE TABLE IF NOT EXISTS avatar_frame_choices(uid TEXT PRIMARY KEY, frame TEXT NOT NULL, updated INTEGER NOT NULL);
              CREATE TABLE IF NOT EXISTS avatar_frame_sources(source TEXT PRIMARY KEY, payload TEXT NOT NULL, updated INTEGER NOT NULL);
            ''')

    def ingest(self,data):
        # A missing field is an unavailable source, never a revocation of all frames.
        if 'frames' not in data or 'worn' not in data:return
        frames=data['frames'];worn=data['worn']
        if not isinstance(frames,list) or len(frames)>64 or not isinstance(worn,dict) or len(worn)>100000:
            raise ValueError('Invalid frame catalogue')
        rows=[];ids=set()
        def image(path):
            if not isinstance(path,str) or not re.fullmatch(r'[A-Za-z0-9_.-]+\.(?:png|webp|jpg)(?:\?v=[a-fA-F0-9]+)?',path):
                raise ValueError('Invalid frame image')
            if path not in self.images:self.images[path]=self.imports.image('https://margy.narez.xyz/api/icon/'+path)
            return self.images[path]
        for item in frames:
            key=item.get('id')
            if not isinstance(key,str) or not re.fullmatch(r'[a-zA-Z0-9_-]{1,64}',key) or key in ids:raise ValueError('Invalid frame id')
            ids.add(key)
            scale=float(item.get('scale',1.5));dy=float(item.get('dy',0))
            if not math.isfinite(scale) or not .5<=scale<=3 or not math.isfinite(dy) or abs(dy)>1:raise ValueError('Invalid geometry')
            shapes=item.get('shapes',{})
            if not isinstance(shapes,dict) or len(shapes)>32:raise ValueError('Invalid shapes')
            rows.append({'id':'margyt:'+key,'image':image(item['image']),'scale':scale,'dy':dy,
                         'title':str(item.get('title_ru') or item.get('title') or key)[:160],
                         'text':str(item.get('text_ru') or item.get('text') or '')[:1000]+' · Источник: MargyT',
                         'shapes':{k:image(v) for k,v in shapes.items() if isinstance(k,str) and len(k)<40}})
        active={};held={}
        for uid,key in worn.items():
            if not isinstance(uid,str) or not UID.fullmatch(uid) or key not in ids:raise ValueError('Invalid frame owner')
            active[uid]='margyt:'+key;held[uid]=[active[uid]]
        # These two entitlements are documented by the source. Other manual grants
        # are only knowable when actually published in worn; never guess them.
        for badge in data.get('badges',[]):
            key=badge.get('id')
            if key not in ('owner','supporter') or key not in ids:continue
            for uid in badge.get('users',[]):
                if isinstance(uid,str) and UID.fullmatch(uid):
                    group=held.setdefault(uid,[])
                    if 'margyt:'+key not in group:group.append('margyt:'+key)
        payload=json.dumps({'frames':rows,'worn':active,'held':held},ensure_ascii=False)
        with self.database() as db:
            db.execute('INSERT INTO avatar_frame_sources VALUES(?,?,?) ON CONFLICT(source) DO UPDATE SET payload=excluded.payload,updated=excluded.updated',('margyt',payload,int(time.time())))

    def source(self,db):
        row=db.execute("SELECT payload FROM avatar_frame_sources WHERE source='margyt'").fetchone()
        return json.loads(row[0]) if row else {'frames':[],'worn':{},'held':{}}

    @staticmethod
    def allowed(uid,source):
        return list(FREE)+([EXCLUSIVE] if uid==CREATOR else [])+list(source['held'].get(uid,[]))

    def choose(self,uid,code,holder,frame):
        if not isinstance(frame,str) or len(frame)>96:raise LinkError('Некорректная рамка.')
        with self.database() as db:
            session=self.identity.auth(db,uid,code,holder)
            identity=db.execute('SELECT code FROM identities WHERE uid=?',(uid,)).fetchone()
            if not session['verified'] or not identity or identity['code']!=code or not self.identity.visible(db,uid):
                raise LinkError('Подтвердите профиль кодом blaxy-… Это бесплатно.',403)
            source=self.source(db)
            if frame and frame not in self.allowed(uid,source):raise LinkError('Эта рамка принадлежит другому аккаунту.',403)
            db.execute('INSERT INTO avatar_frame_choices VALUES(?,?,?) ON CONFLICT(uid) DO UPDATE SET frame=excluded.frame,updated=excluded.updated',(uid,frame,int(time.time())))
        return {'ok':True,'worn':frame}

    def feed(self,db):
        source=self.source(db);worn=dict(source['worn'])
        worn[CREATOR]=EXCLUSIVE
        for row in db.execute('SELECT uid,frame FROM avatar_frame_choices'):
            uid,frame=row
            if not self.identity.visible(db,uid):continue
            if not frame:worn[uid]=''
            elif frame in self.allowed(uid,source):worn[uid]=frame
        return {'frames':builtin()+source['frames'],'worn':worn,'frame_holders':source['held']}
