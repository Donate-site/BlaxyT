"""Check real sampled-colour math, including letterbox padding and readable dark backgrounds."""
from pathlib import Path
import shutil,subprocess,tempfile,unittest

ROOT=Path(__file__).resolve().parents[1]
class AmbientColours(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_video_colours_and_transitions(self):
  with tempfile.TemporaryDirectory(prefix='blaxyt-ambient-') as directory:
   work=Path(directory)
   source=ROOT/'inject/java/app/blaxyt/mod/AmbientPalette.java'
   check=work/'Check.java'
   check.write_text('''package app.blaxyt.mod;
import java.util.Arrays;
public class Check {
 static int count;
 static void ok(boolean b,String name){if(!b)throw new AssertionError(name);count++;}
 public static void main(String[] args){
  ok(AmbientPalette.colours(null,24,24)==null,"null");
  ok(AmbientPalette.colours(new int[1],24,24)==null,"short frame");
  int[] frame=new int[24*24];Arrays.fill(frame,0xffff0000);
  int[] red=AmbientPalette.colours(frame,24,24);
  ok(red[0]==red[1]&&red[1]==red[2],"solid frame");
  ok(((red[0]>>>16)&255)>100&&(red[0]&65535)==0,"red retained");
  for(int y=0;y<24;y++)Arrays.fill(frame,y*24,(y+1)*24,y<8?0xffff0000:y<16?0xff00ff00:0xff0000ff);
  int[] bands=AmbientPalette.colours(frame,24,24);
  ok((bands[0]&0xff0000)>0&&(bands[1]&0xff00)>0&&(bands[2]&255)>0,"vertical colour bands");
  Arrays.fill(frame,0xff000000);for(int y=8;y<16;y++)Arrays.fill(frame,y*24,(y+1)*24,0xff0000ff);
  int[] padded=AmbientPalette.colours(frame,24,24);
  ok((padded[0]&255)>100&&padded[0]==padded[2],"black letterbox not mistaken for video colour");
  Arrays.fill(frame,0x00ff0000);int[] transparent=AmbientPalette.colours(frame,24,24);
  ok(transparent[0]==0xff101016,"transparent frame ignored");
  Arrays.fill(frame,0xff000000);ok(AmbientPalette.colours(frame,24,24)[1]==0xff101016,"black frame stable");
  Arrays.fill(frame,0xffffffff);int white=AmbientPalette.colours(frame,24,24)[0];
  ok((white&255)<=123&&(white>>>24)==255,"dark readable backdrop");
  int a=0xff112244,b=0xff885522;
  ok(AmbientPalette.mix(a,b,0)==a&&AmbientPalette.mix(a,b,1)==b,"transition endpoints");
  ok(AmbientPalette.mix(a,b,-1)==a&&AmbientPalette.mix(a,b,2)==b,"clamped fraction");
  int previous=0;for(int i=0;i<=100;i++){int mixed=AmbientPalette.mix(a,b,i/100f),r=(mixed>>>16)&255;ok(r>=previous&&(mixed>>>24)==255,"monotonic smooth interpolation");previous=r;}
  ok(AmbientPalette.distance(a,a)==0&&AmbientPalette.distance(a,b)==AmbientPalette.distance(b,a),"stable distance");
  System.out.println(count+" ambient colour checks passed");
 }
}''',encoding='utf8')
   subprocess.run(['javac','-encoding','UTF-8','-d',str(work),str(source),str(check)],check=True,capture_output=True)
   result=subprocess.run(['java','-cp',str(work),'app.blaxyt.mod.Check'],check=True,capture_output=True,text=True)
   print(result.stdout.strip())
if __name__=='__main__':unittest.main()
