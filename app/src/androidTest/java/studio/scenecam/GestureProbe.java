package studio.scenecam;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import java.lang.reflect.Field;
public final class GestureProbe extends Instrumentation {
 private volatile Throwable failure;
 public void onCreate(Bundle b){super.onCreate(b);start();}
 private static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 public void onStart(){Bundle result=new Bundle();try{
  MainActivity activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  waitForIdleSync();runOnMainSync(()->{try{
   Field pf=MainActivity.class.getDeclaredField("preview"),sf=MainActivity.class.getDeclaredField("selected");pf.setAccessible(true);sf.setAccessible(true);
   View preview=(View)pf.get(activity);Layer layer=new Layer("image","Gesture test","");layer.x=.2f;layer.y=.2f;layer.width=.4f;layer.height=.4f;sf.set(activity,layer);
   float x=preview.getWidth()/2f,y=preview.getHeight()/2f;long now=SystemClock.uptimeMillis();
   event(preview,now,0,new float[]{x},new float[]{y});event(preview,now,2,new float[]{x+30},new float[]{y+20});event(preview,now,1,new float[]{x+30},new float[]{y+20});
   check(layer.x>.2f&&layer.y>.2f,"drag");
   float initial=layer.width;event(preview,now,0,new float[]{x-150},new float[]{y});event(preview,now,5|(1<<8),new float[]{x-150,x+150},new float[]{y,y});
   for(int n=0;n<8;n++)event(preview,now,2,new float[]{x-150-n*35,x+150+n*35},new float[]{y,y});
   event(preview,now,6|(1<<8),new float[]{x-395,x+395},new float[]{y,y});event(preview,now,1,new float[]{x-395},new float[]{y});
   check(layer.width>initial,"pinch zoom");check(Math.abs(layer.width/layer.height-1)<.001,"aspect ratio");
  }catch(Throwable e){failure=e;}});
  if(failure!=null)throw new RuntimeException(failure);
  result.putString("stream","GESTURES_OK drag pinch aspect\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("stream","GESTURES_FAILED "+e);finish(Activity.RESULT_CANCELED,result);}}
 private static long tick;
 private static void event(View view,long down,int action,float[] xs,float[] ys){int count=xs.length;MotionEvent.PointerProperties[] pp=new MotionEvent.PointerProperties[count];MotionEvent.PointerCoords[] pc=new MotionEvent.PointerCoords[count];
  for(int i=0;i<count;i++){pp[i]=new MotionEvent.PointerProperties();pp[i].id=i;pp[i].toolType=1;pc[i]=new MotionEvent.PointerCoords();pc[i].x=xs[i];pc[i].y=ys[i];pc[i].pressure=1;pc[i].size=1;}
  MotionEvent event=MotionEvent.obtain(down,down+(tick+=30),action,count,pp,pc,0,0,1,1,0,0,0x1002,0);view.dispatchTouchEvent(event);event.recycle();
 }
}
