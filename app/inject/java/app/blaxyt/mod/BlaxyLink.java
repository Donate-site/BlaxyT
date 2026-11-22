package app.blaxyt.mod;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

/** BlaxyT identity is separate from TikTok identity; private proof never leaves this account's storage. */
final class BlaxyLink {
    static final String BASE="https://doska67.duckdns.org/blaxyt/v1/link/";
    static final String NOTICE="Не удаляйте код blaxy-… из описания профиля. Можно добавить свой текст — главное, чтобы код остался. Без кода значки скроются; после его возвращения и проверки появятся снова. Покупки сохранятся.";
    interface Reply { void done(JSONObject value,String error); }
    private static SharedPreferences prefs(){return BlaxyRuntime.context().getSharedPreferences("blaxyt_identity",0);}
    static String code(){String uid=Account.liveId();return uid==null?"":prefs().getString(uid+".code","");}
    static boolean verified(){String uid=Account.liveId();return uid!=null&&prefs().getBoolean(uid+".verified",false);}
    static String note(){String uid=Account.liveId();return uid!=null&&prefs().getBoolean(uid+".linked",false)?"Telegram привязан · Управление значками":"Подтвердить профиль и привязать Telegram";}
    static JSONObject credentials(String uid){
        JSONObject out=new JSONObject();
        try{out.put("uid",uid).put("code",prefs().getString(uid+".code","")).put("holder",prefs().getString(uid+".holder",""));}catch(Exception ignored){}
        return out;
    }
    /** Refresh an expired voice proof once, using the already bound server-side username. */
    static void refreshVoiceProof(String uid,Reply reply){
        if(uid==null||!uid.equals(Account.liveId())){reply.done(null,"Аккаунт изменился.");return;}
        request("check",uid,credentials(uid),reply);
    }
    private static void request(String action,String uid,JSONObject body,Reply reply){
        Net.away("blaxyt-profile",()->{
            JSONObject value=null;String error="Не удалось связаться с сервером blaxyT. Попробуйте позже.";
            try{
                Net.Said said=BlaxyTransport.talk(BASE+action,body.toString());
                JSONObject parsed=new JSONObject(said.body);
                if(said.ok()){
                    value=parsed;error="";
                    // Replies are scoped to their original account, never the currently selected account.
                    SharedPreferences.Editor edit=prefs().edit();
                    if(parsed.has("code"))edit.putString(uid+".code",parsed.getString("code"));
                    if(parsed.has("holder"))edit.putString(uid+".holder",parsed.getString("holder"));
                    if(parsed.has("verified"))edit.putBoolean(uid+".verified",parsed.optBoolean("verified"));
                    if(parsed.has("linked"))edit.putBoolean(uid+".linked",parsed.optBoolean("linked"));
                    edit.apply();
                }else error=parsed.optString("error",error);
            }catch(Exception ignored){}
            final JSONObject result=value;final String trouble=error;
            new Handler(Looper.getMainLooper()).post(()->{
                if(!uid.equals(Account.liveId())){reply.done(null,"Аккаунт изменился. Откройте настройки заново.");return;}
                reply.done(result,trouble);
            });
        });
    }
    static void admin(String action,String uid,JSONObject extra,Reply reply){
        if(uid==null||!uid.equals(Account.liveId())||!BlaxyAdmin.ownerAccount()){
            reply.done(null,"Аккаунт изменился. Откройте управление заново.");return;
        }
        JSONObject body=credentials(uid);
        try{java.util.Iterator<String> keys=extra.keys();while(keys.hasNext()){String key=keys.next();body.put(key,extra.get(key));}}catch(Exception ignored){}
        request(action,uid,body,reply);
    }
    static void importBadges(String source,Reply reply){
        String uid=Account.liveId();
        if(uid==null||!verified()){reply.done(null,"Сначала подтвердите профиль кодом blaxy-…");return;}
        JSONObject body=credentials(uid);try{body.put("source",source);}catch(Exception ignored){}
        request("import-badges",uid,body,reply);
    }
    static void show(Activity activity,Skin skin,Runnable changed){
        final String uid=Account.liveId();
        if(uid==null||uid.length()==0){Screen.say("Сначала войдите в аккаунт TikTok и откройте свой профиль.");return;}
        Panel panel=Panel.with(activity,skin,"Подтверждение аккаунта blaxyT");
        TextView identity=new TextView(activity);identity.setTextColor(skin.text);identity.setTextSize(19);identity.setTextIsSelectable(true);
        identity.setPadding(12,14,12,14);
        EditText name=new EditText(activity);name.setSingleLine(true);name.setTextColor(skin.text);name.setHintTextColor(skin.muted());name.setHint("Ваш ник TikTok без @");
        try{name.setText(NativeBio.handle(uid));}catch(Exception ignored){}
        panel.view(identity).text(NOTICE).view(name);
        TextView check=panel.action("",true);
        Runnable render=()->{String value=code();identity.setText(value.isEmpty()?"Ваш ID blaxyT ещё не создан":"Ваш ID blaxyT: "+value);check.setText(value.isEmpty()?"Получить ID blaxyT":"Проверить код в профиле");};
        final Reply[] receive=new Reply[1];
        receive[0]=(value,error)->{
            if(activity.isFinishing()||activity.isDestroyed()||!panel.dialog().isShowing())return;
            render.run();
            if(value==null){panel.busy(false,error);return;}
            String status=value.optBoolean("linked")?"Telegram привязан. Покупка значка — в боте.":value.optBoolean("verified")?"Профиль подтверждён. Отправьте ID боту.":"Добавьте этот ID в описание профиля TikTok, затем нажмите проверку.";
            if(value.has("active")&&!value.optBoolean("active")&&value.optBoolean("verified"))status="Код не подтверждён текущей проверкой. Верните его в описание и проверьте снова.";
            panel.busy(false,status);BadgeShop.refresh(activity,true);
            JSONObject pending=value.optJSONObject("pending");
            if(pending!=null){
                String nonce=pending.optString("nonce","");
                Panel.with(activity,skin,"Привязать этот Telegram?")
                    .text(pending.optString("name","")+"\nTelegram ID: "+pending.optString("telegram_id","")+"\nПодтверждайте только свой аккаунт.")
                    .primary("Да, это мой Telegram",()->{
                        JSONObject body=credentials(uid);try{body.put("nonce",nonce);}catch(Exception ignored){}
                        panel.busy(true,"Привязываем…");request("approve",uid,body,receive[0]);
                    }).quiet("Отклонить",()->{
                        JSONObject body=credentials(uid);try{body.put("nonce",nonce);}catch(Exception ignored){}
                        panel.busy(true,"Отклоняем…");request("reject",uid,body,receive[0]);
                    }).show();
            }
        };
        check.setOnClickListener(v->{
            JSONObject body=credentials(uid);try{body.put("name",name.getText().toString().trim());}catch(Exception ignored){}
            String action=code().isEmpty()?"create":"check";
            panel.busy(true,action.equals("create")?"Создаём ваш ID…":"Проверяем код в описании TikTok…");request(action,uid,body,receive[0]);
        });
        panel.keepOpen("Скопировать ID",()->{
            String value=code();if(value.isEmpty()){Screen.say("Сначала получите ID.");return;}
            ClipboardManager copy=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if(copy!=null)copy.setPrimaryClip(ClipData.newPlainText("ID blaxyT",value));Screen.say("ID скопирован");
        }).keepOpen("Получить значок · 15 звёзд",()->{
            String value=code();if(value.isEmpty()){Screen.say("Сначала получите ID.");return;}
            try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://t.me/BlaxyTagent?start="+value)));}catch(Exception ignored){Screen.say("Откройте @BlaxyTagent в Telegram.");}
        }).keepOpen("Обновить статус",()->{
            if(code().isEmpty())return;panel.busy(true,"Обновляем…");request("status",uid,credentials(uid),receive[0]);
        }).quiet("Закрыть",null);
        panel.dialog().setOnDismissListener(d->{if(changed!=null&&!activity.isFinishing())changed.run();});
        render.run();panel.show();
        if(!code().isEmpty()){panel.busy(true,"Обновляем…");request("status",uid,credentials(uid),receive[0]);}
    }
    static void gradient(List<Integer> colours,Gradient.Said then){
        String uid=Account.liveId();
        if(uid==null||!verified()){if(then!=null)then.said(false,Mine.PROVE);return;}
        JSONObject body=credentials(uid);JSONArray list=new JSONArray();
        try{if(colours!=null)for(Integer colour:colours)list.put(Gradient.hex(colour));body.put("colours",list);}catch(Exception ignored){}
        request("gradient",uid,body,(value,error)->{
            if(value!=null)BadgeShop.refresh(BlaxyRuntime.context(),true);
            if(then!=null)then.said(value!=null,error);
        });
    }
    static void frame(String uid,String id,Reply then){
        if(uid==null||!uid.equals(Account.liveId())||!verified()){then.done(null,Mine.PROVE);return;}
        JSONObject body=credentials(uid);
        try{body.put("frame",id);}catch(Exception ignored){}
        request("frame",uid,body,then);
    }
    static void badges(List<Mine.Held> order,Mine.Said then){
        String uid=Account.liveId();
        if(uid==null||!verified()){if(then!=null)then.said(false,Mine.PROVE);return;}
        JSONObject body=credentials(uid);JSONArray places=new JSONArray(),hidden=new JSONArray();
        try{for(Mine.Held item:order){places.put(item.id);if(!item.shown)hidden.put(item.id);}body.put("order",places).put("hidden",hidden);}catch(Exception ignored){}
        request("badges",uid,body,(value,error)->{
            if(value!=null)BadgeShop.refresh(BlaxyRuntime.context(),true);
            if(then!=null)then.said(value!=null,error);
        });
    }
}
