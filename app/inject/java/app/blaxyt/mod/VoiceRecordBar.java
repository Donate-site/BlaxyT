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

/** Non-touchable recording strip: the microphone keeps the original finger gesture. */
final class VoiceRecordBar {
    private final PopupWindow popup;
    private final TextView timer,hint;
    private final Wave wave;
    private long elapsed;
    private boolean cancelled;
    VoiceRecordBar(View anchor){
        android.content.Context c=anchor.getContext();Skin skin=Skin.forSettings(c);
        float d=c.getResources().getDisplayMetrics().density;
        LinearLayout box=new LinearLayout(c);box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int)(18*d),(int)(12*d),(int)(18*d),(int)(12*d));
        GradientDrawable background=new GradientDrawable();background.setColor(skin.card);background.setCornerRadius(24*d);background.setStroke((int)Math.max(1,d),Accent.colour());box.setBackground(background);
        timer=new TextView(c);timer.setTextColor(skin.text);timer.setTextSize(15);box.addView(timer);
        wave=new Wave(c);box.addView(wave,new LinearLayout.LayoutParams(-1,(int)(36*d)));
        hint=new TextView(c);hint.setTextSize(12);hint.setTextColor(skin.text);box.addView(hint);
        Rect visible=new Rect();anchor.getWindowVisibleDisplayFrame(visible);
        int width=Math.min((int)(380*d),Math.max((int)(200*d),visible.width()-(int)(24*d)));
        popup=new PopupWindow(box,width,(int)(116*d),false);popup.setTouchable(false);popup.setOutsideTouchable(false);popup.setElevation(10*d);
        int[] where=new int[2];anchor.getLocationOnScreen(where);
        popup.showAtLocation(anchor,Gravity.TOP|Gravity.LEFT,visible.left+(visible.width()-width)/2,Math.max(visible.top,where[1]-(int)(130*d)));
        sample(0,0);
    }
    void cancelHint(boolean value){cancelled=value;labels();}
    void sample(int amplitude,long time){elapsed=time;wave.sample(amplitude);labels();}
    private void labels(){timer.setText("●  Запись · "+(elapsed/1000)+" / 60 с");hint.setText(cancelled?"Отпустите, чтобы отменить":"Отпустите — отправить · Влево — отменить");}
    void close(){popup.dismiss();}
    private static final class Wave extends View{
        final float[] levels=new float[48];final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);int next;
        Wave(android.content.Context c){super(c);paint.setStrokeCap(Paint.Cap.ROUND);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void sample(int amplitude){levels[next]=Math.max(.06f,Math.min(1f,(float)Math.sqrt(Math.max(0,amplitude)/32767f)));next=(next+1)%levels.length;invalidate();}
        protected void onDraw(Canvas canvas){super.onDraw(canvas);float step=getWidth()/(float)levels.length,mid=getHeight()/2f;paint.setColor(Accent.colour());paint.setStrokeWidth(Math.max(2,step*.4f));for(int i=0;i<levels.length;i++){float half=Math.max(2,levels[(next+i)%levels.length]*(mid-3));canvas.drawLine(step*(i+.5f),mid-half,step*(i+.5f),mid+half,paint);}}
    }
}
