package studio.scenecam;

import android.graphics.SurfaceTexture;
import android.opengl.*;
import android.os.*;
import java.util.concurrent.*;

/** Drains camera buffers on a dedicated GL context so the producer never fills its queue. */
public final class TextureSink implements AutoCloseable {
    private final HandlerThread thread=new HandlerThread("scenecam-drain");
    private Handler handler;private EGLDisplay display;private EGLContext context;private EGLSurface surface;
    public SurfaceTexture texture;private volatile RuntimeException failure;
    public TextureSink(){
        thread.start();handler=new Handler(thread.getLooper());CountDownLatch ready=new CountDownLatch(1);
        handler.post(()->{try{
            display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);int[] version=new int[2];
            if(!EGL14.eglInitialize(display,version,0,version,1))throw new IllegalStateException("EGL initialize failed");
            int[] attributes={EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_NONE};
            EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];EGL14.eglChooseConfig(display,attributes,0,configs,0,1,count,0);
            if(count[0]==0)throw new IllegalStateException("No EGL config");
            context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
            surface=EGL14.eglCreatePbufferSurface(display,configs[0],new int[]{EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE},0);
            if(!EGL14.eglMakeCurrent(display,surface,surface,context))throw new IllegalStateException("EGL makeCurrent failed");
            int[] names=new int[1];GLES20.glGenTextures(1,names,0);texture=new SurfaceTexture(names[0]);texture.setDefaultBufferSize(640,480);
            texture.setOnFrameAvailableListener(t->{try{t.updateTexImage();}catch(RuntimeException ignored){}},handler);
        }catch(RuntimeException e){failure=e;}finally{ready.countDown();}});
        try{if(!ready.await(5,TimeUnit.SECONDS))throw new IllegalStateException("GL initialization timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
        if(failure!=null){close();throw failure;}
    }
    public void close(){handler.post(()->{if(texture!=null)texture.release();if(display!=null&&display!=EGL14.EGL_NO_DISPLAY){
        EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
        if(surface!=null)EGL14.eglDestroySurface(display,surface);if(context!=null)EGL14.eglDestroyContext(display,context);EGL14.eglTerminate(display);
    }thread.quitSafely();});}
}
