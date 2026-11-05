package studio.scenecam.hook;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import java.util.function.Supplier;
import de.robv.android.xposed.*;
/** AudioRecord uses mixer PCM, and never calls AudioFlinger record setup in scoped apps. */
final class VirtualAudio {
 private final Supplier<Context> context;private final Map<Object,Record> records=Collections.synchronizedMap(new WeakHashMap<>());
 private final Set<Object> encoded=Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
 VirtualAudio(Supplier<Context> context){this.context=context;}
 void install(){
  for(Method method:AudioRecord.class.getDeclaredMethods())if(method.getName().startsWith("native_")&&!Modifier.isStatic(method.getModifiers()))XposedBridge.hookMethod(method,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){try{String name=p.method.getName();if(name.equals("native_setup")){int seen=0;for(Object arg:p.args)if(arg instanceof int[]){int[] values=(int[])arg;if(values.length>0){if(seen++==0){if(values[0]==0)values[0]=48000;}else values[0]=23001;}}records.put(p.thisObject,new Record());p.setResult(0);XposedBridge.log("SceneCam AUDIO_VIRTUAL_SETUP: no hardware microphone");return;}
    Record record=records.get(p.thisObject);if(record==null)return;AudioRecord object=(AudioRecord)p.thisObject;
    switch(name){
     case "native_start":if(record.closed){p.setResult(AudioRecord.ERROR_INVALID_OPERATION);return;}record.started=true;record.cursor=-1;record.frames=0;record.next=System.nanoTime();p.setResult(0);return;
     case "native_stop":record.started=false;p.setResult(null);return;
     case "native_release":case "native_finalize":record.started=false;record.closed=true;p.setResult(null);return;
     case "native_get_timestamp":AudioTimestamp stamp=(AudioTimestamp)p.args[0];stamp.framePosition=record.frames;stamp.nanoTime=System.nanoTime();p.setResult(0);return;
     case "native_get_buffer_size_in_frames":p.setResult(Math.max(960,object.getSampleRate()/5));return;
     case "native_getRoutedDeviceIds":p.setResult(new int[0]);return;
     case "native_get_active_microphones":p.setResult(0);return;
     case "native_getMetrics":p.setResult(new PersistableBundle());return;
    }
    if(name.startsWith("native_read_")){p.setResult(record.read(object,p.args));return;}
    Class<?> result=((Method)p.method).getReturnType();p.setResult(result==void.class?null:result==boolean.class?false:result==int.class?0:null);
   }catch(Throwable e){p.setThrowable(e);}}});
  // MediaRecorder encodes natively and does not consume AudioRecord Java PCM. Fail closed.
  XposedBridge.hookAllMethods(MediaRecorder.class,"setAudioSource",new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){encoded.add(p.thisObject);p.setResult(null);}});
  for(String name:new String[]{"prepare","start"})XposedBridge.hookAllMethods(MediaRecorder.class,name,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){if(encoded.contains(p.thisObject))p.setThrowable(new UnsupportedOperationException("SceneCam: native MediaRecorder audio is not supported; hardware microphone was not opened"));}});
  for(String name:new String[]{"reset","release"})XposedBridge.hookAllMethods(MediaRecorder.class,name,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){encoded.remove(p.thisObject);}});
 }
 private final class Record {
  volatile boolean started,closed;double cursor=-1;long generation=-1,frames,next;
  synchronized int read(AudioRecord object,Object[] args){if(!started||closed)return AudioRecord.ERROR_INVALID_OPERATION;Object output=args[0];int count=(Integer)args[output instanceof ByteBuffer?1:2];boolean blocking=(Boolean)args[args.length-1];int offset=output instanceof ByteBuffer?0:(Integer)args[1];int channels=Math.max(1,object.getChannelCount()),encoding=object.getAudioFormat();int sampleBytes=encoding==AudioFormat.ENCODING_PCM_8BIT?1:encoding==AudioFormat.ENCODING_PCM_FLOAT?4:2;
   int samples=output instanceof byte[]||output instanceof ByteBuffer?count/sampleBytes:count;int wanted=Math.min(samples/channels,object.getSampleRate()/2);if(wanted<=0)return 0;
   byte[] mono=null;try{Context c=context.get();if(c!=null){Bundle request=new Bundle();request.putInt("rate",object.getSampleRate());request.putInt("frames",wanted);request.putBoolean("blocking",blocking);request.putDouble("cursor",cursor);request.putLong("generation",generation);Bundle response=c.getContentResolver().call(Uri.parse("content://studio.scenecam.frames"),"audioPull",c.getPackageName(),request);if(response!=null){mono=response.getByteArray("pcm");cursor=response.getDouble("cursor",-1);generation=response.getLong("generation",-1);}}}catch(RuntimeException ignored){cursor=-1;}
   if(mono==null)mono=new byte[blocking?wanted*2:0];int received=mono.length/2;ByteBuffer direct=output instanceof ByteBuffer?((ByteBuffer)output).duplicate().order(ByteOrder.LITTLE_ENDIAN):null;if(direct!=null)direct.clear();
   for(int frame=0;frame<received;frame++){short value=(short)((mono[frame*2]&255)|(mono[frame*2+1]<<8));for(int channel=0;channel<channels;channel++){int at=offset+frame*channels+channel;if(output instanceof short[])((short[])output)[at]=value;else if(output instanceof float[])((float[])output)[at]=value/32768f;else{int byteOffset=(offset+(frame*channels+channel)*sampleBytes);if(direct!=null){if(sampleBytes==1)direct.put(byteOffset,(byte)((value>>8)+128));else if(sampleBytes==4)direct.putFloat(byteOffset,value/32768f);else direct.putShort(byteOffset,value);}else{byte[] bytes=(byte[])output;if(sampleBytes==1)bytes[byteOffset]=(byte)((value>>8)+128);else if(sampleBytes==2){bytes[byteOffset]=(byte)value;bytes[byteOffset+1]=(byte)(value>>8);}else{int bits=Float.floatToIntBits(value/32768f);for(int b=0;b<4;b++)bytes[byteOffset+b]=(byte)(bits>>(8*b));}}}}}
   frames+=received;if(blocking){next+=received*1000000000L/object.getSampleRate();long delay=next-System.nanoTime();if(delay>0)try{Thread.sleep(delay/1000000,(int)(delay%1000000));}catch(InterruptedException e){Thread.currentThread().interrupt();}else if(delay< -100000000)next=System.nanoTime();}
   int result=received*channels;return output instanceof byte[]||output instanceof ByteBuffer?result*sampleBytes:result;
  }
 }
}
