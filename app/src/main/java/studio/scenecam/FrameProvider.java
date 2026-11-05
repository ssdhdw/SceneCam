package studio.scenecam;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import java.io.*;

/** Read-only IPC for the running scene. Hook scope is configured exclusively in LSPosed. */
public final class FrameProvider extends ContentProvider {
    public boolean onCreate(){Studio.load(getContext());return true;}
    private String caller(){
        String name=getCallingPackage();return name==null?"uid="+Binder.getCallingUid():name;
    }
    public Bundle call(String method,String arg,Bundle extras){
        if("audioPull".equals(method))return AudioBus.pull(extras==null?new Bundle():extras);
        if("audioStatus".equals(method)){Bundle result=new Bundle();result.putBoolean("device",AudioBus.wantsDevice());return result;}
        if("audioPush".equals(method)){if(extras!=null){String stream=extras.getString("stream","");AudioBus.push(caller()+":"+stream,extras);}return Bundle.EMPTY;}
        if(!"status".equals(method))throw new UnsupportedOperationException();
        Diagnostics.record(caller(),arg==null?"Модуль подключён":arg);
        Bundle result=new Bundle();result.putBoolean("allowed",true);
        result.putBoolean("running",Studio.running);result.putBoolean("frame",Studio.jpeg!=null);
        result.putInt("fps",Studio.FPS);result.putString("scaleMode",Studio.scaleMode);return result;
    }
    public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if(!"r".equals(mode)||!"/frame".equals(uri.getPath()))throw new FileNotFoundException("Read-only frame endpoint");
        byte[] bytes=Studio.jpeg;
        if(!Studio.running||bytes==null)throw new FileNotFoundException("SceneCam is stopped");
        try{ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createReliablePipe();
            new Thread(()->{try(OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])){out.write(bytes);}catch(IOException ignored){}},"frame-pipe").start();
            return pipe[0];
        }catch(IOException e){throw new FileNotFoundException(e.getMessage());}
    }
    public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}
    public String getType(Uri u){return "image/jpeg";}
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
