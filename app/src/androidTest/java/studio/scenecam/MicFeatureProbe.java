package studio.scenecam;
import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import java.util.*;
public final class MicFeatureProbe extends Instrumentation {
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle result=new Bundle();List<AudioLayer> saved=new ArrayList<>();MainActivity activity=null;try{
  Studio.load(getTargetContext());saved.addAll(Studio.audio);Studio.audio.clear();AudioLayer mic=new AudioLayer("mic","Test real mic","");Studio.audio.add(mic);
  activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();MainActivity target=activity;runOnMainSync(()->target.startForegroundService(new Intent(target,StudioService.class)));Thread.sleep(1200);
  AudioManager manager=getTargetContext().getSystemService(AudioManager.class);int active=manager.getActiveRecordingConfigurations().size();if(!mic.error.isEmpty()||active<1)throw new AssertionError("mic source: "+mic.error+", active="+active);
  mic.enabled=false;Thread.sleep(400);int remaining=manager.getActiveRecordingConfigurations().size();if(remaining!=0)throw new AssertionError("microphone still open: "+remaining);
  runOnMainSync(()->target.stopService(new Intent(target,StudioService.class)));result.putString("stream","PHYSICAL_MIC_OK opened="+active+" after_disable="+remaining+"\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("stream","MIC_FEATURE_FAILED "+e);finish(Activity.RESULT_CANCELED,result);}finally{getTargetContext().stopService(new Intent(getTargetContext(),StudioService.class));Studio.audio.clear();Studio.audio.addAll(saved);Studio.save(getTargetContext());try{org.json.JSONArray json=new org.json.JSONArray();for(AudioLayer layer:saved)json.put(layer.json());getTargetContext().getSharedPreferences("studio",0).edit().putString("audioLayers",json.toString()).commit();}catch(Exception ignored){}}}
}
