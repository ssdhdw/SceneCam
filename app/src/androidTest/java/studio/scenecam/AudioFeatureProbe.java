package studio.scenecam;
import android.app.*;
import android.os.*;
import android.net.Uri;
import java.io.*;
import java.util.*;
public final class AudioFeatureProbe extends Instrumentation {
 public void onCreate(Bundle args){super.onCreate(args);start();}
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 static void wav(File file)throws Exception{try(DataOutputStream out=new DataOutputStream(new FileOutputStream(file))){int samples=7200;out.writeBytes("RIFF");le(out,36+samples*2,4);out.writeBytes("WAVEfmt ");le(out,16,4);le(out,1,2);le(out,1,2);le(out,48000,4);le(out,96000,4);le(out,2,2);le(out,16,2);out.writeBytes("data");le(out,samples*2,4);for(int i=0;i<samples;i++)le(out,(short)Math.round(Math.sin(i*2*Math.PI*440/48000)*12000),2);}}
 static void le(DataOutputStream out,int value,int bytes)throws Exception{for(int i=0;i<bytes;i++)out.writeByte(value>>(i*8));}
 double rms(){Bundle request=new Bundle();request.putInt("frames",960);request.putInt("rate",48000);byte[] pcm=AudioBus.pull(request).getByteArray("pcm");long sum=0;for(int i=0;i<pcm.length;i+=2){int sample=(short)((pcm[i]&255)|(pcm[i+1]<<8));sum+=(long)sample*sample;}return Math.sqrt(sum/(pcm.length/2.));}
 public void onStart(){Bundle result=new Bundle();StringBuilder log=new StringBuilder();AudioMixer mixer=null;try{
  Studio.load(getTargetContext());Studio.audio.clear();File fixture=new File(getTargetContext().getFilesDir(),"tone.wav");wav(fixture);
  AudioLayer layer=new AudioLayer("file","Тест 440 Гц",Uri.fromFile(fixture).toString());Studio.audio.add(layer);Studio.running=true;AudioBus.reset();mixer=new AudioMixer(getTargetContext());Thread.sleep(1600);check(layer.error.isEmpty(),layer.error);double energy=rms();check(energy>4000,"looping file RMS "+energy);log.append("FILE_LOOP_OK rms=").append(energy).append("\n");
  layer.gain=.25f;Thread.sleep(150);double quiet=rms();check(quiet<energy*.4&&quiet>energy*.1,"gain");log.append("GAIN_OK rms=").append(quiet).append("\n");
  layer.enabled=false;Thread.sleep(150);check(rms()==0,"mute silence");log.append("MUTE_OK\n");
  AudioLayer network=new AudioLayer("internet","HTTP test","http://127.0.0.1:18765/tone.wav");Studio.audio.add(network);Thread.sleep(2200);check(network.error.isEmpty()||network.error.contains("завершён"),network.error);double internet=rms();check(internet>4000,"HTTP source RMS "+internet);log.append("INTERNET_OK rms=").append(internet).append("\n");network.enabled=false;
  AudioLayer mic=new AudioLayer("mic","Denied mic","");Studio.audio.add(mic);Thread.sleep(200);check(mic.error.contains("SecurityException"),"mic must refuse without permission: "+mic.error);log.append("MIC_DENIED_OK\n");Studio.audio.remove(mic);
  mixer.close();mixer=null;Studio.running=false;AudioBus.reset();
  Studio.audio.clear();layer.enabled=true;layer.gain=1;Studio.audio.add(layer);Studio.audio.add(new AudioLayer("device","Звук устройства",""));Studio.save(getTargetContext());org.json.JSONArray persisted=new org.json.JSONArray();for(AudioLayer a:Studio.audio)persisted.put(a.json());getTargetContext().getSharedPreferences("studio",0).edit().putString("audioLayers",persisted.toString()).commit();log.append("CONFIG_SAVED tone + device; RECORD_AUDIO not requested\n");
  result.putString("stream",log.toString());finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("stream",log+"AUDIO_FEATURE_FAILED "+e);finish(Activity.RESULT_CANCELED,result);}finally{if(mixer!=null)mixer.close();Studio.running=false;}}
}
