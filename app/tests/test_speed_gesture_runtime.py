"""Exercise the real hold mapping without a TikTok process or network access."""
from pathlib import Path
import shutil,subprocess,tempfile,unittest

ROOT=Path(__file__).resolve().parents[1]
class HoldLifecycle(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_native_reset_and_repeated_two_x(self):
  with tempfile.TemporaryDirectory(prefix='blaxyt-hold-') as directory:
   work=Path(directory)
   sources={
    'android/view/View.java':'package android.view;public class View{}',
    'android/view/MotionEvent.java':'package android.view;public class MotionEvent{}',
    'app/blaxyt/mod/PlayerTools.java':'package app.blaxyt.mod;class PlayerTools{static float value;static float speed(){return value;}}',
    'app/blaxyt/mod/NativeRead.java':'package app.blaxyt.mod;class NativeRead{static Object field(Object o,String n){return ((boolean[])o)[0];}}',
    'app/blaxyt/mod/Check.java':'''package app.blaxyt.mod;public class Check{
     public static void main(String[] args){
      if(SpeedGesture.engineRate(2f)!=2f)throw new AssertionError("ordinary playback");
      for(float rate:new float[]{.5f,1f,1.5f,2f,3f,4f,5f}){
       PlayerTools.value=rate;boolean[] owner={true};SpeedGesture.begin(owner);
       if(SpeedGesture.engineRate(2f)!=rate)throw new AssertionError("native repeated 2x");
       if(SpeedGesture.engineRate(1f)!=1f||SpeedGesture.engineRate(-1f)!=-1f)throw new AssertionError("reset/sentinel changed");
       owner[0]=false;if(SpeedGesture.engineRate(2f)!=2f)throw new AssertionError("release");
      }
      PlayerTools.value=Float.NaN;SpeedGesture.begin(new boolean[]{true});
      if(SpeedGesture.engineRate(2f)!=2f)throw new AssertionError("invalid saved preference");
     }
    }'''}
   for name in ('SpeedGesture','HoldSpeed'):
    sources['app/blaxyt/mod/'+name+'.java']=(ROOT/'inject/java/app/blaxyt/mod'/(name+'.java')).read_text(encoding='utf-8')
   for name,value in sources.items():
    p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(value,encoding='utf-8')
   subprocess.run(['javac','-encoding','UTF-8','-d',str(work),*[str(work/n) for n in sources]],check=True,capture_output=True)
   subprocess.run(['java','-cp',str(work),'app.blaxyt.mod.Check'],check=True,capture_output=True)

if __name__=='__main__':unittest.main()
