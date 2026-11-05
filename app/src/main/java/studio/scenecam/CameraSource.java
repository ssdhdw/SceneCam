package studio.scenecam;

import android.content.Context;
import android.graphics.*;
import android.hardware.camera2.*;
import android.media.*;
import android.os.*;
import android.util.Size;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Consumer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** One Camera2 connection per camera id; Sources shares its frames between duplicate layers. */
public final class CameraSource implements AutoCloseable {
 private final Context context;private final String id;private final Consumer<Bitmap> frames;private final Consumer<String> errors;
 private final HandlerThread thread=new HandlerThread("scene-camera");private Handler handler;
 private CameraDevice device;private CameraCaptureSession session;private ImageReader reader;
 private volatile boolean closed;private long lastFrame;private int orientation;
 public CameraSource(Context c,String id,Consumer<Bitmap> frames,Consumer<String> errors){context=c;this.id=id;this.frames=frames;this.errors=errors;}
 public void start(){thread.start();handler=new Handler(thread.getLooper());handler.post(()->{try{
  CameraManager manager=context.getSystemService(CameraManager.class);CameraCharacteristics info=manager.getCameraCharacteristics(id);
  Integer angle=info.get(CameraCharacteristics.SENSOR_ORIENTATION);orientation=angle==null?0:angle;
  Size[] sizes=info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.YUV_420_888);
  if(sizes==null||sizes.length==0)throw new IllegalStateException("Камера не поддерживает YUV");
  Size best=sizes[0];for(Size size:sizes)if(Math.abs(size.getWidth()*(long)size.getHeight()-640*480)<Math.abs(best.getWidth()*(long)best.getHeight()-640*480))best=size;
  reader=ImageReader.newInstance(best.getWidth(),best.getHeight(),ImageFormat.YUV_420_888,2);
  reader.setOnImageAvailableListener(this::receive,handler);
  if(context.checkSelfPermission(android.Manifest.permission.CAMERA)!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new SecurityException("Нет разрешения на камеру");
  manager.openCamera(id,new CameraDevice.StateCallback(){
   public void onOpened(CameraDevice camera){if(closed){camera.close();return;}device=camera;try{
    camera.createCaptureSession(Collections.singletonList(reader.getSurface()),new CameraCaptureSession.StateCallback(){
     public void onConfigured(CameraCaptureSession configured){if(closed){configured.close();return;}session=configured;try{
      CaptureRequest.Builder request=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);request.addTarget(reader.getSurface());
      session.setRepeatingRequest(request.build(),null,handler);errors.accept("");
     }catch(Exception e){fail(e.toString());}}
     public void onConfigureFailed(CameraCaptureSession failed){failed.close();fail("Не удалось настроить камеру. Эта комбинация камер может не поддерживаться устройством");}
    },handler);
   }catch(Exception e){fail(e.toString());}}
   public void onDisconnected(CameraDevice camera){camera.close();if(!closed)fail("Камера отключена или занята другим приложением");}
   public void onError(CameraDevice camera,int code){camera.close();if(!closed)fail("Камера недоступна (код "+code+"). Возможно, устройство не поддерживает выбранные камеры одновременно");}
  },handler);
 }catch(Exception e){fail(e.toString());}});}
 private void fail(String message){if(!closed){frames.accept(null);errors.accept(message);}}
 private void receive(ImageReader source){try(Image image=source.acquireLatestImage()){
  if(image==null||closed)return;long now=SystemClock.elapsedRealtime();if(now-lastFrame<1000/Studio.FPS)return;lastFrame=now;
  int w=image.getWidth(),h=image.getHeight();byte[] nv21=new byte[w*h*3/2];Image.Plane[] planes=image.getPlanes();
  for(int i=0;i<3;i++){ByteBuffer buffer=planes[i].getBuffer();int offset=buffer.position();int pw=i==0?w:w/2,ph=i==0?h:h/2;
   for(int y=0;y<ph;y++)for(int x=0;x<pw;x++){int out=i==0?y*w+x:w*h+y*w+x*2+(i==1?1:0);nv21[out]=buffer.get(offset+y*planes[i].getRowStride()+x*planes[i].getPixelStride());}}
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();new YuvImage(nv21,ImageFormat.NV21,w,h,null).compressToJpeg(new Rect(0,0,w,h),80,bytes);
  byte[] jpeg=bytes.toByteArray();Bitmap frame=BitmapFactory.decodeByteArray(jpeg,0,jpeg.length);
  if(frame!=null&&orientation!=0){Matrix matrix=new Matrix();matrix.postRotate(orientation);Bitmap rotated=Bitmap.createBitmap(frame,0,0,w,h,matrix,true);if(rotated!=frame)frame.recycle();frame=rotated;}
  if(!closed){frames.accept(frame);errors.accept("");}
 }catch(Exception e){if(!closed)errors.accept("Ошибка кадра камеры: "+e.getMessage());}}
 public void close(){if(closed)return;closed=true;if(handler==null)return;CountDownLatch done=new CountDownLatch(1);handler.post(()->{try{
  if(session!=null)session.close();if(device!=null)device.close();if(reader!=null)reader.close();
 }finally{done.countDown();thread.quitSafely();}});
  try{done.await(1500,TimeUnit.MILLISECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
 }
}
