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
    // GreetingCard's root is the sticker image itself, not the profile card.
    public static void greeting(View root){}
    public static void profile(Object owner,View root){
        attach(root,4);
        if(root!=null&&root.getTag(STATE) instanceof State){State s=(State)root.getTag(STATE);s.owner=new WeakReference<>(owner);s.schedule();}
    }
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
        WeakReference<Object> owner=new WeakReference<>(null);
        android.widget.LinearLayout profilePanel; TextView following,followers,profileButton;
        final WeakHashMap<View,Geometry> geometry=new WeakHashMap<>();
        final WeakReference<View> root;final int kind;final WeakHashMap<View,Saved> changed=new WeakHashMap<>();boolean queued,watching;int remaining;
        State(View v,int k){root=new WeakReference<>(v);kind=k;}
        public void onViewAttachedToWindow(View v){if(!watching){v.getViewTreeObserver().addOnGlobalLayoutListener(this);watching=true;}schedule();}
        public void onViewDetachedFromWindow(View v){MAIN.removeCallbacks(this);queued=false;if(v.getViewTreeObserver().isAlive())v.getViewTreeObserver().removeOnGlobalLayoutListener(this);watching=false;}
        public void onGlobalLayout(){schedule();}
        void schedule(){if(!queued){queued=true;MAIN.postDelayed(this,180);}}
        public void run(){queued=false;View v=root.get();if(v==null||!v.isAttachedToWindow())return;
            try{if(!enabled()){
                for(java.util.Map.Entry<View,Saved> e:changed.entrySet())if(e.getKey()!=null)e.getValue().restore(e.getKey());
                changed.clear();
                for(java.util.Map.Entry<View,Geometry> e:geometry.entrySet())if(e.getKey()!=null)e.getValue().restore(e.getKey());
                geometry.clear();
                if(profilePanel!=null&&profilePanel.getParent() instanceof ViewGroup)((ViewGroup)profilePanel.getParent()).removeView(profilePanel);
                profilePanel=null;following=null;followers=null;profileButton=null;return;}
                remaining=240;boolean dark=Themes.isDark(),you="you".equals(Design.key());
                int accent=you?Accent.colour():BlaxyStyle.VIOLET,ground=you?Themes.backgroundInUse():(dark?0xff100d1b:0xfff5f0ff);
                int card=blend(ground,accent,dark?.22f:.15f),ink=dark?0xfff4edff:0xff27163d;
                if(ContentArtwork.isImage(v))return;
                paint(v,ground,ink,0,false);
                if(kind==2)storyLayout(v,card,ink);
                else if(kind==1)headerLayout(v,card,ink);
                else if(kind==4)profileLayout(v,card,ink);
                else walk(v,0,card,ink,accent);
            }catch(Throwable e){Diary.note("conversation style: "+e.getClass().getSimpleName());}
        }
        void walk(View v,int depth,int card,int ink,int accent){
            if(v==null||depth>12||remaining--<=0||v.getVisibility()!=View.VISIBLE)return;
            String type=v.getClass().getName().toLowerCase(java.util.Locale.ROOT);
            if(ContentArtwork.isImage(v)||type.contains("sticker")||type.contains("emoji")||type.contains("avatar")||type.contains("drawee")||type.contains("recycler")||type.contains("surfaceview")||type.contains("textureview"))return;
            if(v instanceof EditText){paint(v,card,ink,28,false);}
            else if(v.isClickable()&&type.contains("tuxicon")){paint(v,card,ink,20,true);room(v,10,48);}
            else if(v instanceof TextView){TextView t=(TextView)v;String text=t.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
                if(kind==4&&(text.equals("подписчики")||text.equals("подписки")||text.equals("followers")||text.equals("following"))&&v.getParent() instanceof View){View stats=(View)v.getParent();paint(stats,card,ink,24,false);room(stats,16,64);}
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
        int dp(View v,float x){return Math.round(v.getResources().getDisplayMetrics().density*x);}
        Geometry keepGeometry(View v){Geometry g=geometry.get(v);if(g==null){g=new Geometry(v);geometry.put(v,g);}return g;}
        void size(View v,int w,int h){if(v==null)return;ViewGroup.LayoutParams p=v.getLayoutParams();if(p==null||p.width==w&&p.height==h)return;keepGeometry(v);p.width=w;p.height=h;v.setLayoutParams(p);}
        void padding(View v,int x,int y){if(v.getPaddingLeft()==x&&v.getPaddingRight()==x&&v.getPaddingTop()==y&&v.getPaddingBottom()==y)return;keepGeometry(v);v.setPadding(x,y,x,y);}
        void textSize(TextView v,float sp){float px=sp*v.getResources().getDisplayMetrics().scaledDensity;if(Math.abs(v.getTextSize()-px)<.5f)return;keepGeometry(v);v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,sp);}
        void storyLayout(View root,int card,int ink){
            // Native emoji children remain inside the single reply pill. Like/share stay outside.
            View pill=root.findViewById(0x7f0a69a0);
            if(pill!=null){paint(pill,card,ink,28,true);size(pill,ViewGroup.LayoutParams.MATCH_PARENT,dp(root,44));}
            View hint=root.findViewById(0x7f0a69a1);
            if(hint instanceof TextView){textSize((TextView)hint,15);}
        }
        void headerLayout(View root,int card,int ink){
            View arrow=root.findViewById(0x7f0a54fd);
            if(arrow!=null&&arrow.getParent() instanceof View){View back=(View)arrow.getParent();paint(back,card,ink,28,true);size(back,dp(root,44),dp(root,44));padding(back,dp(root,10),dp(root,10));}
            View call=root.findViewById(0x7f0a396c),more=root.findViewById(0x7f0a38ab);
            boolean pair=call!=null&&more!=null&&call.getVisibility()==View.VISIBLE&&more.getVisibility()==View.VISIBLE&&call.getParent()==more.getParent();
            for(View action:new View[]{call,more})if(action!=null&&action.getVisibility()==View.VISIBLE){paint(action,card,ink,22,true);size(action,dp(root,44),dp(root,44));padding(action,dp(root,10),dp(root,10));if(pair){
                ViewGroup.LayoutParams lp=action.getLayoutParams();if(lp instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams m=(ViewGroup.MarginLayoutParams)lp;int end=action==call?dp(root,2):dp(root,8);if(m.getMarginEnd()!=end){keepGeometry(action);m.setMarginEnd(end);action.setLayoutParams(m);}}
                Saved saved=changed.get(action);if(saved!=null&&saved.painted instanceof RippleDrawable){Drawable d=((RippleDrawable)saved.painted).getDrawable(0);if(d instanceof GradientDrawable){float outer=dp(root,24),inner=dp(root,5);boolean left=action==call;if(root.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL)left=!left;float a=left?outer:inner,b=left?inner:outer;((GradientDrawable)d).setCornerRadii(new float[]{a,a,b,b,b,b,a,a});}}
            }}
        }
        void profileLayout(View root,int card,int ink){
            View avatar=root.findViewById(0x7f0a667e),name=root.findViewById(0x7f0a66fd),host=root.findViewById(0x7f0a673f);
            if(avatar==null||!(host instanceof android.widget.LinearLayout))return;
            int width=root.getWidth()>0?root.getWidth():root.getResources().getDisplayMetrics().widthPixels;
            int side=Math.max(dp(root,96),Math.min(dp(root,156),width-dp(root,96)));
            size(avatar,side,side);
            if(name instanceof TextView)textSize((TextView)name,28);
            Object session=NativeRead.get(NativeRead.field(owner.get(),"LLLIL"),"getValue");
            Object user=NativeRead.get(session,"getSingleChatFromUser");
            Object nFollowing=NativeRead.get(user,"getFollowingCount"),nFollowers=NativeRead.get(user,"getFollowerCount");
            // No guessed or scraped counters: only the native model of this conversation.
            if(!(nFollowing instanceof Number)||!(nFollowers instanceof Number)){
                if(profilePanel!=null&&profilePanel.getVisibility()!=View.GONE)profilePanel.setVisibility(View.GONE);
                return;
            }
            if(profilePanel==null){
                android.content.Context context=root.getContext();
                profilePanel=new android.widget.LinearLayout(context);profilePanel.setOrientation(1);profilePanel.setGravity(android.view.Gravity.CENTER);
                android.widget.LinearLayout stats=new android.widget.LinearLayout(context);stats.setGravity(android.view.Gravity.CENTER);
                following=stat(context,stats,"Подписки");followers=stat(context,stats,"Подписчики");
                profilePanel.addView(stats,new android.widget.LinearLayout.LayoutParams(-2,-2));
                profileButton=new TextView(context);profileButton.setText("Профиль");profileButton.setGravity(android.view.Gravity.CENTER);profileButton.setTextSize(16);
                View nativeOpen=root.findViewById(0x7f0a673e);
                final WeakReference<View> target=new WeakReference<>(nativeOpen!=null?nativeOpen:avatar);
                profileButton.setOnClickListener(v->{View a=target.get();if(a!=null)a.performClick();});
                android.widget.LinearLayout.LayoutParams button=new android.widget.LinearLayout.LayoutParams(dp(root,132),dp(root,44));button.topMargin=dp(root,16);button.bottomMargin=dp(root,12);
                profilePanel.addView(profileButton,button);
                android.widget.LinearLayout.LayoutParams panel=new android.widget.LinearLayout.LayoutParams(-1,-2);panel.topMargin=dp(root,16);
                ((android.widget.LinearLayout)host).addView(profilePanel,panel);
            }
            if(profilePanel.getVisibility()!=View.VISIBLE)profilePanel.setVisibility(View.VISIBLE);
            count(following,((Number)nFollowing).longValue());count(followers,((Number)nFollowers).longValue());
            for(TextView value:new TextView[]{following,followers}){
                ViewGroup tile=(ViewGroup)value.getParent();paint(tile,card,ink,22,false);
                for(int i=0;i<tile.getChildCount();i++)if(tile.getChildAt(i) instanceof TextView){TextView t=(TextView)tile.getChildAt(i);if(t.getCurrentTextColor()!=ink)t.setTextColor(ink);}
            }
            paint(profileButton,card,ink,28,true);
        }
        TextView stat(android.content.Context c,android.widget.LinearLayout row,String label){
            android.widget.LinearLayout tile=new android.widget.LinearLayout(c);tile.setOrientation(1);tile.setGravity(android.view.Gravity.CENTER);tile.setPadding(dp(row,14),dp(row,10),dp(row,14),dp(row,10));
            TextView value=new TextView(c);value.setTextSize(20);value.setGravity(android.view.Gravity.CENTER);
            TextView caption=new TextView(c);caption.setText(label);caption.setTextSize(12);caption.setGravity(android.view.Gravity.CENTER);
            tile.addView(value);tile.addView(caption);
            android.widget.LinearLayout.LayoutParams p=new android.widget.LinearLayout.LayoutParams(-2,-2);p.leftMargin=dp(row,3);p.rightMargin=dp(row,3);row.addView(tile,p);return value;
        }
        void count(TextView text,long value){String next=java.text.NumberFormat.getIntegerInstance(java.util.Locale.getDefault()).format(Math.max(0,value));if(!next.contentEquals(text.getText()))text.setText(next);}
        void paint(View v,int colour,int ink,float radius,boolean ripple){
            if(ContentArtwork.isImage(v))return;
            int signature=colour*31+ink+(int)radius;Saved saved=changed.get(v);if(saved==null){saved=new Saved(v);changed.put(v,saved);}
            if(saved.signature==signature&&saved.painted==v.getBackground())return;
            float d=v.getResources().getDisplayMetrics().density;GradientDrawable shape=new GradientDrawable();shape.setColor(colour);shape.setCornerRadius(radius*d);

            Drawable made=ripple?new RippleDrawable(ColorStateList.valueOf(0x339674ed),shape,null):shape;
            int l=v.getPaddingLeft(),t=v.getPaddingTop(),r=v.getPaddingRight(),b=v.getPaddingBottom();v.setBackground(made);v.setPadding(l,t,r,b);
            if(v instanceof TextView){((TextView)v).setTextColor(ink);((TextView)v).setHintTextColor((ink&0xffffff)|0x99000000);}
            if(v instanceof android.widget.ImageView&&!ContentArtwork.isImage(v))((android.widget.ImageView)v).setImageTintList(ColorStateList.valueOf(ink));
            saved.painted=made;saved.signature=signature;
        }
    }
    private static final class Geometry{
        final int width,height,left,top,right,bottom,marginStart,marginEnd;final float textSize;
        Geometry(View v){ViewGroup.LayoutParams p=v.getLayoutParams();width=p==null?-2:p.width;height=p==null?-2:p.height;marginStart=p instanceof ViewGroup.MarginLayoutParams?((ViewGroup.MarginLayoutParams)p).getMarginStart():0;marginEnd=p instanceof ViewGroup.MarginLayoutParams?((ViewGroup.MarginLayoutParams)p).getMarginEnd():0;left=v.getPaddingLeft();top=v.getPaddingTop();right=v.getPaddingRight();bottom=v.getPaddingBottom();textSize=v instanceof TextView?((TextView)v).getTextSize():0;}
        void restore(View v){ViewGroup.LayoutParams p=v.getLayoutParams();if(p!=null&&(p.width!=width||p.height!=height)){p.width=width;p.height=height;v.setLayoutParams(p);}if(p instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams m=(ViewGroup.MarginLayoutParams)p;m.setMarginStart(marginStart);m.setMarginEnd(marginEnd);v.setLayoutParams(m);}v.setPadding(left,top,right,bottom);if(v instanceof TextView&&textSize>0)((TextView)v).setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,textSize);}
    }
    private static int blend(int a,int b,float f){int r=(int)(((a>>16)&255)*(1-f)+((b>>16)&255)*f),g=(int)(((a>>8)&255)*(1-f)+((b>>8)&255)*f),v=(int)((a&255)*(1-f)+(b&255)*f);return 0xff000000|(r<<16)|(g<<8)|v;}
}
