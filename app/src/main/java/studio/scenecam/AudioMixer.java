package studio.scenecam;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.net.Uri;
import android.os.SystemClock;
import java.nio.*;
import java.util.*;
/** Sources are silent unless explicitly enabled. Only the mic worker constructs a hardware AudioRecord. */
public final class AudioMixer implements AutoCloseable {
 private final Context context;private volatile boolean active=true;private final Thread thread;private final Map<String,Worker> workers=new HashMap<>();
 public AudioMixer(Context context){this.context=context;thread=new Thread(this::mix,"scene-audio-mixer");thread.start();}
 private void mix(){long next=System.nanoTime();try{while(active){Set<String> live=new HashSet<>();for(AudioLayer layer:Studio.audio)if(layer.enabled&&!layer.kind.equals("device")){live.add(layer.id);if(!workers.containsKey(layer.id)){Worker worker=new Worker(layer);workers.put(layer.id,worker);worker.start();}}
  Iterator<Map.Entry<String,Worker>> iterator=workers.entrySet().iterator();while(iterator.hasNext()){Map.Entry<String,Worker> entry=iterator.next();if(!live.contains(entry.getKey())){entry.getValue().close();iterator.remove();}}
  int[] mix=new int[AudioBus.BLOCK];short[] device=null;for(AudioLayer layer:Studio.audio){layer.level=0;if(!layer.enabled)continue;short[] pcm=new short[AudioBus.BLOCK];if(layer.kind.equals("device")){if(device==null)device=AudioBus.deviceBlock();pcm=device;}else{Worker worker=workers.get(layer.id);if(worker!=null)worker.pipe.read(pcm);}
   long energy=0;for(int i=0;i<pcm.length;i++){int value=Math.round(pcm[i]*Math.max(0,Math.min(2,layer.gain)));mix[i]+=value;energy+=(long)value*value;}layer.level=(float)Math.min(1,Math.sqrt(energy/(double)pcm.length)/32768);}
  short[] pcm=new short[mix.length];long energy=0;for(int i=0;i<pcm.length;i++){pcm[i]=(short)Math.max(-32768,Math.min(32767,mix[i]));energy+=(long)pcm[i]*pcm[i];}AudioBus.level=(float)(Math.sqrt(energy/(double)pcm.length)/32768);AudioBus.append(pcm);
  next+=20000000;long delay=next-System.nanoTime();if(delay>0)Thread.sleep(delay/1000000,(int)(delay%1000000));else if(delay< -100000000)next=System.nanoTime();
 }}catch(InterruptedException ignored){}finally{for(Worker worker:workers.values())worker.close();workers.clear();}}
 public void close(){active=false;thread.interrupt();if(Thread.currentThread()!=thread)try{thread.join(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
 private final class Worker extends Thread implements AutoCloseable {
  final AudioLayer layer;final PcmPipe pipe=new PcmPipe(AudioBus.RATE);volatile boolean alive=true;volatile AudioRecord recorder;double phase;int inputRate,inputChannels;
  Worker(AudioLayer layer){super("audio-source-"+layer.kind);this.layer=layer;}
  public void close(){alive=false;pipe.close();AudioRecord record=recorder;if(record!=null)try{record.stop();}catch(RuntimeException ignored){}interrupt();}
  public void run(){while(alive){layer.error="";try{if(layer.kind.equals("mic"))mic();else decode();return;}catch(Exception e){if(alive)layer.error=e.getClass().getSimpleName()+": "+e.getMessage();if(!layer.kind.equals("internet"))return;try{Thread.sleep(1500);}catch(InterruptedException stop){return;}}}}
  void mic(){if(context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("Нет разрешения микрофона");int size=Math.max(3840,AudioRecord.getMinBufferSize(AudioBus.RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT));
   AudioRecord record=new AudioRecord(MediaRecorder.AudioSource.MIC,AudioBus.RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,size);recorder=record;
   try{if(record.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("Микрофон недоступен");record.startRecording();short[] block=new short[960];while(alive){int count=record.read(block,0,block.length);if(count<0)throw new IllegalStateException("AudioRecord error "+count);if(count>0)try{pipe.write(Arrays.copyOf(block,count),false);}catch(InterruptedException e){return;}}}finally{recorder=null;record.release();}}
  void decode()throws Exception{while(alive){MediaExtractor extractor=new MediaExtractor();MediaCodec codec=null;try{
    if(layer.kind.equals("internet"))extractor.setDataSource(layer.uri,new HashMap<>());else extractor.setDataSource(context,Uri.parse(layer.uri),null);
    int track=-1;MediaFormat format=null;for(int i=0;i<extractor.getTrackCount();i++){MediaFormat candidate=extractor.getTrackFormat(i);if(candidate.getString(MediaFormat.KEY_MIME).startsWith("audio/")){track=i;format=candidate;break;}}
    if(track<0)throw new IllegalArgumentException("Источник не содержит аудио");extractor.selectTrack(track);codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));codec.configure(format,null,null,0);codec.start();
    inputRate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE);inputChannels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);int encoding=AudioFormat.ENCODING_PCM_16BIT;boolean eof=false,done=false;MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
    while(alive&&!done){if(!eof){int index=codec.dequeueInputBuffer(10000);if(index>=0){ByteBuffer buffer=codec.getInputBuffer(index);int size=extractor.readSampleData(buffer,0);if(size<0){codec.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);eof=true;}else{codec.queueInputBuffer(index,0,size,extractor.getSampleTime(),0);extractor.advance();}}}
     int index=codec.dequeueOutputBuffer(info,10000);if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat output=codec.getOutputFormat();inputRate=output.getInteger(MediaFormat.KEY_SAMPLE_RATE);inputChannels=output.getInteger(MediaFormat.KEY_CHANNEL_COUNT);if(output.containsKey(MediaFormat.KEY_PCM_ENCODING))encoding=output.getInteger(MediaFormat.KEY_PCM_ENCODING);}
     else if(index>=0){try{if(info.size>0){ByteBuffer buffer=codec.getOutputBuffer(index).duplicate().order(ByteOrder.LITTLE_ENDIAN);buffer.position(info.offset);buffer.limit(info.offset+info.size);convert(buffer,encoding);}done=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;}finally{codec.releaseOutputBuffer(index,false);}}}
   }finally{if(codec!=null){try{codec.stop();}catch(Exception ignored){}codec.release();}extractor.release();}
   if(alive&&layer.kind.equals("internet")){layer.error="Поток завершён; переподключение";Thread.sleep(1000);}phase=0;
  }}
  void convert(ByteBuffer buffer,int encoding)throws InterruptedException{int bytes=encoding==AudioFormat.ENCODING_PCM_FLOAT?4:encoding==AudioFormat.ENCODING_PCM_16BIT?2:0;if(bytes==0)throw new IllegalArgumentException("Unsupported PCM format "+encoding);int frames=buffer.remaining()/(bytes*inputChannels);int start=buffer.position();double step=inputRate/(double)AudioBus.RATE;short[] output=new short[(int)Math.ceil((frames+1)/step)];int n=0;double at=phase;for(;at<frames;at+=step){int position=start+(int)at*inputChannels*bytes;float sum=0;for(int c=0;c<inputChannels;c++)sum+=bytes==4?buffer.getFloat(position+c*bytes)*32767:buffer.getShort(position+c*bytes);output[n++]=(short)Math.max(-32768,Math.min(32767,Math.round(sum/inputChannels)));}phase=at-frames;pipe.write(Arrays.copyOf(output,n),true);}
 }
}
