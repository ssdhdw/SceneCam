package studio.scenecam.hook;
import android.content.Context;
import android.media.*;
import android.net.Uri;
import android.os.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import de.robv.android.xposed.*;
/** Captures only Java AudioTrack PCM from scoped apps; does not request RECORD_AUDIO. */
final class DeviceAudioTap {
 private final Supplier<Context> context;private final ArrayBlockingQueue<Bundle> queue=new ArrayBlockingQueue<>(24);private volatile boolean enabled;private final ThreadLocal<Integer> depth=ThreadLocal.withInitial(()->0);
 DeviceAudioTap(Supplier<Context> context){this.context=context;}
 void install(){new Thread(()->{long status=0;while(true)try{Context c=context.get();if(c==null){Thread.sleep(300);continue;}long now=SystemClock.elapsedRealtime();if(now-status>500){Bundle response=c.getContentResolver().call(Uri.parse("content://studio.scenecam.frames"),"audioStatus",null,null);enabled=response!=null&&response.getBoolean("device");status=now;if(!enabled)queue.clear();}Bundle packet=queue.poll(100,TimeUnit.MILLISECONDS);if(packet!=null&&enabled)c.getContentResolver().call(Uri.parse("content://studio.scenecam.frames"),"audioPush",c.getPackageName(),packet);
 }catch(InterruptedException e){return;}catch(RuntimeException e){enabled=false;try{Thread.sleep(500);}catch(InterruptedException stop){return;}}},"device-audio-tap").start();
  XposedBridge.hookAllMethods(AudioTrack.class,"write",new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){p.setObjectExtra("outer",depth.get()==0);depth.set(depth.get()+1);if(enabled&&p.args[0] instanceof ByteBuffer)p.setObjectExtra("position",((ByteBuffer)p.args[0]).position());}
   protected void afterHookedMethod(MethodHookParam p){depth.set(Math.max(0,depth.get()-1));if(!Boolean.TRUE.equals(p.getObjectExtra("outer")))return;if(!enabled||p.hasThrowable()||!(p.getResult() instanceof Integer)||(Integer)p.getResult()<=0)return;try{
    AudioTrack track=(AudioTrack)p.thisObject;int channels=track.getChannelCount(),rate=track.getSampleRate(),format=track.getAudioFormat();Object input=p.args[0];int count=(Integer)p.getResult(),offset=input instanceof ByteBuffer?(Integer)p.getObjectExtra("position"):(Integer)p.args[1];int bytes=format==AudioFormat.ENCODING_PCM_16BIT?2:format==AudioFormat.ENCODING_PCM_FLOAT?4:format==AudioFormat.ENCODING_PCM_8BIT?1:0;if(bytes==0)return;
    int samples=input instanceof byte[]||input instanceof ByteBuffer?count/bytes:count;samples-=samples%channels;byte[] pcm=new byte[Math.min(samples,16384)*2];ByteBuffer buffer=input instanceof ByteBuffer?((ByteBuffer)input).duplicate().order(ByteOrder.LITTLE_ENDIAN):input instanceof byte[]?ByteBuffer.wrap((byte[])input).order(ByteOrder.LITTLE_ENDIAN):null;
    for(int i=0;i<pcm.length/2;i++){int value;if(input instanceof short[])value=((short[])input)[offset+i];else if(input instanceof float[])value=Math.round(((float[])input)[offset+i]*32767);else{int at=offset+i*bytes;value=bytes==2?buffer.getShort(at):bytes==4?Math.round(buffer.getFloat(at)*32767):((buffer.get(at)&255)-128)*256;}value=Math.max(-32768,Math.min(32767,value));pcm[i*2]=(byte)value;pcm[i*2+1]=(byte)(value>>8);}
    Bundle packet=new Bundle();packet.putByteArray("pcm",pcm);packet.putInt("rate",rate);packet.putInt("channels",channels);packet.putString("stream",android.os.Process.myPid()+":"+System.identityHashCode(track));queue.offer(packet);
   }catch(RuntimeException ignored){}}
  });
 }
}
