package studio.scenecam.hook;

import android.app.*;
import android.os.*;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import de.robv.android.xposed.XposedBridge;

/** Displays in the target process; does not rely on the scene provider being reachable. */
final class HookNotice implements Application.ActivityLifecycleCallbacks {
    // Legacy LSPosed may instantiate the module before ActivityThread prepares
    // the main looper. Constructors must not create Android UI objects.
    private Handler main;
    private synchronized Handler mainHandler(){
        Looper looper=Looper.getMainLooper();if(looper==null)return null;
        if(main==null)main=new Handler(looper);return main;
    }
    private WeakReference<Activity> foreground=new WeakReference<>(null);
    private boolean announced;private long lastToast;private String lastError="";
    void install(Application application){mainHandler();application.registerActivityLifecycleCallbacks(this);}
    public void onActivityResumed(Activity activity){
        foreground=new WeakReference<>(activity);
        if(!announced){announced=true;show("SceneCam: модуль LSPosed загружен\n"+activity.getPackageName());}
    }
    void error(String message){Handler handler=mainHandler();if(handler==null){XposedBridge.log("SceneCam: "+message);return;}handler.post(()->{
        if(message.equals(lastError))return;lastError=message;
        long delay=Math.max(0,3000-(SystemClock.elapsedRealtime()-lastToast));
        handler.postDelayed(()->show("SceneCam: "+message),delay);
    });}
    private void show(String message){
        Activity activity=foreground.get();if(activity==null||activity.isFinishing()||activity.isDestroyed())return;
        try{Toast.makeText(activity,message,Toast.LENGTH_LONG).show();lastToast=SystemClock.elapsedRealtime();}
        catch(RuntimeException e){XposedBridge.log("SceneCam toast: "+e);}
    }
    public void onActivityPaused(Activity activity){if(foreground.get()==activity)foreground.clear();}
    public void onActivityCreated(Activity activity,Bundle state){}
    public void onActivityStarted(Activity activity){}
    public void onActivityStopped(Activity activity){}
    public void onActivitySaveInstanceState(Activity activity,Bundle state){}
    public void onActivityDestroyed(Activity activity){if(foreground.get()==activity)foreground.clear();}
}
