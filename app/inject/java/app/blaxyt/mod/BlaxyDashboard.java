package app.blaxyt.mod;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Settings studio with a compact identity header and numbered directory. */
final class BlaxyDashboard {
    interface Selected { void open(int group); }
    private static final String[] ICONS={"palette","group","play_circle","tune","bolt","extension","chat","pets"};
    private BlaxyDashboard() {}
    static View make(Context c,Skin skin,int[] order,String[] titles,String[] notes,Selected selected){
        LinearLayout page=column(c);page.setPadding(dp(c,20),dp(c,12),dp(c,20),dp(c,24));
        LinearLayout hero=column(c);hero.setPadding(dp(c,22),dp(c,22),dp(c,22),dp(c,24));
        hero.setBackground(surface(c,skin,.13f,30));
        LinearLayout identity=new LinearLayout(c);identity.setGravity(Gravity.CENTER_VERTICAL);
        TextView stamp=text(c,"BLAXYT / СТУДИЯ",11,ink(skin),true);stamp.setLetterSpacing(.12f);
        identity.addView(stamp,new LinearLayout.LayoutParams(0,-2,1));
        ImageView logo=new ImageView(c);logo.setImageBitmap(ChargeArt.picture(true));
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        identity.addView(logo,new LinearLayout.LayoutParams(dp(c,40),dp(c,40)));hero.addView(identity);
        TextView heading=text(c,"Всё по-твоему.",29,skin.text,true);heading.setPadding(0,dp(c,14),0,dp(c,10));hero.addView(heading);
        hero.addView(text(c,"Твой экран, твоя лента, твой характер.",14,skin.muted(),false));
        page.addView(hero);

        LinearLayout motion=new LinearLayout(c);motion.setGravity(Gravity.CENTER_VERTICAL);
        motion.setPadding(dp(c,16),dp(c,18),dp(c,16),dp(c,18));motion.setBackground(surface(c,skin,.035f,22));
        ImageView wave=new ImageView(c);wave.setImageDrawable(BlaxyGlyph.make("animation",ink(skin)));
        LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(dp(c,26),dp(c,26));wp.rightMargin=dp(c,14);motion.addView(wave,wp);
        LinearLayout words=column(c);words.setPadding(0,0,dp(c,10),0);
        words.addView(text(c,"След движения",15,skin.text,true));
        TextView note=text(c,android.os.Build.VERSION.SDK_INT>=31?"Моушен блюр":"Моушен блюр · Android 12+",12,skin.muted(),false);
        note.setPadding(0,dp(c,5),0,0);words.addView(note);motion.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        M3Switch toggle=new M3Switch(c);toggle.setContentDescription("След движения — моушен блюр");
        toggle.colours(Accent.colour(),skin.muted(),skin.card);toggle.setChecked(MotionBlur.on());
        toggle.setEnabled(android.os.Build.VERSION.SDK_INT>=31);toggle.setOnChanged(MotionBlur::set);motion.addView(toggle);
        motion.setOnClickListener(v->{if(toggle.isEnabled())toggle.performClick();});Motion.touch(motion);
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.topMargin=dp(c,12);page.addView(motion,mp);

        TextView label=text(c,"СОБЕРИ СВОЙ BLAXYT",11,skin.muted(),true);
        label.setLetterSpacing(.12f);label.setPadding(dp(c,4),dp(c,30),0,dp(c,14));page.addView(label);
        for(int i=0;i<order.length;i++){
            final int group=order[i];if(group<1||group>ICONS.length)continue;
            LinearLayout entry=new LinearLayout(c);entry.setGravity(Gravity.CENTER_VERTICAL);
            entry.setPadding(dp(c,16),dp(c,18),dp(c,16),dp(c,18));entry.setMinimumHeight(dp(c,90));
            entry.setBackground(new RippleDrawable(ColorStateList.valueOf((Accent.colour()&0xffffff)|0x22000000),surface(c,skin,i==0?.07f:.015f,22),null));
            ImageView symbol=new ImageView(c);symbol.setImageDrawable(BlaxyGlyph.make(ICONS[group-1],ink(skin)));
            symbol.setPadding(dp(c,11),dp(c,11),dp(c,11),dp(c,11));symbol.setBackground(surface(c,skin,.13f,16));
            symbol.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(c,48),dp(c,48));sp.rightMargin=dp(c,16);entry.addView(symbol,sp);
            LinearLayout copy=column(c);copy.addView(text(c,titles[group-1],17,skin.text,true));
            TextView sub=text(c,notes[group-1],12,skin.muted(),false);sub.setPadding(0,dp(c,6),dp(c,8),0);copy.addView(sub);
            entry.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
            TextView number=text(c,String.format(java.util.Locale.ROOT,"%02d",i+1),11,skin.muted(),true);
            number.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);entry.addView(number);
            entry.setFocusable(true);entry.setContentDescription(titles[group-1]+". "+notes[group-1]);
            entry.setOnClickListener(v->selected.open(group));Motion.touch(entry);
            LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);ep.bottomMargin=dp(c,8);page.addView(entry,ep);
        }
        return page;
    }
    static int ink(Skin skin){return Expressive.mix(Accent.colour(),skin.text,skin.dark()?.40f:.48f);}
    static GradientDrawable surface(Context c,Skin skin,float tint,int radius){
        GradientDrawable shape=new GradientDrawable();shape.setColor(Expressive.mix(skin.card,Accent.colour(),tint));
        shape.setCornerRadius(dp(c,radius));shape.setStroke(dp(c,1),Expressive.mix(skin.card,skin.text,.08f));return shape;
    }
    private static LinearLayout column(Context c){LinearLayout view=new LinearLayout(c);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private static TextView text(Context c,String value,int size,int colour,boolean bold){
        TextView view=new TextView(c);view.setText(value);view.setTextSize(size);view.setTextColor(colour);
        view.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));view.setIncludeFontPadding(false);return view;
    }
    private static int dp(Context c,float value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
}
