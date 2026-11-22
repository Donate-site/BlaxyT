package app.blaxyt.mod;

import android.content.SharedPreferences;
import com.ss.android.ugc.aweme.feed.model.Aweme;
import com.ss.android.ugc.aweme.feed.model.FeedItemList;
import java.util.List;

/** BlaxyT feed pipeline: capture preferences, select entries, then invoke plugins once. */
public final class Feed {
    public static final String KEY="hide_ads", KEY_LIVE="hide_live", KEY_PHOTOS="hide_photos";
    public static final String KEY_STORIES="hide_stories", KEY_SUGGESTIONS="hide_suggestions";
    private static int dropped;
    private static long reportedAt;
    private Feed() {}
    static void reloadSettings() {}
    public static boolean isEnabled() { return true; }
    public static void setEnabled(boolean enabled) { setHides(KEY,enabled); }
    public static boolean hides(String key) {
        if (KEY_SUGGESTIONS.equals(key)) return false;
        SharedPreferences store=preferences();
        return store!=null && store.getBoolean(key,false);
    }
    public static void setHides(String key,boolean enabled) {
        SharedPreferences store=preferences();
        if (store!=null) store.edit().putBoolean(key,enabled).apply();
    }
    private static SharedPreferences preferences() {
        try {
            android.content.Context context=BlaxyRuntime.context();
            return context==null ? null : context.getSharedPreferences(BlaxyRuntime.PREFS,0);
        } catch (Throwable unavailable) { return null; }
    }

    /** One immutable option set per page; no preference reads inside the item loop. */
    private static final class PageRules implements StableFilter.Exclusion {
        final boolean live,photos,stories,suggestions,authors,dates,shortVideos;
        final String account;
        final PostFacts facts;
        PageRules() {
            SharedPreferences p=preferences();
            live=read(p,KEY_LIVE); photos=read(p,KEY_PHOTOS);
            stories=read(p,KEY_STORIES); suggestions=read(p,KEY_SUGGESTIONS);
            authors=!FeedBlacklist.items().isEmpty() || !FeedBlacklist.cards().isEmpty();
            dates=FeedDates.active();
            shortVideos=BlaxyExtensions.active(ExtensionPolicy.SHORT);
            String id;
            try { id=Account.id(); } catch (Throwable unavailable) { id=null; }
            account=id;
            facts=new PostFacts(account);
        }
        private static boolean read(SharedPreferences p,String key) { return p!=null && p.getBoolean(key,false); }
        public boolean rejects(Object item) {
            if (!(item instanceof Aweme)) return false;
            PostFacts post=facts.load((Aweme)item);
            // Preserve the native distinction between reposts and suggestion/story cards.
            if (stories && !socialRepost(item) && Boolean.TRUE.equals(NativeRead.get(item,"getIsTikTokStory"))) return true;
            if (suggestions && suggestion(item)) return true;
            if (authors && FeedBlacklist.hides(item)) return true;
            if (dates && post.outsideDates()) return true;
            if (post.mine()) return false;
            try {
                if (advert(item)) return true;
                if (post.live()) return live;
                if (photos && post.photos()) return true;
                if(shortVideos && !post.photos() && ExtensionPolicy.longVideo(NativeRead.number(NativeRead.get(NativeRead.get(item,"getVideo"),"getDuration")),false))return true;
                return Tags.blocks(post.value);
            } catch (Throwable unavailable) { return false; }
        }
    }

    /** Lazy facts avoid repeating live-room and ownership checks for the same post. */
    private static final class PostFacts {
        Aweme value;
        final String account;
        Boolean live,mine;
        PostFacts(String account) { this.account=account; }
        PostFacts load(Aweme next) { value=next; live=null; mine=null; return this; }
        boolean mine() {
            if (mine==null) {
                boolean answer=false;
                try { answer=account!=null && !account.isEmpty() && account.equals(value.getAuthorUid()); }
                catch (Throwable unavailable) {}
                mine=answer;
            }
            return mine;
        }
        boolean live() {
            if (live==null) {
                boolean answer=false;
                try { answer=value.getAwemeType()==101 || value.getLiveId()!=0 || value.getRoomFeedCellStruct()!=null; }
                catch (Throwable unavailable) {}
                live=answer;
            }
            return live;
        }
        boolean photos() {
            try { return value.getPhotoModeImageInfo()!=null; }
            catch (Throwable unavailable) { return false; }
        }
        boolean outsideDates() {
            try {
                return !mine() && !live() && !advert(value)
                    && FeedDates.leavesOut(NativeRead.number(NativeRead.get(value,"getCreateTime")));
            } catch (Throwable unavailable) { return false; }
        }
    }

    static boolean advert(Object post) {
        return post instanceof Aweme && (((Aweme)post).isAd() || Boolean.TRUE.equals(NativeRead.field(post,"_isAd")));
    }
    static boolean socialRepost(Object post) {
        return NativeRead.get(post,"getUpvoteReason")!=null || NativeRead.get(post,"getUpvoteInfo")!=null || NativeRead.get(post,"getUpvotePreload")!=null;
    }
    static boolean suggestion(Object post) {
        return NativeRead.number(NativeRead.get(post,"getRecommendCardType"))>0 && !socialRepost(post);
    }
    public static List withoutAds(List items) {
        return StableFilter.select(items,item -> {
            boolean direct=item instanceof Aweme;
            Object post=direct ? item : NativeRead.get(item,"getAweme");
            boolean card=!direct && (NativeRead.get(item,"getPreciseAd")!=null || NativeRead.get(item,"getAiAdCard")!=null);
            return advert(post) || card;
        });
    }
    public static List rawItems(FeedItemList page) { return page==null ? null : withoutAds(page.items); }
    public static List searchItems(com.ss.android.ugc.aweme.search.pages.result.topsearch.core.model.SearchMixFeedList page) {
        if (page==null) return null;
        List given=page.mItems, result=withoutAds(given);
        int had=given==null ? -1 : given.size(), left=result==null ? -1 : result.size();
        if(had<=0||left!=had)Diary.note("search: a page of "+(had<0?"nothing at all":had+" from TikTok")+(left!=had?", "+left+" after the mod":""));
        return result;
    }
    public static List getItems(FeedItemList page) {
        if (page==null) return null;
        long started=Probe.start();
        try { return filter(page.getItems()); }
        finally { Probe.stop(Probe.FEED,started); }
    }
    private static List filter(List items) {
        if (items==null) return null;
        PageRules rules=new PageRules();
        try {
            List selected=StableFilter.select(items,rules);
            if (selected!=items) report(items.size()-selected.size());
            return Plugins.feed(selected);
        } catch (Throwable failure) {
            Diary.note("feed: leaving the page alone, "+failure);
            return items;
        }
    }
    private static synchronized void report(int count) {
        dropped+=count;
        long now=System.currentTimeMillis();
        if (now-reportedAt<1000L) return;
        reportedAt=now;
        Diary.note("feed: "+count+" left out, "+dropped+" so far");
    }
}
