package app.blaxyt.mod;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

/** Respect the phone's animation scale, including disabled motion. */
final class Motion {
    private Motion() {}

    /** A small press response keeps the original click and scrolling intact. */
    static void press(android.view.View view) {
        view.setFocusable(true);
        touch(view);
        android.graphics.drawable.Drawable base = view.getBackground();
        if (base instanceof android.graphics.drawable.RippleDrawable) return;
        if (base == null) base = new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT);
        view.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf((Accent.colour() & 0xFFFFFF) | 0x22000000),
                base, new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)));
    }

    /** Non-consuming feedback: scrolling cancels it, and the original click still runs. */
    static void touch(android.view.View view) {
        view.setOnTouchListener((target,event) -> {
            if (Design.glass()) {
                GlassOptics.viewTouchX=target.getWidth()>0 ? event.getX()/target.getWidth()*2f-1f : 0;
                GlassOptics.viewTouchY=target.getHeight()>0 ? event.getY()/target.getHeight()*2f-1f : 0;
            }
            int action=event.getActionMasked();
            if(action==android.view.MotionEvent.ACTION_DOWN || action==android.view.MotionEvent.ACTION_UP
                    || action==android.view.MotionEvent.ACTION_CANCEL) {
                boolean down=action==android.view.MotionEvent.ACTION_DOWN && target.isEnabled() && target.isClickable() && enabled(target.getContext());
                float scale=down ? Design.style().press : 1f;
                target.animate().scaleX(scale).scaleY(scale)
                        .setDuration(down ? 110L : (Design.glass() ? 340L : 140L))
                        .setInterpolator(Design.glass() ? new android.view.animation.OvershootInterpolator(.85f)
                                : new android.view.animation.PathInterpolator(0.2f,0f,0f,1f)).start();
            }
            return false;
        });
    }
    static void change(android.view.View view) {
        if(!enabled(view.getContext()))return;
        view.setAlpha(0.65f);view.animate().alpha(1f).setDuration(140L).start();
    }
    /** Finish the outgoing page before entering the selected section. */
    static void navigate(android.view.View view,float direction,Runnable replace) {
        view.animate().withEndAction(null).cancel();
        if(!enabled(view.getContext())){view.setAlpha(1f);view.setTranslationX(0f);replace.run();return;}
        MotionBlur.moving(view);
        view.animate().alpha(0.8f).translationX(-direction*14*view.getResources().getDisplayMetrics().density)
                .setDuration(110L).setInterpolator(new android.view.animation.PathInterpolator(.4f,0f,1f,1f)).withEndAction(() -> {replace.run();enter(view,direction);}).start();
    }

    /** Section navigation has one short directional transition, never per-row delays. */
    static void enter(android.view.View view, float direction) {
        view.animate().withEndAction(null).cancel();
        if (!enabled(view.getContext())) { view.setAlpha(1f); view.setTranslationX(0f); return; }
        MotionBlur.moving(view);
        view.setAlpha(0.75f);
        view.setTranslationX(direction * 24 * view.getResources().getDisplayMetrics().density);
        view.animate().alpha(1f).translationX(0f).setDuration(240L)
                .setInterpolator(new android.view.animation.PathInterpolator(.2f,0f,0f,1f)).start();
    }

    /** Begin below the measured surface, then settle without overshoot. */
    static Runnable rise(android.view.View view) {
        view.animate().withEndAction(null).cancel();
        if (!enabled(view.getContext())) {
            view.setAlpha(1f); view.setTranslationY(0f); return null;
        }
        view.setAlpha(0f);
        Runnable start = () -> {
            if (!view.isAttachedToWindow()) return;
            MotionBlur.moving(view);
            view.setTranslationY(Design.fluent()?8*view.getResources().getDisplayMetrics().density:Math.min(Math.max(view.getHeight(), 1), 64*view.getResources().getDisplayMetrics().density));
            view.animate().alpha(1f).translationY(0f).setDuration(Design.fluent()?180L:280L)
                    .setInterpolator(new android.view.animation.PathInterpolator(0.2f,0f,0f,1f))
                    .start();
        };
        view.post(start);
        return start;
    }

    static boolean enabled(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= 26) return ValueAnimator.areAnimatorsEnabled();
            return Settings.Global.getFloat(context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
        } catch (Throwable ignored) {
            return true;
        }
    }
}
