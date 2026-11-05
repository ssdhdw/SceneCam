package studio.scenecam;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import java.io.*;
/** Installs a deterministic square grid as a real image source, including JPEG IPC. */
public final class GridFixtureProbe extends Instrumentation {
 public void onCreate(Bundle args){super.onCreate(args);start();}
 public void onStart(){Bundle result=new Bundle();try{
  Studio.load(getTargetContext());Studio.layers.clear();Studio.audio.clear();Studio.WIDTH=720;Studio.HEIGHT=1280;Studio.scaleMode=FrameScale.CROP;Studio.FPS=15;
  Bitmap grid=Bitmap.createBitmap(720,1280,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(grid);c.drawColor(Color.rgb(25,25,25));Paint p=new Paint();p.setColor(Color.WHITE);p.setStrokeWidth(4);
  for(int x=40;x<720;x+=80)c.drawLine(x,0,x,1280,p);for(int y=40;y<1280;y+=80)c.drawLine(0,y,720,y,p);
  p.setColor(Color.RED);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(6);c.drawCircle(360,640,180,p);
  File file=new File(getTargetContext().getFilesDir(),"square-grid.png");try(FileOutputStream out=new FileOutputStream(file)){grid.compress(Bitmap.CompressFormat.PNG,100,out);}grid.recycle();
  Studio.layers.add(new Layer("image","Квадратная сетка 80×80",Uri.fromFile(file).toString()));Studio.save(getTargetContext());
  // Persist before instrumentation's process exits.
  org.json.JSONArray layers=new org.json.JSONArray();for(Layer layer:Studio.layers)layers.put(layer.json());getTargetContext().getSharedPreferences("studio",0).edit().putString("layers",layers.toString()).commit();
  result.putString("stream","GRID_FIXTURE_OK 720x1280 cells=80x80 crop\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("stream","GRID_FIXTURE_FAILED "+e);finish(Activity.RESULT_CANCELED,result);}}
}
