package app.blaxyt.mod;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Spannable;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.ReplacementSpan;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.TextView;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/** Text comments travel through TikTok; explicitly recorded AAC travels through BlaxyT. */
public final class VoiceComments {
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final String BASE="https://doska67.duckdns.org/blaxyt/v1/voice/";
    private static final Pattern MARK=Pattern.compile("\\[blaxy-voice:([a-f0-9]{32})\\]");
    private static final int BOUND=0x7e0c0392;
    private static WeakReference<Object> input=new WeakReference<Object>(null);
    private static final java.util.WeakHashMap<Object,Boolean> inputs=new java.util.WeakHashMap<Object,Boolean>();
    private static Recording recording;
    private static Listening listening;
    private VoiceComments(){}

    // Anchored to BaseInputAssem.onViewCreated, not arbitrary EditTexts in the app.
    public static void inputReady(Object owner){if(owner!=null)inputs.put(owner,Boolean.TRUE);}
    /** 47.2.41: LLLFZ is the audio icon, never the send/emoji buttons. */
    public static void microphoneReady(Object group){
        try{
            Object icon=NativeRead.field(group,"LLLFZ");if(!(icon instanceof View))return;
            View view=(View)icon;view.setContentDescription("Удерживайте для записи. Отпустите для прослушивания, сдвиньте влево для отмены.");
            view.setOnTouchListener(new Hold(group));
            // Accessibility opens the same comment-anchored composer.
            view.setOnClickListener(v->open(group));
        }catch(Throwable e){Diary.note("voice hold: "+e.getClass().getSimpleName());}
    }
    private static final class Hold implements View.OnTouchListener{
        final WeakReference<Object> group;Recording active;float downX;boolean cancelled;
        Hold(Object value){group=new WeakReference<Object>(value);}
        public boolean onTouch(View view,MotionEvent event){
            try{
                switch(event.getActionMasked()){
                    case MotionEvent.ACTION_DOWN:
                        cancelled=false;downX=event.getRawX();
                        if(recording!=null&&recording.busy){Screen.say("Дождитесь отправки записи.");return true;}
                        active=create(group.get());if(active==null)return true;
                        active.start();
                        if(active.mic==null){active.show();active=null;return true;}
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        if(view.getParent()!=null)view.getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if(active!=null){cancelled=downX-event.getRawX()>96*view.getResources().getDisplayMetrics().density;if(active.bar!=null)active.bar.cancelHint(cancelled);}
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if(view.getParent()!=null)view.getParent().requestDisallowInterceptTouchEvent(false);
                        Recording current=active;active=null;
                        if(current==null||current.closed)return true;
                        if(cancelled||event.getActionMasked()==MotionEvent.ACTION_CANCEL){current.close();Screen.say("Запись отменена");return true;}
                        if(current.interrupted){current.show();return true;}
                        if(current.mic!=null)current.stop();
                        if(current.attach.isEnabled()){current.show();}
                        else{current.close();Screen.say("Удерживайте микрофон хотя бы секунду.");}
                        return true;
                    default:return true;
                }
            }catch(Throwable e){if(active!=null)active.close();active=null;Diary.note("voice gesture: "+e.getClass().getSimpleName());return true;}
        }
    }
    private static Object findInput(String aid){
        Object found=null;int count=0;
        for(Object candidate:inputs.keySet()){
            EditText e=editor(candidate);
            if(e==null||!e.isShown()||!e.isAttachedToWindow()||!aid.equals(video(candidate,"Bq")))continue;
            if(e.hasFocus())return candidate;found=candidate;count++;
        }
        return count==1?found:null;
    }
    private static EditText editor(Object owner){Object value=NativeRead.get(owner,"KH1");return value instanceof EditText?(EditText)value:null;}
    private static String video(Object owner,String contextMethod){
        Object source=NativeRead.get(owner,contextMethod);
        return NativeRead.aid(NativeRead.field(NativeRead.get(source,"getCommonModel"),"aweme"));
    }
    private static boolean valid(String value){return value!=null&&value.matches("[0-9]{6,24}");}
    private static boolean live(String uid){return uid!=null&&uid.equals(Account.liveId());}
    private static boolean proof(Activity activity){
        if(valid(Account.liveId())&&BlaxyLink.verified())return true;
        BlaxyLink.show(activity,Skin.forSettings(activity),()->{});return false;
    }
    private static Recording create(Object iconGroup){
        try{
            Activity activity=Screen.now();if(activity==null)return null;
            if(!proof(activity))return null;
            String aid=video(iconGroup,"jq");Object owner=findInput(aid);EditText edit=editor(owner);
            if(edit==null||!edit.isShown()||!valid(aid)||!aid.equals(video(owner,"Bq"))){
                Screen.say("Откройте поле комментария к ролику и снова нажмите микрофон.");return null;
            }
            if(edit.length()!=0||hasAttachments(owner)){Screen.say("Сначала отправьте или сохраните черновик комментария.");return null;}
            if(recording!=null)recording.close();if(listening!=null)listening.close();
            input=new WeakReference<Object>(owner);
            recording=new Recording(activity,owner,aid,Account.liveId());return recording;
        }catch(Throwable error){Diary.note("voice entry: "+error.getClass().getSimpleName());Screen.say("Не удалось открыть запись. Попробуйте открыть комментарии заново.");}
        return null;
    }
    public static boolean open(Object iconGroup){
        if(recording!=null&&recording.busy)return true;
        Recording next=create(iconGroup);if(next!=null)next.show();
        return true;
    }
    private static boolean hasAttachments(Object owner){
        Object vm=NativeRead.get(owner,"Hq");
        if(NativeRead.get(vm,"m43")!=null||Boolean.TRUE.equals(NativeRead.get(vm,"s43")))return true;
        for(String method:new String[]{"j43","e43"}){Object list=NativeRead.get(vm,method);if(list instanceof java.util.Collection&&!((java.util.Collection)list).isEmpty())return true;}
        return false;
    }
    private static String replyTo(Object owner){
        Object state=NativeRead.get(NativeRead.get(owner,"Hq"),"getState");
        Object reply=NativeRead.field(NativeRead.field(state,"LLJJIJIL"),"LIZ");
        return NativeRead.string(NativeRead.get(reply,"getCid"));
    }
    static void paused(Activity activity){
        if(recording!=null&&recording.activity==activity)recording.close();
        if(listening!=null&&listening.activity==activity)listening.close();
    }
    interface Reply{void done(JSONObject value,String error);}
    private static void request(String action,String uid,JSONObject extra,Reply then){
        request(action,uid,extra,then,true);
    }
    private static void request(String action,String uid,JSONObject extra,Reply then,boolean refresh){
        final JSONObject body=BlaxyLink.credentials(uid);
        Net.away("voice-"+action,()->{
            JSONObject result=null;String error="Сервер голосовых не ответил. Попробуйте ещё раз.";boolean expired=false;
            try{
                java.util.Iterator<String> keys=extra.keys();while(keys.hasNext()){String k=keys.next();body.put(k,extra.get(k));}
                Net.Said said=BlaxyTransport.talk(BASE+action,body.toString());
                error="Сервер ответил HTTP "+said.code+". Запись сохранена здесь — попробуйте отправить ещё раз.";
                JSONObject value=new JSONObject(said.body);
                if(said.ok()){result=value;error="";}else{error=value.optString("error",error);expired=said.code==403&&error.startsWith("Откройте «Профиль");}
            }catch(java.net.SocketTimeoutException e){error="Сервер не успел ответить. Запись сохранена — повторите отправку.";}
            catch(java.net.UnknownHostException e){error="Не удалось найти адрес сервера. Запись сохранена — повторите отправку.";}
            catch(javax.net.ssl.SSLException e){error="Не удалось проверить защищённое соединение. Проверьте дату и время телефона.";}
            catch(Exception e){Diary.note("voice request: "+e.getClass().getSimpleName());}
            final JSONObject value=result;final String message=error;final boolean retry=expired&&refresh;
            MAIN.post(()->{
                if(retry&&live(uid))BlaxyLink.refreshVoiceProof(uid,(proof,trouble)->{
                    if(proof!=null&&proof.optBoolean("active")&&live(uid))request(action,uid,extra,then,false);
                    else then.done(null,trouble.isEmpty()?message:trouble);
                });
                else then.done(value,message);
            });
        });
    }
    private static JSONObject params(String... pairs){JSONObject v=new JSONObject();try{for(int i=0;i<pairs.length;i+=2)v.put(pairs[i],pairs[i+1]);}catch(Exception ignored){}return v;}
    private static byte[] bytes(File file)throws Exception{
        try(FileInputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1){if(out.size()+n>1048576)throw new java.io.IOException("limit");out.write(block,0,n);}return out.toByteArray();
        }
    }
    private static File file()throws Exception{return File.createTempFile("blaxyt-voice-",".aac",BlaxyRuntime.context().getCacheDir());}

    private static final class Recording {
        final Activity activity;final WeakReference<Object> owner;final String aid,uid;
        final TextView status,record,preview,attach;
        MediaRecorder mic;MediaPlayer player;File audio;long began;boolean closed,busy;String nonce="";
        VoiceRecordBar bar;boolean interrupted;final Object context;final String reply;
        final AudioManager sound;final AudioManager.OnAudioFocusChangeListener focus;
        final Runnable monitor=new Runnable(){public void run(){if(closed)return;if(!target()||bar==null||!bar.attached()){close();return;}if(player!=null){try{bar.progress(player.getDuration()>0?player.getCurrentPosition()/(float)player.getDuration():0);}catch(Exception ignored){}}MAIN.postDelayed(this,200);}};
        final Runnable tick=new Runnable(){public void run(){if(closed||mic==null)return;if(!live(uid)||!target()){close();return;}long elapsed=SystemClock.elapsedRealtime()-began;status.setText("Идёт запись · "+(elapsed/1000)+" / 60 с");if(bar!=null){int amplitude=0;try{amplitude=mic.getMaxAmplitude();}catch(Exception ignored){}bar.sample(amplitude,elapsed);}if(elapsed>=60000)stop();else MAIN.postDelayed(this,80);}};
        Recording(Activity a,Object source,String id,String account){
            activity=a;owner=new WeakReference<Object>(source);aid=id;uid=account;
            context=NativeRead.get(source,"Bq");
            reply=replyTo(source);
            sound=(AudioManager)a.getSystemService(Context.AUDIO_SERVICE);
            focus=change->{if(change<0)MAIN.post(()->focusLost());};
            bar=new VoiceRecordBar(editor(source));bar.onDismiss(()->dispose());
            status=bar.status;record=bar.record;preview=bar.preview;attach=bar.attach;
            record.setOnClickListener(v->{if(mic==null)start();else stop();});
            preview.setEnabled(false);preview.setOnClickListener(v->preview());
            attach.setEnabled(false);attach.setOnClickListener(v->upload());
            MAIN.postDelayed(monitor,200);
        }
        void show(){if(!closed&&bar!=null)bar.ready();}
        void focusLost(){if(closed)return;if(mic!=null){interrupted=true;stop();show();status.setText("Запись остановлена: микрофон прерван. Прослушайте перед отправкой.");}stopPreview();}
        void start(){
            if(closed||busy)return;if(!live(uid)){close();return;}
            if(android.os.Build.VERSION.SDK_INT>=23&&activity.checkSelfPermission("android.permission.RECORD_AUDIO")!=PackageManager.PERMISSION_GRANTED){
                status.setText("Разрешите микрофон. Затем снова откройте запись.");activity.requestPermissions(new String[]{"android.permission.RECORD_AUDIO"},4831);return;
            }
            stopPreview();if(bar!=null)bar.recording();
            interrupted=false;
            try{
                if(sound==null||sound.requestAudioFocus(focus,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){status.setText("Микрофон занят. Завершите звонок или воспроизведение и попробуйте снова.");return;}
                if(audio!=null)audio.delete();audio=file();nonce=UUID.randomUUID().toString().replace("-","");
                mic=new MediaRecorder();mic.setAudioSource(MediaRecorder.AudioSource.MIC);mic.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
                mic.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);mic.setAudioChannels(1);mic.setAudioSamplingRate(24000);mic.setAudioEncodingBitRate(48000);
                mic.setMaxDuration(60000);mic.setMaxFileSize(1000000);mic.setOutputFile(audio.getAbsolutePath());
                mic.setOnInfoListener((m,what,extra)->{if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED||what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED)MAIN.post(()->stop());});
                mic.setOnErrorListener((m,what,extra)->MAIN.post(()->{releaseMic();status.setText("Запись прервалась. Запишите ещё раз.");attach.setEnabled(false);preview.setEnabled(false);}));
                mic.prepare();mic.start();began=SystemClock.elapsedRealtime();record.setText("Остановить запись");preview.setEnabled(false);attach.setEnabled(false);MAIN.post(tick);
            }catch(Exception e){releaseMic();status.setText("Не удалось включить микрофон. Проверьте разрешение и попробуйте снова.");}
        }
        void releaseMic(){MAIN.removeCallbacks(tick);if(mic!=null){try{mic.release();}catch(Exception ignored){}mic=null;}if(sound!=null)sound.abandonAudioFocus(focus);record.setText("Записать заново");}
        void stop(){
            if(mic==null)return;long duration=SystemClock.elapsedRealtime()-began;boolean okay=false;
            try{mic.stop();okay=duration>=400&&audio!=null&&audio.length()>100;}catch(Exception ignored){}finally{releaseMic();}
            preview.setEnabled(okay);attach.setEnabled(okay);status.setText(okay?"Прослушайте и нажмите «Отправить».":"Запись слишком короткая. Попробуйте ещё раз.");show();
        }
        void stopPreview(){if(player!=null){try{player.release();}catch(Exception ignored){}player=null;}preview.setText("▶ Слушать");if(bar!=null)bar.progress(0);if(sound!=null&&mic==null)sound.abandonAudioFocus(focus);}
        void preview(){
            if(player!=null){try{if(player.isPlaying()){player.pause();preview.setText("▶ Продолжить");}else{player.start();preview.setText("Ⅱ Пауза");}}catch(Exception e){stopPreview();}return;}if(audio==null||closed||busy)return;
            try{
                if(sound!=null&&sound.requestAudioFocus(focus,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)return;
                MediaPlayer next=new MediaPlayer();player=next;next.setDataSource(audio.getAbsolutePath());
                next.setOnPreparedListener(p->{if(!closed&&player==p){p.start();preview.setText("Ⅱ Пауза");}});
                next.setOnCompletionListener(p->stopPreview());next.setOnErrorListener((p,a,b)->{stopPreview();status.setText("Не удалось воспроизвести запись.");return true;});next.prepareAsync();
            }catch(Exception e){stopPreview();status.setText("Не удалось воспроизвести запись.");}
        }
        boolean target(){Object o=owner.get();EditText e=editor(o);return live(uid)&&input.get()==o&&e!=null&&e.isAttachedToWindow()&&e.isShown()&&e.length()==0&&context==NativeRead.get(o,"Bq")&&reply.equals(replyTo(o))&&!hasAttachments(o)&&aid.equals(video(o,"Bq"));}
        void upload(){
            if(busy||closed||audio==null||mic!=null)return;
            if(!target()){status.setText("Поле комментария изменилось. Откройте запись заново; черновик сохранён в поле.");return;}
            stopPreview();busy=true;attach.setEnabled(false);record.setEnabled(false);preview.setEnabled(false);status.setText("Загружаю аудио… Комментарий ещё не отправлен.");
            // File and base64 work never run on the main thread.
            final File source=audio;
            Net.away("voice-file",()->{
                try{
                    String encoded=android.util.Base64.encodeToString(bytes(source),android.util.Base64.NO_WRAP);
                    request("upload",uid,params("video",aid,"request_id",nonce,"audio",encoded),(value,error)->{
                        busy=false;
                        if(closed||!live(uid)){
                            if(value!=null)request("delete",uid,params("id",value.optString("id")),(v,e)->{});return;
                        }
                        record.setEnabled(true);preview.setEnabled(true);attach.setEnabled(true);
                        if(value==null){status.setText(error);return;}
                        String key=value.optString("id");
                        // Keep the transport within older TikTok comment limits as well.
                        String text="Голосовое BlaxyT. "+clock(value.optInt("duration"))+" · Слушать: ТГК @BlaxyT [blaxy-voice:"+key+"]";
                        if(!target()||!key.matches("[a-f0-9]{32}")||!text.contains("[blaxy-voice:"+key+"]")){
                            status.setText("Поле комментария изменилось. Запись не вставлена. Её можно удалить в «Мои голосовые».");return;
                        }
                        try{
                            EditText edit=editor(owner.get());edit.setText(text);
                            if(!text.contentEquals(edit.getText())){edit.setText("");status.setText("TikTok ограничил длину комментария. Аудио сохранено в «Мои голосовые», но комментарий не отправлен.");return;}
                            edit.setSelection(edit.length());edit.requestFocus();
                        }catch(Exception failure){status.setText("TikTok не принял текст в поле. Аудио сохранено в «Мои голосовые»; комментарий не отправлен.");return;}
                        final Object destination=owner.get();close();
                        // Let TikTok's TextWatcher update the send button, then use its ordinary click path.
                        MAIN.postDelayed(()->send(destination,aid,uid,context,reply,text),180);
                    });
                }catch(Exception e){MAIN.post(()->{busy=false;if(!closed){record.setEnabled(true);preview.setEnabled(true);attach.setEnabled(true);status.setText("Не удалось прочитать запись. Запишите заново.");}});}
            });
        }
        void close(){dispose();}
        void dispose(){if(closed)return;closed=true;MAIN.removeCallbacks(monitor);releaseMic();stopPreview();if(bar!=null){bar.close();bar=null;}if(audio!=null)audio.delete();if(recording==this)recording=null;}
    }

    private static void send(Object owner,String aid,String uid,Object context,String reply,String text){
        EditText edit=editor(owner);Object button=NativeRead.field(owner,"LLLIIIIL");
        if(!live(uid)||edit==null||!edit.isAttachedToWindow()||!edit.hasWindowFocus()||!edit.isShown()||!text.contentEquals(edit.getText())||context!=NativeRead.get(owner,"Bq")||!reply.equals(replyTo(owner))||!aid.equals(video(owner,"Bq"))||hasAttachments(owner)){
            Screen.say("Комментарий изменился. Голосовое автоматически не отправлено.");return;
        }
        if(button instanceof View){View view=(View)button;
            if(view.isAttachedToWindow()&&view.isShown()&&view.isEnabled()&&view.hasOnClickListeners()){
                try{if(view.performClick())return;}catch(Throwable ignored){}
            }
        }
        Screen.say("Запись готова в поле комментария. Нажмите кнопку отправки TikTok.");
    }

    static void bind(Object cell,TextView view){
        Object model=NativeRead.get(cell,"D6");String author=NativeRead.uid(NativeRead.get(model,"getUser"));
        String aid=NativeRead.string(NativeRead.get(model,"getAwemeId"));String body=NativeRead.string(NativeRead.get(model,"getText"));
        // Newly inserted comments may omit aweme_id; use this row's own video context.
        String rowAid=NativeRead.aid(NativeRead.get(cell,"q6"));
        if(!valid(aid))aid=rowAid;
        else if(valid(rowAid)&&!aid.equals(rowAid))aid="";
        view.setTag(BOUND,valid(author)&&valid(aid)&&MARK.matcher(body).find()?new Bound(author,aid,body):null);
        if(listening!=null&&listening.host.get()==view&&!listening.visible())listening.close();
    }
    private static final class Bound{final String author,video,body,duration;Bound(String a,String v,String b){author=a;video=v;body=b;
        Matcher m=Pattern.compile("^Голосовое BlaxyT\\. ([0-9]:[0-5][0-9]) ·").matcher(b);duration=m.find()?m.group(1):"";}}
    static void mark(TextView view,Spannable text){
        for(VoiceTap old:text.getSpans(0,text.length(),VoiceTap.class))text.removeSpan(old);
        for(VoiceLabel old:text.getSpans(0,text.length(),VoiceLabel.class))text.removeSpan(old);
        Object tag=view.getTag(BOUND);if(!(tag instanceof Bound))return;Bound b=(Bound)tag;
        if(!b.body.contentEquals(text))return;Matcher found=MARK.matcher(text);if(!found.find())return;
        String key=found.group(1);int start=found.start(),end=found.end();
        // Hide only our own explanatory payload, never arbitrary surrounding comment text.
        if((text.toString().startsWith("Это голосовое сообщение из BlaxyT.")||text.toString().startsWith("Голосовое BlaxyT."))&&end==text.length())start=0;
        text.setSpan(new VoiceLabel(key,b.duration),start,end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new VoiceTap(key,b),start,end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    static final class VoiceTap extends ClickableSpan{
        final String key;final Bound bound;VoiceTap(String k,Bound b){key=k;bound=b;}
        public void onClick(View view){
            Activity activity=Screen.now();if(activity==null||!proof(activity))return;
            if(listening!=null&&listening.key.equals(key)&&listening.host.get()==view){listening.play();return;}
            if(listening!=null)listening.close();if(recording!=null)recording.close();
            listening=new Listening(activity,key,bound,(TextView)view);listening.play();
        }
        public void updateDrawState(TextPaint p){p.setColor(Accent.colour());p.setUnderlineText(false);}
    }
    private static final class VoiceLabel extends ReplacementSpan{
        final String key,duration;final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        final android.graphics.RectF box=new android.graphics.RectF();final android.graphics.Path triangle=new android.graphics.Path();
        VoiceLabel(String id,String length){key=id;duration=length;}
        public int getSize(Paint p,CharSequence t,int start,int end,Paint.FontMetricsInt fm){
            if(fm!=null){p.getFontMetricsInt(fm);fm.ascent=Math.min(fm.ascent,-Math.round(p.getTextSize()*1.9f));fm.top=fm.ascent;fm.descent=Math.max(fm.descent,Math.round(p.getTextSize()*.7f));fm.bottom=fm.descent;}
            return Math.round(p.getTextSize()*13f);
        }
        public void draw(Canvas c,CharSequence t,int start,int end,float x,int top,int y,int bottom,Paint p){
            float unit=p.getTextSize(),width=unit*13f,cy=y-unit*.5f;
            Listening current=listening;String label=duration.isEmpty()?"Голосовое":duration;float progress=0;boolean playing=false;
            if(current!=null&&current.key.equals(key)){
                if(current.loading)label="Загрузка…";
                else if(current.ready&&current.player!=null){try{int position=current.player.getCurrentPosition(),length=current.player.getDuration();label=clock(position)+" / "+clock(length);progress=length>0?Math.min(1f,position/(float)length):0;playing=current.player.isPlaying();}catch(Exception ignored){}}
            }
            ink.setColor(Accent.colour());ink.setAlpha(30);box.set(x,cy-unit*1.25f,x+width,cy+unit*1.1f);c.drawRoundRect(box,unit,unit,ink);
            ink.setAlpha(255);float cx=x+unit*1.35f;
            if(playing){c.drawRect(cx-unit*.3f,cy-unit*.45f,cx-unit*.08f,cy+unit*.45f,ink);c.drawRect(cx+unit*.08f,cy-unit*.45f,cx+unit*.3f,cy+unit*.45f,ink);}
            else{triangle.reset();triangle.moveTo(cx-unit*.25f,cy-unit*.5f);triangle.lineTo(cx+unit*.5f,cy);triangle.lineTo(cx-unit*.25f,cy+unit*.5f);triangle.close();c.drawPath(triangle,ink);}
            ink.setTextSize(unit*.9f);ink.setTypeface(p.getTypeface());c.drawText(label,x+unit*2.6f,cy+unit*.15f,ink);
            float left=x+unit*2.6f,right=x+width-unit,yline=cy+unit*.65f;
            ink.setAlpha(60);box.set(left,yline,right,yline+unit*.13f);c.drawRoundRect(box,unit*.1f,unit*.1f,ink);
            ink.setAlpha(255);box.right=left+(right-left)*progress;c.drawRoundRect(box,unit*.1f,unit*.1f,ink);
        }
    }
    private static String clock(int ms){int seconds=Math.max(0,ms/1000);return (seconds/60)+":"+(seconds%60<10?"0":"")+(seconds%60);}
    private static final class Listening{
        final Activity activity;final String key,uid;final Bound bound;final Panel panel;final TextView state,play;boolean closed,loading,ready;File audio;MediaPlayer player;
        final AudioManager sound;final AudioManager.OnAudioFocusChangeListener focus;
        final WeakReference<TextView> host;final boolean inline;final android.graphics.Rect visibleArea=new android.graphics.Rect();
        final View.OnAttachStateChangeListener attachment=new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){close();}};
        final Runnable progress=new Runnable(){public void run(){if(closed)return;if(!live(uid)||(inline&&!visible())){close();return;}redraw();if(ready&&player!=null){try{if(player.isPlaying())MAIN.postDelayed(this,Performance.light()?400:250);}catch(Exception ignored){}}}};
        Listening(Activity a,String id,Bound b){this(a,id,b,null);}
        Listening(Activity a,String id,Bound b,TextView row){
            host=new WeakReference<TextView>(row);inline=row!=null;
            if(row!=null)row.addOnAttachStateChangeListener(attachment);
            activity=a;key=id;bound=b;uid=Account.liveId();Skin skin=Skin.forSettings(a);panel=inline?null:Panel.with(a,skin,"Голосовое BlaxyT");
            state=new TextView(a);state.setTextColor(skin.text);state.setTextSize(17);state.setPadding(12,16,12,16);state.setText("Нажмите, чтобы загрузить и прослушать.");if(panel!=null)panel.view(state);
            play=inline?new TextView(a):panel.action("Прослушать",true);play.setText("Прослушать");play.setOnClickListener(v->play());
            if(panel!=null&&uid.equals(b.author))panel.quiet("Удалить запись с сервера",()->remove(activity,key));if(panel!=null)panel.quiet("Закрыть",null);
            sound=(AudioManager)a.getSystemService(Context.AUDIO_SERVICE);focus=change->{if(change<0)MAIN.post(()->{if(player!=null&&ready){try{player.pause();play.setText("Продолжить");MAIN.removeCallbacks(progress);redraw();}catch(Exception ignored){}}});};
            if(panel!=null)panel.dialog().setOnDismissListener(d->dispose());
        }
        void show(){if(panel!=null)panel.show();}
        boolean visible(){TextView view=host.get();if(view==null||!view.isAttachedToWindow()||!view.isShown()||!view.getGlobalVisibleRect(visibleArea))return false;Object tag=view.getTag(BOUND);if(!(tag instanceof Bound))return false;Bound b=(Bound)tag;return bound.author.equals(b.author)&&bound.video.equals(b.video)&&b.body.contains("[blaxy-voice:"+key+"]");}
        void redraw(){TextView view=host.get();if(view!=null&&visible()){view.invalidate();view.setContentDescription(loading?"Голосовое загружается":play.getText()+" голосовое BlaxyT");}}
        void error(String message){state.setText(message);redraw();if(inline)Screen.say(message);}
        void play(){
            if(closed||loading)return;if(!live(uid)){close();return;}
            if(player!=null&&ready){try{if(player.isPlaying()){player.pause();play.setText("Продолжить");MAIN.removeCallbacks(progress);if(sound!=null)sound.abandonAudioFocus(focus);}else if(focus()){player.start();play.setText("Пауза");MAIN.post(progress);}redraw();}catch(Exception e){release();error("Не удалось воспроизвести запись. Попробуйте снова.");}return;}
            loading=true;play.setEnabled(false);state.setText("Загружаю голосовое…");
            redraw();
            request("read",uid,params("id",key,"author",bound.author,"video",bound.video),(value,error)->{
                if(closed||!live(uid)||(inline&&!visible())){close();return;}if(value==null){loading=false;play.setEnabled(true);error(error);return;}
                Net.away("voice-cache",()->{
                    File saved=null;
                    try{
                        byte[] data=android.util.Base64.decode(value.getString("audio"),android.util.Base64.NO_WRAP);
                        if(data.length>1048576)throw new java.io.IOException("limit");
                        byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(data);StringBuilder hex=new StringBuilder();for(byte n:hash)hex.append(String.format(java.util.Locale.ROOT,"%02x",n&255));
                        if(!hex.toString().equals(value.getString("sha256")))throw new java.io.IOException("digest");
                        saved=file();try(FileOutputStream out=new FileOutputStream(saved)){out.write(data);}final File local=saved;
                        MAIN.post(()->{if(closed||!live(uid)||(inline&&!visible())){local.delete();close();return;}audio=local;prepare(value.optInt("duration"));});
                    }catch(Exception e){if(saved!=null)saved.delete();MAIN.post(()->{if(!closed){loading=false;play.setEnabled(true);error("Запись не загрузилась полностью. Повторите попытку.");}});}
                });
            });
        }
        boolean focus(){return sound!=null&&sound.requestAudioFocus(focus,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;}
        void prepare(int duration){
            try{
                player=new MediaPlayer();player.setDataSource(audio.getAbsolutePath());
                player.setOnPreparedListener(p->{if(closed||p!=player)return;if(!live(uid)||(inline&&!visible())){close();return;}loading=false;ready=true;play.setEnabled(true);state.setText("Голосовое · "+Math.max(1,duration/1000)+" с");if(focus()){p.start();play.setText("Пауза");MAIN.post(progress);}else play.setText("Прослушать");redraw();});
                player.setOnCompletionListener(p->{MAIN.removeCallbacks(progress);play.setText("Прослушать ещё раз");redraw();if(sound!=null)sound.abandonAudioFocus(focus);});
                player.setOnErrorListener((p,a,b)->{release();error("Устройство не смогло воспроизвести запись.");return true;});player.prepareAsync();
            }catch(Exception e){release();error("Не удалось открыть запись.");}
        }
        void release(){MAIN.removeCallbacks(progress);if(player!=null){try{player.release();}catch(Exception ignored){}player=null;}ready=false;loading=false;play.setEnabled(true);play.setText("Прослушать");if(audio!=null){audio.delete();audio=null;}if(sound!=null)sound.abandonAudioFocus(focus);}
        void close(){if(panel!=null)panel.close();dispose();}
        void dispose(){if(closed)return;closed=true;TextView row=host.get();if(row!=null)row.removeOnAttachStateChangeListener(attachment);release();if(listening==this)listening=null;redraw();}
    }
    static void mine(Activity a){
        if(!proof(a))return;String uid=Account.liveId();Panel p=Panel.with(a,Skin.forSettings(a),"Мои голосовые");p.text("Удаление здесь отключит аудио у всех. Сам текстовый комментарий удаляется отдельно в TikTok.");p.busy(true,"Загружаю список…");p.quiet("Закрыть",null);p.show();
        request("list",uid,new JSONObject(),(value,error)->{
            if(!p.dialog().isShowing()||!live(uid))return;p.busy(false,value==null?error:"");if(value==null)return;
            JSONArray items=value.optJSONArray("items");if(items==null||items.length()==0){p.text("Записей пока нет.");return;}
            for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item==null)continue;String id=item.optString("id");String aid=item.optString("video");
                String date=new java.text.SimpleDateFormat("dd.MM HH:mm",java.util.Locale.getDefault()).format(new java.util.Date(item.optLong("created")*1000));
                p.item("mic",date+" · "+Math.max(1,item.optInt("duration")/1000)+" с","Ролик "+aid,()->{if(listening!=null)listening.close();listening=new Listening(a,id,new Bound(uid,aid,""));listening.show();});
            }
        });
    }
    private static void remove(Activity a,String key){
        String uid=Account.liveId();Panel p=Panel.with(a,Skin.forSettings(a),"Удалить голосовое?");p.text("Запись станет недоступна всем. Отменить удаление нельзя.");
        p.primaryKeepOpen("Удалить",()->{p.busy(true,"Удаляю…");request("delete",uid,params("id",key),(v,e)->{if(!p.dialog().isShowing())return;if(v==null)p.busy(false,e);else{p.close();Screen.say("Голосовое удалено. Текст комментария можно удалить в TikTok.");}});});p.quiet("Отмена",null);p.show();
    }
}
