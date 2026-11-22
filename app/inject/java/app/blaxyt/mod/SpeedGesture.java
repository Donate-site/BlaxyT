package app.blaxyt.mod;
import android.view.MotionEvent;
import android.view.View;

/** The native hold-start path asks for its rate synchronously. No timers or player lookup. */
public final class SpeedGesture {
    private static volatile java.lang.ref.WeakReference<Object> active=new java.lang.ref.WeakReference<Object>(null);
    private static volatile float rate=2f;
    private SpeedGesture() {}
    public static float selectedRate() { return PlayerTools.speed(); }
    public static void begin(Object owner){rate=selectedRate();active=new java.lang.ref.WeakReference<Object>(owner);}
    public static String getString(android.content.Context context,int id){
        String original=context.getString(id);
        if(id==0x7f11766d||id==0x7f117671||id==0x7f11766e||id==0x7f117672)return captionText(original);
        return original;
    }
    static String captionText(String original){
        float speed=selectedRate();String value=speed==(int)speed?Integer.toString((int)speed):Float.toString(speed);
        return original.replaceAll("(?<![0-9])2(?:[.,]0)?(?=\\s*[xX×хХ])",java.util.regex.Matcher.quoteReplacement(value))
            .replaceAll("(?<=[xX×хХ])\\s*2(?:[.,]0)?(?![0-9])",java.util.regex.Matcher.quoteReplacement(value));
    }
    /** Old native speed widget has one text child; refresh it whenever it opens. */
    public static void caption(View view){
        if(!(view instanceof android.view.ViewGroup))return;
        android.view.ViewGroup group=(android.view.ViewGroup)view;
        for(int i=0;i<group.getChildCount();i++)if(group.getChildAt(i) instanceof android.widget.TextView){
            android.widget.TextView text=(android.widget.TextView)group.getChildAt(i);
            try{text.setText(captionText(view.getContext().getString(0x7f112122)));}catch(Throwable ignored){}
        }
    }
    /** Native UI callbacks can write 2x again after start. Preserve resets and ordinary playback. */
    public static float engineRate(float requested){
        if(requested!=2f)return requested;
        Object owner=active.get();
        if(owner==null||!Boolean.TRUE.equals(NativeRead.field(owner,"LLLLLZ")))return requested;
        return HoldSpeed.fromSaved(rate);
    }
    /** Retained touch-hook contract: gestures remain owned by TikTok. */
    public static boolean onTouch(Object listener, View target, MotionEvent event) { return false; }
}
