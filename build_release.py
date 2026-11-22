"""Portable source-build entry point; uses the builder's own signing key."""
from pathlib import Path
import argparse
import os
import sys
import zlib

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / 'app'))
from blaxyt.build import Build
from blaxyt.toolchain import Toolchain
from blaxyt.apkzip import Apk
from blaxyt.axml import Axml
from blaxyt.manifest import set_version_code
import blaxyt.build as build_module


class SourceTools(Toolchain):
    version_code = 2024704069

    def deflate_many(self, blobs):
        return [zlib.compress(blob, 6)[2:-4] for blob in blobs]

    def sign(self, apk_path, *args):
        package = Apk(str(apk_path))
        try:
            manifest = Axml.parse(package.read('AndroidManifest.xml'))
            if manifest.attr_string(manifest.elements('manifest')[0], 'package') != 'com.zhiliaoapp.musically':
                raise ValueError('Expected the original TikTok package as input')
            set_version_code(manifest, self.version_code)
            package.replace('AndroidManifest.xml', manifest.build())
            temporary = Path(apk_path).with_suffix('.unsigned.tmp.apk')
            package.write(str(temporary))
        finally:
            package.close()
        os.replace(temporary, apk_path)
        return super().sign(apk_path, *args)


def main():
    parser = argparse.ArgumentParser(description='Build a BlaxyT modification from a user-provided TikTok APK')
    parser.add_argument('apk', type=Path)
    parser.add_argument('--out', type=Path, default=ROOT / 'build' / 'blaxyT.apk')
    parser.add_argument('--keystore', type=Path)
    parser.add_argument('--alias', default='androiddebugkey')
    parser.add_argument('--dex-helper-jar', type=Path, help='APKEditor.jar containing org.jf.dexlib2 and dependencies')
    parser.add_argument('--version-code', type=int, default=2024704069)
    args = parser.parse_args()
    if not args.apk.is_file():
        parser.error('Input APK does not exist')
    if args.apk.resolve() == args.out.resolve():
        parser.error('Input and output APK must be different files')
    if not 0 < args.version_code <= 2100000000:
        parser.error('Invalid Android version code')
    if args.keystore and not args.keystore.is_file():
        parser.error('Keystore does not exist')
    helper = args.dex_helper_jar or Path(os.environ.get('BLAXYT_DEX_HELPER_JAR', ROOT / 'tools' / 'APKEditor.jar'))
    if not helper.is_file():
        parser.error('Supply --dex-helper-jar /path/to/APKEditor.jar; see README.md')
    os.environ['BLAXYT_DEX_HELPER_JAR'] = str(helper.resolve())
    args.out.parent.mkdir(parents=True, exist_ok=True)
    build_module.AUTHORITY_MARKER = ''
    tools = SourceTools(str(ROOT / 'tools'))
    tools.version_code = args.version_code
    build = Build(str(args.apk.resolve()), str(args.out.resolve()), str(ROOT / 'app'),
                  tools, str(ROOT / 'work'), str(args.keystore.resolve()) if args.keystore else None,
                  keystore_alias=args.alias,
                  keystore_password=os.environ.get('BLAXYT_KEYSTORE_PASSWORD', 'android'),
                  key_password=os.environ.get('BLAXYT_KEY_PASSWORD', 'android'))
    build.run()


if __name__ == '__main__':
    main()
