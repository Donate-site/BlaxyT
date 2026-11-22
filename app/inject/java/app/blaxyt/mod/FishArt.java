package app.blaxyt.mod;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** BlaxyT pastel fish. Geometry is allocated once, never during animation. */
final class FishArt {
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path body=new Path(),tail=new Path(),fin=new Path();
    private final RectF shine=new RectF(-.08f,-.13f,.23f,-.055f);
    FishArt() {
        body.moveTo(.47f,0);
        body.cubicTo(.31f,-.34f,-.18f,-.31f,-.31f,0);
        body.cubicTo(-.18f,.31f,.31f,.34f,.47f,0);body.close();
        tail.moveTo(-.24f,0);tail.cubicTo(-.34f,-.08f,-.48f,-.27f,-.53f,-.20f);
        tail.lineTo(-.46f,0);tail.lineTo(-.53f,.20f);
        tail.cubicTo(-.48f,.27f,-.34f,.08f,-.24f,0);tail.close();
        fin.moveTo(-.12f,-.19f);fin.cubicTo(-.06f,-.41f,.13f,-.39f,.17f,-.20f);fin.close();
    }
    void draw(Canvas canvas,float x,float y,float size,float degrees) {
        canvas.save();canvas.translate(x,y);canvas.rotate(degrees);canvas.scale(size,size);
        ink.setStyle(Paint.Style.FILL);
        ink.setColor(0xFFACE6D1);canvas.drawPath(tail,ink);
        ink.setColor(0xFFFFD28F);canvas.drawPath(fin,ink);
        ink.setColor(0xFFC6A5F2);canvas.drawPath(body,ink);
        ink.setColor(0xFFF6E9FF);canvas.drawOval(shine,ink);
        ink.setColor(0xFF49305E);canvas.drawCircle(.26f,-.015f,.055f,ink);
        ink.setColor(0xFFFFFFFF);canvas.drawCircle(.278f,-.036f,.018f,ink);
        ink.setColor(0xFF9671BE);ink.setStrokeWidth(.025f);ink.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(.12f,.01f,.08f,.105f,ink);
        canvas.restore();
    }
}
