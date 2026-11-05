package studio.scenecam.probe;
import android.content.Context;
import android.media.*;
import android.util.Log;
import java.io.*;
import java.nio.*;
public final class AudioProbe {
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 public static void run(Context context){new Thread(()->{try{
  AudioRecord record=new AudioRecord(MediaRecorder.AudioSource.MIC,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,6400);check(record.getState()==AudioRecord.STATE_INITIALIZED,"state");record.startRecording();short[] data=new short[320];long energy=0;int count=0,crossings=0;short last=0;long begin=android.os.SystemClock.elapsedRealtime();
  try(FileOutputStream out=new FileOutputStream(new File(context.getFilesDir(),"virtual-mic.pcm"))){for(int n=0;n<50;n++){int got=record.read(data,0,data.length);check(got==data.length,"read="+got);for(int i=0;i<got;i++){short value=data[i];energy+=(long)value*value;if(last<0&&value>=0)crossings++;last=value;out.write(value&255);out.write(value>>8);count++;}}}
  AudioTimestamp timestamp=new AudioTimestamp();check(record.getTimestamp(timestamp,AudioTimestamp.TIMEBASE_MONOTONIC)==0&&timestamp.framePosition>0,"timestamp");record.stop();check(record.read(data,0,data.length)==AudioRecord.ERROR_INVALID_OPERATION,"stopped");record.release();double rms=Math.sqrt(energy/(double)count);check(rms>3000,"silent RMS "+rms);long elapsed=android.os.SystemClock.elapsedRealtime()-begin;check(elapsed>=800&&elapsed<2500,"pacing "+elapsed);Log.i("SceneCamAudioProbe","VIRTUAL_MIC_OK rms="+rms+" crossings="+crossings+" ms="+elapsed);
  AudioRecord floats=new AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION).setAudioFormat(new AudioFormat.Builder().setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build()).setBufferSizeInBytes(16384).build();floats.startRecording();float[] f=new float[882];check(floats.read(f,0,f.length,AudioRecord.READ_BLOCKING)==f.length,"float count");check(Math.abs(f[100]-f[101])<.00001,"stereo");floats.stop();floats.release();Log.i("SceneCamAudioProbe","FLOAT_STEREO_OK");
  AudioRecord direct=new AudioRecord(MediaRecorder.AudioSource.MIC,48000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,4096);direct.startRecording();ByteBuffer buffer=ByteBuffer.allocateDirect(1920);buffer.position(9);check(direct.read(buffer,1920)==1920&&buffer.position()==9,"direct buffer");direct.stop();direct.release();Log.i("SceneCamAudioProbe","DIRECT_BUFFER_OK");
 }catch(Throwable e){Log.e("SceneCamAudioProbe","AUDIO_PROBE_FAILED",e);}},"audio-probe").start();}
 public static void device(Context context){new Thread(()->{try{AudioTrack track=new AudioTrack.Builder().setAudioFormat(new AudioFormat.Builder().setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setBufferSizeInBytes(9600).setTransferMode(AudioTrack.MODE_STREAM).build();track.play();short[] wave=new short[960];long phase=0;for(int n=0;n<160;n++){for(int i=0;i<wave.length;i++)wave[i]=(short)(Math.sin((phase++)*2*Math.PI*880/48000)*8000);track.write(wave,0,wave.length);}track.stop();track.release();Log.i("SceneCamAudioProbe","DEVICE_PLAYBACK_SENT");}catch(Throwable e){Log.e("SceneCamAudioProbe","DEVICE_FAILED",e);}},"device-probe").start();}
}
