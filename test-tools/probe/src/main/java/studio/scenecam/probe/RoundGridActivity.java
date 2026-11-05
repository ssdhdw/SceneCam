package studio.scenecam.probe;
import android.app.Activity;
import android.os.*;
import android.graphics.*;
import android.hardware.Camera;
import android.hardware.camera2.*;
import android.opengl.*;
import android.view.Surface;
import android.util.Log;
import java.nio.*;
import java.io.*;
import java.util.*;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
/** Exercises the OES input and square texture-coordinate crop used by InstantCameraView.
 * No Telegram code/dependency, account, permission bypass or package-specific hooks. */
@SuppressWarnings("deprecation")
public final class RoundGridActivity extends Activity implements GLSurfaceView.Renderer {
 private GLSurfaceView view;private SurfaceTexture input;private Surface surface;private Camera camera;private CameraDevice device;private CameraCaptureSession session;private int texture,program,frames;private volatile boolean available;private boolean camera2;private RoundGridEncoder encoder;private int width=640,height=480,angle=90;private final float[] st=new float[16];
 public void onCreate(Bundle b){super.onCreate(b);camera2=getIntent().getBooleanExtra("camera2",false);width=getIntent().getIntExtra("width",640);height=getIntent().getIntExtra("height",480);angle=getIntent().getIntExtra("angle",90);view=new GLSurfaceView(this);view.setEGLContextClientVersion(2);view.setRenderer(this);setContentView(view);}
 private int shader(int type,String code){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,code);GLES20.glCompileShader(s);return s;}
 public void onSurfaceCreated(GL10 gl,EGLConfig config){try{
  program=GLES20.glCreateProgram();GLES20.glAttachShader(program,shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 pos;attribute vec2 uv;uniform mat4 st;varying vec2 tex;void main(){gl_Position=vec4(pos,0.,1.);tex=(st*vec4(uv,0.,1.)).xy;}"));GLES20.glAttachShader(program,shader(GLES20.GL_FRAGMENT_SHADER,"#extension GL_OES_EGL_image_external : require\nprecision mediump float;uniform samplerExternalOES image;varying vec2 tex;void main(){gl_FragColor=texture2D(image,tex);}"));GLES20.glLinkProgram(program);
  int[] id=new int[1];GLES20.glGenTextures(1,id,0);texture=id[0];GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
  input=new SurfaceTexture(texture);input.setDefaultBufferSize(width,height);input.setOnFrameAvailableListener(t->available=true);
  if(!camera2){camera=Camera.open(0);Camera.Parameters p=camera.getParameters();p.setPreviewSize(width,height);camera.setParameters(p);camera.setDisplayOrientation(angle);camera.setPreviewTexture(input);camera.startPreview();}
  else{surface=new Surface(input);CameraManager manager=getSystemService(CameraManager.class);manager.openCamera(manager.getCameraIdList()[0],new CameraDevice.StateCallback(){public void onOpened(CameraDevice d){device=d;try{d.createCaptureSession(Collections.singletonList(surface),new CameraCaptureSession.StateCallback(){public void onConfigured(CameraCaptureSession s){session=s;try{CaptureRequest.Builder r=d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);r.addTarget(surface);s.setRepeatingRequest(r.build(),null,new Handler(Looper.getMainLooper()));}catch(Exception e){Log.e("RoundGrid","request",e);}}public void onConfigureFailed(CameraCaptureSession s){}},new Handler(Looper.getMainLooper()));}catch(Exception e){Log.e("RoundGrid","session",e);}}public void onDisconnected(CameraDevice d){d.close();}public void onError(CameraDevice d,int e){Log.e("RoundGrid","camera2 error "+e);}},new Handler(Looper.getMainLooper()));}
 }catch(Exception e){Log.e("RoundGrid","open",e);}}
 public void onSurfaceChanged(GL10 gl,int w,int h){}
 private FloatBuffer floats(float... values){FloatBuffer b=ByteBuffer.allocateDirect(values.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();b.put(values).position(0);return b;}
 public void onDrawFrame(GL10 gl){if(!available)return;available=false;try{input.updateTexImage();input.getTransformMatrix(st);GLES20.glViewport(0,0,400,400);GLES20.glUseProgram(program);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture);
  // Legacy preview is already oriented by setDisplayOrientation. Camera2 is raw sensor data.
  boolean quarter=angle%180!=0;float rx=1,ry=1;float ratio=width/(float)height;if(camera2||!quarter){if(ratio>=1)rx=1/ratio;else ry=ratio;}else{if(ratio>=1)ry=1/ratio;else rx=ratio;}
  int pos=GLES20.glGetAttribLocation(program,"pos"),uv=GLES20.glGetAttribLocation(program,"uv");GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,0,floats(-1,-1,1,-1,-1,1,1,1));GLES20.glEnableVertexAttribArray(pos);GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,0,floats(.5f-rx/2,.5f-ry/2,.5f+rx/2,.5f-ry/2,.5f-rx/2,.5f+ry/2,.5f+rx/2,.5f+ry/2));GLES20.glEnableVertexAttribArray(uv);GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"st"),1,false,st,0);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
  if(++frames==15){ByteBuffer pixels=ByteBuffer.allocateDirect(400*400*4);GLES20.glReadPixels(0,0,400,400,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,pixels);Bitmap b=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888);pixels.rewind();b.copyPixelsFromBuffer(pixels);String name="round-grid-"+(camera2?"camera2":"camera1")+"-"+width+"x"+height+"-"+angle+".png";try(FileOutputStream out=new FileOutputStream(new File(getFilesDir(),name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();Log.i("RoundGrid","SAVED "+name);}
  if(getIntent().getBooleanExtra("encode",false)){if(frames==15)encoder=new RoundGridEncoder(new File(getFilesDir(),"round-grid.mp4"));if(encoder!=null)encoder.frame(input.getTimestamp());if(frames==45&&encoder!=null){encoder.finish();encoder=null;Log.i("RoundGrid","ENCODE_DECODE_OK round-grid.mp4");}}
 }catch(Exception e){Log.e("RoundGrid","frame",e);}}
 protected void onPause(){if(camera!=null){camera.release();camera=null;}if(session!=null)session.close();if(device!=null)device.close();super.onPause();view.onPause();}
}
