package app.blaxyt.mod;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;

/** A cached, reversible budget for our effects; never changes native video frame rate. */
public final class Performance {
    private static volatile int mode=-1;
    private static volatile Boolean small;
    private Performance() {}
    private static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("blaxyt_performance",0);}
    private static int mode(){
        if(mode>=0)return mode;
        Context c=BlaxyRuntime.context();if(c==null)return 0;
        mode=prefs(c).getInt("mode",0);return mode;
    }
    public static boolean light(){
        int value=mode();if(value!=0)return value==1;
        if(small!=null)return small;
        Context c=BlaxyRuntime.context();if(c==null)return false;
        try{ActivityManager manager=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
            small=manager!=null&&(manager.isLowRamDevice()||manager.getMemoryClass()<=128);
        }catch(Throwable ignored){small=false;}
        return small;
    }
    static int frameDelay(){return light()?33:0;}
    static int ambientInterval(){return light()?1000:450;}
    static int glassInterval(){return light()?150:50;}
    private static final ThreadLocal<android.graphics.Rect> visible=new ThreadLocal<android.graphics.Rect>(){
        protected android.graphics.Rect initialValue(){return new android.graphics.Rect();}
    };
    /** Decorative redraws sleep offscreen and share the same low-end budget. */
    public static void redraw(android.view.View view){
        if(view==null||!view.isAttachedToWindow()||!view.isShown()||!view.hasWindowFocus()||!view.getGlobalVisibleRect(visible.get()))return;
        if(light())view.postInvalidateDelayed(33L);else view.postInvalidateOnAnimation();
    }
    static String description(){return mode()==0?"Автоматически · "+(light()?"экономный":"обычный") : mode()==1?"Для слабых устройств":"Все эффекты";}
    static void show(Activity a,Runnable changed){
        Panel p=Panel.with(a,Skin.forSettings(a),"Плавность и нагрузка");
        p.text("Экономный режим ограничивает анимации мода до 30 кадров/с, реже обновляет Ambient и стекло, приостанавливает Motion Blur и вращение аватарок. Видео, голосовые, значки и настройки сохраняются. Авто включает экономию при малом объёме памяти.");
        p.item("tune","Автоматически","Подобрать по памяти устройства",()->set(a,0,changed));
        p.item("bolt","Для слабых устройств","Меньше фоновой работы и тяжёлых эффектов",()->set(a,1,changed));
        p.item("palette","Все эффекты","Исходная частота эффектов BlaxyT",()->set(a,2,changed));
        p.quiet("Закрыть",null);p.show();
    }
    private static void set(Context c,int value,Runnable changed){
        prefs(c).edit().putInt("mode",value).apply();mode=value;
        MotionBlur.refresh();Ambient.clear();GlassBackdrop.changed();
        if(changed!=null)changed.run();
    }
}
