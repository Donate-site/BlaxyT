package app.blaxyt.mod;

/** Playback-rate policy, independent of Android views and player discovery. */
final class HoldSpeed {
    static final float DEFAULT=2f;
    private HoldSpeed() {}
    static boolean valid(float value) { return !Float.isNaN(value)&&!Float.isInfinite(value)&&value>=0.5f&&value<=5f; }
    static float fromSaved(float value) { return valid(value)?value:DEFAULT; }
}
