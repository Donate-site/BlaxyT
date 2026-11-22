package app.blaxyt.mod;
import android.view.View;
/** Compatibility landing sites for existing patched APKs. Native conversation UI is untouched. */
public final class ConversationStyle {
    private ConversationStyle(){}
    static boolean enabled(){return false;}
    public static void input(View root){}
    public static void header(View root){}
    public static void story(View root){}
    public static void greeting(View root){}
    public static void profile(Object owner,View root){}
}
