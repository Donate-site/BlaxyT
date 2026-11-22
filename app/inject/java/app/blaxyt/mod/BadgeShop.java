package app.blaxyt.mod;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Public grants and free profile gradients; no Telegram credentials are shipped in the APK. */
final class BadgeShop {
    static final String ENDPOINT="https://doska67.duckdns.org/blaxyt/v1/badges";
    private static volatile BadgeSnapshot snapshot=BadgeSnapshot.EMPTY;
    private static long attempted;
    private static boolean loaded,busy;
    private static String lastJson="";
    static boolean shown(String uid,String badge){return snapshot.visible(uid,badge);}
    static int orderOf(String uid,String badge){return snapshot.rank(uid,badge);}
    static String[] owned(String uid,boolean visibleOnly){
        if(Badges.isEnabled())ExternalBadges.observe(uid);
        java.util.ArrayList<String> found=new java.util.ArrayList<>();
        for(String id:snapshot.owned(uid,visibleOnly,System.currentTimeMillis()))if(Badges.byId(id)!=null)found.add(id);
        return found.toArray(new String[0]);
    }
    private static final Handler UI=new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> front=new WeakReference<>(null);
    private static final Runnable TICK=new Runnable(){public void run(){
        Activity activity=front.get();if(activity==null||activity.isFinishing())return;
        refresh(activity,false);UI.postDelayed(this,15000);
    }};
    static void resumed(Activity activity){front=new WeakReference<>(activity);UI.removeCallbacks(TICK);refresh(activity,true);UI.postDelayed(TICK,15000);}
    static void paused(Activity activity){if(front.get()==activity){front.clear();UI.removeCallbacks(TICK);}}
    static boolean holds(String uid){return holds(uid,"supporter");}
    static boolean developer(String uid){return holds(uid,"developer");}
    private static boolean holds(String uid,String badge){
        return snapshot.holds(uid,badge,System.currentTimeMillis());
    }
    static void refresh(Context context){refresh(context,false);}
    static synchronized void refresh(Context context,boolean force){
        if(context==null)return;
        final Context app=context.getApplicationContext();
        long now=android.os.SystemClock.elapsedRealtime();
        if(busy||(attempted!=0&&now-attempted<(force?1500:14000)))return;
        attempted=now;busy=true;
        Net.away("blaxyt-badges",()->{
            try{
                android.content.SharedPreferences prefs=app.getSharedPreferences("blaxyt_shop",0);
                if(!loaded){loaded=true;if(ENDPOINT.equals(prefs.getString("endpoint","")))learn(prefs.getString("grants","{}"),prefs.getLong("read_at",0));}
                Net.Answer answer=Net.fetch(ENDPOINT,null);
                if(answer==null||answer.body==null||answer.body.length>2000000)return;
                String json=new String(answer.body,"UTF-8");long timestamp=System.currentTimeMillis();
                boolean changed=!json.equals(lastJson);
                if(learn(json,timestamp)){
                    prefs.edit().putString("endpoint",ENDPOINT).putString("grants",json).putLong("read_at",timestamp).apply();
                    if(changed)UI.post(()->{Mine.serverChanged();Badges.shopChanged();Badge.serverChanged();});
                }
            }catch(Exception ignored){Diary.note("blaxyT badge server unavailable");}
            finally{synchronized(BadgeShop.class){busy=false;}}
        });
    }
    private static boolean learn(String json,long timestamp){
        try{
            JSONObject root=new JSONObject(json);if(root.optInt("schema")!=1)return false;
            BadgeSnapshot next=BadgeSnapshot.parse(root,timestamp);
            Badges.importCatalogue(root.optJSONArray("external_catalog"));
            Looks.learn(root);
            if(!json.equals(lastJson))Frames.learn(root);
            snapshot=next;lastJson=json;return true;
        }catch(Exception ignored){return false;}
    }
}
