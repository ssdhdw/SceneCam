package studio.scenecam.hook;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.os.SystemClock;
import java.io.*;
import android.os.Bundle;
import studio.scenecam.Nv21;
import studio.scenecam.FrameScale;
import de.robv.android.xposed.XposedBridge;

final class FrameClient {
    static volatile int fps=15;
    static volatile String scaleMode=FrameScale.FIT;
    volatile Bitmap bitmap;volatile long received;
    private volatile String event="Модуль 0.9 подключён; ожидание камеры";
    private volatile String error="";
    private final Object firstFrame=new Object();
    FrameClient(Context context){new Thread(()->{long lastReport=0;String logged="";while(true){try{
        if(SystemClock.elapsedRealtime()-lastReport>1000){
            Bundle state=context.getContentResolver().call(Uri.parse("content://studio.scenecam.frames"),"status",context.getPackageName()+": "+event+(error.isEmpty()?"":"; "+error),null);
            if(state!=null){scaleMode=state.getString("scaleMode",FrameScale.FIT);fps=Math.max(1,Math.min(30,state.getInt("fps",15)));}
            lastReport=SystemClock.elapsedRealtime();
        }
        try(InputStream in=context.getContentResolver().openInputStream(Uri.parse("content://studio.scenecam.frames/frame"))){
            Bitmap next=BitmapFactory.decodeStream(in);if(next==null)throw new IOException("Не удалось декодировать кадр");
            boolean first=bitmap==null;bitmap=next;received=SystemClock.elapsedRealtime();error="";
            if(first)XposedBridge.log("SceneCam IPC frame received: "+context.getPackageName()+" "+next.getWidth()+"x"+next.getHeight());
            synchronized(firstFrame){firstFrame.notifyAll();}}
        Thread.sleep(Math.max(1,1000/fps));
    }catch(InterruptedException e){return;}catch(Exception e){bitmap=null;error=e.getClass().getSimpleName()+": "+e.getMessage();
        if(!error.equals(logged)){XposedBridge.log("SceneCam transport: "+error);logged=error;}
        try{Thread.sleep(300);}catch(InterruptedException stop){return;}}}},"scenecam-reader").start();}
    void report(String message){event=message;XposedBridge.log("SceneCam: "+message);}
    String failure(){return error.isEmpty()?"Нет кадра. Запустите сцену в SceneCam":error;}
    boolean awaitFrame(long timeout){
        long until=SystemClock.elapsedRealtime()+timeout;
        synchronized(firstFrame){while(!ready()){long left=until-SystemClock.elapsedRealtime();if(left<=0)return false;
            try{firstFrame.wait(left);}catch(InterruptedException e){Thread.currentThread().interrupt();return false;}}}return true;
    }
    boolean ready(){return bitmap!=null&&SystemClock.elapsedRealtime()-received<1500;}
    Bitmap current(){return ready()?bitmap:null;}
    static byte[] nv21(Bitmap input,int width,int height){
        if((width&1)!=0||(height&1)!=0)throw new IllegalArgumentException("NV21 requires even dimensions");
        Bitmap scaled=FrameScale.bitmap(input,width,height,scaleMode);
        int[] pixels=new int[width*height];scaled.getPixels(pixels,0,width,0,0,width,height);
        byte[] out=Nv21.encode(pixels,width,height);
        if(scaled!=input)scaled.recycle();return out;
    }
}
