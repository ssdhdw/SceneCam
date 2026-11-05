package studio.scenecam;

import android.content.Context;
import android.graphics.*;
import android.hardware.Camera;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.SystemClock;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("deprecation")
public final class Sources implements AutoCloseable {
    private final Context context;
    private final Map<String,Worker> workers=new HashMap<>();
    public Sources(Context c){context=c;}
    public synchronized void reconcile(){
        Set<String> ids=new HashSet<>();for(Layer l:Studio.layers){if(l.kind.equals("camera")&&!l.visible)continue;String key=l.kind.equals("camera")?"camera:"+l.uri:l.id;ids.add(key);if(!workers.containsKey(key)){Worker w=new Worker(l);workers.put(key,w);w.start();}}
        Iterator<Map.Entry<String,Worker>> it=workers.entrySet().iterator();
        while(it.hasNext()){Map.Entry<String,Worker> e=it.next();if(!ids.contains(e.getKey())){e.getValue().shutdown();it.remove();}}
    }
    public synchronized void close(){for(Worker w:workers.values())w.shutdown();workers.clear();}
    private final class Worker extends Thread {
        final Layer layer; volatile boolean active=true; volatile HttpURLConnection connection;
        Worker(Layer l){super("source-"+l.kind);layer=l;}
        void shutdown(){active=false;interrupt();HttpURLConnection c=connection;if(c!=null)c.disconnect();}
        public void run(){layer.error="";try{
            switch(layer.kind){case "image":image();break;case "video":video();break;case "mjpeg":network();break;case "camera":camera();break;}
        }catch(Exception e){if(active)layer.error=e.getClass().getSimpleName()+": "+e.getMessage();}}
        void image()throws Exception{
            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
            try(InputStream in=context.getContentResolver().openInputStream(Uri.parse(layer.uri))){BitmapFactory.decodeStream(in,null,options);}
            options.inSampleSize=1;while(options.outWidth/options.inSampleSize>1920||options.outHeight/options.inSampleSize>1920)options.inSampleSize*=2;
            options.inJustDecodeBounds=false;
            try(InputStream in=context.getContentResolver().openInputStream(Uri.parse(layer.uri))){layer.frame=BitmapFactory.decodeStream(in,null,options);}
            if(layer.frame==null)throw new IOException("Не удалось прочитать изображение");
        }
        void video()throws Exception{
            MediaMetadataRetriever r=new MediaMetadataRetriever();try{
                r.setDataSource(context,Uri.parse(layer.uri));
                String duration=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                long length=duration==null?0:Long.parseLong(duration);if(length<=0)throw new IOException("Неизвестная длина видео");
                int sw=Integer.parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)),sh=Integer.parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                String rotation=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);if("90".equals(rotation)||"270".equals(rotation)){int swap=sw;sw=sh;sh=swap;}
                float factor=Math.min(1f,Math.min(1280f/sw,1280f/sh));int vw=Math.max(1,Math.round(sw*factor)),vh=Math.max(1,Math.round(sh*factor));
                long start=SystemClock.elapsedRealtime();
                while(active){long position=(SystemClock.elapsedRealtime()-start)%length;
                    Bitmap b=r.getScaledFrameAtTime(position*1000,MediaMetadataRetriever.OPTION_CLOSEST,vw,vh);
                    if(b!=null)layer.frame=b;Thread.sleep(Math.max(1,1000/Studio.FPS));}
            }finally{r.release();}
        }
        void network()throws Exception{
            URL url=new URL(layer.uri);if(!url.getProtocol().equals("http")&&!url.getProtocol().equals("https"))throw new IOException("Нужен HTTP(S) MJPEG URL");
            while(active){try{
                connection=(HttpURLConnection)url.openConnection();connection.setConnectTimeout(5000);connection.setReadTimeout(5000);
                if(connection.getResponseCode()!=200)throw new IOException("HTTP "+connection.getResponseCode());
                try(InputStream in=new BufferedInputStream(connection.getInputStream())){
                    ByteArrayOutputStream frame=null;int prev=-1,value;
                    while(active&&(value=in.read())!=-1){
                        if(frame==null){if(prev==255&&value==216){frame=new ByteArrayOutputStream();frame.write(255);frame.write(216);}}
                        else{frame.write(value);if(frame.size()>4*1024*1024)throw new IOException("Кадр MJPEG больше 4 МБ");
                            if(prev==255&&value==217){byte[] bytes=frame.toByteArray();BitmapFactory.Options opt=new BitmapFactory.Options();opt.inSampleSize=2;
                                Bitmap b=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opt);if(b!=null){layer.frame=b;layer.error="";}frame=null;}}
                        prev=value;
                    }
                }
            }catch(IOException e){if(active)layer.error="Переподключение: "+e.getMessage();}finally{if(connection!=null)connection.disconnect();}
                if(active)Thread.sleep(1500);
            }
        }
        void camera()throws Exception{
            try(CameraSource source=new CameraSource(context,layer.uri,frame->{for(Layer l:Studio.layers)if(l.kind.equals("camera")&&l.uri.equals(layer.uri))l.frame=frame;},error->{for(Layer l:Studio.layers)if(l.kind.equals("camera")&&l.uri.equals(layer.uri))l.error=error;})){
                source.start();while(active)Thread.sleep(100);
            }
        }
    }
}
