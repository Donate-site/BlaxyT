package app.blaxyt.mod;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/**
 * One callback for everything that needs to know a screen came up: the frame
 * rate, the HDR headroom, the test mark. Three registrations would be three
 * times the work on every screen change, and this app changes screens often.
 */
public final class Watch implements Application.ActivityLifecycleCallbacks {

    private Watch() {}

    // One optional module must not abort the Activity lifecycle or later modules.
    static void safely(String module, Runnable action) {
        try { action.run(); }
        catch (RuntimeException | LinkageError error) {
            try { Diary.note(module + ": " + error.getClass().getSimpleName()); }
            catch (RuntimeException | LinkageError ignored) {}
        }
    }

    /** The screen on top, for anything that has to ask about TikTok's own. */
    private static volatile java.lang.ref.WeakReference<Activity> here =
            new java.lang.ref.WeakReference<Activity>(null);

    public static Activity here() {
        return here.get();
    }

    public static void start(Application application) {
        try {
            application.registerActivityLifecycleCallbacks(new Watch());
        } catch (Throwable error) {
            Diary.note("watch: " + error);
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        here = new java.lang.ref.WeakReference<Activity>(activity);
        safely("extensions.resume",()->BlaxyExtensions.resumed(activity));
        try {
            Rate.apply(activity);
        } catch (Throwable ignored) {
        }
        try {
            Brightness.apply(activity);
        } catch (Throwable ignored) {
        }
        try {
            Tester.mark(activity);
        } catch (Throwable ignored) {
        }
        safely("Lag.frames", () -> Lag.frames(activity));
        // what follows dresses TikTok's own screens, and Lite's are not those
        if (!Version.LITE) {
            safely("TabBadges.attach", () -> TabBadges.attach(activity));
            safely("NavBar.attach", () -> NavBar.attach(activity));
            safely("Md3.attach", () -> Md3.attach(activity));
            safely("CommentStyle.attach", () -> CommentStyle.attach(activity));
            safely("ChatFolders.attach", () -> ChatFolders.attach(activity));
            safely("InboxClean.attach", () -> InboxClean.attach(activity));
            safely("ViewEditor.attach", () -> ViewEditor.attach(activity));
            safely("Ghost.resumed", () -> Ghost.resumed(activity));
            safely("TextAnim.resumed", () -> TextAnim.resumed(activity));
            safely("MotionBlur.attach", () -> MotionBlur.attach(activity));
            safely("SponsorBlock.attach", () -> SponsorBlock.attach(activity));
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        safely("extensions.pause",()->BlaxyExtensions.paused(activity));
        safely("MotionBlur.detach", () -> MotionBlur.detach(activity));
        if (!Version.LITE) safely("Ghost.paused", () -> Ghost.paused(activity));
        if (here.get() == activity) {
            here = new java.lang.ref.WeakReference<Activity>(null);
        }
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) { safely("LiteLogin.created", () -> LiteLogin.created(activity)); }
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle out) {}
    @Override public void onActivityDestroyed(Activity activity) { safely("extensions.destroy",()->BlaxyExtensions.paused(activity)); safely("MotionBlur.detach", () -> MotionBlur.detach(activity)); safely("TextAnim.destroyed", () -> TextAnim.destroyed(activity)); }
}
