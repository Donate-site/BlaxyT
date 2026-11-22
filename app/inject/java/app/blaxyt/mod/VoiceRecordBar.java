package app.blaxyt.mod;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

/** Comment-anchored recorder; release keeps a draft instead of opening a menu or sending. */
final class VoiceRecordBar {
    private final PopupWindow popup;
    private final TextView timer,hint;
    private final Wave wave;
    final TextView status,record,preview,attach;
    private final LinearLayout actions;
    private final View anchor;
    private Runnable dismissed;
    private boolean closing;
    private long elapsed;
    private boolean cancelled;
    VoiceRecordBar(View anchor){
        this.anchor=anchor;
        android.content.Context c=anchor.getContext();Skin skin=Skin.forSettings(c);
        float d=c.getResources().getDisplayMetrics().density;
        LinearLayout box=new LinearLayout(c);box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int)(18*d),(int)(12*d),(int)(18*d),(int)(12*d));
        GradientDrawable background=new GradientDrawable();background.setColor(skin.card);background.setCornerRadius(24*d);background.setStroke((int)Math.max(1,d),Accent.colour());box.setBackground(background);
        timer=new TextView(c);timer.setTextColor(skin.text);timer.setTextSize(15);box.addView(timer);
        wave=new Wave(c);box.addView(wave,new LinearLayout.LayoutParams(-1,(int)(36*d)));
        hint=new TextView(c);hint.setTextSize(12);hint.setTextColor(skin.text);box.addView(hint);
        status=hint;
        actions=new LinearLayout(c);box.addView(actions);
        record=button(c,skin,"Заново");preview=button(c,skin,"▶ Слушать");attach=button(c,skin,"Отправить");
        TextView cancel=button(c,skin,"✕");cancel.setContentDescription("Удалить запись");cancel.setOnClickListener(v->close());
        actions.setVisibility(View.GONE);
        Rect visible=new Rect();anchor.getWindowVisibleDisplayFrame(visible);
        int width=Math.min((int)(380*d),Math.max((int)(200*d),visible.width()-(int)(24*d)));
        popup=new PopupWindow(box,width,-2,false);popup.setTouchable(false);popup.setOutsideTouchable(false);popup.setElevation(10*d);
        popup.setOnDismissListener(()->{if(!closing&&dismissed!=null)dismissed.run();});
        int[] where=new int[2];anchor.getLocationOnScreen(where);
        popup.showAtLocation(anchor,Gravity.TOP|Gravity.LEFT,visible.left+(visible.width()-width)/2,Math.max(visible.top,where[1]-(int)(130*d)));
        sample(0,0);
    }
    private TextView button(android.content.Context c,Skin skin,String text){TextView b=new TextView(c);b.setText(text);b.setTextSize(13);b.setTextColor(skin.text);b.setGravity(Gravity.CENTER);b.setPadding(4,12,4,12);actions.addView(b,new LinearLayout.LayoutParams(0,-2,1));return b;}
    void onDismiss(Runnable value){dismissed=value;}
    void ready(){popup.setTouchable(true);actions.setVisibility(View.VISIBLE);timer.setText("Запись · "+Math.max(1,elapsed/1000)+" с");popup.update();}
    void recording(){elapsed=0;cancelled=false;wave.count=0;wave.progress=0;wave.invalidate();labels();}
    boolean attached(){return anchor.isAttachedToWindow()&&anchor.isShown();}
    void progress(float value){wave.progress=value;wave.invalidate();}
    void cancelHint(boolean value){cancelled=value;labels();}
    void sample(int amplitude,long time){elapsed=time;wave.sample(amplitude);labels();}
    private void labels(){timer.setText("●  Запись · "+(elapsed/1000)+" / 60 с");hint.setText(cancelled?"Отпустите, чтобы отменить":"Отпустите — прослушать · Влево — отменить");}
    void close(){if(closing)return;closing=true;popup.dismiss();if(dismissed!=null)dismissed.run();}
    private static final class Wave extends View{
        final float[] levels=new float[760];final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);int count;float progress;
        Wave(android.content.Context c){super(c);paint.setStrokeCap(Paint.Cap.ROUND);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void sample(int amplitude){if(count<levels.length)levels[count++]=Math.max(.06f,Math.min(1f,(float)Math.sqrt(Math.max(0,amplitude)/32767f)));invalidate();}
        protected void onDraw(Canvas canvas){super.onDraw(canvas);int bars=48;float step=getWidth()/(float)bars,mid=getHeight()/2f;paint.setColor(Accent.colour());paint.setStrokeWidth(Math.max(2,step*.4f));for(int i=0;i<bars;i++){paint.setAlpha(progress>0&&i>=progress*bars?85:255);float level=.06f;for(int j=i*count/bars;j<(i+1)*count/bars;j++)level=Math.max(level,levels[j]);float half=Math.max(2,level*(mid-3));canvas.drawLine(step*(i+.5f),mid-half,step*(i+.5f),mid+half,paint);}}
    }
}
