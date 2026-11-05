package studio.scenecam;

import android.content.Context;
import android.graphics.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Studio {
    public static final CopyOnWriteArrayList<AudioLayer> audio=new CopyOnWriteArrayList<>();
    public static final CopyOnWriteArrayList<Layer> layers=new CopyOnWriteArrayList<>();
    public static volatile boolean running=false;
    public static volatile byte[] jpeg;
    public static volatile Bitmap preview;
    public static volatile String status="Остановлено";
    public static volatile int WIDTH=640, HEIGHT=360;
    public static volatile int FPS=15, previewFps=15;
    public static volatile String scaleMode=FrameScale.FIT;
    private static boolean loaded;
    public static synchronized void load(Context c) {
        if(loaded)return; loaded=true;
        android.content.SharedPreferences prefs=c.getSharedPreferences("studio",0);
        FPS=Math.max(1,Math.min(30,prefs.getInt("fps",15)));previewFps=Math.max(1,Math.min(60,prefs.getInt("previewFps",15)));
        WIDTH=prefs.getInt("width",640);HEIGHT=prefs.getInt("height",360);scaleMode=prefs.getString("scaleMode",FrameScale.FIT);
        try {JSONArray audioJson=new JSONArray(prefs.getString("audioLayers","[]"));for(int i=0;i<audioJson.length();i++)audio.add(AudioLayer.read(audioJson.getJSONObject(i)));JSONArray a=new JSONArray(c.getSharedPreferences("studio",0).getString("layers","[]"));
            for(int i=0;i<a.length();i++)layers.add(Layer.read(a.getJSONObject(i)));
        }catch(Exception e){status="Не удалось загрузить сцену: "+e.getMessage();}
    }
    public static void save(Context c) {
        try{JSONArray audioJson=new JSONArray();for(AudioLayer layer:audio)audioJson.put(layer.json());JSONArray a=new JSONArray();for(Layer l:layers)a.put(l.json());
            c.getSharedPreferences("studio",0).edit().putString("audioLayers",audioJson.toString()).putString("layers",a.toString()).putInt("fps",FPS).putInt("previewFps",previewFps).putInt("width",WIDTH).putInt("height",HEIGHT).putString("scaleMode",scaleMode).remove("allowed").apply();
        }catch(Exception e){status=e.toString();}
    }
    public static Bitmap compose() {
        final int WIDTH=Studio.WIDTH,HEIGHT=Studio.HEIGHT;
        Bitmap b=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(b);c.drawColor(Color.BLACK);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        for(Layer l:layers){Bitmap src=l.frame;if(!l.visible||src==null)continue;
            float left=l.x*WIDTH,top=l.y*HEIGHT,w=l.width*WIDTH,h=l.height*HEIGHT;
            c.save();c.clipRect(left,top,left+w,top+h);c.translate(left+w/2,top+h/2);
            c.rotate(l.rotation);if(l.mirror)c.scale(-1,1);
            boolean quarter=l.rotation%180!=0;int rw=Math.max(1,Math.round(quarter?h:w)),rh=Math.max(1,Math.round(quarter?w:h));
            c.translate(-rw/2f,-rh/2f);
            int alpha=Math.round(l.alpha*255);int composite=c.saveLayerAlpha(0,0,rw,rh,alpha);
            FrameScale.drawLayer(c,src,rw,rh,"inherit".equals(l.scaleMode)?scaleMode:l.scaleMode);c.restoreToCount(composite);c.restore();
        }return b;
    }
}
