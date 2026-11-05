package studio.scenecam.probe;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.*;
import android.net.*;
import android.graphics.*;
import android.hardware.Camera;
import android.view.*;
import android.hardware.camera2.*;
import android.media.ImageReader;
import android.media.Image;
import android.os.Handler;
import android.os.Looper;
import java.util.Collections;
import java.nio.ByteBuffer;
import java.io.*;

public final class ProbeActivity extends Activity {
    private Camera camera;private TextureView texture;
    private CameraDevice camera2;private ImageReader reader;private boolean savedCamera2;
    public void onCreate(Bundle state){super.onCreate(state);Log.i("SceneCamProbe","Activity.onCreate");
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(40,120,40,40);
        TextView title=new TextView(this);title.setText("SceneCam Hook Probe\nWaiting for module startup Toast");title.setTextSize(22);root.addView(title);
        Button reopen=new Button(this);reopen.setText("Recreate Activity");reopen.setOnClickListener(v->recreate());root.addView(reopen);setContentView(root);
        Button read=new Button(this);read.setText("Read scene URI");root.addView(read);ImageView image=new ImageView(this);root.addView(image,new LinearLayout.LayoutParams(-1,300));
        read.setOnLongClickListener(v->{AudioProbe.run(this);return true;});
        reopen.setOnLongClickListener(v->{AudioProbe.device(this);return true;});
        read.setOnClickListener(v->new Thread(()->{Uri frameUri=Uri.parse("content://studio.scenecam.frames/frame");try(InputStream in=getContentResolver().openInputStream(frameUri)){
            Bitmap frame=BitmapFactory.decodeStream(in);if(frame==null)throw new IOException("Bad JPEG");
            Log.i("SceneCamProbe","URI_FRAME_OK "+frame.getWidth()+"x"+frame.getHeight());runOnUiThread(()->{image.setImageBitmap(frame);title.setText("URI_FRAME_OK");});
        }catch(Exception error){Log.i("SceneCamProbe","URI_READ_FAILED "+error);runOnUiThread(()->title.setText("URI_READ_FAILED "+error));}},"probe-reader").start());
        Button openCamera=new Button(this);openCamera.setText("Open Camera1");root.addView(openCamera);
        openCamera.setOnLongClickListener(v->{if(camera==null)return false;Camera active=camera;active.addCallbackBuffer(new byte[640*480*3/2]);active.setPreviewCallbackWithBuffer(new Camera.PreviewCallback(){int count;public void onPreviewFrame(byte[] bytes,Camera c){Log.i("SceneCamProbe","BUFFER_CALLBACK_OK main="+(Looper.myLooper()==Looper.getMainLooper()));if(++count<3){c.addCallbackBuffer(bytes);return;}c.setPreviewCallbackWithBuffer(null);c.autoFocus((success,focused)->focused.takePicture(null,null,(jpeg,owner)->{try(FileOutputStream out=new FileOutputStream(new File(getFilesDir(),"camera1-photo.jpg"))){out.write(jpeg);Log.i("SceneCamProbe","CAMERA1_PHOTO_OK "+jpeg.length);}catch(Exception e){Log.e("SceneCamProbe","CAMERA1_PHOTO_FAILED",e);}}));}});return true;});
        texture=new TextureView(this);root.addView(texture,new LinearLayout.LayoutParams(-1,400));
        openCamera.setOnClickListener(v->{try{
            if(camera!=null){camera.release();camera=null;}
            if(camera2!=null){camera2.close();camera2=null;}if(reader!=null){reader.close();reader=null;}camera=Camera.open(0);Camera.Parameters p=camera.getParameters();p.setPreviewSize(640,480);p.setPreviewFormat(ImageFormat.NV21);camera.setParameters(p);
            camera.setPreviewTexture(texture.getSurfaceTexture());
            camera.setOneShotPreviewCallback((bytes,c)->{try{
                File output=new File(getFilesDir(),"callback.jpg");try(FileOutputStream stream=new FileOutputStream(output)){
                    new YuvImage(bytes,ImageFormat.NV21,640,480,null).compressToJpeg(new Rect(0,0,640,480),95,stream);}
                Log.i("SceneCamProbe","CAMERA_CALLBACK_SAVED "+bytes.length);
            }catch(Exception error){Log.e("SceneCamProbe","callback failed",error);}});
            camera.startPreview();title.setText("Camera1 started");
        }catch(Exception error){title.setText(error.toString());Log.e("SceneCamProbe","camera failed",error);}});
        Button open2=new Button(this);open2.setText("Open Camera2 YUV");root.addView(open2);
        open2.setOnLongClickListener(v->{jpegProbe();return true;});
        open2.setOnClickListener(v->{if(camera!=null){camera.release();camera=null;}if(camera2!=null){camera2.close();camera2=null;}if(reader!=null)reader.close();savedCamera2=false;
            reader=ImageReader.newInstance(640,480,ImageFormat.YUV_420_888,3);Handler handler=new Handler(Looper.getMainLooper());
            reader.setOnImageAvailableListener(r->{try(Image frame=r.acquireLatestImage()){
                if(frame==null||savedCamera2)return;Image.Plane[] planes=frame.getPlanes();byte[] nv21=new byte[640*480*3/2];
                for(int plane=0;plane<3;plane++){int w=plane==0?640:320,h=plane==0?480:240;ByteBuffer buffer=planes[plane].getBuffer();int rowStride=planes[plane].getRowStride(),pixelStride=planes[plane].getPixelStride();
                    for(int y=0;y<h;y++)for(int x=0;x<w;x++){int output=plane==0?y*640+x:640*480+y*640+x*2+(plane==1?1:0);nv21[output]=buffer.get(y*rowStride+x*pixelStride);}}
                try(FileOutputStream stream=new FileOutputStream(new File(getFilesDir(),"camera2-callback.jpg"))){new YuvImage(nv21,ImageFormat.NV21,640,480,null).compressToJpeg(new Rect(0,0,640,480),95,stream);}
                savedCamera2=true;title.setText("CAMERA2_IMAGE_SAVED");Log.i("SceneCamProbe","CAMERA2_IMAGE_SAVED");
            }catch(Exception error){Log.e("SceneCamProbe","camera2 frame failed",error);}},handler);
            try{CameraManager manager=getSystemService(CameraManager.class);manager.openCamera(manager.getCameraIdList()[0],new CameraDevice.StateCallback(){
                public void onOpened(CameraDevice device){camera2=device;try{device.createCaptureSession(Collections.singletonList(reader.getSurface()),new CameraCaptureSession.StateCallback(){
                    public void onConfigured(CameraCaptureSession session){Log.i("SceneCamProbe","CONFIGURED_ENTER");try{CaptureRequest.Builder request=device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);request.addTarget(reader.getSurface());session.setRepeatingRequest(request.build(),new CameraCaptureSession.CaptureCallback(){private boolean logged;public void onCaptureCompleted(CameraCaptureSession s,CaptureRequest r,TotalCaptureResult result){if(!logged){logged=true;Log.i("SceneCamProbe","CAPTURE_RESULT_OK timestamp="+result.get(CaptureResult.SENSOR_TIMESTAMP)+" frame="+result.getFrameNumber());}}},handler);}catch(Exception e){Log.e("SceneCamProbe","camera2 request failed",e);}}
                    public void onConfigureFailed(CameraCaptureSession session){Log.e("SceneCamProbe","camera2 configuration failed");}
                },handler);}catch(Exception e){Log.e("SceneCamProbe","camera2 session failed",e);}}
                public void onDisconnected(CameraDevice device){device.close();}
                public void onError(CameraDevice device,int error){device.close();Log.e("SceneCamProbe","camera2 error "+error);}
            },handler);}catch(Exception e){title.setText(e.toString());Log.e("SceneCamProbe","camera2 open failed",e);}
        });
    }
    private void jpegProbe(){if(camera!=null){camera.release();camera=null;}if(camera2!=null){camera2.close();camera2=null;}if(reader!=null)reader.close();
        reader=ImageReader.newInstance(640,480,ImageFormat.JPEG,2);Handler handler=new Handler(Looper.getMainLooper());
        reader.setOnImageAvailableListener(r->{try(Image image=r.acquireLatestImage()){if(image==null)return;ByteBuffer buffer=image.getPlanes()[0].getBuffer();byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);try(FileOutputStream out=new FileOutputStream(new File(getFilesDir(),"virtual-jpeg.jpg"))){out.write(bytes);}Log.i("SceneCamProbe","JPEG_IMAGE_OK "+bytes.length);}catch(Exception e){Log.e("SceneCamProbe","JPEG_FAILED",e);}},handler);
        try{CameraManager manager=getSystemService(CameraManager.class);manager.openCamera(manager.getCameraIdList()[0],getMainExecutor(),new CameraDevice.StateCallback(){public void onOpened(CameraDevice device){camera2=device;try{android.hardware.camera2.params.SessionConfiguration config=new android.hardware.camera2.params.SessionConfiguration(0,Collections.singletonList(new android.hardware.camera2.params.OutputConfiguration(reader.getSurface())),getMainExecutor(),new CameraCaptureSession.StateCallback(){public void onConfigured(CameraCaptureSession session){Log.i("SceneCamProbe","CONFIGURED_ENTER");try{CaptureRequest.Builder request=device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);request.addTarget(reader.getSurface());session.capture(request.build(),new CameraCaptureSession.CaptureCallback(){public void onCaptureCompleted(CameraCaptureSession s,CaptureRequest r,TotalCaptureResult result){Log.i("SceneCamProbe","JPEG_RESULT_OK "+result.getFrameNumber());}},handler);}catch(Exception e){Log.e("SceneCamProbe","JPEG_REQUEST_FAILED",e);}}public void onConfigureFailed(CameraCaptureSession s){}});device.createCaptureSession(config);}catch(Exception e){Log.e("SceneCamProbe","JPEG_SESSION_FAILED",e);}}public void onDisconnected(CameraDevice d){d.close();}public void onError(CameraDevice d,int e){d.close();}});}catch(Exception e){Log.e("SceneCamProbe","JPEG_OPEN_FAILED",e);}
    }
    protected void onResume(){super.onResume();Log.i("SceneCamProbe","Activity.onResume");}
    protected void onPause(){if(camera!=null){camera.release();camera=null;}if(camera2!=null){camera2.close();camera2=null;}if(reader!=null){reader.close();reader=null;}super.onPause();}
}
