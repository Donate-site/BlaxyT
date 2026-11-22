package app.blaxyt.mod;
/** Small verified model accessors; cache misses, avoid repeated method scans. */
final class NativeRead {
 static Object get(Object object,String name){return ModelAccess.call(object,name);}
 static Object field(Object object,String name){return ModelAccess.read(object,name);}
 static long number(Object object){return object instanceof Number?((Number)object).longValue():0;}
 static String string(Object object){return object instanceof String?(String)object:"";}
 static String uid(Object user){return string(get(user,"getUid"));}
 static String aid(Object post){return string(get(post,"getAid"));}
 static Object item(Object component){
  // 47.2.41: ReusedAssem.LLJJJJLIIL -> 1B0Z.LL is VideoItemParams.
  Object payload=field(field(component,"LLJJJJLIIL"),"LL");if(payload!=null)return payload;
  try{Class<?> type=Class.forName("com.bytedance.assem.arch.reused.ReusedAssem");return Class.forName("X.0cP2").getMethod("LIZ",type).invoke(null,component);}catch(Throwable ignored){return null;}
 }
 static void floating(Object object,String name,float value)throws Exception{
  java.lang.reflect.Method method=object.getClass().getMethod(name,float.class);method.setAccessible(true);method.invoke(object,value);
 }
}
