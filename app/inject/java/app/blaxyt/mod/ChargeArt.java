package app.blaxyt.mod;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/** Local blaxyT marks, drawn once without network or per-frame allocations. */
final class ChargeArt {
    private ChargeArt() {}
    private static final Bitmap[] CACHE = new Bitmap[4];

    static synchronized Bitmap ambassador() {
        if(CACHE[2]!=null)return CACHE[2];
        Bitmap bitmap=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);canvas.scale(1.92f,1.92f);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);Path shape=new Path();
        Expressive.shape(shape,Expressive.COOKIE9,50,50,46,1,0);
        paint.setColor(0xff9a73ed);canvas.drawPath(shape,paint);
        paint.setColor(0xfffff7ff);canvas.drawCircle(50,36,12,paint);
        android.graphics.RectF shoulders=new android.graphics.RectF(27,52,73,83);
        canvas.drawRoundRect(shoulders,16,16,paint);
        CACHE[2]=bitmap;return bitmap;
    }

    static synchronized Bitmap helper() {
        if (CACHE[3] != null) return CACHE[3];
        Bitmap bitmap=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bitmap);c.scale(1.92f,1.92f);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);Path shape=new Path();
        Expressive.shape(shape,Expressive.COOKIE9,50,50,46,1,0);
        p.setColor(0xff9270dc);c.drawPath(shape,p);
        p.setColor(0xfffff7ff);c.drawCircle(43,39,11,p);
        c.drawRoundRect(new android.graphics.RectF(23,54,65,79),14,14,p);
        p.setColor(0xffffd67b);
        c.drawArc(new android.graphics.RectF(28,19,58,47),180,180,true,p);
        c.drawRoundRect(new android.graphics.RectF(25,32,61,38),3,3,p);
        c.drawRoundRect(new android.graphics.RectF(40,17,46,34),2,2,p);
        c.save();c.rotate(28,72,64);
        p.setColor(0xff40265e);c.drawRoundRect(new android.graphics.RectF(68,49,75,83),3,3,p);
        p.setColor(0xfffff7ff);c.drawRoundRect(new android.graphics.RectF(59,43,83,55),3,3,p);
        c.restore();CACHE[3]=bitmap;return bitmap;
    }

    static synchronized Bitmap picture(boolean developer) {
        int index = developer ? 1 : 0;
        if (CACHE[index] != null) return CACHE[index];
        Bitmap bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(1.92f, 1.92f);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (developer) {
            Path cookie = new Path();
            for (int i = 0; i < 360; i++) {
                double a = Math.PI * 2 * i / 360;
                double r = 42 + 3.8 * Math.cos(10 * a);
                float x = (float) (50 + r * Math.cos(a));
                float y = (float) (50 + r * Math.sin(a));
                if (i == 0) cookie.moveTo(x, y); else cookie.lineTo(x, y);
            }
            cookie.close();
            paint.setColor(0xff7856d8);
            canvas.drawPath(cookie, paint);
        }
        Path bolt = new Path();
        bolt.moveTo(58, 20); bolt.lineTo(31, 56); bolt.lineTo(48, 56);
        bolt.lineTo(42, 81); bolt.lineTo(72, 44); bolt.lineTo(54, 44); bolt.close();
        paint.setColor(developer ? 0xfff6edff : 0xff9568f5);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setStrokeWidth(1.7f);
        paint.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawPath(bolt, paint);
        CACHE[index] = bitmap;
        return bitmap;
    }
}
