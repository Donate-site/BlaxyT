package app.blaxyt.mod;

import android.app.Activity;
import android.widget.EditText;
import android.widget.ImageView;
import org.json.JSONObject;

/** Cosmetic roles never imply authority. Every action is authorized again by our server. */
final class BlaxyAdmin {
    private static final String OWNER="7662448315568931873";
    private static final String[] IDS={"supporter","ambassador","developer","helper"};
    private static final String[] TITLES={"Поддержка · Заряд","Амбассадор · Рекламщик","Участник разработки","Помощник разработчика"};
    static boolean ownerAccount(){return OWNER.equals(Account.liveId());}
    private static boolean alive(Activity a,Panel p){return !a.isFinishing()&&!a.isDestroyed()&&p.dialog().isShowing();}

    static void show(Activity a,Skin skin){
        if(!ownerAccount())return;
        final String uid=Account.liveId();
        Panel gate=Panel.with(a,skin,"Управление значками").text("Доступ владельца blaxyT").quiet("Закрыть",null);
        gate.show();gate.busy(true,"Проверяем доступ…");
        BlaxyLink.admin("admin-status",uid,new JSONObject(),(value,error)->{
            if(!alive(a,gate))return;
            if(value==null){gate.busy(false,error);return;}
            gate.close();search(a,skin,uid);
        });
    }
    private static void search(Activity a,Skin skin,String uid){
        Panel panel=Panel.with(a,skin,"Кому выдать значок?")
            .text("Введи ник TikTok. Затем выбери роль. Выдача здесь бесплатная и не создаёт оплату.");
        EditText name=panel.field("Ник TikTok, например @androidtexhhh");name.setSingleLine(true);
        name.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        panel.primaryKeepOpen("Найти профиль",()->{
            JSONObject data=new JSONObject();try{data.put("target_name",name.getText().toString().trim());}catch(Exception ignored){}
            panel.busy(true,"Находим профиль TikTok…");
            BlaxyLink.admin("admin-resolve",uid,data,(value,error)->{
                if(!alive(a,panel))return;
                panel.busy(false,error);
                if(value!=null){panel.close();choose(a,skin,uid,value);}
            });
        }).quiet("Отмена",null).show();
    }
    private static void choose(Activity a,Skin skin,String uid,JSONObject target){
        Panel panel=Panel.with(a,skin,"@"+target.optString("name"))
            .text("ID TikTok: "+target.optString("target_uid")+"\nВыбери значок blaxyT. Это не официальная верификация TikTok.");
        org.json.JSONArray manual=target.optJSONArray("manual_badges");
        for(int i=0;i<IDS.length;i++){
            final int at=i;boolean given=false;
            if(manual!=null)for(int n=0;n<manual.length();n++)given|=IDS[i].equals(manual.optString(n));
            panel.item("verified",TITLES[i],given?"Уже выдан вручную":"Выдать участнику",()->confirm(a,skin,uid,target,at));
        }
        panel.quiet("Другой профиль",()->search(a,skin,uid)).show();
    }
    private static void confirm(Activity a,Skin skin,String uid,JSONObject target,int which){
        Panel panel=Panel.with(a,skin,TITLES[which]);
        ImageView picture=new ImageView(a);
        picture.setImageBitmap(which==3?ChargeArt.helper():which==1?ChargeArt.ambassador():ChargeArt.picture(which==2));
        picture.setAdjustViewBounds(true);picture.setMaxHeight(Math.round(80*a.getResources().getDisplayMetrics().density));
        panel.view(picture).text("Получатель: @"+target.optString("name")+"\nID: "+target.optString("target_uid"));
        if(which>=2)panel.text("Эта роль не даёт права выдавать значки. Управление остаётся только у тебя.");
        if(!target.optBoolean("active"))panel.text("Значок сохранится на сервере. Чтобы его увидеть, получатель должен подтвердить профиль в blaxyT.");
        panel.primaryKeepOpen("Выдать значок",()->apply(a,skin,panel,uid,target,which,"grant"))
            .keepOpen("Снять ручную выдачу",()->{
                Panel.with(a,skin,"Снять этот значок?").text("@"+target.optString("name")+" · "+TITLES[which]+"\nОплаченный значок поддержки и покупки не отменяются.")
                    .primary("Снять",()->apply(a,skin,panel,uid,target,which,"revoke")).quiet("Назад",null).show();
            }).quiet("Назад",()->choose(a,skin,uid,target)).show();
    }
    private static void apply(Activity a,Skin skin,Panel panel,String uid,JSONObject target,int which,String action){
        JSONObject data=new JSONObject();
        try{data.put("token",target.optString("token")).put("badge",IDS[which]).put("action",action);}catch(Exception ignored){}
        panel.busy(true,"grant".equals(action)?"Выдаём значок…":"Снимаем ручную выдачу…");
        BlaxyLink.admin("admin-grant",uid,data,(value,error)->{
            if(!alive(a,panel))return;
            if(value==null){panel.busy(false,error);return;}
            panel.close();BadgeShop.refresh(a,true);
            Panel.with(a,skin,"Готово").text("@"+value.optString("name")+"\n"+value.optString("notice"))
                .primary("Ещё один профиль",()->search(a,skin,uid)).quiet("Закрыть",null).show();
        });
    }
}
