package app.blaxyt.mod;

import android.graphics.*;

/** Original standard badge silhouette from https://ttyou.app/account/ (TikTok You).
 * Kept separate from blaxyT artwork; attribution is displayed on each imported badge. */
final class YouBadgeArt {
    private static Bitmap cached;
    static synchronized Bitmap picture(){
        if(cached!=null)return cached;
        Bitmap bitmap=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);canvas.scale(192f/21f,192f/21f);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(0xFF8B5CF6);
        Path path=new Path();
        path.moveTo(11.7782f,1.2598f);
        path.cubicTo(12.6049f,1.25983f,13.2752f,1.93323f,13.2752f,2.7639f);
        path.cubicTo(13.2752f,4.63296f,14.7833f,6.14813f,16.6436f,6.14813f);
        path.cubicTo(17.4703f,6.14816f,18.1406f,6.82156f,18.1406f,7.65223f);
        path.cubicTo(18.1406f,8.4829f,17.4703f,9.15629f,16.6436f,9.15633f);
        path.cubicTo(15.4065f,9.15633f,14.2521f,8.80131f,13.2752f,8.18762f);
        path.lineTo(13.2752f,11.584f);
        path.cubicTo(13.3787f,11.9237f,13.355f,12.3028f,13.1784f,12.6434f);
        path.cubicTo(12.9674f,13.05f,12.9674f,13.5348f,13.1784f,13.9415f);
        path.cubicTo(13.5397f,14.6381f,13.2621f,15.4971f,12.5622f,15.8473f);
        path.cubicTo(12.1536f,16.0517f,11.8703f,16.4437f,11.803f,16.8972f);
        path.cubicTo(11.6878f,17.6741f,10.9605f,18.2051f,10.1894f,18.0752f);
        path.cubicTo(9.73927f,17.9993f,9.28039f,18.1489f,8.96062f,18.4762f);
        path.cubicTo(8.41285f,19.0368f,7.51393f,19.0368f,6.96615f,18.4762f);
        path.cubicTo(6.64639f,18.149f,6.18751f,17.9993f,5.73738f,18.0752f);
        path.cubicTo(4.96631f,18.2051f,4.23893f,17.6741f,4.12375f,16.8972f);
        path.cubicTo(4.05645f,16.4437f,3.77315f,16.0517f,3.36463f,15.8473f);
        path.cubicTo(2.6647f,15.4971f,2.38703f,14.6381f,2.74841f,13.9415f);
        path.cubicTo(2.95936f,13.5348f,2.95937f,13.05f,2.74841f,12.6434f);
        path.cubicTo(2.38706f,11.9467f,2.66471f,11.0877f,3.36463f,10.7375f);
        path.cubicTo(3.77316f,10.5331f,4.05648f,10.1412f,4.12375f,9.68768f);
        path.cubicTo(4.23893f,8.91071f,4.96631f,8.37973f,5.73738f,8.50967f);
        path.cubicTo(6.18753f,8.58558f,6.64638f,8.43593f,6.96615f,8.10867f);
        path.cubicTo(7.51393f,7.54809f,8.41285f,7.54809f,8.96062f,8.10867f);
        path.cubicTo(9.28039f,8.43593f,9.73925f,8.58558f,10.1894f,8.50967f);
        path.cubicTo(10.22f,8.5045f,10.2507f,8.50065f,10.2811f,8.49755f);
        path.lineTo(10.2811f,2.7639f);
        path.cubicTo(10.2811f,1.93321f,10.9514f,1.2598f,11.7782f,1.2598f);
        path.close();
        canvas.drawPath(path,paint);cached=bitmap;return bitmap;
    }
}
