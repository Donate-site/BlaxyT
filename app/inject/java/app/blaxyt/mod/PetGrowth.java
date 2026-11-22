package app.blaxyt.mod;

/** Persistent growth is a count of eaten fish, independent of pixels and skins. */
final class PetGrowth {
    static final String PREFIX="blaxyt_kyksik_meals_v1_";
    static final int MAX_MEALS=20;
    private PetGrowth() {}
    static int clamp(int meals) { return Math.max(0,Math.min(MAX_MEALS,meals)); }
    static int eat(int meals) { return Math.min(MAX_MEALS,clamp(meals)+1); }
    static float scale(int meals) { return 1f+clamp(meals)/(float)MAX_MEALS; }
    static float approach(float current,float target,float seconds) {
        if (!(seconds>0) || Float.isNaN(seconds) || Float.isInfinite(seconds)) return current;
        float difference=target-current;
        if (Math.abs(difference)<.01f) return target;
        return current+difference*(1f-(float)Math.exp(-8f*Math.min(seconds,.1f)));
    }
}
