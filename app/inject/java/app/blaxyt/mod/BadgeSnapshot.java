package app.blaxyt.mod;

import java.util.*;
import org.json.*;

/** Immutable ownership snapshot, published in one assignment after full validation. */
final class BadgeSnapshot {
    static final BadgeSnapshot EMPTY=new BadgeSnapshot(0,Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap());
    final long received;
    private final Map<String,Map<String,Long>> grants;
    private final Map<String,List<String>> ordering;
    private final Map<String,Set<String>> hidden;
    private BadgeSnapshot(long time,Map<String,Map<String,Long>> grants,Map<String,List<String>> ordering,Map<String,Set<String>> hidden) {
        received=time;this.grants=Collections.unmodifiableMap(grants);
        this.ordering=Collections.unmodifiableMap(ordering);this.hidden=Collections.unmodifiableMap(hidden);
    }
    static BadgeSnapshot parse(JSONObject root,long time) throws JSONException {
        if(root.getInt("schema")!=1)throw new JSONException("Unsupported badge schema");
        Map<String,Map<String,Long>> grants=new HashMap<>();
        JSONObject local=root.getJSONObject("badges");
        Iterator<String> people=local.keys();
        while(people.hasNext()) {
            String uid=people.next();if(!uid.matches("[0-9]{6,24}"))continue;
            JSONObject row=local.getJSONObject(uid);Map<String,Long> badges=new HashMap<>();
            for(String role:BadgeRoles.IDS)if(row.has(role)) {
                long expiry=row.getLong(role);if(expiry>=0)badges.put(role,expiry);
            }
            grants.put(uid,badges);
        }
        JSONObject external=root.optJSONObject("external_badges");
        if(external!=null) {
            people=external.keys();
            while(people.hasNext()) {
                String uid=people.next();if(!uid.matches("[0-9]{6,24}"))continue;
                JSONArray ids=external.optJSONArray(uid);if(ids==null)continue;
                Map<String,Long> badges=grants.get(uid);
                if(badges==null){badges=new HashMap<>();grants.put(uid,badges);}
                for(int i=0;i<Math.min(ids.length(),64);i++) {
                    String id=ids.optString(i);if(BadgeRoles.imported(id))badges.put(id,0L);
                }
            }
        }
        Map<String,List<String>> ordering=new HashMap<>();Map<String,Set<String>> hidden=new HashMap<>();
        JSONObject rules=root.optJSONObject("badge_settings");
        if(rules!=null) {
            people=rules.keys();
            while(people.hasNext()) {
                String uid=people.next();JSONObject rule=rules.optJSONObject(uid);if(rule==null)continue;
                ordering.put(uid,Collections.unmodifiableList(ids(rule.optJSONArray("order"))));
                hidden.put(uid,Collections.unmodifiableSet(new HashSet<>(ids(rule.optJSONArray("hidden")))));
            }
        }
        for(Map.Entry<String,Map<String,Long>> row:grants.entrySet())row.setValue(Collections.unmodifiableMap(row.getValue()));
        return new BadgeSnapshot(time,grants,ordering,hidden);
    }
    private static List<String> ids(JSONArray list) {
        LinkedHashSet<String> ids=new LinkedHashSet<>();
        if(list!=null)for(int i=0;i<Math.min(list.length(),68);i++) {
            String id=list.optString(i);if(BadgeRoles.local(id)||BadgeRoles.imported(id))ids.add(id);
        }
        return new ArrayList<>(ids);
    }
    boolean visible(String uid,String id) {Set<String> list=hidden.get(uid);return list==null||!list.contains(id);}
    boolean holds(String uid,String id,long now) {
        if(BadgeRoles.local(id)&&now-received>86400000L)return false;
        Map<String,Long> row=grants.get(uid);Long end=row==null?null:row.get(id);
        return end!=null&&(end==0||end>now/1000L);
    }
    int rank(String uid,String id) {
        List<String> order=ordering.get(uid);int at=order==null?-1:order.indexOf(id);if(at>=0)return at;
        for(int i=0;i<BadgeRoles.IDS.length;i++)if(BadgeRoles.IDS[i].equals(id))return 100+i;
        if(id.startsWith("import:margyt:"))return 1000;
        if(id.startsWith("import:you:"))return 2000;
        return 3000;
    }
    String[] owned(String uid,boolean visibleOnly,long now) {
        Map<String,Long> row=grants.get(uid);if(row==null)return new String[0];
        List<String> result=new ArrayList<>();
        for(String id:row.keySet())if(holds(uid,id,now)&&(!visibleOnly||visible(uid,id)))result.add(id);
        Collections.sort(result,(a,b)->{int difference=Integer.compare(rank(uid,a),rank(uid,b));return difference!=0?difference:a.compareTo(b);});
        return result.toArray(new String[0]);
    }
}
