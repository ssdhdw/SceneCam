package studio.scenecam.hook;

import android.graphics.*;
import android.hardware.Camera;
import android.hardware.camera2.*;
import android.hardware.camera2.params.*;
import android.media.ImageReader;
import android.os.*;
import android.view.*;
import java.lang.reflect.*;
import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.*;
import de.robv.android.xposed.*;
import studio.scenecam.FrameScale;

/** In-process virtual devices. No CameraService connection, even when scene is stopped. */
@SuppressWarnings("deprecation")
final class VirtualCamera {
 private final Supplier<Bitmap> source;private final Runnable prime;private final Consumer<String> report;
 private final Map<Object,String> retired=Collections.synchronizedMap(new WeakHashMap<>());
 private final Map<Object,Legacy> legacy=Collections.synchronizedMap(new WeakHashMap<>());
 private final Map<Object,Device> devices=Collections.synchronizedMap(new WeakHashMap<>());
 private final Map<Object,Session> sessions=Collections.synchronizedMap(new WeakHashMap<>());
 private final VirtualJpeg jpeg=new VirtualJpeg();
 private final Map<Surface,WeakReference<ImageReader>> readers=Collections.synchronizedMap(new WeakHashMap<>());
 private Class<?> deviceType,sessionType,metadataType;private static int nextSession;
 VirtualCamera(Supplier<Bitmap> source,Runnable prime,Consumer<String> report){this.source=source;this.prime=prime;this.report=report;}
 static Object allocate(Class<?> type)throws Exception{Class<?> unsafe=Class.forName("sun.misc.Unsafe");Field field=unsafe.getDeclaredField("theUnsafe");field.setAccessible(true);Object instance=field.get(null);return unsafe.getMethod("allocateInstance",Class.class).invoke(instance,type);}
 private static Executor executor(Object[] args){for(Object arg:args)if(arg instanceof Executor)return (Executor)arg;for(Object arg:args)if(arg instanceof Handler){Handler h=(Handler)arg;return task->h.post(task);}Handler h=new Handler(Looper.myLooper()==null?Looper.getMainLooper():Looper.myLooper());return task->h.post(task);}
 private interface Action {Object run(XC_MethodHook.MethodHookParam p)throws Throwable;}
 private static XC_MethodHook replace(Action action){return new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){try{p.setResult(action.run(p));}catch(Throwable e){p.setThrowable(e);}}};}
 private void dispatch(Class<?> type,Map<?,?> objects,Action action){for(Method method:type.getDeclaredMethods())if(!Modifier.isStatic(method.getModifiers())&&!Modifier.isAbstract(method.getModifiers()))XposedBridge.hookMethod(method,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){if(!objects.containsKey(p.thisObject)){if(retired.containsKey(p.thisObject)){String name=p.method.getName();if(name.equals("getId"))p.setResult(retired.get(p.thisObject));else if(name.equals("close")||name.equals("release")||name.equals("finalize"))p.setResult(null);else p.setThrowable(new IllegalStateException("Virtual camera/session closed"));}return;}try{p.setResult(action.run(p));}catch(Throwable error){p.setThrowable(error);}}});}
 void install(ClassLoader loader){
  try{jpeg.install();}catch(Throwable e){report.accept("Virtual JPEG unavailable: "+e);}
  XC_MethodHook openLegacy=replace(p->{prime.run();Camera camera=(Camera)allocate(Camera.class);legacy.put(camera,new Legacy(camera));report.accept("Camera1 VIRTUAL_OPEN: no hardware connection");return camera;});
  XposedBridge.hookAllMethods(Camera.class,"open",openLegacy);XposedBridge.hookAllMethods(Camera.class,"openLegacy",openLegacy);
  XposedBridge.hookAllMethods(Camera.class,"native_setup",replace(p->{throw new UnsupportedOperationException("SceneCam: direct hardware camera construction is disabled");}));
  dispatch(Camera.class,legacy,p->legacy.get(p.thisObject).call(p.method.getName(),p.args));
  XposedHelpers.findAndHookMethod(ImageReader.class,"getSurface",new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){if(!p.hasThrowable()&&p.getResult()!=null)readers.put((Surface)p.getResult(),new WeakReference<>((ImageReader)p.thisObject));}});
  try{
   deviceType=Class.forName("android.hardware.camera2.impl.CameraDeviceImpl");sessionType=Class.forName("android.hardware.camera2.impl.CameraCaptureSessionImpl");metadataType=Class.forName("android.hardware.camera2.impl.CameraMetadataNative");
   dispatch(deviceType,devices,p->devices.get(p.thisObject).call(p.method.getName(),p.args));
   dispatch(sessionType,sessions,p->sessions.get(p.thisObject).call(p.method.getName(),p.args));
   XposedBridge.hookAllMethods(CameraManager.class,"openCamera",replace(p->{
    prime.run();CameraDevice.StateCallback callback=null;String id=null;for(Object arg:p.args){if(arg instanceof String&&id==null)id=(String)arg;if(arg instanceof CameraDevice.StateCallback)callback=(CameraDevice.StateCallback)arg;}
    if(callback==null||id==null)throw new IllegalArgumentException("Unsupported openCamera signature");
    Device device=new Device(id,callback,executor(p.args));devices.put(device.object,device);device.executor.execute(()->device.callback.onOpened(device.object));report.accept("Camera2 VIRTUAL_OPEN "+id+": no hardware connection");return null;
   }));
  }catch(Throwable error){report.accept("Virtual Camera2 installation failed: "+error);/* Fail closed: never fall back to hardware. */
   XposedBridge.hookAllMethods(CameraManager.class,"openCamera",replace(p->{throw new UnsupportedOperationException("SceneCam virtual Camera2 unavailable",error);}));}
 }
 private static final String PARAMETERS="preview-size=640x480;preview-size-values=320x240,640x360,640x480,1280x720,1920x1080;picture-size=640x480;picture-size-values=640x480,1280x720,1920x1080;preview-format=yuv420sp;preview-format-values=yuv420sp;picture-format=jpeg;picture-format-values=jpeg;preview-frame-rate=30;preview-frame-rate-values=15,24,30;preview-fps-range=15000,30000;preview-fps-range-values=(15000,30000),(30000,30000);jpeg-quality=90;jpeg-thumbnail-quality=90;jpeg-thumbnail-width=0;jpeg-thumbnail-height=0;jpeg-thumbnail-size-values=0x0;focus-mode=continuous-video;focus-mode-values=auto,fixed,infinity,continuous-video,continuous-picture;flash-mode=off;flash-mode-values=off;whitebalance=auto;whitebalance-values=auto;effect=none;effect-values=none;scene-mode=auto;scene-mode-values=auto;zoom=0;max-zoom=0;zoom-supported=false;smooth-zoom-supported=false;zoom-ratios=100;video-stabilization-supported=false;video-stabilization=false;auto-exposure-lock-supported=false;auto-whitebalance-lock-supported=false;exposure-compensation=0;min-exposure-compensation=0;max-exposure-compensation=0;exposure-compensation-step=1;max-num-focus-areas=0;max-num-metering-areas=0;max-num-detected-faces-hw=0;horizontal-view-angle=60;vertical-view-angle=45;focal-length=4;video-size-values=640x480,1280x720;preferred-preview-size-for-video=640x480";
 private final class Legacy {
  final Camera camera;final HandlerThread thread=new HandlerThread("virtual-camera1");final Handler handler;final Handler callbacks=new Handler(Looper.myLooper()==null?Looper.getMainLooper():Looper.myLooper());String parameters=PARAMETERS;Surface surface;SurfaceTexture texture;int displayOrientation;boolean owned,playing,oneShot,withBuffer;volatile boolean closed;Camera.PreviewCallback callback;final ArrayDeque<byte[]> buffers=new ArrayDeque<>();
  Legacy(Camera camera){this.camera=camera;thread.start();handler=new Handler(thread.getLooper());}
  Camera.Parameters parameters(){Camera.Parameters p=(Camera.Parameters)XposedHelpers.newInstance(Camera.Parameters.class,camera);p.unflatten(parameters);return p;}
  synchronized Object call(String name,Object[] args)throws Exception{
   if(name.equals("release")||name.equals("finalize")){if(!closed){closed=true;playing=false;legacy.remove(camera);retired.put(camera,"");handler.post(()->{synchronized(this){if(owned&&surface!=null)surface.release();surface=null;}thread.quitSafely();});}return null;}
   if(closed)throw new IllegalStateException("Virtual camera is closed");
   switch(name){
    case "getParameters":return parameters();case "setParameters":parameters=((Camera.Parameters)args[0]).flatten();previewGeometry();return null;
    case "setPreviewTexture":case "setPreviewDisplay":if(owned&&surface!=null)surface.release();owned=name.equals("setPreviewTexture");texture=owned?(SurfaceTexture)args[0]:null;previewGeometry();surface=args[0]==null?null:owned?new Surface(texture):((SurfaceHolder)args[0]).getSurface();return null;
    case "setPreviewCallback":case "setPreviewCallbackWithBuffer":case "setOneShotPreviewCallback":callback=(Camera.PreviewCallback)args[0];oneShot=name.equals("setOneShotPreviewCallback");withBuffer=name.equals("setPreviewCallbackWithBuffer");return null;
    case "addCallbackBuffer":if(args[0]!=null)buffers.add((byte[])args[0]);return null;
    case "startPreview":previewGeometry();if(!playing){playing=true;handler.post(tick);}return null;
    case "stopPreview":playing=false;handler.removeCallbacks(tick);return null;
    case "previewEnabled":return playing;
    case "autoFocus":if(args[0]!=null){Camera.AutoFocusCallback focus=(Camera.AutoFocusCallback)args[0];callbacks.post(()->{if(!closed)focus.onAutoFocus(true,camera);});}return null;
    case "takePicture":picture(args);return null;
    case "enableShutterSound":return true;
    case "setDisplayOrientation":int degrees=(Integer)args[0];if(degrees!=0&&degrees!=90&&degrees!=180&&degrees!=270)throw new IllegalArgumentException("Orientation must be 0, 90, 180 or 270");displayOrientation=degrees;previewGeometry();return null;
    case "setErrorCallback":case "setAutoFocusMoveCallback":case "cancelAutoFocus":case "setZoomChangeListener":case "setFaceDetectionListener":case "startFaceDetection":case "stopFaceDetection":case "lock":case "unlock":case "reconnect":return null;
    default:throw new UnsupportedOperationException("SceneCam Camera1: "+name);
   }
  }
  /** The scene represents the upright preview. A real Camera1 producer attaches a
   * display transform to landscape sensor buffers. Canvas cannot attach that native
   * transform, so publish its equivalent: an already oriented buffer with swapped
   * dimensions for quarter turns. OES consumers then crop in the correct coordinate
   * space. NV21 callbacks/pictures retain their requested raw dimensions. */
  void previewGeometry(){if(texture==null)return;Camera.Size size=parameters().getPreviewSize();boolean quarter=displayOrientation%180!=0;texture.setDefaultBufferSize(quarter?size.height:size.width,quarter?size.width:size.height);}
  private final Runnable tick=new Runnable(){public void run(){long begin=SystemClock.elapsedRealtime();Camera.PreviewCallback deliver;byte[] bytes=null;try{
   synchronized(Legacy.this){if(!playing||closed)return;Bitmap bitmap=source.get();if(surface!=null&&surface.isValid()){Canvas c=surface.lockCanvas(null);try{FrameScale.draw(c,bitmap,c.getWidth(),c.getHeight(),FrameClient.scaleMode);}finally{surface.unlockCanvasAndPost(c);}}
    deliver=callback;if(deliver!=null){Camera.Size size=parameters().getPreviewSize();byte[] frame=FrameClient.nv21(bitmap,size.width,size.height);if(withBuffer){bytes=buffers.poll();if(bytes!=null&&bytes.length>=frame.length)System.arraycopy(frame,0,bytes,0,frame.length);else bytes=null;}else bytes=frame;if(oneShot&&bytes!=null)callback=null;}}
   if(deliver!=null&&bytes!=null){byte[] frame=bytes;callbacks.post(()->{if(!closed)deliver.onPreviewFrame(frame,camera);});}
  }catch(Throwable error){report.accept("Virtual Camera1 frame: "+error);}synchronized(Legacy.this){if(playing&&!closed)handler.postDelayed(this,Math.max(1,1000/FrameClient.fps-(SystemClock.elapsedRealtime()-begin)));}}};
  void picture(Object[] args){playing=false;handler.removeCallbacks(tick);handler.post(()->{try{Camera.Size size=parameters().getPictureSize();Bitmap b=FrameScale.bitmap(source.get(),size.width,size.height,FrameClient.scaleMode);ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.JPEG,90,out);b.recycle();callbacks.post(()->{if(closed)return;for(Object arg:args)if(arg instanceof Camera.ShutterCallback)((Camera.ShutterCallback)arg).onShutter();Object jpeg=args[args.length-1];if(jpeg instanceof Camera.PictureCallback)((Camera.PictureCallback)jpeg).onPictureTaken(out.toByteArray(),camera);});}catch(Throwable e){report.accept("Virtual picture: "+e);}});}
 }
 private final class Device {
  final CameraDevice object;final String id;final CameraDevice.StateCallback callback;final Executor executor;volatile boolean closed;Session active;
  Device(String id,CameraDevice.StateCallback callback,Executor executor)throws Exception{this.id=id;this.callback=callback;this.executor=executor;object=(CameraDevice)allocate(deviceType);}
  synchronized Object call(String name,Object[] args)throws Throwable{
   switch(name){
    case "getId":return id;case "isSessionConfigurationSupported":return true;
    case "createCaptureRequest":return builder(id);
    case "createCaptureSession":case "createCaptureSessionByOutputConfigurations":
     if(closed)throw new IllegalStateException("Virtual device closed");if(active!=null)active.close();
     CameraCaptureSession.StateCallback state=null;Executor deliver=executor(args);List<Surface> surfaces=new ArrayList<>();
     for(Object arg:args){if(arg instanceof CameraCaptureSession.StateCallback)state=(CameraCaptureSession.StateCallback)arg;if(arg instanceof List)for(Object value:(List<?>)arg){if(value instanceof Surface)surfaces.add((Surface)value);if(value instanceof OutputConfiguration)surfaces.addAll(((OutputConfiguration)value).getSurfaces());}
      if(arg instanceof SessionConfiguration){SessionConfiguration config=(SessionConfiguration)arg;if(config.getSessionType()!=SessionConfiguration.SESSION_REGULAR||config.getInputConfiguration()!=null)throw new UnsupportedOperationException("Virtual reprocessing/high speed is unavailable");state=config.getStateCallback();deliver=config.getExecutor();for(OutputConfiguration output:config.getOutputConfigurations())surfaces.addAll(output.getSurfaces());}}
     if(state==null||surfaces.isEmpty())throw new IllegalArgumentException("Missing session callback/outputs");active=new Session(this,state,deliver,surfaces);sessions.put(active.object,active);Session created=active;deliver.execute(()->created.callback.onConfigured(created.object));return null;
    case "close":case "finalize":if(!closed){closed=true;devices.remove(object);retired.put(object,id);if(active!=null)active.close();executor.execute(()->callback.onClosed(object));}return null;
    default:throw new UnsupportedOperationException("SceneCam Camera2 device: "+name);
   }
  }
 }
 private CaptureRequest.Builder builder(String id)throws Exception{
  Object metadata=XposedHelpers.newInstance(metadataType);
  for(Constructor<?> constructor:CaptureRequest.Builder.class.getDeclaredConstructors()){Class<?>[] types=constructor.getParameterTypes();if(types.length>0&&types[0]==metadataType){Object[] args=new Object[types.length];args[0]=metadata;for(int i=1;i<types.length;i++)args[i]=types[i]==boolean.class?false:types[i]==int.class?-1:types[i]==String.class?id:types[i]==Set.class?Collections.emptySet():null;constructor.setAccessible(true);CaptureRequest.Builder builder=(CaptureRequest.Builder)constructor.newInstance(args);builder.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);builder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);return builder;}}
  throw new UnsupportedOperationException("CaptureRequest.Builder constructor");
 }
 @SuppressWarnings("unchecked") private static Collection<Surface> targets(CaptureRequest request){return (Collection<Surface>)XposedHelpers.callMethod(request,"getTargets");}
 private final class Session {
  final CameraCaptureSession object;final Device device;final CameraCaptureSession.StateCallback callback;final Executor executor;final int id=++nextSession;
  final HandlerThread thread=new HandlerThread("virtual-camera2");final Handler handler;final Map<Surface,VirtualOutput> outputs=new HashMap<>();volatile boolean closed;int sequence;long frame;Runnable repeating;
  Session(Device d,CameraCaptureSession.StateCallback callback,Executor executor,List<Surface> surfaces)throws Exception{device=d;this.callback=callback;this.executor=executor;object=(CameraCaptureSession)allocate(sessionType);for(Surface surface:surfaces){WeakReference<ImageReader> reference=readers.get(surface);outputs.put(surface,new VirtualOutput(surface,reference==null?null:reference.get(),jpeg));}thread.start();handler=new Handler(thread.getLooper());}
  synchronized Object call(String name,Object[] args)throws Throwable{
   switch(name){
    case "getDevice":return device.object;case "isReprocessable":return false;case "getInputSurface":return null;
    case "close":case "finalize":close();return null;
    case "stopRepeating":case "abortCaptures":if(repeating!=null){handler.removeCallbacks(repeating);repeating=null;}executor.execute(()->callback.onReady(object));return null;
    case "capture":case "captureBurst":case "setRepeatingRequest":case "setRepeatingBurst":case "captureSingleRequest":case "captureBurstRequests":case "setSingleRepeatingRequest":case "setRepeatingBurstRequests":
     if(closed)throw new IllegalStateException("Virtual session closed");List<CaptureRequest> requests=new ArrayList<>();CameraCaptureSession.CaptureCallback capture=null;Executor deliver=executor(args);
     for(Object arg:args){if(arg instanceof CaptureRequest)requests.add((CaptureRequest)arg);if(arg instanceof List)for(Object r:(List<?>)arg)requests.add((CaptureRequest)r);if(arg instanceof CameraCaptureSession.CaptureCallback)capture=(CameraCaptureSession.CaptureCallback)arg;}
     for(CaptureRequest request:requests)for(Surface surface:targets(request))if(!outputs.containsKey(surface))throw new IllegalArgumentException("Unconfigured virtual Surface");
     if(requests.isEmpty())throw new IllegalArgumentException("No virtual requests");int number=++sequence;CameraCaptureSession.CaptureCallback cb=capture;boolean loop=name.toLowerCase(Locale.ROOT).contains("repeating");if(loop&&repeating!=null)handler.removeCallbacks(repeating);
     Runnable task=new Runnable(){public void run(){long begin=SystemClock.elapsedRealtime();synchronized(Session.this){if(closed||loop&&repeating!=this)return;}
      for(CaptureRequest request:requests){long timestamp=System.nanoTime(),index=++frame;try{
       if(cb!=null)deliver.execute(()->{if(!closed)cb.onCaptureStarted(object,request,timestamp,index);});Bitmap bitmap=source.get();for(Surface surface:targets(request))outputs.get(surface).frame(bitmap,timestamp);
       if(cb!=null){TotalCaptureResult result=result(request,timestamp,index,number,id,device.id);deliver.execute(()->{if(!closed)cb.onCaptureCompleted(object,request,result);});}
      }catch(Throwable error){if(!closed){report.accept("Virtual Camera2 frame failed: "+error);if(cb!=null)try{CaptureFailure failure=failure(request,number,index);deliver.execute(()->{if(!closed)cb.onCaptureFailed(object,request,failure);});}catch(Exception metadataError){report.accept("CaptureFailure unavailable: "+metadataError);}}}}
      synchronized(Session.this){if(loop&&!closed&&repeating==this)handler.postDelayed(this,Math.max(1,1000/FrameClient.fps-(SystemClock.elapsedRealtime()-begin)));else if(!loop&&cb!=null){long last=frame;deliver.execute(()->cb.onCaptureSequenceCompleted(object,number,last));}}
     }};if(loop)repeating=task;handler.post(task);executor.execute(()->callback.onActive(object));return number;
    default:throw new UnsupportedOperationException("SceneCam Camera2 session: "+name);
   }
  }
  synchronized void close(){if(closed)return;closed=true;sessions.remove(object);retired.put(object,"");repeating=null;handler.removeCallbacksAndMessages(null);handler.post(()->{for(VirtualOutput out:outputs.values())try{out.close();}catch(Exception ignored){}thread.quitSafely();executor.execute(()->callback.onClosed(object));});}
 }
 private CaptureFailure failure(CaptureRequest request,int sequence,long frame)throws Exception{
  for(Constructor<?> c:CaptureFailure.class.getDeclaredConstructors()){Class<?>[] t=c.getParameterTypes();if(t.length>=5&&t[0]==CaptureRequest.class){Object[] args=new Object[t.length];args[0]=request;args[1]=CaptureFailure.REASON_ERROR;args[2]=false;args[3]=sequence;args[4]=frame;c.setAccessible(true);return (CaptureFailure)c.newInstance(args);}}throw new UnsupportedOperationException("CaptureFailure constructor");
 }
 private TotalCaptureResult result(CaptureRequest request,long timestamp,long frame,int sequence,int session,String cameraId)throws Exception{
  Object metadata=XposedHelpers.newInstance(metadataType);
  // Native metadata container only; this does not open a camera or call CameraService.
  CaptureResult.Key<Long> timestampKey=CaptureResult.SENSOR_TIMESTAMP;
  XposedHelpers.callMethod(metadata,"set",timestampKey,timestamp);
  XposedHelpers.callMethod(metadata,"set",CaptureResult.CONTROL_AF_STATE,CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED);
  XposedHelpers.callMethod(metadata,"set",CaptureResult.CONTROL_AE_STATE,CaptureResult.CONTROL_AE_STATE_CONVERGED);
  XposedHelpers.callMethod(metadata,"set",CaptureResult.CONTROL_AWB_STATE,CaptureResult.CONTROL_AWB_STATE_CONVERGED);
  XposedHelpers.callMethod(metadata,"set",CaptureResult.SENSOR_FRAME_DURATION,1000000000L/FrameClient.fps);
  for(Constructor<?> constructor:TotalCaptureResult.class.getDeclaredConstructors()){Class<?>[] types=constructor.getParameterTypes();if(types.length==8&&types[0]==String.class&&types[3]==int.class){constructor.setAccessible(true);return (TotalCaptureResult)constructor.newInstance(cameraId,metadata,request,sequence,frame,Collections.emptyList(),session,Array.newInstance(types[7].getComponentType(),0));}}
  // Android 9–16 use CaptureResultExtras instead of the request-id/frame overload.
  Class<?> extrasType=Class.forName("android.hardware.camera2.impl.CaptureResultExtras");Object extras=null;
  for(Constructor<?> c:extrasType.getDeclaredConstructors()){Class<?>[] types=c.getParameterTypes();if(types.length>4&&types[0]==int.class){Object[] a=new Object[types.length];for(int i=0;i<a.length;i++)a[i]=types[i]==int.class?0:types[i]==long.class?0L:types[i]==boolean.class?false:null;a[0]=sequence;a[4]=frame;c.setAccessible(true);extras=c.newInstance(a);break;}}
  for(Constructor<?> c:TotalCaptureResult.class.getDeclaredConstructors()){Class<?>[] t=c.getParameterTypes();if(t.length>=6){Object[] a=new Object[t.length];for(int i=0;i<t.length;i++)a[i]=t[i]==String.class?cameraId:t[i]==metadataType?metadata:t[i]==CaptureRequest.class?request:t[i]==extrasType?extras:t[i]==List.class?Collections.emptyList():t[i]==int.class?session:t[i].isArray()?Array.newInstance(t[i].getComponentType(),0):null;c.setAccessible(true);return (TotalCaptureResult)c.newInstance(a);}}
  throw new UnsupportedOperationException("TotalCaptureResult constructor");
 }
}
