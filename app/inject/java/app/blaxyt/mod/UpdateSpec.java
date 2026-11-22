package app.blaxyt.mod;

import org.json.JSONObject;

/** Untrusted release metadata is bounded before any file or installer action. */
final class UpdateSpec {
    final String version,sha,notes,packageName;final int code,minSdk;final long bytes;final String json;
    UpdateSpec(String body)throws Exception{
        if(body.length()>16000)throw new Exception("Слишком большой ответ сервера.");
        JSONObject o=new JSONObject(body);
        if(o.getInt("schema")!=1)throw new Exception("Неизвестный формат обновления.");
        version=o.getString("version");sha=o.getString("sha256");packageName=o.getString("package");
        code=o.getInt("version_code");minSdk=o.getInt("min_sdk");bytes=o.getLong("bytes");notes=o.optString("notes","");
        if(!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")||!sha.matches("[a-f0-9]{64}")||code<=0||minSdk<23||minSdk>100
            ||bytes<1_000_000||bytes>800_000_000L||notes.length()>2000||!packageName.equals("com.zhiliaoapp.musically")
            ||!"arm64-v8a".equals(o.getString("abi")))throw new Exception("Некорректные сведения об обновлении.");
        json=body;
    }
    boolean newer(int installed,String pkg,int sdk,String[] abis){
        if(code<=installed||!packageName.equals(pkg)||sdk<minSdk)return false;
        for(String abi:abis)if("arm64-v8a".equals(abi))return true;
        return false;
    }
}
