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
