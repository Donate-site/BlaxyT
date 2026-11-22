package app.blaxyt.mod;

import android.app.Activity;

/** A user may copy only badges publicly confirmed for their verified TikTok UID. */
final class BlaxyImport {
    static void show(Activity activity,Skin skin){
        if(!BlaxyLink.verified()){
            Panel.with(activity,skin,"Сначала подтверди профиль")
                .text("Значки переносятся по постоянному ID TikTok. Подтверди свой профиль кодом blaxy-…; код остаётся нашим.")
                .primary("Подтвердить профиль",()->BlaxyLink.show(activity,skin,null)).quiet("Позже",null).show();return;
        }
        Panel.with(activity,skin,"Твои значки остаются с тобой")
            .text("Проверим открытый список источника по ID твоего TikTok и сохраним копию значков. Названия, картинки и источник останутся. Пароль другого мода не нужен.\n\nТвои значки blaxyT и код blaxy-… сохраняются. Смена ника не меняет владельца значков.")
            .item("verified","MargyT","Перенести подтверждённые значки",()->transfer(activity,skin,"margyt","MargyT"))
            .item("verified","TikTok You","Перенести подтверждённый значок",()->transfer(activity,skin,"you","TikTok You"))
            .quiet("Закрыть",null).show();
    }
    private static void transfer(Activity activity,Skin skin,String source,String title){
        Panel panel=Panel.with(activity,skin,"Перенос из "+title)
            .text("Проверяем значки твоего аккаунта. Это может занять до полуминуты.").quiet("Закрыть",null);
        panel.show();panel.busy(true,"Проверяем источник…");
        BlaxyLink.importBadges(source,(value,error)->{
            if(value!=null)BadgeShop.refresh(activity,true);
            if(activity.isFinishing()||activity.isDestroyed()||!panel.dialog().isShowing())return;
            panel.busy(false,value==null?error:value.optString("notice"));
        });
    }
}
