package studio.scenecam;

import java.nio.ByteBuffer;

/** Populates a YUV_420_888 plane while preserving the device's row/pixel stride. */
public final class YuvPlanes {
    private YuvPlanes(){}
    public static ByteBuffer fromNv21(byte[] nv21,int width,int height,int plane,int rowStride,int pixelStride){
        if(width<=0||height<=0||(width&1)!=0||(height&1)!=0||plane<0||plane>2||nv21.length!=width*height*3/2)
            throw new IllegalArgumentException("Invalid NV21 frame/plane");
        int columns=plane==0?width:width/2,rows=plane==0?height:height/2;
        if(pixelStride<1||rowStride<(columns-1)*pixelStride+1)throw new IllegalArgumentException("Invalid plane strides");
        int size=Math.addExact(Math.multiplyExact(rows-1,rowStride),Math.addExact(Math.multiplyExact(columns-1,pixelStride),1));
        ByteBuffer buffer=ByteBuffer.allocateDirect(size);
        for(int y=0;y<rows;y++)for(int x=0;x<columns;x++){
            int source=plane==0?y*width+x:width*height+y*width+x*2+(plane==1?1:0);
            buffer.put(y*rowStride+x*pixelStride,nv21[source]);
        }return buffer;
    }
}
