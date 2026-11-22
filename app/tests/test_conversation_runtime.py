"""Regression checks for real conversation styling with Android doubles (not device rendering)."""
from pathlib import Path
import shutil,subprocess,tempfile,unittest
from android_doubles import STUBS as BASE
ROOT=Path(__file__).resolve().parents[1]
S=dict(BASE)
S.update({
'android/content/res/Resources.java':'package android.content.res;public class Resources{public static class Metrics{public float density=1,scaledDensity=1;public int widthPixels=400;}public Metrics getDisplayMetrics(){return new Metrics();}}',
'android/content/res/ColorStateList.java':'package android.content.res;public class ColorStateList{public int c;public static ColorStateList valueOf(int c){ColorStateList x=new ColorStateList();x.c=c;return x;}public int getDefaultColor(){return c;}}',
'android/graphics/drawable/Drawable.java':'package android.graphics.drawable;public class Drawable{}',
'android/graphics/drawable/BitmapDrawable.java':'package android.graphics.drawable;public class BitmapDrawable extends Drawable{}',
'android/graphics/drawable/GradientDrawable.java':'package android.graphics.drawable;public class GradientDrawable extends Drawable{public int colour;public void setColor(int c){colour=c;}public void setCornerRadius(float r){}public void setCornerRadii(float[] r){}}',
'android/graphics/drawable/RippleDrawable.java':'package android.graphics.drawable;public class RippleDrawable extends Drawable{Drawable d;public RippleDrawable(android.content.res.ColorStateList c,Drawable d,Drawable mask){this.d=d;}public Drawable getDrawable(int i){return d;}}',
'android/os/Looper.java':'package android.os;public class Looper{public static Looper getMainLooper(){return new Looper();}}',
'android/os/Handler.java':'package android.os;public class Handler{public static java.util.ArrayList<Runnable> tasks=new java.util.ArrayList<>();public Handler(Looper l){}public void postDelayed(Runnable r,long delay){tasks.add(r);}public void removeCallbacks(Runnable r){tasks.remove(r);}public static void flush(){java.util.ArrayList<Runnable> a=new java.util.ArrayList<>(tasks);tasks.clear();for(Runnable r:a)r.run();}}',
'android/view/ViewTreeObserver.java':'package android.view;public class ViewTreeObserver{public interface OnGlobalLayoutListener{void onGlobalLayout();}public void addOnGlobalLayoutListener(OnGlobalLayoutListener l){}public void removeOnGlobalLayoutListener(OnGlobalLayoutListener l){}public boolean isAlive(){return true;}}',
'android/util/TypedValue.java':'package android.util;public class TypedValue{public static final int COMPLEX_UNIT_PX=0,COMPLEX_UNIT_SP=2;}',
'android/widget/TextView.java':'''package android.widget;import android.content.res.ColorStateList;public class TextView extends android.view.View{public String text="";public float textSize=14;public ColorStateList colour=ColorStateList.valueOf(1),hint=ColorStateList.valueOf(2);public TextView(android.content.Context c){super(c);}public void setText(String s){text=s;writes++;}public CharSequence getText(){return text;}public void setTextSize(float f){textSize=f;writes++;}public void setTextSize(int unit,float f){setTextSize(f);}public float getTextSize(){return textSize;}public void setTextColor(int c){setTextColor(ColorStateList.valueOf(c));}public void setTextColor(ColorStateList c){colour=c;writes++;}public ColorStateList getTextColors(){return colour;}public ColorStateList getHintTextColors(){return hint;}public int getCurrentTextColor(){return colour.c;}public void setHintTextColor(int c){hint=ColorStateList.valueOf(c);}public void setHintTextColor(ColorStateList c){hint=c;}public void setGravity(int g){}}''',
'android/widget/EditText.java':'package android.widget;public class EditText extends TextView{public EditText(android.content.Context c){super(c);}}',
'android/widget/ImageView.java':'package android.widget;public class ImageView extends android.view.View{public android.graphics.drawable.Drawable image;public android.content.res.ColorStateList tint;public android.graphics.drawable.Drawable getDrawable(){return image;}public android.content.res.ColorStateList getImageTintList(){return tint;}public void setImageTintList(android.content.res.ColorStateList t){tint=t;writes++;}}',
'android/widget/LinearLayout.java':'package android.widget;public class LinearLayout extends android.view.ViewGroup{public LinearLayout(android.content.Context c){super(c);}public void setOrientation(int i){}public void setGravity(int g){}public static class LayoutParams extends android.view.ViewGroup.MarginLayoutParams{public LayoutParams(int w,int h){super(w,h);}}}',
'app/blaxyt/mod/BlaxyRuntime.java':'package app.blaxyt.mod;class BlaxyRuntime{static android.content.Context context(){return new android.content.Context();}}',
'app/blaxyt/mod/AccountAppearance.java':'package app.blaxyt.mod;class AccountAppearance{static P p=new P();static P prefs(android.content.Context c){return p;}static class P{boolean on=true;boolean getBoolean(String s,boolean d){return on;}P edit(){return this;}P putBoolean(String s,boolean v){on=v;return this;}void apply(){}}}',
'app/blaxyt/mod/Names.java':'package app.blaxyt.mod;class Names{static String of(android.view.View v){return v.name;}}',
'app/blaxyt/mod/Diary.java':'package app.blaxyt.mod;class Diary{static void note(String s){throw new AssertionError(s);}}',
'app/blaxyt/mod/Themes.java':'package app.blaxyt.mod;class Themes{static boolean isDark(){return true;}static int backgroundInUse(){return 0xff000000;}}',
'app/blaxyt/mod/Design.java':'package app.blaxyt.mod;class Design{static String key(){return "material";}}',
'app/blaxyt/mod/BlaxyStyle.java':'package app.blaxyt.mod;class BlaxyStyle{static int VIOLET=0xff9747ff;}',
'app/blaxyt/mod/Md3.java':'package app.blaxyt.mod;class Md3{static String KEY_INBOX="inbox";static boolean isOn(String s){return true;}}',
'app/blaxyt/mod/NativeRead.java':'package app.blaxyt.mod;class NativeRead{static Object get(Object v,String k){if(v instanceof java.util.Map)return ((java.util.Map)v).get(k);return null;}static Object field(Object v,String k){return get(v,k);}}',
'app/blaxyt/mod/SmartImageView.java':'package app.blaxyt.mod;class SmartImageView extends android.widget.ImageView{}',
'app/blaxyt/mod/TuxIconView.java':'package app.blaxyt.mod;class TuxIconView extends android.widget.ImageView{}',
})
v=S['android/view/View.java'].replace('public class View implements ViewParent {','''public class View implements ViewParent{
public static final int GONE=8,LAYOUT_DIRECTION_RTL=1;public static int writes;public String name="";public int id,left,top,right,bottom,minHeight;public android.graphics.drawable.Drawable bg;java.util.Map<Integer,Object> tags=new java.util.HashMap<>();
public interface OnAttachStateChangeListener{void onViewAttachedToWindow(View v);void onViewDetachedFromWindow(View v);}
public void addOnAttachStateChangeListener(OnAttachStateChangeListener l){}public ViewTreeObserver getViewTreeObserver(){return new ViewTreeObserver();}
public Object getTag(int k){return tags.get(k);}public void setTag(int k,Object v){tags.put(k,v);}public int getVisibility(){return visibility;}public void setVisibility(int v){visibility=v;writes++;}public boolean isClickable(){return clickable||click!=null;}
public boolean performClick(){if(click!=null){click.onClick(this);return true;}return false;}public int getLayoutDirection(){return 0;}
public View findViewById(int i){if(id==i)return this;if(this instanceof ViewGroup)for(View v:((ViewGroup)this).children){View found=v.findViewById(i);if(found!=null)return found;}return null;}
public android.graphics.drawable.Drawable getBackground(){return bg;}public void setBackground(android.graphics.drawable.Drawable d){bg=d;writes++;}
public int getMinimumHeight(){return minHeight;}public void setMinimumHeight(int h){minHeight=h;writes++;}public int getPaddingLeft(){return left;}public int getPaddingRight(){return right;}public int getPaddingTop(){return top;}public int getPaddingBottom(){return bottom;}public void setPadding(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;writes++;}
''')
v=v.replace('params=p;','params=p;writes++;')
S['android/view/View.java']=v
v=S['android/view/ViewGroup.java'].replace('WRAP_CONTENT=-2;','WRAP_CONTENT=-2,MATCH_PARENT=-1;')
v=v.rstrip()[:-1]
v+='''public void addView(View v){addView(v,new LayoutParams(-2,-2));}public int getChildCount(){return children.size();}public View getChildAt(int i){return children.get(i);}public static class MarginLayoutParams extends LayoutParams{public int leftMargin,rightMargin,topMargin,bottomMargin;public MarginLayoutParams(int w,int h){super(w,h);}public int getMarginStart(){return leftMargin;}public int getMarginEnd(){return rightMargin;}public void setMarginStart(int x){leftMargin=x;}public void setMarginEnd(int x){rightMargin=x;}}}'''
S['android/view/ViewGroup.java']=v
HARNESS='''package app.blaxyt.mod;
import android.view.*;import android.widget.*;import android.graphics.drawable.*;import java.util.*;
public class Check{
static int checks,clicks;static void ok(boolean v,String s){if(!v)throw new AssertionError(s);checks++;}
static void flush(){android.os.Handler.flush();}static void layout(View root){((ViewTreeObserver.OnGlobalLayoutListener)root.getTag(0x7e0c0740)).onGlobalLayout();flush();}
static java.util.Map<String,Object> map(String k,Object v){Map<String,Object> m=new HashMap<>();m.put(k,v);return m;}
public static void main(String[] args){
android.content.Context c=new android.content.Context();
SmartImageView sticker=new SmartImageView();Drawable art=new BitmapDrawable();sticker.image=art;sticker.setOnClickListener(v->clicks++);ConversationStyle.greeting(sticker);flush();
ok(sticker.getBackground()==null&&sticker.tint==null&&sticker.image==art,"greeting keeps image colours");sticker.performClick();ok(clicks==1,"sticker sending listener preserved");
ViewGroup input=new ViewGroup();input.addView(sticker);ConversationStyle.input(input);flush();ok(sticker.tint==null&&sticker.image==art,"composer excludes content image");
ImageView bitmap=new ImageView();bitmap.image=new BitmapDrawable();ok(ContentArtwork.isImage(bitmap),"obfuscated bitmap view protected");TuxIconView icon=new TuxIconView();ok(!ContentArtwork.isImage(icon),"native icon still themeable");
ViewGroup story=new ViewGroup(),pill=new ViewGroup(),emoji=new ViewGroup();pill.id=0x7f0a69a0;emoji.id=0x7f0a281e;story.addView(pill,new ViewGroup.LayoutParams(-1,44));pill.addView(emoji);SmartImageView reaction=new SmartImageView();emoji.addView(reaction);ConversationStyle.story(story);flush();ok(pill.getBackground()!=null&&reaction.tint==null&&reaction.getBackground()==null,"one story pill does not paint reaction artwork");
ViewGroup profile=new ViewGroup();LinearLayout host=new LinearLayout(c);host.id=0x7f0a673f;profile.addView(host);LinearLayout open=new LinearLayout(c);open.id=0x7f0a673e;open.setOnClickListener(v->clicks++);host.addView(open);SmartImageView avatar=new SmartImageView();avatar.id=0x7f0a667e;open.addView(avatar,new LinearLayout.LayoutParams(96,96));TextView name=new TextView(c);name.id=0x7f0a66fd;open.addView(name);
Map<String,Object> user=map("getFollowingCount",42);user.put("getFollowerCount",2750);
Object owner=map("LLLIL",map("getValue",map("getSingleChatFromUser",user)));
ConversationStyle.profile(owner,profile);flush();ok(avatar.params.width>96&&avatar.tint==null,"large avatar without tint");
LinearLayout panel=(LinearLayout)host.getChildAt(host.getChildCount()-1);ViewGroup stats=(ViewGroup)panel.getChildAt(0);TextView follower=(TextView)((ViewGroup)stats.getChildAt(1)).getChildAt(0);ok(follower.getText().toString().replaceAll("[^0-9]","").equals("2750"),"native counterpart count");
((TextView)panel.getChildAt(1)).performClick();ok(clicks==2,"profile button delegates original avatar action");
int writes=View.writes;layout(profile);ok(View.writes==writes,"unchanged layout does not trigger restyle loop");
user.put("getFollowerCount",2751);layout(profile);ok(follower.getText().toString().replaceAll("[^0-9]","").equals("2751"),"counter updates after native model change");
ConversationStyle.set(false);flush();ok(avatar.params.width==96&&name.getTextSize()==14&&panel.getParent()==null,"disable restores geometry and removes added panel");
writes=View.writes;layout(profile);ok(View.writes==writes,"disabled layout remains stable");ConversationStyle.set(true);flush();ok(host.getChildCount()==2,"reenabling adds exactly one profile panel");
System.out.println(checks+" conversation checks passed");}}
'''
class ConversationRegression(unittest.TestCase):
 def test_content_geometry_and_lifecycle(self):
  with tempfile.TemporaryDirectory(prefix='blaxyt-conversation-') as directory:
   work=Path(directory);sources=dict(S);sources['app/blaxyt/mod/Check.java']=HARNESS
   for name in ('ConversationStyle','ContentArtwork'):sources['app/blaxyt/mod/'+name+'.java']=(ROOT/'inject/java/app/blaxyt/mod'/(name+'.java')).read_text(encoding='utf8')
   for name,text in sources.items():
    p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf8')
   result=subprocess.run(['javac','-encoding','UTF-8','-d',str(work),*[str(work/n) for n in sources]],capture_output=True,text=True);self.assertEqual(result.returncode,0,result.stderr)
   result=subprocess.run(['java','-cp',str(work),'app.blaxyt.mod.Check'],capture_output=True,text=True);self.assertEqual(result.returncode,0,result.stdout+result.stderr);print(result.stdout.strip())
if __name__=='__main__':unittest.main()
