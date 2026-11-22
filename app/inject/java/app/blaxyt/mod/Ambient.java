package app.blaxyt.mod;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.*;
import android.view.animation.AccelerateDecelerateInterpolator;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/** Colours native player padding, behind video and controls. No overlay, screenshot or network access. */
final class Ambient {
    static final String KEY="blaxyt_video_ambient";
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static volatile Boolean chosen;
    private static Session current;
    private static boolean copying;
    private Ambient() {}
    static boolean enabled(){
        if(chosen!=null)return chosen;
        if(BlaxyRuntime.context()==null)return false;
        try{chosen=AccountAppearance.prefs(BlaxyRuntime.context()).getBoolean(KEY,false);}catch(Throwable ignored){return false;}
        return chosen;
    }
    static void set(boolean value){
        AccountAppearance.prefs(BlaxyRuntime.context()).edit().putBoolean(KEY,value).apply();chosen=value;
        if(!value)clear();
    }
    static void reload(){chosen=null;clear();}
    static void clear(){if(Looper.myLooper()!=Looper.getMainLooper()){MAIN.post(Ambient::clear);return;}if(current!=null){current.dispose();current=null;}}
    static void paused(Activity activity){if(current!=null&&current.belongs(activity))clear();}
    static void paused(){if(current!=null)current.stopAnimation();}
    static void video(View video,int generation){
        try{
        if(!enabled()){clear();return;}
        if(video==null||!video.isAttachedToWindow()||!video.isShown()||!video.hasWindowFocus())return;
        if(current==null||current.video.get()!=video){if(current!=null)current.dispose();current=new Session(video,generation);}
        current.sample(generation);
        }catch(Throwable unavailable){clear();chosen=false;}
    }
    private static final class Layer {
        final WeakReference<View> view;final Drawable original;final GradientDrawable background;
        Layer(View v,int[] colours){view=new WeakReference<>(v);original=v.getBackground();background=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,colours.clone());put(v,background);}
        static void put(View v,Drawable d){int l=v.getPaddingLeft(),t=v.getPaddingTop(),r=v.getPaddingRight(),b=v.getPaddingBottom();v.setBackground(d);v.setPadding(l,t,r,b);}
        void colour(int[] colours){View v=view.get();if(v!=null){if(v.getBackground()!=background&&v.getBackground()!=original)return;background.setColors(colours.clone());if(v.getBackground()!=background)put(v,background);}}
        void restore(){View v=view.get();if(v!=null&&v.getBackground()==background)put(v,original);}
    }
    private static final class Session implements View.OnAttachStateChangeListener {
        final WeakReference<View> video;
        final ArrayList<Layer> layers=new ArrayList<>();
        final int[] pixels=new int[24*24];
        int[] colours={0xff171021,0xff191122,0xff100e18};
        Bitmap sample;ValueAnimator animator;long sampled=-1000;int generation;boolean disposed,pending;
        Session(View v,int g){video=new WeakReference<>(v);generation=g;v.addOnAttachStateChangeListener(this);bind();}
        boolean belongs(Activity a){View v=video.get();return v!=null&&a.getWindow()!=null&&v.getRootView()==a.getWindow().getDecorView();}
        void bind(){
            View v=video.get();if(v==null)return;
            // Stay inside one feed cell, never style the pager, comments or the activity window.
            View root=v.getRootView();ViewParent p=v.getParent();
            for(int depth=0;depth<6&&p instanceof ViewGroup;depth++){
                ViewGroup group=(ViewGroup)p;String name=group.getClass().getName().toLowerCase(java.util.Locale.ROOT);
                if(group==root||name.contains("recycler")||name.contains("viewpager")||name.contains("scrollview"))break;
                Drawable bg=group.getBackground();if(bg==null||bg instanceof ColorDrawable)layers.add(new Layer(group,colours));
                if(group.getHeight()>=root.getHeight()*.72f&&group.getWidth()>=root.getWidth()*.85f)break;
                p=group.getParent();
            }
        }
        boolean valid(int ticket){View v=video.get();return !disposed&&current==this&&enabled()&&generation==ticket&&GlassVideo.generation()==ticket&&v!=null&&v.isAttachedToWindow()&&v.isShown()&&v.hasWindowFocus()&&GlassVideo.render(v.getRootView())==v;}
        void sample(int nextGeneration){
            if(generation!=nextGeneration){generation=nextGeneration;sampled=-1000;}
            View v=video.get();long now=SystemClock.uptimeMillis();
            if(!valid(generation)||copying||pending||now-sampled<450||!GlassVideo.playing()||!GlassVideo.frameReady(v))return;
            sampled=now;int ticket=generation;
            try{
                if(sample==null)sample=Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888);
                if(v instanceof TextureView&&((TextureView)v).isAvailable()){
                    if(((TextureView)v).getBitmap(sample)!=null)accept(ticket);
                }else if(Build.VERSION.SDK_INT>=24){
                    Surface surface=GlassVideo.surface();if(surface==null)return;
                    copying=pending=true;
                    PixelCopy.request(surface,sample,result->{
                        copying=pending=false;
                        if(disposed){recycle();return;}
                        if(result==PixelCopy.SUCCESS)accept(ticket);
                    },MAIN);
                }
            }catch(Throwable unavailable){copying=pending=false;sampled=now+1500;}
        }
        void accept(int ticket){
            if(!valid(ticket)||sample==null)return;
            sample.getPixels(pixels,0,24,0,0,24,24);int[] next=AmbientPalette.colours(pixels,24,24);
            int delta=0;for(int i=0;i<3;i++)delta=Math.max(delta,AmbientPalette.distance(colours[i],next[i]));
            if(delta<3)return;
            stopAnimation();int[] from=colours.clone();
            animator=ValueAnimator.ofFloat(0,1);animator.setDuration(650);animator.setInterpolator(new AccelerateDecelerateInterpolator());
            animator.addUpdateListener(a->{if(!valid(ticket)){stopAnimation();return;}float f=(Float)a.getAnimatedValue();for(int i=0;i<3;i++)colours[i]=AmbientPalette.mix(from[i],next[i],f);for(Layer layer:layers)layer.colour(colours);});
            animator.start();
        }
        void stopAnimation(){if(animator!=null){animator.cancel();animator.removeAllUpdateListeners();animator=null;}}
        void recycle(){if(sample!=null){sample.recycle();sample=null;}}
        void dispose(){if(disposed)return;disposed=true;stopAnimation();View v=video.get();if(v!=null)v.removeOnAttachStateChangeListener(this);for(Layer layer:layers)layer.restore();layers.clear();if(!pending)recycle();}
        public void onViewAttachedToWindow(View v){}
        public void onViewDetachedFromWindow(View v){dispose();if(current==this)current=null;}
    }
}
