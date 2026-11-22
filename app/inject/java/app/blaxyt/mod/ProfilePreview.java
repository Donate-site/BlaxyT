package app.blaxyt.mod;

import android.view.View;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

/** One bounded public-profile refresh shared by all visible settings previews. */
final class ProfilePreview {
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final java.util.WeakHashMap<View,Boolean> VIEWS=new java.util.WeakHashMap<>();
    private static volatile Bitmap avatar;
    private static volatile String avatarUrl="";
    private static boolean busy;
    private static long attempted;
    private static final Runnable TICK=new Runnable(){public void run(){
        boolean visible=false;for(View view:VIEWS.keySet())if(view.isShown()&&view.getWindowVisibility()==View.VISIBLE){visible=true;break;}
        if(visible)refresh();
        if(!VIEWS.isEmpty())MAIN.postDelayed(this,30000);
    }};
    static Bitmap avatar(){Bitmap value=avatar;return value==null?Launcher.preview("default"):value;}
    static void attach(View view){VIEWS.put(view,Boolean.TRUE);MAIN.removeCallbacks(TICK);TICK.run();view.invalidate();}
    static void detach(View view){VIEWS.remove(view);if(VIEWS.isEmpty())MAIN.removeCallbacks(TICK);}
    static void changed(){MAIN.post(()->{for(View view:VIEWS.keySet())view.invalidate();});}
    private static void refresh(){
        long now=android.os.SystemClock.elapsedRealtime();if(busy||attempted!=0&&now-attempted<29000)return;
        attempted=now;busy=true;
        Net.away("creator-preview",()->{
            try{ProfileSnapshot.fetch();}catch(Exception ignored){}
            try{
                String url=ProfileSnapshot.read().avatar;
                if(!url.isEmpty()&&!url.equals(avatarUrl)){
                    byte[] raw=Net.bytes(url);
                    if(raw!=null&&raw.length<=4*1024*1024){BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(raw,0,raw.length,opts);
                        if(opts.outWidth>0&&opts.outHeight>0){opts.inSampleSize=1;while(opts.outWidth/opts.inSampleSize>256||opts.outHeight/opts.inSampleSize>256)opts.inSampleSize*=2;
                            opts.inJustDecodeBounds=false;Bitmap made=BitmapFactory.decodeByteArray(raw,0,raw.length,opts);if(made!=null){avatar=made;avatarUrl=url;}}
                    }
                }
            }finally{MAIN.post(()->{busy=false;for(View view:VIEWS.keySet())view.invalidate();});}
        });
    }
}
