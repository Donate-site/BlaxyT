package app.blaxyt.mod;

import android.view.View;
import android.widget.ImageView;

/** Content pictures must never inherit the flat tint used for interface glyphs. */
final class ContentArtwork {
    private ContentArtwork(){}
    static boolean isImage(View view){
        if(view==null)return false;
        String name=Names.of(view).toLowerCase(java.util.Locale.ROOT);
        for(Class<?> type=view.getClass();type!=null&&type!=View.class;type=type.getSuperclass()){
            String t=type.getName().toLowerCase(java.util.Locale.ROOT);
            if(t.contains("tuxicon"))return false;
            if(t.contains("drawee")||t.contains("smartimage")||t.contains("sticker")||t.contains("avatar")||t.contains("animatedimage"))return true;
        }
        if(view instanceof ImageView){android.graphics.drawable.Drawable d=((ImageView)view).getDrawable();if(d instanceof android.graphics.drawable.BitmapDrawable)return true;}
        return view instanceof ImageView&&(name.contains("sticker")||name.contains("emoji")||name.contains("avatar")||name.contains("thumbnail")||name.contains("cover")||name.contains("photo"));
    }
}
