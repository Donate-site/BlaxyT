package app.blaxyt.mod;

import android.app.Activity;
import android.content.Context;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.MotionEvent;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** Optional motion blur: bounded discovery, cached effects, immediate off, no bitmap capture. */
public final class MotionBlur {
    private MotionBlur() {}
    static final String KEY="motion_blur", STRENGTH="motion_blur_strength";
    private static final int TAG=0x7e0d0442, LIMIT=24;
    private static final WeakHashMap<Window,Boolean> WINDOWS=new WeakHashMap<>();
    private static final ArrayList<Watcher> ACTIVE=new ArrayList<>();
    private static Boolean enabled;
    private static int amount=-1;
    static boolean on() {
        if(Build.VERSION.SDK_INT<31||Performance.light()||BlaxyExtensions.active(ExtensionPolicy.LIGHT))return false;
        if(enabled==null){Context c=BlaxyRuntime.context();if(c==null)return false;
            enabled=c.getSharedPreferences(BlaxyRuntime.PREFS,0).getBoolean(KEY,false);}
        return enabled;
    }
    static void set(boolean value) {
        enabled=value;Context c=BlaxyRuntime.context();
        if(c!=null)c.getSharedPreferences(BlaxyRuntime.PREFS,0).edit().putBoolean(KEY,value).apply();
        refresh();
    }
    static void refresh() {
        Runnable apply=()->{
            if(!on())for(Watcher watcher:new ArrayList<>(ACTIVE))watcher.close();
            else {
                Activity activity=Watch.here();if(activity!=null)attach(activity);
                for(Window window:new ArrayList<>(WINDOWS.keySet()))if(window!=null)attach(window);
            }
        };
        if(Looper.myLooper()==Looper.getMainLooper())apply.run();
        else new android.os.Handler(Looper.getMainLooper()).post(apply);
    }
    static int strength() {
        if(amount<0){Context c=BlaxyRuntime.context();amount=c==null?35:c.getSharedPreferences(BlaxyRuntime.PREFS,0).getInt(STRENGTH,35);}
        return Math.max(0,Math.min(100,amount));
    }
    static void setStrength(int value) {
        amount=Math.max(0,Math.min(100,value));Context c=BlaxyRuntime.context();
        if(c!=null)c.getSharedPreferences(BlaxyRuntime.PREFS,0).edit().putInt(STRENGTH,amount).apply();
    }
    public static boolean textureVideo(){try{return on();}catch(Throwable ignored){return false;}}
    static void attach(Activity activity){if(activity!=null)attach(activity.getWindow());}
    static void attach(Window window) {
        if(window==null||Build.VERSION.SDK_INT<31)return;
        WINDOWS.put(window,Boolean.TRUE);if(!on())return;
        View root=window.getDecorView();if(root.getTag(TAG) instanceof Watcher)return;
        Watcher watcher=new Watcher(window,root);
        try {
            root.setTag(TAG,watcher);ACTIVE.add(watcher);root.addOnAttachStateChangeListener(watcher);
            root.getViewTreeObserver().addOnGlobalLayoutListener(watcher);
            root.getViewTreeObserver().addOnPreDrawListener(watcher);
            watcher.scan();watcher.observeTouch();
        }catch(Throwable error){watcher.close();Diary.note("motion blur: "+error.getClass().getSimpleName());}
    }
    static void detach(Activity activity) {
        if(activity==null)return;View root=activity.getWindow().getDecorView();
        Object value=root.getTag(TAG);if(value instanceof Watcher)((Watcher)value).close();
        WINDOWS.remove(activity.getWindow());
    }
    static void moving(View view) {
        if(!on()||view==null)return;Object tag=view.getRootView().getTag(TAG);
        if(tag instanceof Watcher)((Watcher)tag).add(view);
    }
    private static final class Mover {
        final WeakReference<View> view;
        WeakReference<View> probe=new WeakReference<>(null);
        WeakReference<View> first=new WeakReference<>(null);
        float sx,sy,px,py,cx,cy,bx,by;
        int applied=0;boolean ready;
        Mover(View view){this.view=new WeakReference<>(view);}
    }
    private static final class Watcher implements ViewTreeObserver.OnGlobalLayoutListener,
            ViewTreeObserver.OnPreDrawListener,View.OnAttachStateChangeListener {
        final WeakReference<View> root;final WeakReference<Window> window;
        final ArrayList<Mover> movers=new ArrayList<>();
        final android.graphics.Rect hit=new android.graphics.Rect();
        Window.Callback previous,proxy;Mover touched;
        WeakReference<View> commentPanel=new WeakReference<>(null);
        boolean scanQueued;
        final Runnable scanLater=()->{scanQueued=false;try{scan();}catch(RuntimeException error){close();}};
        float touchX,touchY,gestureX,gestureY,quality=1f;
        boolean down,closed;long scanned,frame;
        Watcher(Window window,View root){this.window=new WeakReference<>(window);this.root=new WeakReference<>(root);}
        void observeTouch() {
            Window w=window.get();if(w==null)return;previous=w.getCallback();if(previous==null)return;
            final Window.Callback base=previous;final WeakReference<Watcher> reference=new WeakReference<>(this);
            proxy=(Window.Callback)Proxy.newProxyInstance(Window.Callback.class.getClassLoader(),new Class<?>[]{Window.Callback.class},(object,method,args)->{
                Watcher watcher=reference.get();
                if(watcher!=null&&!watcher.closed&&"dispatchTouchEvent".equals(method.getName())&&args!=null&&args.length==1&&args[0] instanceof MotionEvent)
                    try{watcher.touch((MotionEvent)args[0]);}catch(RuntimeException error){watcher.close();}
                try{return method.invoke(base,args);}catch(InvocationTargetException error){throw error.getCause();}
            });w.setCallback(proxy);
        }
        void touch(MotionEvent event) {
            if(!on())return;int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                // A sheet may have appeared since the last throttled layout scan.
                scan();
                down=true;gestureX=gestureY=0;touchX=event.getRawX();touchY=event.getRawY();touched=null;
                for(int i=0;i<movers.size();i++){Mover m=movers.get(i);View v=m.view.get();
                    if(v!=null&&v.getGlobalVisibleRect(hit)&&hit.contains((int)touchX,(int)touchY)){touched=m;break;}}
                if(touched==null&&movers.isEmpty()){View r=root.get();View content=r==null?null:r.findViewById(android.R.id.content);
                    if(content!=null){add(content);for(Mover m:movers)if(m.view.get()==content){touched=m;break;}}}
            }else if(action==MotionEvent.ACTION_MOVE&&down){
                gestureX+=Math.abs(event.getRawX()-touchX);gestureY+=Math.abs(event.getRawY()-touchY);
                touchX=event.getRawX();touchY=event.getRawY();
            }else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){down=false;}
        }
        @Override public void onGlobalLayout(){
            try{
                if(closed)return;
                long wait=150-(SystemClock.uptimeMillis()-scanned);
                if(wait<=0)scan();
                else {View r=root.get();if(r!=null&&!scanQueued){scanQueued=true;r.postDelayed(scanLater,wait);}}
            }catch(RuntimeException error){close();}
        }
        void scan(){
            if(closed)return;scanned=SystemClock.uptimeMillis();
            View r=root.get();if(r==null)return;
            if(scanQueued){r.removeCallbacks(scanLater);scanQueued=false;}
            View panel=CommentStyle.motionPanel(r),old=commentPanel.get();
            if(panel!=old){
                for(Mover m:movers){View v=m.view.get();if(v!=null)clear(v,m);}
                movers.clear();touched=null;gestureX=gestureY=0;
                commentPanel=new WeakReference<>(panel);
            }
            if(panel!=null){
                if(movers.isEmpty())movers.add(new Mover(panel));
                Mover m=movers.get(0);
                View probe=scrollProbe(panel,0,new int[]{600});
                if(m.probe.get()!=probe){m.probe=new WeakReference<>(probe);m.ready=false;}
                return;
            }
            for(int i=movers.size()-1;i>=0;i--){Mover m=movers.get(i);View v=m.view.get();
                if(v==null||!v.isAttachedToWindow()){if(v!=null)clear(v,m);movers.remove(i);}}
            find(r,0,new int[]{1200});
        }
        /** Prefer the actual row list, not the outer pager holding the sheet. */
        View scrollProbe(View view,int depth,int[] budget){
            if(depth>24||budget[0]--<=0||view.getVisibility()!=View.VISIBLE)return null;
            if(view instanceof android.widget.AbsListView)return view;
            for(Class<?> type=view.getClass();type!=null&&type!=View.class;type=type.getSuperclass())
                if(type.getName().endsWith("RecyclerView"))return view;
            View best=scrolls(view)?view:null;
            if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;
                for(int i=0;i<group.getChildCount()&&budget[0]>0;i++){
                    View candidate=scrollProbe(group.getChildAt(i),depth+1,budget);
                    if(candidate!=null&&(best==null||best==view||
                            (long)candidate.getWidth()*candidate.getHeight()>(long)best.getWidth()*best.getHeight()))best=candidate;
                }
            }
            return best;
        }
        boolean scrolls(View view){
            if(view instanceof android.widget.ScrollView||view instanceof android.widget.HorizontalScrollView||view instanceof android.widget.AbsListView)return true;
            for(Class<?> type=view.getClass();type!=null&&type!=View.class;type=type.getSuperclass()){
                String n=type.getName();if(n.endsWith("RecyclerView")||n.endsWith("ViewPager")||n.endsWith("NestedScrollView")||n.endsWith("AndroidComposeView"))return true;}
            return false;
        }
        void find(View view,int depth,int[] budget){
            if(depth>24||budget[0]--<=0||movers.size()>=LIMIT||view.getVisibility()!=View.VISIBLE)return;
            if(scrolls(view)){add(view);return;}
            if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;
                for(int i=0;i<group.getChildCount()&&budget[0]>0&&movers.size()<LIMIT;i++)find(group.getChildAt(i),depth+1,budget);}
        }
        void add(View view){
            if(closed||view==null||movers.size()>=LIMIT)return;
            if(commentPanel.get()!=null)return; // one effect on the foreground sheet
            for(Mover m:movers){View existing=m.view.get();if(existing==view||ancestor(existing,view))return;}
            for(int i=movers.size()-1;i>=0;i--){Mover m=movers.get(i);View existing=m.view.get();
                if(existing!=null&&ancestor(view,existing)){clear(existing,m);movers.remove(i);}}
            movers.add(new Mover(view));
        }
        boolean ancestor(View parent,View child){if(parent==null)return false;
            for(int i=0;child!=null&&i<32;i++){if(child==parent)return true;child=child.getParent() instanceof View?(View)child.getParent():null;}return false;}
        @Override public boolean onPreDraw(){
            try{return drawFrame();}catch(RuntimeException error){close();return true;}
        }
        boolean drawFrame(){
            View r=root.get();if(closed||r==null)return true;if(!on()){close();return true;}
            if(!r.isHardwareAccelerated())return true;
            long now=SystemClock.uptimeMillis();float elapsed=frame==0?16:Math.max(1,now-frame);frame=now;
            if(elapsed>30&&elapsed<250)quality=Math.max(.35f,quality*.9f);else if(elapsed<22)quality=Math.min(1,quality+.01f);
            boolean animate=Motion.enabled(r.getContext())&&strength()>0;
            float density=r.getResources().getDisplayMetrics().density;
            float gain=(.10f+.26f*strength()/100f)*quality,limit=(2f+8f*strength()/100f)*density*quality;
            float decay=(float)Math.exp(-Math.min(250,elapsed)/65f);
            for(int i=0;i<movers.size();i++){
                Mover m=movers.get(i);View view=m.view.get();if(view==null)continue;
                if(!animate||!view.isShown()||!view.isAttachedToWindow()){clear(view,m);m.ready=false;continue;}
                View probe=m.probe.get();if(probe==null||!probe.isAttachedToWindow())probe=view;
                float sx=probe.getScrollX(),sy=probe.getScrollY(),px=view.getX(),py=view.getY(),cx=0,cy=0;
                View child=probe instanceof ViewGroup&&((ViewGroup)probe).getChildCount()>0?((ViewGroup)probe).getChildAt(0):null;
                if(child!=null){cx=child.getX();cy=child.getY();}
                float dx=0,dy=0;
                if(m.ready&&elapsed<200){dx=Math.abs(sx-m.sx)+Math.abs(px-m.px);dy=Math.abs(sy-m.sy)+Math.abs(py-m.py);
                    if(child!=null&&m.first.get()==child){dx+=Math.abs(cx-m.cx);dy+=Math.abs(cy-m.cy);}}
                m.sx=sx;m.sy=sy;m.px=px;m.py=py;m.cx=cx;m.cy=cy;
                if(m.first.get()!=child)m.first=new WeakReference<>(child);m.ready=true;
                if(m==touched){dx=Math.max(dx,gestureX);dy=Math.max(dy,gestureY);}
                float time=16f/Math.max(8,Math.min(50,elapsed));
                m.bx=Math.max(Math.min(limit,dx*time*gain),m.bx*decay);m.by=Math.max(Math.min(limit,dy*time*gain),m.by*decay);
                if(m.bx<.65f)m.bx=0;if(m.by<.65f)m.by=0;
                int bx=Math.min(255,Math.round(m.bx)),by=Math.min(255,Math.round(m.by)),key=(bx<<8)|by;
                if(key!=m.applied){Api31.apply(view,bx,by);m.applied=key;}
                if(key!=0)view.postInvalidateOnAnimation();
            }
            gestureX=gestureY=0;return true;
        }
        void clear(View view,Mover m){try{if(m.applied>0)Api31.apply(view,0,0);}catch(RuntimeException ignored){}m.applied=0;m.bx=m.by=0;}
        void close(){
            if(closed)return;closed=true;
            for(Mover m:movers){View view=m.view.get();if(view!=null)clear(view,m);}movers.clear();
            View r=root.get();if(r!=null){ViewTreeObserver tree=r.getViewTreeObserver();
                r.removeCallbacks(scanLater);scanQueued=false;
                if(tree.isAlive()){tree.removeOnGlobalLayoutListener(this);tree.removeOnPreDrawListener(this);}
                r.removeOnAttachStateChangeListener(this);if(r.getTag(TAG)==this)r.setTag(TAG,null);}
            Window w=window.get();try{if(w!=null&&w.getCallback()==proxy)w.setCallback(previous);}catch(RuntimeException ignored){}
            previous=proxy=null;ACTIVE.remove(this);touched=null;commentPanel.clear();
        }
        @Override public void onViewAttachedToWindow(View view){try{scan();}catch(RuntimeException error){close();}}
        @Override public void onViewDetachedFromWindow(View view){close();}
    }
    private static final class Api31 {
        private static final android.util.LruCache<Integer,RenderEffect> EFFECTS=new android.util.LruCache<>(48);
        static void apply(View view,int x,int y){
            if(x==0&&y==0){view.setRenderEffect(null);return;}
            int key=(x<<8)|y;RenderEffect effect=EFFECTS.get(key);
            if(effect==null){effect=RenderEffect.createBlurEffect(Math.max(.01f,x),Math.max(.01f,y),Shader.TileMode.CLAMP);EFFECTS.put(key,effect);}
            view.setRenderEffect(effect);
        }
    }
}
