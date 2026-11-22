"""Exercise real Ambient with controlled renderer callbacks, not an Android rendering test."""
from pathlib import Path
import shutil,subprocess,tempfile,unittest
from android_doubles import STUBS as BASE
ROOT=Path(__file__).resolve().parents[1]
STUBS=dict(BASE)
STUBS.update({
'android/graphics/drawable/Drawable.java':'package android.graphics.drawable;public class Drawable{}',
'android/graphics/drawable/ColorDrawable.java':'package android.graphics.drawable;public class ColorDrawable extends Drawable{}',
'android/graphics/drawable/GradientDrawable.java':'package android.graphics.drawable;public class GradientDrawable extends Drawable{public enum Orientation{TOP_BOTTOM}public int[] colours;public GradientDrawable(Orientation o,int[] c){colours=c;}public GradientDrawable(){}public void setColor(int c){}public void setCornerRadius(float f){}public void setColors(int[] c){colours=c;}}',
'android/graphics/Bitmap.java':'package android.graphics;public class Bitmap{public enum Config{ARGB_8888}public boolean recycled;public int colour=0xff0000ff;public static int created;public static Bitmap createBitmap(int w,int h,Config c){created++;return new Bitmap();}public int getWidth(){return 24;}public int getHeight(){return 24;}public void recycle(){recycled=true;}public void getPixels(int[] p,int o,int s,int x,int y,int w,int h){if(recycled)throw new AssertionError("recycled capture");java.util.Arrays.fill(p,colour);}}',
'android/os/Looper.java':'package android.os;public class Looper{static Looper main=new Looper();public static Looper myLooper(){return main;}public static Looper getMainLooper(){return main;}}',
'android/os/Handler.java':'package android.os;public class Handler{public Handler(Looper l){}public void post(Runnable r){r.run();}}',
'android/os/SystemClock.java':'package android.os;public class SystemClock{public static long now=1000;public static long uptimeMillis(){return now;}}',
'android/os/Build.java':'package android.os;public class Build{public static class VERSION{public static int SDK_INT=35;}}',
'android/view/animation/AccelerateDecelerateInterpolator.java':'package android.view.animation;public class AccelerateDecelerateInterpolator{}',
'android/animation/ValueAnimator.java':'package android.animation;public class ValueAnimator{public interface AnimatorUpdateListener{void onAnimationUpdate(ValueAnimator a);}AnimatorUpdateListener listener;public static int starts;public static ValueAnimator ofFloat(float a,float b){return new ValueAnimator();}public void setDuration(long l){}public void setInterpolator(Object i){}public void addUpdateListener(AnimatorUpdateListener l){listener=l;}public void removeAllUpdateListeners(){listener=null;}public Object getAnimatedValue(){return Float.valueOf(1);}public void start(){starts++;if(listener!=null)listener.onAnimationUpdate(this);}public void cancel(){}}',
'android/view/Surface.java':'package android.view;public class Surface{}',
'android/view/TextureView.java':'package android.view;public class TextureView extends View{public static int copies;public boolean isAvailable(){return true;}public android.graphics.Bitmap getBitmap(android.graphics.Bitmap b){copies++;return b;}}',
'android/view/SurfaceView.java':'package android.view;public class SurfaceView extends View{}',
'android/view/PixelCopy.java':'package android.view;public class PixelCopy{public static final int SUCCESS=0;public interface OnPixelCopyFinishedListener{void onPixelCopyFinished(int code);}public static OnPixelCopyFinishedListener pending;public static android.graphics.Bitmap bitmap;public static int copies;public static void request(Surface s,android.graphics.Bitmap b,OnPixelCopyFinishedListener l,android.os.Handler h){if(pending!=null)throw new AssertionError("concurrent GPU copies");copies++;bitmap=b;pending=l;}public static void finish(){OnPixelCopyFinishedListener p=pending;pending=null;p.onPixelCopyFinished(0);}}',
'android/view/Window.java':'package android.view;public class Window{public View root;public Window(View v){root=v;}public View getDecorView(){return root;}}',
'android/app/Activity.java':'package android.app;public class Activity{public android.view.Window window;public Activity(android.view.View root){window=new android.view.Window(root);}public android.view.Window getWindow(){return window;}}',
'app/blaxyt/mod/BlaxyRuntime.java':'package app.blaxyt.mod;class BlaxyRuntime{static android.content.Context context(){return new android.content.Context();}}',
'app/blaxyt/mod/AccountAppearance.java':'package app.blaxyt.mod;class AccountAppearance{static P saved=new P();static P prefs(android.content.Context c){return saved;}static class P{boolean on;boolean getBoolean(String k,boolean d){return on;}P edit(){return this;}P putBoolean(String k,boolean b){on=b;return this;}void apply(){}}}',
'app/blaxyt/mod/GlassVideo.java':'package app.blaxyt.mod;class GlassVideo{static android.view.View selected;static int epoch=1;static boolean play=true;static int generation(){return epoch;}static android.view.View render(android.view.View root){return selected;}static boolean playing(){return play;}static boolean frameReady(android.view.View v){return true;}static android.view.Surface surface(){return new android.view.Surface();}}',
})
v=STUBS['android/view/View.java']
v=v.replace('public class View implements ViewParent {','''public class View implements ViewParent {
public int width=300,height=1000,left,top,right,bottom;public boolean focused=true;public android.graphics.drawable.Drawable background;
public interface OnAttachStateChangeListener{void onViewAttachedToWindow(View v);void onViewDetachedFromWindow(View v);}
public java.util.ArrayList<OnAttachStateChangeListener> listeners=new java.util.ArrayList<>();
public void addOnAttachStateChangeListener(OnAttachStateChangeListener l){listeners.add(l);}public void removeOnAttachStateChangeListener(OnAttachStateChangeListener l){listeners.remove(l);}
public boolean hasWindowFocus(){return focused;}public boolean isShown(){return visibility==0;}
public android.graphics.drawable.Drawable getBackground(){return background;}public void setBackground(android.graphics.drawable.Drawable d){background=d;}
public int getPaddingLeft(){return left;}public int getPaddingTop(){return top;}public int getPaddingRight(){return right;}public int getPaddingBottom(){return bottom;}public void setPadding(int a,int b,int c,int d){left=a;top=b;right=c;bottom=d;}
''').replace('public int getWidth(){return 300;}public int getHeight(){return 50;}','public int getWidth(){return width;}public int getHeight(){return height;}')
STUBS['android/view/View.java']=v
HARNESS='''package app.blaxyt.mod;
import android.view.*;import android.graphics.drawable.*;
public class Check {
 static int count;static void ok(boolean b,String n){if(!b)throw new AssertionError(n);count++;}
 static ViewGroup root=new ViewGroup(),cell=new ViewGroup();
 static void select(View v){cell.addView(v,new ViewGroup.LayoutParams(300,200));GlassVideo.selected=v;GlassVideo.epoch++;}
 public static void main(String[] args){
  root.addView(cell,new ViewGroup.LayoutParams(300,800));cell.height=800;Drawable original=new ColorDrawable();cell.setBackground(original);cell.setPadding(1,2,3,4);
  TextureView texture=new TextureView();select(texture);Object click=new Object();texture.setOnClickListener(v->{});Object handler=texture.click;
  Ambient.video(texture,GlassVideo.epoch);ok(TextureView.copies==0&&cell.getBackground()==original,"disabled is inert");
  Ambient.set(true);Ambient.video(texture,GlassVideo.epoch);
  ok(TextureView.copies==1&&cell.getBackground() instanceof GradientDrawable,"current texture sampled");
  ok(texture.click==handler&&cell.left==1&&cell.top==2&&cell.right==3&&cell.bottom==4,"touch and padding preserved");
  Ambient.video(texture,GlassVideo.epoch);ok(TextureView.copies==1,"throttled");
  android.os.SystemClock.now+=500;GlassVideo.play=false;Ambient.video(texture,GlassVideo.epoch);ok(TextureView.copies==1,"paused avoids copies");GlassVideo.play=true;
  Ambient.set(false);ok(cell.getBackground()==original&&texture.listeners.isEmpty(),"toggle restores background/listener");
  Ambient.set(true);SurfaceView surface=new SurfaceView();select(surface);Ambient.video(surface,GlassVideo.epoch);
  ok(PixelCopy.copies==1,"surface path");int before=android.animation.ValueAnimator.starts;
  GlassVideo.epoch++;Ambient.video(surface,GlassVideo.epoch);PixelCopy.finish();
  ok(android.animation.ValueAnimator.starts==before,"stale video callback ignored");
  android.os.SystemClock.now+=500;Ambient.video(surface,GlassVideo.epoch);ok(PixelCopy.copies==2,"new generation captures");
  android.graphics.Bitmap pending=PixelCopy.bitmap;Ambient.set(false);ok(!pending.recycled,"inflight bitmap not recycled early");PixelCopy.finish();
  ok(pending.recycled&&cell.getBackground()==original,"late callback releases buffer without repainting");
  Ambient.set(true);android.os.Build.VERSION.SDK_INT=23;Ambient.video(surface,GlassVideo.epoch);ok(PixelCopy.copies==2,"API23 surface unsupported safely");Ambient.clear();
  android.os.Build.VERSION.SDK_INT=35;select(texture);android.os.SystemClock.now+=500;Ambient.video(texture,GlassVideo.epoch);
  Ambient.paused(new android.app.Activity(root));ok(cell.getBackground()==original&&texture.listeners.isEmpty(),"activity pause restores");
  Ambient.video(texture,GlassVideo.epoch);Drawable other=new ColorDrawable();cell.setBackground(other);Ambient.clear();ok(cell.getBackground()==other,"later native background survives cleanup");
  System.out.println(count+" ambient lifecycle checks passed");
 }
}'''
class AmbientLifecycle(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_capture_restore_and_stale_callbacks(self):
  with tempfile.TemporaryDirectory(prefix='blaxyt-ambient-life-') as directory:
   work=Path(directory);sources=dict(STUBS);sources['app/blaxyt/mod/Check.java']=HARNESS
   for name in ('Ambient','AmbientPalette'):sources['app/blaxyt/mod/'+name+'.java']=(ROOT/'inject/java/app/blaxyt/mod'/(name+'.java')).read_text(encoding='utf8')
   for name,text in sources.items():
    p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf8')
   result=subprocess.run(['javac','-encoding','UTF-8','-d',str(work),*[str(work/n) for n in sources]],capture_output=True,text=True)
   self.assertEqual(result.returncode,0,result.stderr)
   result=subprocess.run(['java','-cp',str(work),'app.blaxyt.mod.Check'],capture_output=True,text=True)
   self.assertEqual(result.returncode,0,result.stdout+result.stderr);print(result.stdout.strip())
if __name__=='__main__':unittest.main()
