package studio.scenecam.hook;
import android.app.*;
import android.content.Context;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
public final class CameraHook implements IXposedHookLoadPackage {
 private volatile Context appContext;private volatile FrameClient client;private final HookNotice notice=new HookNotice();private boolean noticesInstalled;
 private synchronized void connect(Context context){if(context==null)return;appContext=context;if(context instanceof Application&&!noticesInstalled){notice.install((Application)context);noticesInstalled=true;}if(client==null)client=new FrameClient(context);}
 private void prime(){connect(AndroidAppHelper.currentApplication());if(client!=null&&!client.awaitFrame(1200))notice.error(client.failure());}
 public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p){if(p.packageName.equals("studio.scenecam")||p.packageName.equals("android"))return;
  XposedBridge.log("SceneCam 0.9 virtual-only: "+p.packageName+" / "+p.processName);
  XposedHelpers.findAndHookMethod(Application.class,"attach",Context.class,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){connect((Application)p.thisObject);}});
  connect(AndroidAppHelper.currentApplication());
  new VirtualAudio(()->appContext).install();
  new DeviceAudioTap(()->appContext).install();
  new VirtualCamera(()->client==null?null:client.current(),this::prime,message->{if(client!=null)client.report(message);else XposedBridge.log("SceneCam: "+message);}).install(p.classLoader);
 }
}
