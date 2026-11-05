package studio.scenecam;
/** Bounded mono PCM queue. Decoder back-pressure prevents loading whole files into memory. */
public final class PcmPipe {
 private final short[] data;private int read,size;private boolean closed;
 public PcmPipe(int capacity){data=new short[capacity];}
 public synchronized void write(short[] input,boolean wait)throws InterruptedException{int offset=0;while(offset<input.length&&!closed){if(size==data.length){if(wait){wait(50);continue;}read=(read+1)%data.length;size--;}
  int count=Math.min(input.length-offset,data.length-size);for(int i=0;i<count;i++)data[(read+size+i)%data.length]=input[offset+i];size+=count;offset+=count;notifyAll();}}
 public synchronized int read(short[] output){int count=Math.min(size,output.length);for(int i=0;i<count;i++)output[i]=data[(read+i)%data.length];read=(read+count)%data.length;size-=count;java.util.Arrays.fill(output,count,output.length,(short)0);notifyAll();return count;}
 public synchronized void close(){closed=true;size=0;notifyAll();}
}
