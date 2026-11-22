package app.blaxyt.mod;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Four destinations, one brand header; no continuous decorative redraws. */
final class BlaxyDashboard {
    interface Selected { void open(int group); }
    private BlaxyDashboard() {}
    static View make(Context c,Skin skin,Selected selected,Runnable back,View search,View footer){
        LinearLayout page=column(c);page.setBackgroundColor(0xff7354b9);
        LinearLayout hero=column(c);hero.setPadding(dp(c,20),dp(c,8),dp(c,20),dp(c,48));
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xffa681ea,0xff7852bf,0xff563793}));
        LinearLayout toolbar=new LinearLayout(c);
        ImageView arrow=new ImageView(c);arrow.setImageDrawable(BlaxyGlyph.make("arrow_forward",Color.WHITE));
        arrow.setScaleX(-1f);arrow.setPadding(dp(c,12),dp(c,12),dp(c,12),dp(c,12));
        arrow.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44ffffff),null,round(0xffffffff,24,c)));
        arrow.setContentDescription("Вернуться в TikTok");arrow.setFocusable(true);arrow.setOnClickListener(v->back.run());
        toolbar.addView(arrow,new LinearLayout.LayoutParams(dp(c,48),dp(c,48)));hero.addView(toolbar,new LinearLayout.LayoutParams(-1,-2));
        ImageView logo=new ImageView(c);logo.setImageBitmap(ChargeArt.picture(true));logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(c,88),dp(c,88));lp.topMargin=dp(c,12);hero.addView(logo,lp);
        TextView title=text(c,Version.LITE?"BlaxyT Lite":"BlaxyT",30,Color.WHITE,true);title.setPadding(0,dp(c,16),0,dp(c,8));hero.addView(title);
        hero.addView(text(c,"v"+Version.MOD,14,0xffefe5ff,false));page.addView(hero,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout sheet=column(c);sheet.setPadding(dp(c,16),dp(c,12),dp(c,16),dp(c,24));
        GradientDrawable panel=round(skin.dark()?0xff1d1926:0xfff7f5fb,0,c);float r=dp(c,32);
        panel.setCornerRadii(new float[]{r,r,r,r,0,0,0,0});sheet.setBackground(panel);
        LinearLayout.LayoutParams sheetSize=new LinearLayout.LayoutParams(-1,-2);sheetSize.topMargin=-dp(c,28);page.addView(sheet,sheetSize);
        View handle=new View(c);handle.setBackground(round(skin.dark()?0xff62596e:0xffd6cfdf,3,c));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(c,34),dp(c,4));hp.gravity=Gravity.CENTER_HORIZONTAL;hp.bottomMargin=dp(c,18);sheet.addView(handle,hp);
        int[] ids=Version.LITE?new int[]{2,3,1}:new int[]{2,3,1,7};
        String[] names={"Регион","Лента","Внешний вид","Автопродление"};
        String[] icons={"public","play_circle","palette","schedule"};
        String[] notes={"Страна и лента региона","Видео и инструменты","Оформление и профиль","Серии и уведомления"};
        int columns=c.getResources().getConfiguration().fontScale>1.35f?1:2;
        for(int i=0;i<ids.length;i+=columns){
            LinearLayout row=new LinearLayout(c);row.setBaselineAligned(false);
            for(int j=i;j<Math.min(i+columns,ids.length);j++){
                final int group=ids[j];LinearLayout tile=column(c);tile.setPadding(dp(c,18),dp(c,18),dp(c,14),dp(c,18));tile.setMinimumHeight(dp(c,132));
                tile.setBackground(new RippleDrawable(ColorStateList.valueOf(0x227954c1),round(skin.dark()?0xff292332:Color.WHITE,24,c),null));
                ImageView icon=new ImageView(c);icon.setImageDrawable(BlaxyGlyph.make(icons[j],skin.dark()?0xffceb4ff:0xff7954bb));icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                tile.addView(icon,new LinearLayout.LayoutParams(dp(c,27),dp(c,27)));
                TextView name=text(c,names[j],16,skin.dark()?0xfff6efff:0xff292133,true);name.setPadding(0,dp(c,18),0,dp(c,6));tile.addView(name);
                tile.addView(text(c,notes[j],12,skin.dark()?0xffc3b8ce:0xff746a80,false));
                tile.setContentDescription(names[j]+". "+notes[j]);tile.setFocusable(true);tile.setOnClickListener(v->selected.open(group));Motion.touch(tile);
                LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-1,1);if(j>i)tp.leftMargin=dp(c,10);row.addView(tile,tp);
            }
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(c,10);sheet.addView(row,rp);
        }
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(c,8);sheet.addView(search,sp);
        TextView community=text(c,"Сообщество",13,skin.muted(),true);community.setPadding(dp(c,6),dp(c,24),0,dp(c,8));sheet.addView(community);
        sheet.addView(footer,new LinearLayout.LayoutParams(-1,-2));return page;
    }
    static int ink(Skin skin){return Expressive.mix(Accent.colour(),skin.text,skin.dark()?.40f:.48f);}
    static GradientDrawable surface(Context c,Skin skin,float tint,int radius){
        GradientDrawable shape=round(Expressive.mix(skin.card,Accent.colour(),tint),radius,c);
        shape.setStroke(dp(c,1),Expressive.mix(skin.card,skin.text,.08f));return shape;
    }
    private static GradientDrawable round(int colour,int radius,Context c){GradientDrawable d=new GradientDrawable();d.setColor(colour);d.setCornerRadius(dp(c,radius));return d;}
    private static LinearLayout column(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private static TextView text(Context c,String value,int size,int colour,boolean bold){TextView v=new TextView(c);v.setText(value);v.setTextSize(size);v.setTextColor(colour);v.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));v.setIncludeFontPadding(false);return v;}
    private static int dp(Context c,float value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
}
