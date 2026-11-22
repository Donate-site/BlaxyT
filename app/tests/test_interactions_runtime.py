"""Exercise actual runtime classes with Android doubles. Requires compiled module dependencies."""
from pathlib import Path
import subprocess, os, json, argparse, tempfile

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--classes',required=True,type=Path)
    parser.add_argument('--android',required=True,type=Path)
    args=parser.parse_args()
    root=Path(__file__).resolve().parents[1]
    sources=json.loads((root/'tests/fixtures/interactions.json').read_text(encoding='utf8'))
    with tempfile.TemporaryDirectory(prefix='blaxyt-interactions-') as folder:
        work=Path(folder)
        for name,source in sources.items():
            file=work/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(source,encoding='utf8')
        cp=os.pathsep.join(map(str,[args.classes.resolve(),args.android.resolve()]))
        files=[root/'inject/java/app/blaxyt/mod'/name for name in ['VoiceComments.java','Performance.java','PlaybackSpeeds.java','SpeedGesture.java']]
        subprocess.run(['javac','-encoding','UTF-8','-cp',cp,'-d',str(work/'production'),*map(str,files)],check=True)
        cp=str(work/'production')+os.pathsep+cp
        subprocess.run(['javac','-encoding','UTF-8','-cp',cp,'-d',str(work/'classes'),*[str(work/name) for name in sources]],check=True)
        subprocess.run(['java','-cp',str(work/'classes')+os.pathsep+cp,'app.blaxyt.mod.InteractionChecks'],check=True)
if __name__=='__main__':main()
