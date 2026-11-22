package app.blaxyt.mod;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Styles only native input/header/story/greeting components, never the message/video list. */
public final class ConversationStyle {
    static final String KEY="blaxyt_conversation_design";
    private static final int STATE=0x7e0c0740;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final java.util.ArrayList<WeakReference<State>> SESSIONS=new java.util.ArrayList<>();
    private ConversationStyle(){}
    static boolean enabled(){try{return AccountAppearance.prefs(BlaxyRuntime.context()).getBoolean(KEY,Md3.isOn(Md3.KEY_INBOX));}catch(Throwable ignored){return false;}}
    static void set(boolean value){AccountAppearance.prefs(BlaxyRuntime.context()).edit().putBoolean(KEY,value).apply();for(java.util.Iterator<WeakReference<State>> i=SESSIONS.iterator();i.hasNext();){State s=i.next().get();if(s==null)i.remove();else s.schedule();}}
    public static void input(View root){attach(root,0);}
    public static void header(View root){attach(root,1);}
    public static void story(View root){attach(root,2);}
    public static void greeting(View root){attach(root,3);}
    private static void attach(View root,int kind){
        try{if(root==null||root.getTag(STATE) instanceof State)return;
            State state=new State(root,kind);root.setTag(STATE,state);root.addOnAttachStateChangeListener(state);
            for(java.util.Iterator<WeakReference<State>> i=SESSIONS.iterator();i.hasNext();)if(i.next().get()==null)i.remove();
            SESSIONS.add(new WeakReference<>(state));
            if(root.isAttachedToWindow())state.onViewAttachedToWindow(root);
        }catch(Throwable ignored){}
    }
    private static final class Saved{
        final Drawable background;final ColorStateList text,hint,icon;final int left,top,right,bottom,minHeight;Drawable painted;int signature;boolean roomy;
        Saved(View v){background=v.getBackground();text=v instanceof TextView?((TextView)v).getTextColors():null;hint=v instanceof TextView?((TextView)v).getHintTextColors():null;icon=v instanceof android.widget.ImageView?((android.widget.ImageView)v).getImageTintList():null;left=v.getPaddingLeft();top=v.getPaddingTop();right=v.getPaddingRight();bottom=v.getPaddingBottom();minHeight=v.getMinimumHeight();}
        void restore(View v){if(painted==null)return;if(v.getBackground()==painted)v.setBackground(background);if(v instanceof TextView&&text!=null){((TextView)v).setTextColor(text);if(hint!=null)((TextView)v).setHintTextColor(hint);}if(v instanceof android.widget.ImageView)((android.widget.ImageView)v).setImageTintList(icon);if(roomy){v.setPadding(left,top,right,bottom);v.setMinimumHeight(minHeight);roomy=false;}painted=null;signature=0;}
    }
    private static final class State implements View.OnAttachStateChangeListener,ViewTreeObserver.OnGlobalLayoutListener,Runnable{
        final WeakReference<View> root;final int kind;final WeakHashMap<View,Saved> changed=new WeakHashMap<>();boolean queued,watching;int remaining;
        State(View v,int k){root=new WeakReference<>(v);kind=k;}
        public void onViewAttachedToWindow(View v){if(!watching){v.getViewTreeObserver().addOnGlobalLayoutListener(this);watching=true;}schedule();}
        public void onViewDetachedFromWindow(View v){MAIN.removeCallbacks(this);queued=false;if(v.getViewTreeObserver().isAlive())v.getViewTreeObserver().removeOnGlobalLayoutListener(this);watching=false;}
        public void onGlobalLayout(){schedule();}
        void schedule(){if(!queued){queued=true;MAIN.postDelayed(this,180);}}
        public void run(){queued=false;View v=root.get();if(v==null||!v.isAttachedToWindow())return;
            try{if(!enabled()){for(java.util.Map.Entry<View,Saved> e:changed.entrySet())if(e.getKey()!=null)e.getValue().restore(e.getKey());return;}
                remaining=240;boolean dark=Themes.isDark(),you="you".equals(Design.key());
                int accent=you?Accent.colour():BlaxyStyle.VIOLET,ground=you?Themes.backgroundInUse():(dark?0xff100d1b:0xfff5f0ff);
                int card=blend(ground,accent,dark?.22f:.15f),ink=dark?0xfff4edff:0xff27163d;
                paint(v,ground,ink,kind==2?28:0,false);walk(v,0,card,ink,accent);
            }catch(Throwable e){Diary.note("conversation style: "+e.getClass().getSimpleName());}
        }
        void walk(View v,int depth,int card,int ink,int accent){
            if(v==null||depth>12||remaining--<=0||v.getVisibility()!=View.VISIBLE)return;
            String type=v.getClass().getName().toLowerCase(java.util.Locale.ROOT);
            if(type.contains("avatar")||type.contains("drawee")||type.contains("recycler")||type.contains("surfaceview")||type.contains("textureview"))return;
            if(v instanceof EditText){paint(v,card,ink,28,false);}
            else if(v.isClickable()&&(type.contains("tuxicon")||v instanceof android.widget.ImageButton)){paint(v,card,ink,20,true);room(v,10,48);}
            else if(v instanceof TextView){TextView t=(TextView)v;String text=t.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
                if(kind==3&&(text.equals("подписчики")||text.equals("подписки")||text.equals("followers")||text.equals("following"))&&v.getParent() instanceof View){View stats=(View)v.getParent();paint(stats,card,ink,24,false);room(stats,16,64);}
                else if(v.isClickable()&&!(v instanceof EditText)){paint(v,card,ink,28,true);room(v,16,48);}
                else if(kind==2&&(text.startsWith("написать")||text.startsWith("send message")||text.startsWith("отправить сообщение")))paint(v,card,ink,28,true);
            }
            if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)walk(group.getChildAt(i),depth+1,card,ink,accent);}
        }
        void room(View v,int horizontal,int minimum){
            ViewGroup.LayoutParams p=v.getLayoutParams();Saved saved=changed.get(v);
            // Fixed native controls retain their measured size; only content-sized cards get extra space.
            if(saved==null||saved.roomy||p==null||p.height!=ViewGroup.LayoutParams.WRAP_CONTENT)return;
            float d=v.getResources().getDisplayMetrics().density;v.setPadding(Math.max(saved.left,Math.round(horizontal*d)),Math.max(saved.top,Math.round(8*d)),Math.max(saved.right,Math.round(horizontal*d)),Math.max(saved.bottom,Math.round(8*d)));v.setMinimumHeight(Math.max(saved.minHeight,Math.round(minimum*d)));saved.roomy=true;
        }
        void paint(View v,int colour,int ink,float radius,boolean ripple){
            int signature=colour*31+ink+(int)radius;Saved saved=changed.get(v);if(saved==null){saved=new Saved(v);changed.put(v,saved);}
            if(saved.signature==signature&&saved.painted==v.getBackground())return;
            float d=v.getResources().getDisplayMetrics().density;GradientDrawable shape=new GradientDrawable();shape.setColor(colour);shape.setCornerRadius(radius*d);
            if(radius>0){shape.setStroke(Math.max(1,Math.round(d)),0x239674ed);}
            Drawable made=ripple?new RippleDrawable(ColorStateList.valueOf(0x339674ed),shape,null):shape;
            int l=v.getPaddingLeft(),t=v.getPaddingTop(),r=v.getPaddingRight(),b=v.getPaddingBottom();v.setBackground(made);v.setPadding(l,t,r,b);
            if(v instanceof TextView){((TextView)v).setTextColor(ink);((TextView)v).setHintTextColor((ink&0xffffff)|0x99000000);}
            if(v instanceof android.widget.ImageView)((android.widget.ImageView)v).setImageTintList(ColorStateList.valueOf(ink));
            saved.painted=made;saved.signature=signature;
        }
    }
    private static int blend(int a,int b,float f){int r=(int)(((a>>16)&255)*(1-f)+((b>>16)&255)*f),g=(int)(((a>>8)&255)*(1-f)+((b>>8)&255)*f),v=(int)((a&255)*(1-f)+(b&255)*f);return 0xff000000|(r<<16)|(g<<8)|v;}
}
