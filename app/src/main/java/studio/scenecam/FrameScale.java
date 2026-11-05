package studio.scenecam;
import android.graphics.*;
public final class FrameScale {
 public static final String FIT="fit",CROP="crop",STRETCH="stretch",FILL="fill";
 public static RectF rect(int sw,int sh,int w,int h,boolean crop){float k=crop?Math.max(w/(float)sw,h/(float)sh):Math.min(w/(float)sw,h/(float)sh);return new RectF((w-sw*k)/2,(h-sh*k)/2,(w+sw*k)/2,(h+sh*k)/2);}
 public static void draw(Canvas c,Bitmap b,int w,int h,String mode){c.drawColor(Color.BLACK);drawLayer(c,b,w,h,mode);}
 public static void drawLayer(Canvas c,Bitmap b,int w,int h,String mode){if(b==null)return;Paint p=new Paint(3);int save=c.save();c.clipRect(0,0,w,h);
 if(FILL.equals(mode)){Bitmap small=Bitmap.createScaledBitmap(b,16,16,true);c.drawBitmap(small,null,rect(b.getWidth(),b.getHeight(),w,h,true),p);small.recycle();p.setColor(0x66000000);c.drawRect(0,0,w,h,p);p.setColor(Color.WHITE);}
 c.drawBitmap(b,null,STRETCH.equals(mode)?new RectF(0,0,w,h):rect(b.getWidth(),b.getHeight(),w,h,CROP.equals(mode)),p);c.restoreToCount(save);}
 public static Bitmap bitmap(Bitmap b,int w,int h,String mode){if(w<=0||h<=0||w>4096||h>4096)throw new IllegalArgumentException("Unsupported frame size");Bitmap result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);draw(new Canvas(result),b,w,h,mode);return result;}
}
