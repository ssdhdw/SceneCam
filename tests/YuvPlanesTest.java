import studio.scenecam.YuvPlanes;
import java.nio.ByteBuffer;

public final class YuvPlanesTest {
    private static void eq(int expected,int actual){if(expected!=actual)throw new AssertionError(expected+" != "+actual);}
    public static void main(String[] args){
        byte[] nv21={1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,101,51,102,52,103,53,104,54};
        ByteBuffer y=YuvPlanes.fromNv21(nv21,4,4,0,6,1);eq(22,y.remaining());eq(5,y.get(6));eq(16,y.get(21));eq(0,y.get(4));
        ByteBuffer u=YuvPlanes.fromNv21(nv21,4,4,1,6,2);eq(9,u.remaining());eq(51,u.get(0));eq(52,u.get(2));eq(53,u.get(6));eq(54,u.get(8));
        ByteBuffer v=YuvPlanes.fromNv21(nv21,4,4,2,2,1);eq(4,v.remaining());for(int i=0;i<4;i++)eq(101+i,v.get(i));
        if(!u.isDirect()||!v.isDirect())throw new AssertionError("JNI requires direct buffers");
        try{YuvPlanes.fromNv21(nv21,4,4,1,1,2);throw new AssertionError("bad stride accepted");}catch(IllegalArgumentException expected){}
        System.out.println("YUV planes: padded Y, pixel-stride-2 U, packed V, direct buffers and invalid stride passed");
    }
}
