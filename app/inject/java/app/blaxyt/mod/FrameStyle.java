package app.blaxyt.mod;

/** Shared public style names; a creator decoration is scoped to one immutable TikTok ID. */
final class FrameStyle {
    static final String CREATOR="7662448315568931873", EXCLUSIVE="blaxy:creator";
    static final String[] SKINS={"classic","glossy","kris","susie","ralsei","noelle","roaring","cosmic","frost","cyber","gold"};
    static final String[] TITLES={"Плюшевый","Глянцевый","Крис","Сьюзи","Ралзей","Ноэль","Рокочущий","Космический","Ледяной","Кибер","Золотой"};
    static boolean creator(String uid){return CREATOR.equals(uid);}
    static boolean free(String id){for(String skin:SKINS)if(("blaxy:kyksik:"+skin).equals(id))return true;return false;}
    static float jump(long time){return (float)Math.abs(Math.sin((time%1600L)*Math.PI/800.0));}
    private FrameStyle(){}
}
