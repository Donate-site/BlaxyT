"""Local server operator CLI. Never grants privileges through public HTTP or bot input."""
import argparse
import time
from shop import UID, database, prepare


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('uid', help='Verified stable numeric TikTok UID, not a username')
    parser.add_argument('badge', choices=['developer', 'supporter', 'ambassador', 'helper'])
    parser.add_argument('--revoke', action='store_true')
    parser.add_argument('--expires', type=int, default=0)
    args = parser.parse_args()
    if not UID.fullmatch(args.uid) or args.expires < 0:
        parser.error('Invalid UID or expiry')
    prepare()
    with database() as db:
        db.execute('INSERT INTO grants(uid,badge,expires,revoked,updated) VALUES(?,?,?,?,?) '
                   'ON CONFLICT(uid,badge) DO UPDATE SET expires=excluded.expires, '
                   'revoked=excluded.revoked,updated=excluded.updated',
                   (args.uid,args.badge,args.expires,int(args.revoke),int(time.time())))
    print('Grant revoked' if args.revoke else 'Grant stored')


if __name__ == '__main__':
    main()
