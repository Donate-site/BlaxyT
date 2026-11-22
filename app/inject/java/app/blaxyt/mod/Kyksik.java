package app.blaxyt.mod;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Bundled blaxyT mascots. Small, bounded decoded sprites, no network. */
final class Kyksik {
    private Kyksik() {}

    static final String[] STYLES = {"classic", "glossy", "kris", "susie", "ralsei", "noelle", "roaring", "cosmic", "frost", "cyber", "gold"};
    private static final Bitmap[] CACHE = new Bitmap[STYLES.length];

    private static int index(String style) {
        for (int i = 0; i < STYLES.length; i++) if (STYLES[i].equals(style)) return i;
        return 0;
    }

    private static String path(String style) {
        return "blaxyt/kyksik_" + (index(style) == 0 ? "plush" : STYLES[index(style)]) + ".png";
    }

    static synchronized Bitmap picture(Context context, String style) {
        int at = index(style);
        if (CACHE[at] != null) return CACHE[at];
        if (context == null) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream input = context.getAssets().open(path(style))) {
                BitmapFactory.decodeStream(input, null, bounds);
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 384) {
                options.inSampleSize *= 2;
            }
            options.inScaled = false;
            try (InputStream input = context.getAssets().open(path(style))) {
                CACHE[at] = BitmapFactory.decodeStream(input, null, options);
            }
        } catch (Exception error) {
            Diary.note("blaxyT mascot: " + error.getClass().getSimpleName());
        }
        return CACHE[at];
    }

    /** The existing widget accepts PNG bytes as well as JPEG bytes. */
    static byte[] bytes(Context context, String style) {
        if (context == null) return null;
        try (InputStream input = context.getAssets().open(path(style));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } catch (Exception error) {
            Diary.note("blaxyT mascot widget: " + error.getClass().getSimpleName());
            return null;
        }
    }
}
