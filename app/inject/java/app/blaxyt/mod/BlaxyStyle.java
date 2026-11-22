package app.blaxyt.mod;

import android.content.Context;
import android.content.SharedPreferences;

/** One-time visual migration. Network, account, download and motion-blur choices stay untouched. */
final class BlaxyStyle {
    private BlaxyStyle() {}
    static final String KEY = "blaxyt";
    static final int VIOLET = 0xff9674ed;

    static void install(Context context) {
        SharedPreferences settings = context.getSharedPreferences(BlaxyRuntime.PREFS, 0);
        if (!settings.getBoolean("blaxyt_brand_017", false)) {
            if (Themes.currentPreset() == 2) Themes.preset(2);
            settings.edit().putBoolean("blaxyt_brand_017", true).apply();
        }
        if (!settings.getBoolean("blaxyt_removed_pranks_016", false)) {
            settings.edit().remove("screamers").remove("screamers_sound")
                    .remove("fun_avariyka").remove("fun_lag")
                    .putBoolean("blaxyt_removed_pranks_016", true).apply();
        }
        if (settings.getBoolean("blaxyt_visual_014", false)) return;
        SharedPreferences.Editor appearance = AccountAppearance.prefs(context).edit();
        appearance.putInt("appearance_color_background_dark", 0xff100d1b)
                .putInt("appearance_color_background_light", 0xfff5f0ff)
                .putInt("appearance_color_surface_dark", 0xff21192f)
                .putInt("appearance_color_surface_light", 0xffebe2f8)
                .putInt("appearance_color_secondary_background_dark", 0xff191323)
                .putInt("appearance_color_secondary_background_light", 0xfff0e8fc);
        appearance.putBoolean(Themes.KEY_ON, true).putBoolean(Themes.KEY_MATERIAL, false)
                .putBoolean(Themes.KEY_AMOLED, false).putInt(Themes.KEY_STRENGTH, 100)
                .putInt(Themes.KEY_BACKGROUND + "_dark", 0xff100d1b)
                .putInt(Themes.KEY_BACKGROUND + "_light", 0xfff5f0ff)
                .putInt(Themes.KEY_TEXT + "_dark", 0xfff5efff)
                .putInt(Themes.KEY_TEXT + "_light", 0xff211632)
                .putBoolean(Accent.KEY_MATERIAL, false).putInt(Accent.KEY, VIOLET)
                .remove(Themes.KEY_PRESET).putBoolean(Themes.KEY_GRADIENT, false).apply();
        settings.edit().putString(Design.KEY, KEY).putBoolean(NavBar.KEY, true)
                .putBoolean(Md3.KEY_FEED, true).putBoolean(Md3.KEY_PROFILE, true)
                .putBoolean(Md3.KEY_INBOX, true).putBoolean(Corners.KEY, true)
                .putBoolean("blaxyt_visual_014", true).apply();
        Accent.reload(); AppearanceColors.reload(); Themes.forget(); Design.reload();
    }
}
