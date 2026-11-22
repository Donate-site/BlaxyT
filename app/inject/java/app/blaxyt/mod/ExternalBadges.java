package app.blaxyt.mod;

import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;

/** Public UID lookups run away from drawing. No viewer identity or proof is sent. */
final class ExternalBadges {
    private static final Pattern UID=Pattern.compile("[0-9]{6,24}");
    private static final Handler UI=new Handler(Looper.getMainLooper());
    private static final LinkedHashSet<String> pending=new LinkedHashSet<>();
    private static final LinkedHashMap<String,Long> attempted=new LinkedHashMap<>();
    private static boolean busy,scheduled;
    private static final Runnable REFRESH=()->BadgeShop.refresh(BlaxyRuntime.context(),true);

    static synchronized void observe(String uid){
        if(uid==null||!UID.matcher(uid).matches()||BlaxyRuntime.context()==null)return;
        long now=android.os.SystemClock.elapsedRealtime();Long last=attempted.get(uid);
        if(last!=null&&now-last<300000L)return;
        if(pending.size()>=128)return;
        pending.add(uid);schedule();
    }
    private static void schedule(){
        if(busy||scheduled||pending.isEmpty())return;
        scheduled=true;UI.postDelayed(ExternalBadges::flush,1500);
    }
    private static void flush(){
        final ArrayList<String> batch=new ArrayList<>();
        synchronized(ExternalBadges.class){
            scheduled=false;if(busy)return;
            java.util.Iterator<String> it=pending.iterator();
            long now=android.os.SystemClock.elapsedRealtime();
            while(it.hasNext()&&batch.size()<12){String uid=it.next();it.remove();batch.add(uid);attempted.put(uid,now);}
            while(attempted.size()>512)attempted.remove(attempted.keySet().iterator().next());
            if(batch.isEmpty())return;busy=true;
        }
        Net.away("blaxyt-public-badges",()->{
            try{
                StringBuilder query=new StringBuilder();
                for(String uid:batch){if(query.length()>0)query.append(',');query.append(uid);}
                Net.Answer answer=Net.fetch("https://doska67.duckdns.org/blaxyt/v1/external-badges?uids="+query,null);
                if(answer!=null&&answer.body!=null&&answer.body.length<8192){
                    org.json.JSONObject value=new org.json.JSONObject(new String(answer.body,"UTF-8"));
                    if(value.optBoolean("ok"))UI.post(()->{UI.removeCallbacks(REFRESH);UI.postDelayed(REFRESH,3500);});
                }
            }catch(Exception ignored){}
            finally{synchronized(ExternalBadges.class){busy=false;schedule();}}
        });
    }
}
