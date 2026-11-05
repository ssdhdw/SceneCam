package studio.scenecam;
import android.os.Bundle;
import android.os.SystemClock;
import java.util.*;
/** 48 kHz mono PCM16 timeline, independently readable by every virtual microphone. */
public final class AudioBus {
 public static final int RATE=48000,BLOCK=960;private static final short[] ring=new short[RATE*2];private static long written,generation;public static volatile float level;
 private static final class DeviceStream{final PcmPipe pipe=new PcmPipe(RATE/2);long seen;double phase;}
 private static final Map<String,DeviceStream> device=new HashMap<>();
 public static synchronized void reset(){written=0;generation++;level=0;Arrays.fill(ring,(short)0);device.clear();}
 public static synchronized void append(short[] data){for(short sample:data)ring[(int)(written++%ring.length)]=sample;}
 public static synchronized Bundle pull(Bundle request){int rate=Math.max(8000,Math.min(192000,request.getInt("rate",RATE))),count=Math.max(0,Math.min(rate/2,request.getInt("frames",960)));boolean blocking=request.getBoolean("blocking",true);
  double cursor=request.getDouble("cursor",-1);if(request.getLong("generation",-1)!=generation||cursor<0||cursor<written-ring.length||cursor>written)cursor=Math.max(0,written-BLOCK);
  double step=RATE/(double)rate;int available=Studio.running?(int)Math.max(0,Math.floor((written-cursor)/step)):0;
  int frames=blocking?count:Math.min(count,available);byte[] pcm=new byte[frames*2];for(int i=0;i<frames;i++){long at=(long)cursor;short sample=Studio.running&&at<written&&at>=Math.max(0,written-ring.length)?ring[(int)(at%ring.length)]:0;pcm[i*2]=(byte)sample;pcm[i*2+1]=(byte)(sample>>8);cursor+=step;}
  Bundle result=new Bundle();result.putByteArray("pcm",pcm);result.putDouble("cursor",Studio.running?Math.min(cursor,written):-1);result.putLong("generation",generation);return result;
 }
 public static boolean wantsDevice(){for(AudioLayer layer:Studio.audio)if(layer.enabled&&layer.kind.equals("device"))return Studio.running;return false;}
 public static synchronized void push(String key,Bundle data){if(!wantsDevice())return;byte[] pcm=data.getByteArray("pcm");int rate=data.getInt("rate"),channels=data.getInt("channels");if(pcm==null||pcm.length>65536||rate<8000||rate>192000||channels<1||channels>8)return;
  if(!device.containsKey(key)&&device.size()>=16)return;DeviceStream stream=device.computeIfAbsent(key,k->new DeviceStream());stream.seen=SystemClock.elapsedRealtime();int frames=pcm.length/(2*channels);double step=rate/(double)RATE;short[] out=new short[(int)Math.ceil((frames+1)/step)];int n=0;double at=stream.phase;
  for(;at<frames;at+=step){int frame=(int)at,sum=0;for(int c=0;c<channels;c++){int p=(frame*channels+c)*2;sum+=(short)((pcm[p]&255)|(pcm[p+1]<<8));}out[n++]=(short)(sum/channels);}stream.phase=at-frames;
  try{stream.pipe.write(Arrays.copyOf(out,n),false);}catch(InterruptedException e){Thread.currentThread().interrupt();}
 }
 public static synchronized short[] deviceBlock(){int[] sum=new int[BLOCK];short[] scratch=new short[BLOCK];Iterator<DeviceStream> it=device.values().iterator();long now=SystemClock.elapsedRealtime();while(it.hasNext()){DeviceStream stream=it.next();if(now-stream.seen>1000){stream.pipe.close();it.remove();continue;}stream.pipe.read(scratch);for(int i=0;i<BLOCK;i++)sum[i]+=scratch[i];}
  short[] result=new short[BLOCK];for(int i=0;i<BLOCK;i++)result[i]=(short)Math.max(-32768,Math.min(32767,sum[i]));return result;
 }
}
