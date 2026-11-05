package studio.scenecam;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.camera2.*;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
public final class FeatureProbe extends Instrumentation {
 public void onCreate(Bundle b){super.onCreate(b);start();}
 private static void check(boolean condition,String text){if(!condition)throw new AssertionError(text);}
 public void onStart(){Bundle result=new Bundle();StringBuilder log=new StringBuilder();List<Layer> saved=new ArrayList<>(Studio.layers);int sw=Studio.WIDTH,sh=Studio.HEIGHT,rate=Studio.FPS;
 try{
  Studio.layers.clear();Studio.WIDTH=200;Studio.HEIGHT=200;
  Bitmap src=Bitmap.createBitmap(400,200,Bitmap.Config.ARGB_8888);src.eraseColor(Color.BLUE);Canvas canvas=new Canvas(src);Paint paint=new Paint();paint.setColor(Color.RED);canvas.drawCircle(200,100,40,paint);
  Layer l=new Layer("image","test","");l.frame=src;l.scaleMode=FrameScale.CROP;Studio.layers.add(l);
  Bitmap composite=Studio.compose();circle(composite);check(composite.getPixel(5,5)==Color.BLUE,"crop must fill canvas");
  Bitmap output=FrameScale.bitmap(src,200,200,FrameScale.CROP);circle(output);
  l.rotation=90;circle(Studio.compose());
  Layer restored=Layer.read(l.json());check(FrameScale.CROP.equals(restored.scaleMode),"layer persistence");
  log.append("CROP_OK layer output rotation persistence\n");
  Studio.layers.clear();Studio.WIDTH=320;Studio.HEIGHT=240;
  MainActivity activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
  Studio.FPS=5;runOnMainSync(()->activity.startForegroundService(new Intent(activity,StudioService.class)));Thread.sleep(1000);
  int slow=count(1400);Studio.FPS=20;Thread.sleep(300);int fast=count(1400);check(fast>slow*2,"dynamic FPS "+slow+" / "+fast);
  log.append("FPS_OK 5fps=").append(slow).append(" 20fps=").append(fast).append(" frames/1.4s\n");
  runOnMainSync(()->activity.stopService(new Intent(activity,StudioService.class)));Thread.sleep(500);
  CameraManager manager=getTargetContext().getSystemService(CameraManager.class);String[] ids=manager.getCameraIdList();log.append("CAMERA_IDS ").append(Arrays.toString(ids)).append("\n");
  if(ids.length>0){CountDownLatch first=new CountDownLatch(1);try(CameraSource camera=new CameraSource(getTargetContext(),ids[0],b->{if(b!=null)first.countDown();},e->{if(!e.isEmpty())synchronized(log){log.append("CAMERA_NOTICE ").append(e).append("\n");}})){
   camera.start();check(first.await(8,TimeUnit.SECONDS),"first Camera2 source");log.append("CAMERA_SOURCE_OK\n");
  }}
  if(ids.length>1){CountDownLatch both=new CountDownLatch(2);Set<String> seen=ConcurrentHashMap.newKeySet();ConcurrentHashMap<String,Long> stamps=new ConcurrentHashMap<>();List<CameraSource> cameras=new ArrayList<>();try{
   for(int i=0;i<2;i++){String id=ids[i];CameraSource camera=new CameraSource(getTargetContext(),id,b->{if(b!=null){stamps.put(id,SystemClock.elapsedRealtime());if(seen.add(id))both.countDown();}},e->{if(!e.isEmpty())synchronized(log){log.append("MULTI_NOTICE ").append(id).append(": ").append(e).append("\n");}});cameras.add(camera);camera.start();}
   boolean received=both.await(8,TimeUnit.SECONDS);Thread.sleep(1500);long now=SystemClock.elapsedRealtime();boolean live=received&&stamps.size()==2;for(Long stamp:stamps.values())live&=now-stamp<1000;log.append(live?"MULTI_CAMERA_OK simultaneous fresh frames\n":"MULTI_CAMERA_DEVICE_LIMIT\n");
  }finally{for(CameraSource camera:cameras)camera.close();}}
  if(ids.length>0){Studio.layers.clear();Layer a=new Layer("camera","A",ids[0]),b=new Layer("camera","B",ids[0]);Studio.layers.add(a);Studio.layers.add(b);
   try(Sources sources=new Sources(getTargetContext())){sources.reconcile();long end=SystemClock.elapsedRealtime()+7000;while(a.frame==null&&SystemClock.elapsedRealtime()<end)Thread.sleep(50);check(a.frame!=null&&a.frame==b.frame,"shared camera source");log.append("DUPLICATE_CAMERA_OK shared connection\n");}
  }
  result.putString("stream",log.toString());finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("stream",log+"FEATURE_FAILED "+e);finish(Activity.RESULT_CANCELED,result);
 }finally{getTargetContext().stopService(new Intent(getTargetContext(),StudioService.class));Studio.layers.clear();Studio.layers.addAll(saved);Studio.WIDTH=sw;Studio.HEIGHT=sh;Studio.FPS=rate;}}
 private static void circle(Bitmap b){int minx=b.getWidth(),maxx=0,miny=b.getHeight(),maxy=0;for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++){int p=b.getPixel(x,y);if(Color.red(p)>200&&Color.blue(p)<30){minx=Math.min(minx,x);maxx=Math.max(maxx,x);miny=Math.min(miny,y);maxy=Math.max(maxy,y);}}
 check(maxx>minx&&Math.abs((maxx-minx)-(maxy-miny))<=2,"crop distorted circle");}
 private static int count(long duration)throws Exception{int count=0;byte[] old=null;long end=SystemClock.elapsedRealtime()+duration;while(SystemClock.elapsedRealtime()<end){byte[] next=Studio.jpeg;if(next!=null&&next!=old){count++;old=next;}Thread.sleep(5);}return count;}
}
