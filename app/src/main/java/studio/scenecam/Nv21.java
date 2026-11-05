package studio.scenecam;

/** BT.601 limited-range ARGB to NV21; independent of Android for deterministic tests. */
public final class Nv21 {
    private Nv21(){}
    public static byte[] encode(int[] pixels,int width,int height){
        if(width<=0||height<=0||(width&1)!=0||(height&1)!=0||pixels.length!=width*height)
            throw new IllegalArgumentException("NV21 requires an even-sized complete frame");
        byte[] out=new byte[width*height*3/2];int yIndex=0,uv=width*height;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            int color=pixels[y*width+x],r=(color>>16)&255,g=(color>>8)&255,b=color&255;
            int yy=((66*r+129*g+25*b+128)>>8)+16;
            out[yIndex++]=(byte)clamp(yy);
            if((y&1)==0&&(x&1)==0){
                // Average the 2x2 chroma block rather than sampling one corner.
                int rs=0,gs=0,bs=0;
                for(int dy=0;dy<2;dy++)for(int dx=0;dx<2;dx++){int p=pixels[(y+dy)*width+x+dx];rs+=(p>>16)&255;gs+=(p>>8)&255;bs+=p&255;}
                rs/=4;gs/=4;bs/=4;
                out[uv++]=(byte)clamp(((112*rs-94*gs-18*bs+128)>>8)+128);
                out[uv++]=(byte)clamp(((-38*rs-74*gs+112*bs+128)>>8)+128);
            }
        }return out;
    }
    private static int clamp(int v){return Math.max(0,Math.min(255,v));}
}
