package app.blaxyt.mod;

/** Small deterministic rules shared by the bundled extensions. */
final class ExtensionPolicy {
    static final int LIGHT=1, PETS=2, SHORT=4, BREAK=8;
    static final long BREAK_MS=20L*60L*1000L;
    static boolean active(int installed,int enabled,int bit){return (installed&enabled&bit)!=0;}
    static boolean longVideo(long durationMs,boolean own){return !own&&durationMs>60000L;}
    static final class ForegroundTime {
        private long elapsed,started=-1;
        void resume(long now){if(started<0)started=now;}
        void pause(long now){if(started>=0){elapsed+=Math.max(0,now-started);started=-1;}}
        long remaining(long now){return Math.max(0,BREAK_MS-elapsed-(started<0?0:Math.max(0,now-started)));}
        void reset(){elapsed=0;started=-1;}
    }
}
