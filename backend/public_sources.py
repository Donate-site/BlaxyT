"""Explicitly supported public TikTok-UID catalogues. Never accept a client-supplied URL."""
from dataclasses import dataclass
import re

@dataclass(frozen=True)
class CatalogueSource:
    key: str
    label: str
    catalogue: str
    icons: str
    default_icon: str = ''

CATALOGUES=(
    CatalogueSource('margyt','MargyT','https://margy.narez.xyz/api/badges','https://margy.narez.xyz/api/icon/'),
    CatalogueSource('tiktokmi','TikTok Mi','https://fuckramochka.github.io/tiktokmi/api/badges.json',
                    'https://fuckramochka.github.io/tiktokmi/api/icon/','note.png'),
)
UID=re.compile(r'[0-9]{6,24}\Z')
KEY=re.compile(r'[A-Za-z0-9_-]{1,64}\Z')
IMAGE=re.compile(r'[A-Za-z0-9_.-]+(?:\?v=[A-Za-z0-9_-]+)?\Z')

def parse_catalogue(source,data):
    if not isinstance(data,dict) or not isinstance(data.get('badges'),list) or len(data['badges'])>256:
        raise ValueError('Invalid source catalogue')
    records=[];seen=set();assignments=0
    for item in data['badges']:
        if not isinstance(item,dict):raise ValueError('Invalid source badge')
        key=item.get('id','');owners=item.get('users')
        if not isinstance(key,str) or not KEY.fullmatch(key) or key in seen:raise ValueError('Invalid badge id')
        seen.add(key)
        if not isinstance(owners,list) or any(not isinstance(uid,str) or not UID.fullmatch(uid) for uid in owners):
            raise ValueError('Invalid owner ID')
        assignments+=len(owners)
        if assignments>100000:raise ValueError('Catalogue exceeds assignment limit')
        # New event badges are published before their first assignment. Keep their
        # artwork in the catalogue, but an empty owner set grants them to nobody.
        image=item.get('image') or source.default_icon
        if not isinstance(image,str) or len(image)>256 or not IMAGE.fullmatch(image) or '..' in image:
            raise ValueError('Invalid image')
        records.append(({'key':key,'title':item.get('title_ru') or item.get('title') or key,
                         'description':item.get('text_ru') or item.get('text') or '',
                         'colour':item.get('colour',''),'url':source.icons+image},set(owners)))
    return records
