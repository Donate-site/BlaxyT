package app.blaxyt.mod;
import android.view.MotionEvent;
import android.view.View;

/** The native hold-start path asks for its rate synchronously. No timers or player lookup. */
public final class SpeedGesture {
    private SpeedGesture() {}
    public static float selectedRate() { return PlayerTools.speed(); }
    /** Retained touch-hook contract: gestures remain owned by TikTok. */
    public static boolean onTouch(Object listener, View target, MotionEvent event) { return false; }
}
