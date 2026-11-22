package app.blaxyt.mod;

/** Small frame samples only; no Android dependencies so colour transitions can be checked on JVM. */
final class AmbientPalette {
    private AmbientPalette() {}
    static int[] colours(int[] pixels,int width,int height) {
        if(pixels==null||width<1||height<1||pixels.length<width*height)return null;
        int top=height,bottom=-1;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            int c=pixels[y*width+x];if((c>>>24)>=128&&Math.max((c>>>16)&255,Math.max((c>>>8)&255,c&255))>=14){top=Math.min(top,y);bottom=Math.max(bottom,y);}
        }
        if(bottom<top)return new int[]{0xff101016,0xff101016,0xff101016};
        int[] result=new int[3];
        for(int band=0;band<3;band++){
            long r=0,g=0,b=0,weight=0;
            int span=bottom-top+1,first=top+band*span/3,last=Math.max(first+1,top+(band+1)*span/3);
            for(int y=first;y<Math.min(height,last);y++)for(int x=0;x<width;x++){
                int c=pixels[y*width+x],red=(c>>>16)&255,green=(c>>>8)&255,blue=c&255;
                if((c>>>24)<128)continue;
                int high=Math.max(red,Math.max(green,blue)),low=Math.min(red,Math.min(green,blue));
                if(high<14)continue; // Letterbox pixels should not dilute the colour of the video.
                int w=24+high-low;r+=(long)red*w;g+=(long)green*w;b+=(long)blue*w;weight+=w;
            }
            result[band]=weight==0?0xff101016:rgb((int)(r/weight)*.48f,(int)(g/weight)*.48f,(int)(b/weight)*.48f);
        }
        return result;
    }
    static int mix(int a,int b,float fraction){
        float f=Math.max(0f,Math.min(1f,fraction));
        return rgb(((a>>>16)&255)*(1-f)+((b>>>16)&255)*f,((a>>>8)&255)*(1-f)+((b>>>8)&255)*f,(a&255)*(1-f)+(b&255)*f);
    }
    static int distance(int a,int b){return Math.max(Math.abs(((a>>>16)&255)-((b>>>16)&255)),Math.max(Math.abs(((a>>>8)&255)-((b>>>8)&255)),Math.abs((a&255)-(b&255))));}
    private static int rgb(float r,float g,float b){return 0xff000000|(Math.round(r)<<16)|(Math.round(g)<<8)|Math.round(b);}
}
