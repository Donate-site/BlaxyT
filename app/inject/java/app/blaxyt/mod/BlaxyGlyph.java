package app.blaxyt.mod;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Rounded outline symbols drawn without image downloads or frame allocations. */
final class BlaxyGlyph extends Drawable {
    private final String kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF box = new RectF();
    private int alpha = 255;
    static Drawable make(String name, int colour) {
        String kind;
        switch (name) {
            case "palette": case "contrast": kind="colour"; break;
            case "group": case "account_circle": case "fingerprint": kind="profile"; break;
            case "play_circle": case "view_compact": kind="feed"; break;
            case "tune": case "settings": kind="controls"; break;
            case "info": case "verified": case "bolt": kind="charge"; break;
            case "extension": kind="blocks"; break;
            case "chat": case "notifications": kind="chat"; break;
            case "flare": case "image": case "pets": kind="kyksik"; break;
            case "animation": case "fast_forward": kind="motion"; break;
            case "text_fields": case "edit": kind="type"; break;
            case "public": kind="world"; break;
            case "download": kind="save"; break;
            case "restore": case "repeat": kind="restore"; break;
            case "bug_report": case "article": kind="activity"; break;
            case "link": kind="link"; break;
            case "visibility_off": case "eye_open": kind="shield"; break;
            case "schedule": kind="clock"; break;
            case "open_with": kind="scatter"; break;
            case "chevron_right": case "arrow_forward": kind="arrow"; break;
            default: return SettingsGlyph.make(name,colour);
        }
        return new BlaxyGlyph(kind,colour);
    }
    private BlaxyGlyph(String kind,int colour) {
        this.kind=kind; paint.setColor(colour); paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.7f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
    }
    private void line(Canvas c,float... points) {
        path.reset();path.moveTo(points[0],points[1]);
        for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);c.drawPath(path,paint);
    }
    private void rect(Canvas c,float l,float t,float r,float b,float radius) {
        box.set(l,t,r,b);c.drawRoundRect(box,radius,radius,paint);
    }
    private void dot(Canvas c,float x,float y,float r) {
        paint.setStyle(Paint.Style.FILL);c.drawCircle(x,y,r,paint);paint.setStyle(Paint.Style.STROKE);
    }
    @Override public void draw(Canvas c) {
        int save=c.save();c.translate(getBounds().left,getBounds().top);
        c.scale(getBounds().width()/24f,getBounds().height()/24f);paint.setAlpha(alpha);
        switch(kind) {
            case "colour": rect(c,3,3,14,14,4);rect(c,10,10,21,21,4);dot(c,7,7,1);break;
            case "profile":
                rect(c,3,2,21,22,6);c.drawCircle(12,8,3,paint);
                box.set(6,13,18,23);c.drawArc(box,195,150,false,paint);break;
            case "feed": rect(c,5,2,19,22,4);line(c,10,8,15,12,10,16,10,8);break;
            case "controls":
                line(c,3,6,8,6);line(c,13,6,21,6);c.drawCircle(10.5f,6,2.5f,paint);
                line(c,3,17,11,17);line(c,16,17,21,17);c.drawCircle(13.5f,17,2.5f,paint);break;
            case "charge":
                Expressive.shape(path,Expressive.COOKIE7,12,12,10,1,0);c.drawPath(path,paint);
                line(c,13,5,8,13,12,13,11,19,16,11,12,11,13,5);break;
            case "blocks":
                rect(c,3,3,10,10,2);rect(c,14,14,21,21,2);rect(c,3,14,10,21,2);
                line(c,14,6.5f,21,6.5f);line(c,17.5f,3,17.5f,10);break;
            case "chat":
                rect(c,3,3,21,19,5);line(c,7,19,3,22,3,12);
                line(c,8,9,16,9);line(c,8,13,13,13);break;
            case "kyksik":
                path.reset();path.moveTo(3,9);path.lineTo(3,3);path.lineTo(8,6);
                path.quadTo(12,4,16,6);path.lineTo(21,3);path.lineTo(21,9);
                path.cubicTo(24,24,0,24,3,9);c.drawPath(path,paint);
                dot(c,8,12,1.1f);dot(c,16,12,1.1f);line(c,10,16,12,17,14,16);break;
            case "motion":
                line(c,2,7,8,7);line(c,2,12,6,12);line(c,2,17,8,17);
                rect(c,10,4,21,20,5);line(c,14,9,17,12,14,15);break;
            case "type":
                line(c,3,18,8,5,13,18);line(c,5,13,11,13);line(c,16,10,21,10);line(c,18.5f,10,18.5f,19);break;
            case "world":
                c.drawCircle(12,12,9,paint);box.set(8,3,16,21);c.drawOval(box,paint);line(c,3,12,21,12);break;
            case "save":
                line(c,12,3,12,14);line(c,8,10,12,14,16,10);line(c,3,15,3,20,21,20,21,15);break;
            case "restore":
                box.set(4,4,20,20);c.drawArc(box,210,285,false,paint);line(c,3,3,3,9,9,9);line(c,12,8,12,13,15,15);break;
            case "activity": rect(c,3,3,21,21,5);line(c,5,13,8,13,10,8,14,17,16,12,19,12);break;
            case "link":
                c.save();c.rotate(40,12,12);rect(c,8,2,16,14,4);rect(c,8,10,16,22,4);c.restore();break;
            case "shield":
                path.reset();path.moveTo(12,2);path.lineTo(21,6);path.lineTo(20,14);
                path.quadTo(19,19,12,22);path.quadTo(5,19,4,14);path.lineTo(3,6);path.close();c.drawPath(path,paint);
                line(c,8,12,11,15,16,9);break;
            case "clock": c.drawCircle(12,12,9,paint);line(c,12,6,12,12,16,14);break;
            case "scatter":
                rect(c,3,4,10,11,2);rect(c,13,13,20,20,2);line(c,16,3,16,8);line(c,14,5,19,5);line(c,4,18,8,18);break;
            case "arrow": line(c,4,12,20,12);line(c,14,6,20,12,14,18);break;
        }
        c.restoreToCount(save);
    }
    @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter value){paint.setColorFilter(value);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    @Override public int getIntrinsicWidth(){return 24;}
    @Override public int getIntrinsicHeight(){return 24;}
}
