package studio.scenecam.probe;
import android.graphics.*;
import java.lang.reflect.Method;
public final class FrameScaleProbe {
 public static void main(String[] args)throws Exception{
  Method render=Class.forName("studio.scenecam.FrameScale").getMethod("bitmap",Bitmap.class,int.class,int.class,String.class);
  Bitmap source=Bitmap.createBitmap(400,200,Bitmap.Config.ARGB_8888);source.eraseColor(Color.RED);
  for(String mode:new String[]{"fit","crop","stretch","fill"}){
   Bitmap b=(Bitmap)render.invoke(null,source,200,400,mode);
   if(b.getPixel(100,200)!=Color.RED)throw new AssertionError(mode+" center");
   boolean black=b.getPixel(100,10)==Color.BLACK;
   if(black!=mode.equals("fit"))throw new AssertionError(mode+" framing");
   System.out.println("SCALE_OK "+mode);b.recycle();
  }
 }
}
