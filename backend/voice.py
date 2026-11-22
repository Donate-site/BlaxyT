"""Bounded AAC voice attachments; never submits comments or forwards TikTok credentials."""
import base64
import contextlib
import hashlib
import re
import secrets
import shutil
import sqlite3
import time
from identity import LinkError

MAX_AUDIO = 1024 * 1024
MAX_BODY = 1400000 + 8192
ID = re.compile(r'[a-f0-9]{32}\Z')
UID = re.compile(r'[0-9]{6,24}\Z')
NOTICE = ('Это голосовое сообщение из BlaxyT. Чтобы прослушать, скачайте BlaxyT из ТГК @BlaxyT '
          '(это не шутка, а реальное голосовое для пользователей BlaxyT).')


def audio_duration(raw):
    """Accept only bounded, complete mono/stereo AAC-LC ADTS frames, no playlists/URLs."""
    rates = (96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350)
    pos = frames = 0
    shape = None
    if not 100 <= len(raw) <= MAX_AUDIO:
        raise LinkError('Запись слишком большая или пустая.', 413)
    while pos < len(raw):
        h = raw[pos:pos + 7]
        if len(h) < 7 or h[0] != 255 or h[1] & 0xf6 != 0xf0:
            raise LinkError('Нужна запись AAC, созданная в BlaxyT.')
        rate = (h[2] >> 2) & 15
        channels = ((h[2] & 1) << 2) | (h[3] >> 6)
        size = ((h[3] & 3) << 11) | (h[4] << 3) | (h[5] >> 5)
        header = 7 if h[1] & 1 else 9
        if rate >= len(rates) or channels not in (1, 2) or h[2] >> 6 != 1 or h[6] & 3:
            raise LinkError('Неподдерживаемый формат голоса.')
        if size <= header or pos + size > len(raw):
            raise LinkError('Запись повреждена или не завершена.')
        current = (rate, channels)
        if shape is not None and shape != current:
            raise LinkError('Формат записи изменился.')
        shape = current
        frames += 1
        pos += size
    duration = round(frames * 1024000 / rates[shape[0]])
    if not 300 <= duration <= 61500:
        raise LinkError('Запишите голосовое длительностью от 1 до 60 секунд.')
    return duration


