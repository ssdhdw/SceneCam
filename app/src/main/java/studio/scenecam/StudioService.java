package studio.scenecam;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.os.*;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.*;

public final class StudioService extends Service {
    private AudioMixer audioMixer;private ScheduledExecutorService executor;private Sources sources;private SceneAccess access;private long lastAccessRefresh;
    public void onCreate(){super.onCreate();Studio.load(this);
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("studio","Виртуальная камера",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,StudioService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(this,"studio").setContentTitle("SceneCam работает")
            .setContentText("Сцена доступна локальным приложениям").setSmallIcon(android.R.drawable.presence_video_online)
            .setContentIntent(open).addAction(new Notification.Action.Builder(null,"Остановить",stop).build()).setOngoing(true).build();
        int type=0;if(Build.VERSION.SDK_INT>=34){type=ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
            if(Studio.layers.stream().anyMatch(l->l.visible&&l.kind.equals("camera"))&&checkSelfPermission(android.Manifest.permission.CAMERA)==android.content.pm.PackageManager.PERMISSION_GRANTED)type|=ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
            if(Studio.audio.stream().anyMatch(l->l.enabled&&l.kind.equals("mic"))&&checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED)type|=ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;}
        if(Build.VERSION.SDK_INT>=34)startForeground(1,notification,type);else startForeground(1,notification);
        AudioBus.reset();Studio.running=true;audioMixer=new AudioMixer(this);Studio.status="640 × 360 · 15 FPS";sources=new Sources(this);
        access=new SceneAccess(this);access.refresh();lastAccessRefresh=SystemClock.elapsedRealtime();
        executor=Executors.newSingleThreadScheduledExecutor();executor.execute(new Runnable(){public void run(){long begin=SystemClock.elapsedRealtime();
            try{if(SystemClock.elapsedRealtime()-lastAccessRefresh>5000){access.refresh();lastAccessRefresh=SystemClock.elapsedRealtime();}Studio.status=Studio.WIDTH+" × "+Studio.HEIGHT+" · "+Studio.FPS+" FPS";sources.reconcile();Bitmap b=Studio.compose();ByteArrayOutputStream out=new ByteArrayOutputStream();
                b.compress(Bitmap.CompressFormat.JPEG,80,out);Studio.jpeg=out.toByteArray();Studio.preview=b;
            }catch(Exception e){Studio.status="Ошибка: "+e.getMessage();}
        if(Studio.running&&!executor.isShutdown())try{executor.schedule(this,Math.max(1,1000/Studio.FPS-(SystemClock.elapsedRealtime()-begin)),TimeUnit.MILLISECONDS);}catch(RejectedExecutionException ignored){}
        }});
    }
    public int onStartCommand(Intent i,int flags,int id){if(i!=null&&"stop".equals(i.getAction()))stopSelf();return START_NOT_STICKY;}
    public void onDestroy(){Studio.running=false;if(audioMixer!=null)audioMixer.close();AudioBus.reset();if(executor!=null)executor.shutdownNow();if(access!=null)access.close();if(sources!=null)sources.close();Studio.jpeg=null;Studio.preview=null;Studio.status="Остановлено";super.onDestroy();}
    public IBinder onBind(Intent i){return null;}
}
