package app.blaxyt.mod;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Optional first-party modules shipped with the APK; no remote executable catalogue. */
final class BlaxyExtensions {
    private static final String PREFS="blaxyt_extensions_v1";
    static final class Entry {
        final String id,name,about,icon,group;final int bit;
        Entry(String id,String name,String about,String icon,String group,int bit){this.id=id;this.name=name;this.about=about;this.icon=icon;this.group=group;this.bit=bit;}
    }
    static final Entry[] ALL={
        new Entry("light","Лёгкие эффекты","Приостанавливает размытие в движении и вращение аватарок. Формы, цвета и сохранённые настройки остаются. После отключения эффекты возвращаются.","bolt","Для старых телефонов",ExtensionPolicy.LIGHT),
        new Entry("pets","Кыксики • эконом","Ограничивает перерисовку кыксиков примерно до 30 кадров/с. Кормление, рост и скины работают как прежде. Частоту самого видео не меняет.","pets","Для старых телефонов",ExtensionPolicy.PETS),
        new Entry("short","Короткая лента","Скрывает в загружаемой ленте ролики длиннее минуты. Свои публикации, фото и ролики с неизвестной длительностью оставляет. Применяется к следующим загруженным страницам.","play_circle","Для тебя",ExtensionPolicy.SHORT),
        new Entry("break","Пауза для глаз","Мягкое напоминание каждые 20 минут в приложении. Фоновое время не считается; видео не останавливается. После закрытия процесса отсчёт начинается заново.","schedule","Для тебя",ExtensionPolicy.BREAK)
    };
    private static volatile int installed,enabled;
    private static volatile boolean loaded;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final java.util.WeakHashMap<Activity,Boolean> FOREGROUND=new java.util.WeakHashMap<>();
    private static final ExtensionPolicy.ForegroundTime TIME=new ExtensionPolicy.ForegroundTime();
    private static final Runnable REMIND=()->{
        if(!active(ExtensionPolicy.BREAK)||FOREGROUND.isEmpty())return;
        long now=SystemClock.elapsedRealtime();
        if(TIME.remaining(now)==0){Screen.say("BlaxyT · Ты уже 20 минут в приложении. Можно немного отдохнуть от экрана.");TIME.reset();TIME.resume(now);}
        schedule();
    };
    private BlaxyExtensions(){}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,0);}
    static synchronized void load(Context c){
        if(c==null)return;
        installed=prefs(c).getInt("installed",0)&15;enabled=prefs(c).getInt("enabled",0)&installed;loaded=true;
    }
    static boolean active(int bit){if(!loaded)load(BlaxyRuntime.context());return ExtensionPolicy.active(installed,enabled,bit);}
    static boolean installed(Entry e){if(!loaded)load(BlaxyRuntime.context());return (installed&e.bit)!=0;}
    static int count(){if(!loaded)load(BlaxyRuntime.context());return Integer.bitCount(installed);}
    static void install(Context c,Entry e){change(c,installed|e.bit,enabled);}
    static void toggle(Context c,Entry e){change(c,installed,enabled^e.bit);}
    static void remove(Context c,Entry e){change(c,installed&~e.bit,enabled&~e.bit);}
    private static void change(Context c,int have,int on){
        prefs(c).edit().putInt("installed",have&15).putInt("enabled",on&have&15).apply();load(c);
        // Only effective behaviour changes. Never overwrite the user's original preferences.
        MotionBlur.refresh();
        if(!active(ExtensionPolicy.BREAK)){TIME.reset();MAIN.removeCallbacks(REMIND);}
        else if(!FOREGROUND.isEmpty()){TIME.resume(SystemClock.elapsedRealtime());schedule();}
    }
    static void resumed(Activity a){FOREGROUND.put(a,Boolean.TRUE);if(active(ExtensionPolicy.BREAK)){TIME.resume(SystemClock.elapsedRealtime());schedule();}}
    static void paused(Activity a){FOREGROUND.remove(a);if(FOREGROUND.isEmpty()){TIME.pause(SystemClock.elapsedRealtime());MAIN.removeCallbacks(REMIND);}}
    private static void schedule(){MAIN.removeCallbacks(REMIND);if(active(ExtensionPolicy.BREAK)&&!FOREGROUND.isEmpty())MAIN.postDelayed(REMIND,Math.max(1,TIME.remaining(SystemClock.elapsedRealtime())));}
}
