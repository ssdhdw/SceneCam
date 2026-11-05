package studio.scenecam.hook;
import android.graphics.*;
import android.media.*;
import android.view.Surface;
import java.nio.ByteBuffer;
import studio.scenecam.FrameScale;
/** Produces real BufferQueue buffers without a camera device. */
final class VirtualOutput implements AutoCloseable {
 private final VirtualJpeg jpeg;private final Surface surface;private final ImageReader reader;private ImageWriter writer;
 VirtualOutput(Surface surface,ImageReader reader,VirtualJpeg jpeg){this.surface=surface;this.reader=reader;this.jpeg=jpeg;if(reader!=null&&reader.getImageFormat()==ImageFormat.JPEG)jpeg.activate(reader);}
 void frame(Bitmap bitmap,long timestamp){
  if(reader==null){Canvas canvas=surface.lockCanvas(null);try{FrameScale.draw(canvas,bitmap,canvas.getWidth(),canvas.getHeight(),FrameClient.scaleMode);}finally{surface.unlockCanvasAndPost(canvas);}return;}
  int format=reader.getImageFormat();if(format==ImageFormat.JPEG){jpeg.frame(reader,bitmap,timestamp);return;}if(format!=ImageFormat.YUV_420_888)throw new UnsupportedOperationException("Virtual ImageReader format "+format+" (supported: YUV_420_888)");
  if(writer==null)writer=ImageWriter.newInstance(surface,2);
  Image image=writer.dequeueInputImage();try{
   int w=image.getWidth(),h=image.getHeight();byte[] nv21=FrameClient.nv21(bitmap,w,h);Image.Plane[] planes=image.getPlanes();
   for(int i=0;i<3;i++){ByteBuffer buffer=planes[i].getBuffer();int offset=buffer.position(),pw=i==0?w:w/2,ph=i==0?h:h/2;
    for(int y=0;y<ph;y++)for(int x=0;x<pw;x++){int src=i==0?y*w+x:w*h+y*w+x*2+(i==1?1:0);buffer.put(offset+y*planes[i].getRowStride()+x*planes[i].getPixelStride(),nv21[src]);}}
   image.setTimestamp(timestamp);writer.queueInputImage(image);image=null;
  }finally{if(image!=null)image.close();}
 }
 public void close(){if(writer!=null){writer.close();writer=null;}}
}
