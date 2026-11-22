package app.blaxyt.mod;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * BlaxyT presentation catalogue. Stable slots belong to badge IDs, not refresh order.
 * Ownership arrives through BadgeSnapshot; source names only identify external artwork.
 */
public final class Badges {

    private Badges() {}

    /**
     * Where the badges live now.
     *
     * They used to be a file in the repository, which worked and cost nothing
     * and had two limits worth leaving it for: granting one meant a commit,
     * and nobody could decide anything about their own. So there is a small
     * service instead, and it answers with the same shape the file had.
     */
    public static final String SERVER = "https://margy.narez.xyz/api";

    private static final String FILES = SERVER + "/icon/";


    /** One badge, as the file describes it. */
    public static final class Badge {
        public final String id;
        public final String image;
        public final int colour;
        public final String title;
        public final String text;
        public final String button;

        Badge(String id, String image, int colour, String title, String text, String button) {
            this.id = id;
            this.image = image;
            this.colour = colour;
            this.title = title;
            this.text = text;
            this.button = button;
        }
    }

public static final String KEY = "badges_on";

    private static volatile Boolean cached;

    public static boolean isEnabled() {
        Boolean known = cached;
        if (known != null) return known;
        try {
            android.content.Context context = BlaxyRuntime.context();
            if (context == null) return true;  // on until there is somewhere to read from
            boolean on = context.getSharedPreferences(BlaxyRuntime.PREFS,
                    android.content.Context.MODE_PRIVATE).getBoolean(KEY, true);
            cached = on;
            return on;
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void setEnabled(boolean enabled) {
        cached = enabled;
        try {
            android.content.Context context = BlaxyRuntime.context();
            if (context == null) return;
            context.getSharedPreferences(BlaxyRuntime.PREFS, android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY, enabled).apply();
        } catch (Throwable ignored) {
        }
    }

    /**
     * uid -> every badge that account has, in the order the file lists them.
     * Replaced wholesale on a refresh, never edited in place.
     */
    private static volatile Map<String, Badge[]> known = new HashMap<String, Badge[]>();

    /**
     * The same badges by number, because a name has to carry which one it has.
     *
     * The mark left on a name is a single character, and there is nothing else
     * about it that says whose badge it is -- the view that draws it never sees
     * the account. So the character *is* the number: the first badge is
     * U+E000, the second U+E001, and the list here says which is which. They
     * are private-use codepoints, so nothing else can collide with them, and
     * the numbering only has to hold for as long as the app is running.
     */
    private static volatile Badge[] numbered = new Badge[0];

    /** What the administration says about an account: good or not, and why. */
    static final class Verdict {
        final boolean good;
        final String reason;
        Verdict(boolean good, String reason) { this.good = good; this.reason = reason; }
    }

    private static volatile Map<String, Verdict> verdicts = new HashMap<String, Verdict>();

    /** The mark the administration put on an account, or null. */
    static Verdict verdict(String uid) {
        return uid == null ? null : verdicts.get(uid);
    }

    /**
     * Where the marks sit in the private-use area -- and it moves every start.
     *
     * A mark used to be U+E000 plus the badge's number, the same on every
     * phone and every run. Which meant anybody could put that character in
     * their own name and wear somebody else's badge: the drawing code sees a
     * character, not an account.
     *
     * Two things fix that, and this is the second one. The first is that a
     * name is stripped of every private-use character before the mod adds its
     * own, so nothing typed into a name survives. This covers everywhere else
     * -- a comment, a bio, anything that is not a name: the run picks a random
     * place in the private-use area, so a character copied out of somebody's
     * screenshot is not a mark on anybody else's phone, and not on the same
     * phone tomorrow.
     */
    private static final char FIRST = pick();

    private static final int MOST = 2048;

    private static char pick() {
        // U+E000..U+F8FF is the private-use area. Room for the badges and for
        // the gradients, which take the block straight after.
        int room = 0xF8FF - 0xE000 - MOST - Looks.MOST;
        return (char) (0xE000 + (int) (Math.random() * room));
    }

    /** Where the gradient marks begin, next door to the badges. */
    static char looksFirst() {
        return (char) (FIRST + MOST);
    }

    /** Anything in the private-use area, ours or not. Names are cleared of it. */
    public static boolean isPrivate(char c) {
        return c >= '\uE000' && c <= '\uF8FF';
    }

    private static volatile boolean started;

    /** The mod's own note, for anything that needs a picture and has none. */
    public static Bitmap note() {
        Bitmap known = ownNote;
        if (known != null) return known;
        try {
            byte[] png = android.util.Base64.decode(Emblem.PNG, android.util.Base64.DEFAULT);
            ownNote = BitmapFactory.decodeByteArray(png, 0, png.length);
        } catch (Throwable error) {
            Diary.note("badge: " + error);
        }
        return ownNote;
    }

    private static volatile Bitmap ownNote;

    /** One badge by the name the server gives it. */
    public static Badge byId(String id) {
        for (Badge badge : numbered) {
            if (badge != null && badge.id.equals(id)) return badge;
        }
        return null;
    }

    /** Every badge the server knows, in its order: what the owner's admin offers to give. */
    static Badge[] all() {
        return numbered.clone();
    }

    public static Badge[] of(String uid) {
        if (uid == null) return null;
        String[] ids=BadgeShop.owned(uid,true);
        if(ids.length==0)return null;
        java.util.ArrayList<Badge> result=new java.util.ArrayList<>();
        for(String id:ids){Badge badge=byId(id);if(badge!=null)result.add(badge);}
        return result.isEmpty()?null:result.toArray(new Badge[0]);
    }

    /**
     * The characters that stand for this account's badges, in order.
     *
     * An account can hold several -- one for supporting the mod, one for
     * drawing an icon it ships with -- and each is its own character, so each
     * becomes its own picture and answers its own tap.
     */
    public static String marksFor(String uid) {
        if (!isEnabled()) return "";
        Badge[] held = of(uid);
        if (held == null || held.length == 0) return "";
        Badge[] list = numbered;
        StringBuilder out = new StringBuilder(held.length);
        for (Badge badge : held) {
            for (int i = 0; i < list.length; i++) {
                if (list[i].id.equals(badge.id)) {
                    out.append((char) (FIRST + i));
                    break;
                }
            }
        }
        return out.toString();
    }

    /** Whether a character is one of ours, without looking anything up. */
    public static boolean isMark(char c) {
        return c >= FIRST && c < FIRST + MOST;
    }

    public static Badge byMark(char c) {
        Badge[] list = numbered;
        int at = c - FIRST;
        return at >= 0 && at < list.length ? list[at] : null;
    }

    // ------------------------------------------------------------- keeping up

    /**
     * Read what is on disk, then ask the server -- now and every five minutes.
     *
     * Called from the mod's start-up hook, so the first read happens before
     * TikTok has drawn anything.
     */
    public static synchronized void start(Context context) {
        if (started) return;
        Badge developer = new Badge("developer", "charge_material", 0, "blaxyT · Заряд разработчика",
                "Этот человек участвует в разработке blaxyT", "Понятно");
        Badge supporter = new Badge("supporter", "charge", 0, "blaxyT · Заряд",
                "Заряд — значок сообщества blaxyT", "Понятно");
        Badge ambassador = new Badge("ambassador", "ambassador_material", 0, "blaxyT · Амбассадор",
                "Помогает людям узнавать о blaxyT. Значок выдаётся командой за приглашённых пользователей.", "Понятно");
        Badge helper = new Badge("helper", "helper_material", 0, "blaxyT · Помощник разработчика",
                "Помогает команде разрабатывать и улучшать blaxyT. Значок не даёт прав администратора.", "Понятно");
        numbered = new Badge[] {developer, supporter, ambassador, helper};
        purchased = new Badge[] {supporter};
        development = new Badge[] {developer};
        both = new Badge[] {developer,supporter};
        reversed = new Badge[] {supporter,developer};
        known = new HashMap<String, Badge[]>();
        verdicts = new HashMap<String, Verdict>();
        started = true;
        BadgeShop.refresh(context);
    }

    private static volatile Badge[] purchased,development,both,reversed;
    static void shopChanged(){told();}

    /** Append-only slot allocation: existing text spans keep their meaning during refreshes. */
    static synchronized void importCatalogue(JSONArray catalogue){
        if(!started)start(BlaxyRuntime.context());
        if(catalogue==null)return;
        java.util.ArrayList<Badge> list=new java.util.ArrayList<>(java.util.Arrays.asList(numbered));
        for(int i=0;i<Math.min(catalogue.length(),MOST);i++){
            JSONObject item=catalogue.optJSONObject(i);if(item==null)continue;
            String id=item.optString("id"),source=item.optString("source"),image=item.optString("image");
            if(!BadgeRoles.imported(id)||!id.startsWith("import:"+source+":"))continue;
            if(!image.equals("you_supporter")&&!image.matches("import/[a-f0-9]{64}\\.(png|jpg|webp)"))continue;
            String label=BadgeRoles.sourceLabel(source);if(label==null)continue;
            String tint=item.optString("colour");
            Badge badge=new Badge(id,image,"app".equals(tint)?Accent.colour():colour(tint),item.optString("title")+" · "+label,
                item.optString("description")+"\n\nИсточник: "+label+". Автоматически подтверждается по ID TikTok. Это значок сообщества, не верификация TikTok и не права управления blaxyT.","Понятно");
            int at=-1;for(int n=0;n<list.size();n++)if(list.get(n).id.equals(id)){at=n;break;}
            if(at>=0)list.set(at,badge);else if(list.size()<MOST)list.add(badge);
        }
        numbered=list.toArray(new Badge[0]);
        prefetch(BlaxyRuntime.context());
    }

    /** Ownership is supplied by the server; a renamed handle cannot grant developer access. */
    static void bindUser(String uid,String username) {}

    static void refresh() {
        start(BlaxyRuntime.context());
        BadgeShop.refresh(BlaxyRuntime.context());
        told();
    }

    /**
     * Somebody waiting to hear that the list has arrived.
     *
     * The settings screen, and nothing else: it is built from what is known at
     * the moment it opens, and on the first run after an update what is known
     * is a file cached before any of this existed. Without this the free badge
     * would be offered a minute after the screen it is offered on was drawn.
     */
    private static volatile Runnable waiting;

    public static void tell(Runnable then) {
        waiting = then;
    }

    /** The screen that asked to be told is going away: stop holding on to it. */
    public static void stopTelling(Runnable then) {
        if (waiting == then) waiting = null;
    }

    private static void told() {
        final Runnable then = waiting;
        if (then == null) return;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(then);
    }

    /** When the free badge stops being given out, as the server reckons it. */
    private static volatile long freeUntil;
    private static volatile long serverNow;
    private static volatile long readAt;

    /** Whether the offer is still open, by the server's clock and not ours. */
    public static boolean freeStillOpen() {
        if (serverNow <= 0) return false;
        long since = (android.os.SystemClock.elapsedRealtime() - readAt) / 1000;
        return serverNow + since < freeUntil;
    }

    /**
     * Fetch every badge's picture now rather than when a name needs it.
     *
     * A picture asked for while a name is being drawn cannot be waited on --
     * the badge falls back to the mod's own note and only becomes itself the
     * next time that view is drawn, which is why badges used to appear a beat
     * late or not at all on a profile. There are a handful of pictures and
     * they are cached on disk, so fetching them all at the start costs one
     * round trip on the first run and nothing afterwards.
     */
    private static void prefetch(Context context) {
        try {
            for (Badge badge : numbered) {
                if (badge != null && badge.image.length() > 0) {
                    picture(context, badge.image);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static int colour(String hex) {
        try {
            if (hex.length() == 0) return 0;
            return 0xFF000000 | Integer.parseInt(hex.replace("#", ""), 16);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    // -------------------------------------------------------- their pictures

    private static final Map<String, Bitmap> pictures = new HashMap<String, Bitmap>();

    /** When each picture was last asked for, so a failure is not forever. */
    private static final Map<String, Long> asked = new HashMap<String, Long>();
    private static final java.util.concurrent.ExecutorService IMAGE_WORKERS=java.util.concurrent.Executors.newFixedThreadPool(2);

    /** How long a picture that did not arrive is left alone. */
    private static final long AGAIN = 60000;

    /**
     * The picture a badge names, or null for the mod's own note.
     *
     * Answers from memory or from the cache on disk, and never waits: a
     * picture that is not here yet is fetched on a thread, and the badge draws
     * the note until the next time it is drawn.
     *
     * A fetch that fails is tried again a minute later rather than never: the
     * marker used to say "asked for" and nothing ever took it off, so one bad
     * minute of network left a badge as a note until the app was restarted.
     */
    public static Bitmap picture(final Context context, final String path) {
        if ("charge".equals(path)) return ChargeArt.picture(false);
        if ("charge_material".equals(path)) return ChargeArt.picture(true);
        if ("ambassador_material".equals(path)) return ChargeArt.ambassador();
        if ("helper_material".equals(path)) return ChargeArt.helper();
        if ("you_supporter".equals(path)) return YouBadgeArt.picture();
        if (path == null || path.length() == 0) return null;
        if(path.startsWith("import/")&&!path.matches("import/[a-f0-9]{64}\\.(png|jpg|webp)"))return null;
        long now = android.os.SystemClock.elapsedRealtime();
        synchronized (pictures) {
            Bitmap known = pictures.get(path);
            if (known != null) return known;
            Long when = asked.get(path);
            if (when != null && now - when.longValue() < AGAIN) return null;
            asked.put(path, Long.valueOf(now));
        }

        final File cache = new File(context.getFilesDir(), "margyt/badges/" + name(path));
        IMAGE_WORKERS.execute(new Runnable() {
            @Override
            public void run() {
                byte[] have=Net.read(cache);
                if(have!=null&&remember(path,have)!=null){
                    new Handler(Looper.getMainLooper()).post(()->app.blaxyt.mod.Badge.picturesChanged());return;
                }
                byte[] raw = Net.bytes(path.startsWith("import/")?"https://doska67.duckdns.org/blaxyt/v1/import-icon/"+path.substring(7):FILES + path);
                if (raw == null || raw.length>2000000) return;
                Net.save(cache, raw);
                remember(path, raw);
                new Handler(Looper.getMainLooper()).post(()->{told();app.blaxyt.mod.Badge.picturesChanged();app.blaxyt.mod.Badge.serverChanged();});
            }
        });
        return null;
    }

    private static Bitmap remember(String path, byte[] raw) {
        try {
            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(raw,0,raw.length,options);
            if(options.outWidth<=0||options.outHeight<=0||options.outWidth>8192||options.outHeight>8192)return null;
            options.inJustDecodeBounds=false;options.inSampleSize=1;
            while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>256)options.inSampleSize*=2;
            Bitmap bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.length,options);
            if (bitmap == null) return null;
            // trimmed the way the mod's own note is trimmed. A badge is drawn
            // into a box the height of the line it sits in, so a picture with
            // empty margin around it comes out smaller than one without --
            // which is why the server's badges looked shrunken beside it.
            // spelled out, because `Badge` inside this class means the one
            // nested in it rather than the class that draws them
            bitmap = app.blaxyt.mod.Badge.crop(bitmap);
            synchronized (pictures) {
                pictures.put(path, bitmap);
            }
            return bitmap;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** A file name that is a path's, without being a path. */
    private static String name(String path) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            out.append(Character.isLetterOrDigit(c) || c == '.' ? c : '_');
        }
        String cleaned = out.toString();
        // "." and ".." are not names, they are places
        return cleaned.replace("..", "__");
    }
}
