package app.blaxyt.mod;
import android.content.Context;
import android.view.View;

/** Silent heading feedback and local mascot artwork. No audio or surprise dialogs. */
public final class Cats {
    private Cats() {}
    public static void tapped(View heading) {
        if (heading == null) return;
        heading.animate().cancel();
        heading.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80)
            .withEndAction(() -> heading.animate().scaleX(1f).scaleY(1f).setDuration(150).start()).start();
    }
    static byte[] fat(Context context) { return Kyksik.bytes(context, "classic"); }
}
