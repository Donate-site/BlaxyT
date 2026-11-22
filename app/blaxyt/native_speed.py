"""Version-pinned ARM64 playback-rate ceiling for the supplied TikTok 47.2.41.

The native setPlaySpeed guard compares the requested float against 3.0 when
its extended-range flag is zero (20.0 otherwise), then returns -1 for larger
values. Replace only FMOV S2,#3.0 with FMOV S2,#5.0. The lower bound,
comparison, rejection branch, extended mode and audio/video pipeline remain.
Never pattern-patch an unknown library or ship the proprietary library here.
"""
import hashlib

LIBRARY = "lib/arm64-v8a/libttmplayer.so"
ORIGINAL_SHA256 = "e4350dc79afe603fe704ecdd3c9c25e8b8b855d1f5eebd83ba4cfed728470355"
OFFSET = 0x11197c
GUARD_OFFSET = 0x111970
GUARD = bytes.fromhex("41e140bd0090261e095542b90210211e3f010071400c201e2021211e640900542021201e2c090054")
BEFORE = bytes.fromhex("0210211e")
AFTER = bytes.fromhex("0290221e")

def patch(data: bytes) -> bytes:
    if hashlib.sha256(data).hexdigest() != ORIGINAL_SHA256:
        raise ValueError("Unrecognized native player: refusing to change its speed limit")
    if data[GUARD_OFFSET:GUARD_OFFSET+len(GUARD)] != GUARD:
        raise ValueError("Native playback-rate guard changed")
    if data[OFFSET:OFFSET+4] != BEFORE:
        raise ValueError("Native speed instruction changed")
    return data[:OFFSET] + AFTER + data[OFFSET+4:]
