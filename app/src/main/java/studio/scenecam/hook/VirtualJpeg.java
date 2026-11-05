package studio.scenecam.hook;
import android.graphics.*;
import android.media.*;
import android.os.*;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.Executor;
import de.robv.android.xposed.*;
import studio.scenecam.FrameScale;
/** Java ImageReader JPEG path. YUV frames use native BufferQueue through ImageWriter. */
final class VirtualJpeg {
 private static final class State {final ArrayDeque<Image> queue=new ArrayDeque<>();ImageReader.OnImageAvailableListener listener;Executor executor;boolean active,closed;int acquired;}
 private final Map<Image,Data> images=Collections.synchronizedMap(new WeakHashMap<>());
 private final Map<Image.Plane,Data> planes=Collections.synchronizedMap(new WeakHashMap<>());
 private Class<?> imageType,planeType;
 private final Map<ImageReader,State> states=Collections.synchronizedMap(new WeakHashMap<>());
 private State state(ImageReader reader){synchronized(states){return states.computeIfAbsent(reader,r->new State());}}
 void activate(ImageReader reader){state(reader).active=true;}
 void install(){
  try{imageType=Class.forName("android.media.ImageReader$SurfaceImage");for(Class<?> nested:imageType.getDeclaredClasses())if(Image.Plane.class.isAssignableFrom(nested))planeType=nested;
   for(java.lang.reflect.Method method:imageType.getDeclaredMethods())if(!java.lang.reflect.Modifier.isStatic(method.getModifiers()))XposedBridge.hookMethod(method,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){Data data=images.get(p.thisObject);if(data==null)return;try{String name=p.method.getName();if(name.equals("close")){data.close();XposedHelpers.setBooleanField(p.thisObject,"mIsImageValid",false);p.setResult(null);return;}data.valid();switch(name){case "getFormat":p.setResult(ImageFormat.JPEG);break;case "getWidth":p.setResult(data.width);break;case "getHeight":p.setResult(data.height);break;case "getTimestamp":p.setResult(data.timestamp);break;case "setTimestamp":data.timestamp=(Long)p.args[0];p.setResult(null);break;case "getPlanes":p.setResult(data.planes.clone());break;case "getCropRect":p.setResult(new Rect(0,0,data.width,data.height));break;default:p.setThrowable(new UnsupportedOperationException("Virtual JPEG: "+name));}}catch(Throwable e){p.setThrowable(e);}}});
   if(planeType==null)throw new IllegalStateException("SurfacePlane unavailable");
  }catch(Exception e){throw new IllegalStateException("JPEG virtualization initialization",e);}

  for(String name:new String[]{"setOnImageAvailableListener","setOnImageAvailableListenerWithExecutor"})XposedBridge.hookAllMethods(ImageReader.class,name,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){ImageReader reader=(ImageReader)p.thisObject;if(reader.getImageFormat()!=ImageFormat.JPEG)return;State state=state(reader);synchronized(state){state.listener=null;for(Object arg:p.args){if(arg instanceof ImageReader.OnImageAvailableListener)state.listener=(ImageReader.OnImageAvailableListener)arg;if(arg instanceof Handler){Handler h=(Handler)arg;state.executor=r->h.post(r);}if(arg instanceof Executor)state.executor=(Executor)arg;}if(state.executor==null){Handler h=new Handler(Looper.myLooper()==null?Looper.getMainLooper():Looper.myLooper());state.executor=r->h.post(r);}}}});
  for(String name:new String[]{"acquireNextImage","acquireLatestImage"})XposedBridge.hookAllMethods(ImageReader.class,name,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){State state=states.get(p.thisObject);if(state==null||!state.active)return;try{synchronized(state){if(state.closed)throw new IllegalStateException("ImageReader closed");if(state.acquired>=((ImageReader)p.thisObject).getMaxImages())throw new IllegalStateException("maxImages already acquired");if(p.method.getName().equals("acquireLatestImage"))while(state.queue.size()>1)state.queue.removeFirst().close();Image image=state.queue.poll();if(image!=null){state.acquired++;images.get(image).acquired=true;}p.setResult(image);}}catch(Throwable e){p.setThrowable(e);}}});
  XposedBridge.hookAllMethods(ImageReader.class,"close",new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){State state=states.remove(p.thisObject);if(state!=null)synchronized(state){state.closed=true;while(!state.queue.isEmpty())state.queue.remove().close();}}});
 }
 void frame(ImageReader reader,Bitmap bitmap,long timestamp){State state=state(reader);Bitmap scaled=FrameScale.bitmap(bitmap,reader.getWidth(),reader.getHeight(),FrameClient.scaleMode);ByteArrayOutputStream bytes=new ByteArrayOutputStream();try{scaled.compress(Bitmap.CompressFormat.JPEG,90,bytes);}finally{scaled.recycle();}
  ImageReader.OnImageAvailableListener listener;Executor executor;synchronized(state){if(state.closed)return;while(state.queue.size()>=reader.getMaxImages())state.queue.removeFirst().close();try{Data data=new Data(state,reader.getWidth(),reader.getHeight(),timestamp,bytes.toByteArray());Image image=(Image)VirtualCamera.allocate(imageType);Image.Plane plane=(Image.Plane)VirtualCamera.allocate(planeType);data.planes=new Image.Plane[]{plane};XposedHelpers.setBooleanField(image,"mIsImageValid",true);
    for(java.lang.reflect.Field field:planeType.getDeclaredFields()){field.setAccessible(true);if(Image.class.isAssignableFrom(field.getType()))field.set(plane,image);if(field.getType()==ByteBuffer.class)field.set(plane,data.buffer);}
    images.put(image,data);state.queue.add(image);}catch(Exception e){throw new IllegalStateException(e);}listener=state.listener;executor=state.executor;}
  if(listener!=null&&executor!=null)executor.execute(()->{if(!state.closed)listener.onImageAvailable(reader);});
 }
 private static final class Data {
  final State owner;final int width,height;long timestamp;Image.Plane[] planes;final ByteBuffer buffer;boolean closed,acquired;
  Data(State owner,int w,int h,long timestamp,byte[] bytes){this.owner=owner;width=w;height=h;this.timestamp=timestamp;buffer=ByteBuffer.allocateDirect(bytes.length);buffer.put(bytes).flip();}
  void valid(){if(closed)throw new IllegalStateException("Image closed");}
  void close(){synchronized(owner){if(closed)return;closed=true;if(acquired)owner.acquired--;}}
 }
}
