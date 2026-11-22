package app.blaxyt.mod;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;

/** BlaxyT's bundled, offline extension catalogue. Existing file plugins remain independent. */
public class StoreActivity extends Activity {
    private Skin skin;private LinearLayout column;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);BlaxyRuntime.attach(this);BlaxyExtensions.load(this);skin=Skin.remembered(this);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        column=new LinearLayout(this);column.setOrientation(1);column.setPadding(dp(20),dp(36),dp(20),dp(40));scroll.addView(column);
        scroll.setBackgroundColor(skin.page);setContentView(scroll);Design.page(this,scroll,skin.page);
        getWindow().setStatusBarColor(skin.page);getWindow().setNavigationBarColor(skin.page);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{column.setPadding(dp(20),insets.getSystemWindowInsetTop()+dp(12),dp(20),insets.getSystemWindowInsetBottom()+dp(28));return insets;});scroll.requestApplyInsets();rebuild();
    }
    private TextView text(String value,int size,int colour){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(colour);Fonts.apply(v);return v;}
    private GradientDrawable surface(int colour,int radius){GradientDrawable d=new GradientDrawable();d.setColor(colour);d.setCornerRadius(dp(radius));return d;}
    private void rebuild(){
        column.removeAllViews();TextView back=text("← Назад",16,skin.text);back.setPadding(0,dp(12),0,dp(18));back.setOnClickListener(v->finish());column.addView(back);
        TextView title=text("Магазин BlaxyT",30,skin.text);title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);column.addView(title);
        TextView note=text("4 бесплатных расширения · без интернета\nУстановка добавляет расширение в коллекцию. Включай только нужное.",14,skin.muted());note.setPadding(0,dp(12),0,dp(22));column.addView(note);
        String group="";
        for(BlaxyExtensions.Entry item:BlaxyExtensions.ALL){
            if(!group.equals(item.group)){group=item.group;TextView h=text(group,17,Accent.colour());h.setPadding(0,dp(12),0,dp(12));column.addView(h);}
            LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(18),dp(18),dp(18),dp(14));card.setBackground(surface(skin.card,26));
            LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
            ImageView icon=new ImageView(this);android.graphics.drawable.Drawable art=BlaxyGlyph.make(item.icon,Accent.colour());if(art!=null)icon.setImageDrawable(art);else icon.setImageBitmap(Badges.note());icon.setPadding(dp(10),dp(10),dp(10),dp(10));icon.setBackground(surface(Expressive.mix(skin.card,Accent.colour(),.14f),16));
            header.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));
            LinearLayout name=new LinearLayout(this);name.setOrientation(1);name.setPadding(dp(14),0,0,0);name.addView(text(item.name,18,skin.text));name.addView(text("BlaxyT · 1.0 · бесплатно",12,skin.muted()));header.addView(name,new LinearLayout.LayoutParams(0,-2,1));card.addView(header);
            TextView about=text(item.about,14,skin.muted());about.setPadding(0,dp(14),0,dp(16));card.addView(about);
            boolean have=BlaxyExtensions.installed(item),on=BlaxyExtensions.active(item.bit);
            TextView state=text(have?(on?"Включено":"Установлено · выключено"):"Встроенное расширение",12,on?Accent.colour():skin.muted());state.setPadding(0,0,0,dp(10));card.addView(state);
            LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);
            TextView button=text(have?(on?"Выключить":"Включить"):"Установить",15,skin.text);button.setGravity(Gravity.CENTER);button.setPadding(dp(16),dp(12),dp(16),dp(12));button.setBackground(surface(Expressive.mix(skin.card,Accent.colour(),.22f),20));Motion.press(button);
            button.setOnClickListener(v->{if(BlaxyExtensions.installed(item))BlaxyExtensions.toggle(this,item);else BlaxyExtensions.install(this,item);rebuild();});actions.addView(button,new LinearLayout.LayoutParams(0,-2,1));
            if(have){TextView remove=text("Удалить",14,skin.muted());remove.setPadding(dp(18),dp(12),0,dp(12));remove.setOnClickListener(v->Panel.with(this,skin,"Удалить расширение?").text(item.name+" будет выключено и убрано из коллекции. Его можно установить снова.").primary("Удалить",()->{BlaxyExtensions.remove(this,item);rebuild();}).quiet("Отмена",null).show());actions.addView(remove);}
            card.addView(actions);LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.bottomMargin=dp(14);column.addView(card,layout);
        }
        TextView foot=text("Облегчённые режимы уменьшают работу отдельных эффектов. Результат зависит от телефона. Твои установленные файлы .mtp доступны в разделе «Расширения».",12,skin.muted());foot.setPadding(0,dp(10),0,0);column.addView(foot);
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
