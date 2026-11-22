package app.blaxyt.mod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded model metadata cache. Class identity keeps plugin class loaders isolated. */
final class ModelAccess {
    private static final Object ABSENT=new Object();
    private static final int LIMIT=256;
    private static final Map<Key,Object> METHODS=cache();
    private static final Map<Key,Object> FIELDS=cache();
    private ModelAccess() {}

    private static Map<Key,Object> cache() {
        return new LinkedHashMap<Key,Object>(32,.75f,true) {
            protected boolean removeEldestEntry(Map.Entry<Key,Object> entry) { return size()>LIMIT; }
        };
    }
    private static final class Key {
        final Class<?> owner;
        final String name;
        Key(Class<?> owner,String name) { this.owner=owner; this.name=name; }
        public int hashCode() { return 31*System.identityHashCode(owner)+name.hashCode(); }
        public boolean equals(Object value) {
            if (!(value instanceof Key)) return false;
            Key other=(Key)value;
            return owner==other.owner && name.equals(other.name);
        }
    }
    private static Object lookup(Class<?> owner,String name,boolean field) throws Exception {
        Map<Key,Object> store=field ? FIELDS : METHODS;
        Key key=new Key(owner,name);
        synchronized(store) {
            Object known=store.get(key);
            if (known!=null) return known;
            try {
                if (field) known=owner.getField(name);
                else {
                    Method method=owner.getMethod(name);
                    method.setAccessible(true);
                    known=method;
                }
            } catch (NoSuchFieldException | NoSuchMethodException missing) { known=ABSENT; }
            store.put(key,known);
            return known;
        }
    }
    static Object read(Object object,String name) {
        if (object==null || name==null) return null;
        try {
            Object field=lookup(object.getClass(),name,true);
            return field==ABSENT ? null : ((Field)field).get(object);
        } catch (Throwable unavailable) { return null; }
    }
    static Object call(Object object,String name) {
        if (object==null || name==null) return null;
        try {
            Object method=lookup(object.getClass(),name,false);
            return method==ABSENT ? null : ((Method)method).invoke(object);
        } catch (Throwable unavailable) { return null; }
    }
}
