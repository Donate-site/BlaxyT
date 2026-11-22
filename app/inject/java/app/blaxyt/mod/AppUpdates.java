package app.blaxyt.mod;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;

/** Optional release checks. Downloads and Android's installer always follow a user's action. */
final class AppUpdates {
    private static final String BASE="https://doska67.duckdns.org/blaxyt/v1/update";
    private static final String CERT="cbbd6f7a3729349c746d96ea99f2ce2d39020c013d7578fd18ef17bff8217aa0";
    private static final Handler UI=new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> front=new WeakReference<>(null),panelOwner=new WeakReference<>(null);
    private static WeakReference<Panel> shown=new WeakReference<>(null);
    private static WeakReference<TextView> status=new WeakReference<>(null);
    private static WeakReference<ProgressBar> progress=new WeakReference<>(null);
    private static boolean checking,verifying,awaitingPermission,sessionAsked;
    private static volatile boolean downloading,cancel;
    private static volatile int percent;
    private static long checkedAt;private static int offered,checkGeneration;
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("blaxyt_updates",0);}
    static boolean enabled(Context c){return prefs(c).getBoolean("enabled",false);}
    static void setEnabled(Context c,boolean on){prefs(c).edit().putBoolean("asked",true).putBoolean("enabled",on).apply();}
    private static boolean home(Activity a){return a instanceof SettingsActivity||a.getClass().getName().equals("com.ss.android.ugc.aweme.main.MainActivity");}
    private static boolean visible(Activity a){
        if(a==null||front.get()!=a||a.isFinishing()||a.isDestroyed())return false;
        Panel own=shown.get();
        return a.getWindow().getDecorView().hasWindowFocus()
            ||(panelOwner.get()==a&&own!=null&&own.dialog().isShowing());
    }
    static void resumed(Activity activity){
        front=new WeakReference<>(activity);
        WeakReference<Activity> target=new WeakReference<>(activity);
        UI.postDelayed(()->{
            Activity a=target.get();if(!visible(a)||!home(a)||!Tutorial.finished(a))return;
            if(awaitingPermission){awaitingPermission=false;UpdateSpec ready=pending(a);
                if(ready!=null&&canInstall(a))install(a,ready);else Screen.say("Установку можно продолжить через «Проверить обновления».");return;}
            if(downloading){downloadPanel(a);return;}
            if(!prefs(a).getBoolean("asked",false)&&!sessionAsked){consent(a);return;}
            if(enabled(a))check(a,false);
        },1800);
    }
    static void paused(Activity activity){
        if(front.get()==activity)front.clear();
        if(panelOwner.get()==activity){Panel p=shown.get();if(p!=null)p.close();shown.clear();panelOwner.clear();status.clear();progress.clear();}
    }
    private static void present(Activity a,Panel panel){
        Panel old=shown.get();if(old!=null)old.close();shown=new WeakReference<>(panel);panelOwner=new WeakReference<>(a);panel.show();
    }
    private static void consent(Activity a){
        sessionAsked=true;
        Panel p=Panel.with(a,Skin.remembered(a),"Обновлять BlaxyT?");
        p.text("При выходе новой версии показывать карточку обновления? После нажатия «Обновить» приложение скачает APK и откроет установку Android. Можно отключить в настройках.");
        p.primary("Да, предлагать обновления",()->{setEnabled(a,true);check(a,true);});
        p.quiet("Нет, спасибо",()->setEnabled(a,false));
        p.dialog().setOnCancelListener(d->setEnabled(a,false));present(a,p);
    }
    private static File target(Context c){File dir=new File(c.getFilesDir(),"margyt");dir.mkdirs();return new File(dir,"blaxyt-update.apk");}
    private static UpdateSpec pending(Context c){
        try{UpdateSpec s=new UpdateSpec(prefs(c).getString("ready",""));if(target(c).length()==s.bytes&&s.newer(installed(c).versionCode,c.getPackageName(),Build.VERSION.SDK_INT,Build.SUPPORTED_ABIS))return s;}catch(Exception ignored){}
        return null;
    }
    private static PackageInfo installed(Context c)throws Exception{return c.getPackageManager().getPackageInfo(c.getPackageName(),PackageManager.GET_SIGNATURES);}
    private static HttpURLConnection connection(String url)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(10000);c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(false);c.setRequestProperty("Accept-Encoding","identity");c.setRequestProperty("User-Agent","BlaxyT updater/1");return c;
    }
    static void check(Activity activity,boolean manual){
        if(downloading){downloadPanel(activity);return;}
        if(checking||verifying)return;
        UpdateSpec ready=pending(activity);if(ready!=null){if(manual||offered!=ready.code)offer(activity,ready,true);return;}
        long now=android.os.SystemClock.elapsedRealtime();if(!manual&&checkedAt!=0&&now-checkedAt<900000)return;
        checking=true;checkedAt=now;Context app=activity.getApplicationContext();WeakReference<Activity> requester=new WeakReference<>(activity);
        final int generation=++checkGeneration;
        final Panel waiting=manual?Panel.with(activity,Skin.remembered(activity),"Проверка обновлений"):null;
        if(waiting!=null){waiting.text("Проверяем новую версию BlaxyT…").quiet("Скрыть",()->{});present(activity,waiting);}
        UI.postDelayed(()->{
            if(!checking||generation!=checkGeneration)return;
            checking=false;checkGeneration++;checkedAt=0;
            Activity a=requester.get();boolean foreground=visible(a);
            if(waiting!=null)waiting.close();
            if(manual&&foreground)Screen.say("Сервер обновлений не ответил вовремя. Повторите проверку.");
        },35000);
        Net.away("blaxyt-update-check",()->{
            UpdateSpec found=null;String error=null;
            try{
                HttpURLConnection c=connection(BASE);
                try{
                    if(c.getResponseCode()!=200)throw new IOException("Сервис обновлений пока недоступен.");
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] block=new byte[2048];int n;
                    try(InputStream in=c.getInputStream()){while((n=in.read(block))!=-1){if(bytes.size()+n>16000)throw new IOException("Некорректный ответ.");bytes.write(block,0,n);}}
                    UpdateSpec s=new UpdateSpec(bytes.toString("UTF-8"));
                    if(s.newer(installed(app).versionCode,app.getPackageName(),Build.VERSION.SDK_INT,Build.SUPPORTED_ABIS))found=s;
                }finally{c.disconnect();}
            }catch(Throwable e){Diary.note("update check: "+e.getClass().getSimpleName());error="Не удалось проверить обновления. Попробуйте позже.";}
            final UpdateSpec next=found;final String issue=error;
            UI.post(()->{
                if(generation!=checkGeneration)return;
                checking=false;Activity a=requester.get();boolean foreground=visible(a);
                if(waiting!=null)waiting.close();
                if(issue!=null)checkedAt=0;
                if(!foreground)return;
                if(next!=null&&(manual||(enabled(a)&&offered!=next.code&&prefs(a).getLong("later_until",0)<System.currentTimeMillis())))offer(a,next,false);
                else if(manual)Screen.say(issue==null?"Установлена актуальная версия BlaxyT.":issue);
            });
        });
    }
    private static void offer(Activity a,UpdateSpec s,boolean ready){
        offered=s.code;
        Panel p=Panel.with(a,Skin.remembered(a),"BlaxyT "+s.version);
        p.text((ready?"Обновление скачано. ":"Доступно обновление · "+(s.bytes/1048576)+" МБ. ")+s.notes);
        p.primary(ready?"Установить обновление":"Обновить",()->{if(ready)install(a,s);else download(a,s);});
        p.quiet("Позже",()->prefs(a).edit().putLong("later_until",System.currentTimeMillis()+6*3600000L).apply());present(a,p);
    }
    private static void downloadPanel(Activity a){
        Panel old=shown.get();if(old!=null&&old.dialog().isShowing())return;
        Skin skin=Skin.remembered(a);Panel p=Panel.with(a,skin,"Загрузка BlaxyT");
        TextView label=new TextView(a);label.setTextColor(skin.text);label.setText("Загружено "+percent+"%");p.view(label);
        ProgressBar bar=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgress(percent);p.view(bar);
        p.quiet("Скрыть",()->{});p.quiet("Отменить загрузку",()->cancel=true);
        status=new WeakReference<>(label);progress=new WeakReference<>(bar);present(a,p);
    }
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
    private static void verify(Context c,UpdateSpec s,File file)throws Exception{
        if(file.length()!=s.bytes)throw new IOException("APK скачан не полностью.");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] block=new byte[65536];int n;
        try(InputStream in=new FileInputStream(file)){while((n=in.read(block))!=-1)digest.update(block,0,n);}
        if(!hex(digest.digest()).equals(s.sha))throw new IOException("Контрольная сумма APK не совпала.");
        PackageInfo own=installed(c),apk=c.getPackageManager().getPackageArchiveInfo(file.getPath(),PackageManager.GET_SIGNATURES);
        if(apk==null||!s.packageName.equals(apk.packageName)||apk.versionCode!=s.code||apk.versionCode<=own.versionCode
            ||apk.signatures==null||apk.signatures.length!=1||own.signatures==null||own.signatures.length!=1
            ||!Arrays.equals(apk.signatures[0].toByteArray(),own.signatures[0].toByteArray())
            ||!hex(MessageDigest.getInstance("SHA-256").digest(apk.signatures[0].toByteArray())).equals(CERT))throw new IOException("Подпись или версия APK не подходит. Установка отменена.");
    }
    private static void download(Activity a,UpdateSpec s){
        if(downloading)return;
        Context app=a.getApplicationContext();File dest=target(app),part=new File(dest.getParentFile(),"blaxyt-update.part");
        if(dest.getParentFile().getUsableSpace()<s.bytes*2+67108864){Screen.say("Освободите около 1 ГБ для загрузки и установки обновления.");return;}
        downloading=true;cancel=false;percent=0;downloadPanel(a);
        Net.away("blaxyt-update-download",()->{
            String issue=null;boolean done=false;HttpURLConnection c=null;
            try{
                c=connection(BASE+"/"+s.sha+".apk");
                if(c.getResponseCode()!=200)throw new IOException("Сервер занят. Попробуйте позже.");
                long received=0,last=0;byte[] block=new byte[65536];int n;
                try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(part)){
                    while((n=in.read(block))!=-1){if(cancel)throw new IOException("Загрузка отменена.");received+=n;if(received>s.bytes)throw new IOException("Неверный размер APK.");out.write(block,0,n);
                        percent=(int)(received*100/s.bytes);long now=android.os.SystemClock.elapsedRealtime();if(now-last>250){last=now;UI.post(()->{TextView t=status.get();ProgressBar b=progress.get();if(t!=null)t.setText("Загружено "+percent+"%");if(b!=null)b.setProgress(percent);});}}
                    out.getFD().sync();
                }
                if(cancel)throw new IOException("Загрузка отменена.");verify(app,s,part);
                if(dest.exists()&&!dest.delete())throw new IOException("Не удалось заменить предыдущую загрузку.");
                if(!part.renameTo(dest))throw new IOException("Не удалось сохранить APK.");
                prefs(app).edit().putString("ready",s.json).apply();done=true;
            }catch(Exception e){issue=e instanceof IOException?e.getMessage():"Не удалось загрузить обновление.";}
            finally{if(c!=null)c.disconnect();if(part.exists())part.delete();}
            final boolean success=done;final String message=issue;
            UI.post(()->{downloading=false;Panel p=shown.get();if(p!=null)p.close();Activity current=front.get();
                if(!visible(current)){if(success)offered=0;return;}if(success)install(current,s);else Screen.say(message);
            });
        });
    }
    private static boolean canInstall(Activity a){return Build.VERSION.SDK_INT<26||a.getPackageManager().canRequestPackageInstalls();}
    private static void install(Activity a,UpdateSpec s){
        if(verifying)return;
        if(!canInstall(a)){
            Panel p=Panel.with(a,Skin.remembered(a),"Разрешить установку обновления");
            p.text("В настройках Android разрешите установку из источника BlaxyT. После возвращения откроется подтверждение установки.");
            p.primary("Открыть настройки Android",()->{try{awaitingPermission=true;a.startActivity(new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES",Uri.parse("package:"+a.getPackageName())));}catch(Exception e){awaitingPermission=false;Screen.say("Не удалось открыть настройки установки.");}});
            p.quiet("Позже",()->{});present(a,p);return;
        }
        verifying=true;Context app=a.getApplicationContext();WeakReference<Activity> requester=new WeakReference<>(a);
        Net.away("blaxyt-update-verify",()->{
            String error=null;try{verify(app,s,target(app));}catch(Exception e){error=e.getMessage();prefs(app).edit().remove("ready").apply();}
            final String issue=error;UI.post(()->{verifying=false;Activity current=requester.get();if(!visible(current))return;
                if(issue!=null){Screen.say(issue);return;}
                try{
                    Uri uri=BlaxyProvider.share(current,target(current));if(uri==null)throw new IOException();
                    Intent intent=new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.setClipData(android.content.ClipData.newRawUri("BlaxyT",uri));current.startActivity(intent);
                }catch(Exception e){Screen.say("Android не открыл установку. Повторите через настройки BlaxyT.");}
            });
        });
    }
    private AppUpdates(){}
}
