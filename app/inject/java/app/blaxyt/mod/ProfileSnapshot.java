package app.blaxyt.mod;

import android.content.SharedPreferences;
import org.json.JSONObject;

/** Public creator preview, isolated from the signed-in account and its identity settings. */
final class ProfileSnapshot {
    static final String UID="7662448315568931873", HANDLE="androidtexhhh";
    final String uid=UID,name,handle,avatar;
    final String[] counts;
    private static volatile ProfileSnapshot cached;
    private ProfileSnapshot(String name,String handle,String avatar,String[] counts){this.name=name;this.handle=handle;this.avatar=avatar;this.counts=counts;}
    private static SharedPreferences prefs(){return BlaxyRuntime.context().getSharedPreferences("blaxyt_creator_preview_v1",0);}
    static ProfileSnapshot read(){
        ProfileSnapshot known=cached;if(known!=null)return known;
        try{SharedPreferences p=prefs();known=new ProfileSnapshot(p.getString("name","@"+HANDLE),"@"+p.getString("handle",HANDLE),p.getString("avatar",""),new String[]{p.getString("count0","—"),p.getString("count1","—"),p.getString("count2","—")});}
        catch(Throwable ignored){known=new ProfileSnapshot("@"+HANDLE,"@"+HANDLE,"",new String[]{"—","—","—"});}
        cached=known;return known;
    }
    private static String count(Object value,String fallback){
        if(!(value instanceof Number))return fallback;
        double d=((Number)value).doubleValue();long n=((Number)value).longValue();
        return n>=0&&d==n?Long.toString(n):fallback;
    }
    static ProfileSnapshot parse(JSONObject info) throws Exception {
        JSONObject user=info.getJSONObject("user"),stats=info.optJSONObject("stats");
        if(!UID.equals(user.optString("id")))throw new java.io.IOException("Unexpected preview account");
        ProfileSnapshot old=read();String[] counts=old.counts.clone();
        if(stats!=null){String[] keys={"followingCount","followerCount","heartCount"};for(int i=0;i<3;i++)counts[i]=count(stats.opt(keys[i]),counts[i]);}
        String name=user.optString("nickname",old.name),handle=user.optString("uniqueId",HANDLE),avatar=user.optString("avatarThumb",old.avatar);
        if(name.isEmpty())name=old.name;if(!handle.matches("[A-Za-z0-9._]{1,40}"))handle=HANDLE;
        if(!avatar.startsWith("https://"))avatar=old.avatar;
        return new ProfileSnapshot(name,"@"+handle,avatar,counts);
    }
    static synchronized void store(ProfileSnapshot next){
        ProfileSnapshot old=read();
        if(old.name.equals(next.name)&&old.handle.equals(next.handle)&&old.avatar.equals(next.avatar)&&java.util.Arrays.equals(old.counts,next.counts))return;
        cached=next;
        try{SharedPreferences.Editor e=prefs().edit().putString("name",next.name).putString("handle",next.handle.substring(1)).putString("avatar",next.avatar);for(int i=0;i<3;i++)e.putString("count"+i,next.counts[i]);e.apply();}catch(Throwable ignored){}
        ProfilePreview.changed();
    }
    static void observe(Object user){
        if(user==null||!UID.equals(NativeRead.uid(user)))return;
        try{
            ProfileSnapshot old=read();String name=NativeRead.string(NativeRead.get(user,"getNickname")),handle=NativeRead.string(NativeRead.get(user,"getUniqueId"));
            String[] counts=old.counts.clone(),methods={"getFollowingCount","getFollowerCount","getTotalFavorited"};
            for(int i=0;i<3;i++)counts[i]=count(NativeRead.get(user,methods[i]),counts[i]);
            String avatar=DownloadQuality.first(NativeRead.get(user,"getAvatarThumb"));
            store(new ProfileSnapshot(name.isEmpty()?old.name:name,handle.isEmpty()?old.handle:"@"+handle,avatar==null?old.avatar:avatar,counts));
        }catch(Throwable ignored){}
    }
    static void fetch() throws Exception {
        java.net.HttpURLConnection link=(java.net.HttpURLConnection)new java.net.URL("https://www.tiktok.com/@"+HANDLE).openConnection();
        try{
            link.setConnectTimeout(8000);link.setReadTimeout(8000);link.setInstanceFollowRedirects(false);
            link.setRequestProperty("User-Agent","Mozilla/5.0");link.setRequestProperty("Accept-Language","en-US");
            if(link.getResponseCode()!=200)return;
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
            try(java.io.InputStream in=link.getInputStream()){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){if(out.size()+n>2*1024*1024)return;out.write(b,0,n);}}
            String html=out.toString("UTF-8");java.util.regex.Matcher m=java.util.regex.Pattern.compile("<script[^>]*id=[\"']__UNIVERSAL_DATA_FOR_REHYDRATION__[\"'][^>]*>(.*?)</script>",java.util.regex.Pattern.DOTALL).matcher(html);
            if(m.find())store(parse(new JSONObject(m.group(1)).getJSONObject("__DEFAULT_SCOPE__").getJSONObject("webapp.user-detail").getJSONObject("userInfo")));
        }finally{link.disconnect();}
    }
}
