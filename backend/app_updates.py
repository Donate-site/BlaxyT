"""Public, immutable APK downloads. Publication is a local operator action, never an HTTP write."""
import json
import re
import threading
from pathlib import Path


class AppUpdates:
    def __init__(self,directory):
        self.directory=Path(directory)
        self.slots=threading.BoundedSemaphore(3)

    def handle(self,handler):
        path=handler.path
        if path!='/v1/update' and not path.startswith('/v1/update/'):return False
        if path=='/v1/update':
            try:
                raw=(self.directory/'latest.json').read_bytes()
                if len(raw)>16000:raise ValueError()
                data=json.loads(raw)
                digest=data['sha256']
                if not re.fullmatch('[0-9a-f]{64}',digest):raise ValueError()
                if (self.directory/(digest+'.apk')).stat().st_size!=data['bytes']:raise ValueError()
            except (OSError,ValueError,KeyError,TypeError):
                handler.send_error(503,'Release unavailable');return True
            handler.send_response(200);handler.send_header('Content-Type','application/json; charset=utf-8')
            handler.send_header('Cache-Control','no-store');handler.send_header('Content-Length',str(len(raw)))
            handler.end_headers();handler.wfile.write(raw);return True
        name=path[len('/v1/update/'):]
        if not re.fullmatch(r'[0-9a-f]{64}\.apk',name):handler.send_error(404);return True
        file=self.directory/name
        if not file.is_file():handler.send_error(404);return True
        if not self.slots.acquire(blocking=False):
            handler.send_response(503);handler.send_header('Retry-After','30');handler.send_header('Content-Length','0');handler.end_headers();return True
        try:
            size=file.stat().st_size;start,end=0,size-1
            byte_range=handler.headers.get('Range')
            if byte_range:
                match=re.fullmatch(r'bytes=([0-9]+)-([0-9]*)',byte_range)
                if match:
                    start=int(match[1]);end=int(match[2]) if match[2] else size-1
                if not match or start>end or start>=size or end>=size:
                    handler.send_response(416);handler.send_header('Content-Range','bytes */'+str(size));handler.send_header('Content-Length','0');handler.end_headers();return True
            handler.connection.settimeout(30)
            handler.send_response(206 if byte_range else 200)
            handler.send_header('Content-Type','application/vnd.android.package-archive')
            handler.send_header('X-Content-Type-Options','nosniff')
            handler.send_header('Content-Disposition','attachment; filename="BlaxyT.apk"')
            handler.send_header('Accept-Ranges','bytes');handler.send_header('Cache-Control','public, max-age=31536000, immutable')
            handler.send_header('Content-Length',str(end-start+1))
            if byte_range:handler.send_header('Content-Range',f'bytes {start}-{end}/{size}')
            handler.end_headers()
            with file.open('rb') as stream:
                stream.seek(start);remaining=end-start+1
                while remaining:
                    chunk=stream.read(min(65536,remaining))
                    if not chunk:break
                    handler.wfile.write(chunk);remaining-=len(chunk)
        except (OSError,TimeoutError):pass
        finally:self.slots.release()
        return True
