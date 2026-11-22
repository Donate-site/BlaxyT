package app.blaxyt.mod;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

/** Small cached transparent frames, made from the bundled BlaxyT artwork. */
final class FrameArt {
    private static Bitmap ring;
    static Bitmap create(Context context,String style){
        Bitmap pet="creator".equals(style)?null:Kyksik.picture(context,style);
        if(!"creator".equals(style)&&pet==null)return null;
        Bitmap out=Bitmap.createBitmap(384,384,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setColor(0xFFC8AEFF);
        c.drawCircle(192,192,130,p);
        p.setStrokeWidth(2);p.setColor(0x997DE2D0);c.drawCircle(192,192,137,p);
        p.setStyle(Paint.Style.FILL);
        if(pet!=null){
            stamp(c,pet,new RectF(118,6,266,120),p);
            p.setColor(0xFFE2D4FF);star(c,p,88,90,9);star(c,p,298,128,7);star(c,p,103,301,6);
        }
        return out;
    }
    private static void stamp(Canvas c,Bitmap b,RectF area,Paint p){
        float s=Math.min(area.width()/b.getWidth(),area.height()/b.getHeight());
        float w=b.getWidth()*s,h=b.getHeight()*s;
        c.drawBitmap(b,null,new RectF(area.centerX()-w/2,area.centerY()-h/2,area.centerX()+w/2,area.centerY()+h/2),p);
    }
    private static void star(Canvas c,Paint p,float x,float y,float r){
        Path path=new Path();path.moveTo(x,y-r);path.lineTo(x+r*.32f,y-r*.32f);path.lineTo(x+r,y);
        path.lineTo(x+r*.32f,y+r*.32f);path.lineTo(x,y+r);path.lineTo(x-r*.32f,y+r*.32f);
        path.lineTo(x-r,y);path.lineTo(x-r*.32f,y-r*.32f);path.close();c.drawPath(path,p);
    }
    /** Uses the viewed frame, never the logged-in account. Every viewer sees the same design. */
    static boolean animated(Canvas canvas,Rect bounds,String id,Paint paint,View host){
        boolean creator=FrameStyle.EXCLUSIVE.equals(id);
        if(!creator&&!FrameStyle.free(id))return false;
        if(bounds.width()<=0||bounds.height()<=0)return true;
        if(ring==null)ring=create(host.getContext(),"creator");
        canvas.drawBitmap(ring,null,bounds,paint);
        Bitmap sprite=creator?Badges.note():Kyksik.picture(host.getContext(),id.substring("blaxy:kyksik:".length()));
        if(sprite!=null){
            long time=android.os.SystemClock.uptimeMillis();float unit=bounds.width();
            float x=bounds.left+unit*(creator?FrameMotion.chargeX(time):FrameMotion.petX(time));
            float y=bounds.top+unit*(creator?FrameMotion.chargeY(time):FrameMotion.petY(time));
            float size=unit*(creator?.23f:.35f)*(creator?1:FrameMotion.petScale(time));
            int saved=canvas.save();
            if(!creator)canvas.rotate(FrameMotion.petTilt(time),x,y+size*.4f);
            stamp(canvas,sprite,new RectF(x-size/2,y-size/2,x+size/2,y+size/2),paint);
            canvas.restoreToCount(saved);
        }
        // One redraw on the next display frame; no persistent timer for hidden/recycled avatars.
        if(host.isAttachedToWindow()&&host.isShown()&&host.hasWindowFocus())host.postInvalidateOnAnimation();
        return true;
    }
    private FrameArt(){}
}