class Voices:
    def __init__(self, database, links, path):
        self.database, self.links, self.path = database, links, path

    @contextlib.contextmanager
    def storage(self):
        db = sqlite3.connect(self.path, timeout=10)
        db.row_factory = sqlite3.Row
        try:
            with db:
                yield db
        finally:
            db.close()

    def prepare(self):
        with self.storage() as db:
            db.executescript('''PRAGMA journal_mode=WAL;
            CREATE TABLE IF NOT EXISTS voices(
              id TEXT PRIMARY KEY, uid TEXT NOT NULL, video TEXT NOT NULL,
              request_id TEXT NOT NULL, digest TEXT NOT NULL, duration INTEGER NOT NULL,
              created INTEGER NOT NULL, deleted INTEGER NOT NULL DEFAULT 0, audio BLOB,
              UNIQUE(uid, request_id));
            CREATE INDEX IF NOT EXISTS voices_owner ON voices(uid,created);''')
        if __import__('os').name != 'nt':
            self.path.chmod(0o600)

    def auth(self, obj):
        uid, code, holder = (obj.get(k, '') for k in ('uid', 'code', 'holder'))
        if not all(isinstance(x, str) for x in (uid, code, holder)) or not UID.fullmatch(uid):
            raise LinkError('Подтвердите профиль BlaxyT.', 403)
        with self.database() as db:
            session = self.links.auth(db, uid, code, holder)
            row = db.execute('SELECT * FROM identities WHERE uid=? AND code=?', (uid, code)).fetchone()
            if not session['verified'] or not row or not row['bio_present'] or row['checked'] < time.time() - 86400:
                raise LinkError('Откройте «Профиль → Подтверждение аккаунта» и проверьте код BlaxyT.', 403)
        return uid

    @staticmethod
    def item(row):
        return {key: row[key] for key in ('id', 'uid', 'video', 'duration', 'created')}

    def handle(self, action, obj):
        uid = self.auth(obj)
        if action == 'upload':
            video, nonce, encoded = (obj.get(k, '') for k in ('video', 'request_id', 'audio'))
            if not isinstance(video, str) or not UID.fullmatch(video) or not isinstance(nonce, str) or not ID.fullmatch(nonce):
                raise LinkError('Не удалось определить ролик. Откройте комментарии заново.')
            if not isinstance(encoded, str) or len(encoded) > MAX_BODY - 8192:
                raise LinkError('Запись слишком большая.', 413)
            try:
                raw = base64.b64decode(encoded, validate=True)
            except (ValueError, TypeError):
                raise LinkError('Запись повреждена.') from None
            duration = audio_duration(raw)
            digest = hashlib.sha256(raw).hexdigest()
            now = int(time.time())
            with self.storage() as db:
                db.execute('BEGIN IMMEDIATE')
                old = db.execute('SELECT * FROM voices WHERE uid=? AND request_id=?', (uid, nonce)).fetchone()
                if old:
                    if old['deleted']:
                        raise LinkError('Эта запись уже удалена.', 410)
                    if old['digest'] != digest or old['video'] != video:
                        raise LinkError('Запрос относится к другой записи.', 409)
                    return dict(self.item(old), text=NOTICE + ' [blaxy-voice:' + old['id'] + ']')
                if db.execute('SELECT count(*) FROM voices WHERE uid=? AND created>?', (uid, now - 3600)).fetchone()[0] >= 20:
                    raise LinkError('За час можно загрузить 20 голосовых. Попробуйте позже.', 429)
                usage = db.execute('SELECT count(*),COALESCE(sum(length(audio)),0) FROM voices WHERE uid=? AND deleted=0', (uid,)).fetchone()
                if usage[0] >= 100 or usage[1] + len(raw) > 25 * 1024 * 1024:
                    raise LinkError('Хранилище голосовых заполнено. Удалите ненужные записи в «Мои голосовые».', 409)
                total = db.execute('SELECT COALESCE(sum(length(audio)),0) FROM voices').fetchone()[0]
                if total + len(raw) > 1024 ** 3 or shutil.disk_usage(self.path.parent).free < 512 * 1024 ** 2:
                    raise LinkError('Хранилище временно заполнено. Запись не отправлена.', 503)
                key = secrets.token_hex(16)
                db.execute('INSERT INTO voices(id,uid,video,request_id,digest,duration,created,audio) VALUES(?,?,?,?,?,?,?,?)',
                           (key, uid, video, nonce, digest, duration, now, raw))
                row = db.execute('SELECT * FROM voices WHERE id=?', (key,)).fetchone()
                return dict(self.item(row), text=NOTICE + ' [blaxy-voice:' + key + ']')
        if action == 'list':
            with self.storage() as db:
                return {'items': [self.item(row) for row in db.execute(
                    'SELECT id,uid,video,duration,created FROM voices WHERE uid=? AND deleted=0 ORDER BY created DESC LIMIT 100', (uid,))]}
        key = obj.get('id', '')
        if not isinstance(key, str) or not ID.fullmatch(key):
            raise LinkError('Неверный номер голосового.')
        with self.storage() as db:
            row = db.execute('SELECT * FROM voices WHERE id=?', (key,)).fetchone()
            if not row or row['deleted']:
                raise LinkError('Голосовое удалено или недоступно.', 410)
            if action == 'delete':
                if row['uid'] != uid:
                    raise LinkError('Можно удалять только свои записи.', 403)
                db.execute('UPDATE voices SET audio=NULL,deleted=1 WHERE id=?', (key,))
                return {'deleted': True}
            if action == 'read':
                # The Android player supplies author/video from the actual bound comment model.
                if obj.get('author') != row['uid'] or obj.get('video') != row['video']:
                    raise LinkError('Это голосовое относится к другому автору или ролику.', 403)
                return dict(self.item(row), audio=base64.b64encode(row['audio']).decode('ascii'), sha256=row['digest'])
        raise LinkError('Неизвестное действие.', 404)
