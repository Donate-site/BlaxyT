package app.blaxyt.mod;

/** Continuous motion in frame-relative coordinates, shared by previews and profile avatars. */
final class FrameMotion {
    static float wave(long time,long period){return (float)Math.sin((time%period)*2*Math.PI/period);}
    static float petX(long time){return .5f+wave(time,4800)*.055f;}
    static float petY(long time){return .21f+wave(time,2400)*.018f;}
    static float petTilt(long time){return wave(time,4800)*7f;}
    static float petScale(long time){return 1+wave(time,2400)*.018f;}
    static float chargeX(long time){return .5f+wave(time,6400)*.23f;}
    static float chargeY(long time){return .43f-.22f*(.5f-.5f*(float)Math.cos((time%1600L)*2*Math.PI/1600));}
    private FrameMotion(){}
}
