import studio.scenecam.Nv21;
import java.util.Arrays;

public final class Nv21Test {
    static void expect(int[] pixels,int... expected){byte[] result=Nv21.encode(pixels,2,2);if(result.length!=expected.length)throw new AssertionError("length");for(int i=0;i<result.length;i++)if((result[i]&255)!=expected[i])throw new AssertionError("byte "+i+": "+(result[i]&255)+" != "+expected[i]);}
    static int[] solid(int color){int[] p=new int[4];Arrays.fill(p,color);return p;}
    public static void main(String[] args){
        expect(solid(0xff000000),16,16,16,16,128,128);
        expect(solid(0xffffffff),235,235,235,235,128,128);
        expect(solid(0xffff0000),82,82,82,82,240,90);
        expect(solid(0xff0000ff),41,41,41,41,110,240);
        expect(new int[]{0xff000000,0xffffffff,0xff000000,0xffffffff},16,235,16,235,128,128);
        try{Nv21.encode(new int[6],3,2);throw new AssertionError("odd width accepted");}catch(IllegalArgumentException expected){}
        try{Nv21.encode(new int[3],2,2);throw new AssertionError("short frame accepted");}catch(IllegalArgumentException expected){}
        System.out.println("NV21: 7 cases passed");
    }
}
