package app.blaxyt.mod;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;
import java.util.Random;

/** Local country reels, using bundled flag glyphs; no network or latency measurements. */
final class RegionSlots extends View {
    private final Skin skin;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flagsPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box=new RectF();
    private final Path[] clips={new Path(),new Path(),new Path()};
    private final Random random=new Random();
    private final String[][] countries;
    private final String[] flags;
    private final int[][] reels=new int[3][];
    private final int[] initial=new int[3];
    private final float density;
    private final android.view.animation.PathInterpolator easing=new android.view.animation.PathInterpolator(.18f,0f,.20f,1f);
    private Shader veil;
    private ValueAnimator animator;
    private float progress=1f,slotWidth,slotGap,cellHeight,centre;
    private int winner=-1;
    private Runnable completed;

    private RegionSlots(Context context,Skin skin){
        super(context);this.skin=skin;density=getResources().getDisplayMetrics().density;
        // Fictional joke regions stay in the manual list; these reels show national flags.
        java.util.ArrayList<String[]> actual=new java.util.ArrayList<>();
        for(String[] country:BlaxyRuntime.COUNTRIES)if(!country[BlaxyRuntime.ISO].startsWith("x"))actual.add(country);
        countries=actual.toArray(new String[0][]);flags=new String[countries.length];
        int current=0;
        for(int i=0;i<countries.length;i++){
            String iso=countries[i][BlaxyRuntime.ISO].toUpperCase(Locale.ROOT);
            flags[i]=new String(Character.toChars(0x1f1e6+iso.charAt(0)-'A'))
                    +new String(Character.toChars(0x1f1e6+iso.charAt(1)-'A'));
            if(countries[i][BlaxyRuntime.ISO].equals(BlaxyRuntime.iso()))current=i;
        }
        for(int i=0;i<3;i++)initial[i]=current;
        paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        Typeface flagFont=Fonts.twemoji(context);
        flagsPaint.setTypeface(flagFont!=null?flagFont:Typeface.DEFAULT);
        flagsPaint.setTextAlign(Paint.Align.CENTER);flagsPaint.setColor(0xffffffff);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    static void show(Context context,Skin skin,Runnable changed){
        Panel panel=Panel.with(context,skin,"Выбрать страну").subtitle("Куда отправится твоя лента?");
        RegionSlots slots=new RegionSlots(context,skin);
        TextView result=words(context,skin,"Нажми «Крутить»",17);
        result.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        result.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        TextView message=words(context,skin,"Три флага · одна случайная страна",14);
        message.setTextColor(skin.muted());
        TextView spin=button(context,skin,"Крутить",false);
        TextView apply=button(context,skin,"Применить страну",true);
        apply.setEnabled(false);apply.setAlpha(.4f);
        slots.completed=()->{
            result.setTextSize(25);result.setText(BlaxyRuntime.name(slots.countries[slots.winner]));
            message.setText("Крутая страна, иди листать ленту");
            reveal(result,0,360);reveal(message,100,460);
            spin.setEnabled(true);spin.setAlpha(1f);spin.setText("Крутить ещё");
            apply.setEnabled(true);apply.setAlpha(1f);
        };
        spin.setOnClickListener(v->{
            spin.setEnabled(false);spin.setAlpha(.5f);apply.setEnabled(false);apply.setAlpha(.4f);
            settle(result);settle(message);
            result.setTextSize(17);result.setText("Выбираем страну…");
            message.setText("Флаги остановятся по очереди");slots.spin();
        });
        apply.setOnClickListener(v->{
            if(slots.animator!=null||slots.winner<0)return;
            context.getSharedPreferences(BlaxyRuntime.PREFS,0).edit()
                    .putString(BlaxyRuntime.KEY_COUNTRY,slots.countries[slots.winner][BlaxyRuntime.ISO])
                    .putBoolean(BlaxyRuntime.KEY_ENABLED,true).apply();
            panel.close();changed.run();
        });
        panel.view(slots).view(result).view(message)
                .text("Применение включит подмену региона. Для применения перезапустите приложение.")
                .view(spin).view(apply).quiet(Text.CANCEL,null).show();
        panel.dialog().setOnDismissListener(dialog->{settle(result);settle(message);slots.stop();});
    }
    private static void settle(View view){view.animate().cancel();view.setAlpha(1f);view.setTranslationY(0f);}
    private static void reveal(View view,long delay,long duration){
        settle(view);if(!Motion.enabled(view.getContext()))return;
        view.setAlpha(0f);view.setTranslationY(8*view.getResources().getDisplayMetrics().density);
        view.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(duration)
                .setInterpolator(new android.view.animation.PathInterpolator(.2f,0f,0f,1f)).start();
    }
    private void spin(){
        if(animator!=null||!isAttachedToWindow())return;
        int previous=winner;winner=random.nextInt(countries.length);
        for(int i=0;i<3;i++){
            int steps=18+i*5;reels[i]=new int[steps+1];
            reels[i][0]=previous>=0?previous:initial[i];
            for(int n=1;n<steps;n++){
                int next=random.nextInt(countries.length-1);
                if(next>=reels[i][n-1])next++;
                reels[i][n]=next;
            }
            reels[i][steps]=winner;
        }
        progress=0f;
        if(!Motion.enabled(getContext())){progress=1f;invalidate();completed.run();return;}
        animator=ValueAnimator.ofFloat(0f,1f);animator.setDuration(2600);
        animator.setInterpolator(new android.view.animation.LinearInterpolator());
        animator.addUpdateListener(value->{
            progress=(Float)value.getAnimatedValue();invalidate();
            if(progress>=1f){animator=null;if(completed!=null)completed.run();}
        });animator.start();
    }
    private void stop(){
        if(animator!=null){animator.removeAllUpdateListeners();animator.cancel();animator=null;}
    }
    @Override protected void onDetachedFromWindow(){stop();completed=null;super.onDetachedFromWindow();}
    @Override protected void onMeasure(int width,int height){
        setMeasuredDimension(resolveSize(dp(320),width),resolveSize(dp(180),height));
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        slotGap=dp(10);slotWidth=(w-2*slotGap)/3f;cellHeight=dp(66);centre=h*.5f;
        flagsPaint.setTextSize(Math.min(dp(44),slotWidth*.53f));
        for(int i=0;i<3;i++){
            float left=i*(slotWidth+slotGap);clips[i].reset();
            box.set(left,dp(8),left+slotWidth,h-dp(8));clips[i].addRoundRect(box,dp(24),dp(24),Path.Direction.CW);
        }
        int colour=skin.card,clear=colour&0xffffff;
        veil=new LinearGradient(0,dp(8),0,h-dp(8),new int[]{colour,clear,clear,colour},
                new float[]{0f,.34f,.66f,1f},Shader.TileMode.CLAMP);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(slotWidth<=0)return;
        for(int i=0;i<3;i++){
            float left=i*(slotWidth+slotGap),cx=left+slotWidth/2;
            float delay=i*.045f,end=.66f+i*.17f;
            float local=Math.max(0f,Math.min(1f,(progress-delay)/(end-delay)));
            boolean stopped=winner>=0&&local>=1f;
            float glow=winner<0?0f:Math.max(0f,Math.min(1f,(local-.85f)/.15f));
            box.set(left,dp(8),left+slotWidth,getHeight()-dp(8));
            paint.setStyle(Paint.Style.FILL);paint.setColor(skin.card);canvas.drawRoundRect(box,dp(24),dp(24),paint);
            canvas.save();canvas.clipPath(clips[i]);
            paint.setColor(Expressive.mix(skin.card,Accent.colour(),.07f+.09f*glow));
            canvas.drawRect(left,centre-cellHeight/2,left+slotWidth,centre+cellHeight/2,paint);
            if(winner<0)drawFlag(canvas,initial[i],cx,centre,1f);
            else{
                int[] sequence=reels[i];float position=easing.getInterpolation(local)*(sequence.length-1);
                int at=(int)Math.floor(position);float fraction=position-at;
                for(int offset=-1;offset<=1;offset++){
                    int index=at+offset;if(index<0||index>=sequence.length)continue;
                    float y=centre+(offset-fraction)*cellHeight;
                    float focus=1f-Math.min(1f,Math.abs(y-centre)/cellHeight);
                    drawFlag(canvas,sequence[index],cx,y,.60f+.40f*focus);
                }
            }
            paint.setShader(veil);canvas.drawRect(left,dp(8),left+slotWidth,getHeight()-dp(8),paint);paint.setShader(null);
            canvas.restore();
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));
            paint.setColor(Expressive.mix(skin.card,Accent.colour(),.15f+.5f*glow));
            canvas.drawRoundRect(box,dp(24),dp(24),paint);paint.setStyle(Paint.Style.FILL);
            paint.setColor(stopped?Accent.colour():Expressive.mix(skin.card,skin.text,.24f));
            canvas.drawCircle(cx,getHeight()-dp(19),dp(2),paint);
        }
    }
    private void drawFlag(Canvas canvas,int index,float x,float y,float focus){
        flagsPaint.setAlpha(Math.round(255*focus));
        float scale=.88f+.12f*focus;
        canvas.save();canvas.scale(scale,scale,x,y);
        canvas.drawText(flags[index],x,y-(flagsPaint.ascent()+flagsPaint.descent())/2f,flagsPaint);canvas.restore();
    }
    private static TextView words(Context c,Skin skin,String text,int size){
        TextView view=new TextView(c);view.setText(text);view.setTextSize(size);view.setTextColor(skin.text);
        float d=c.getResources().getDisplayMetrics().density;
        view.setGravity(Gravity.CENTER);view.setPadding(Math.round(8*d),Math.round(5*d),Math.round(8*d),Math.round(5*d));return view;
    }
    private static TextView button(Context c,Skin skin,String title,boolean primary){
        TextView view=words(c,skin,title,16);float d=c.getResources().getDisplayMetrics().density;
        view.setMinHeight(Math.round(52*d));view.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        int colour=primary?Accent.colour():Expressive.mix(skin.card,Accent.colour(),.13f);
        view.setTextColor(Md3.ink(colour));GradientDrawable background=new GradientDrawable();
        background.setColor(colour);background.setCornerRadius(18*d);view.setBackground(background);
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.topMargin=Math.round(10*d);view.setLayoutParams(size);
        view.setFocusable(true);Motion.press(view);return view;
    }
    private int dp(float value){return Math.round(value*density);}
}
