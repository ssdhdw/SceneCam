package studio.scenecam.probe;

import android.app.Application;
import android.content.Context;
import android.util.Log;

public final class ProbeApplication extends Application {
    protected void attachBaseContext(Context context){super.attachBaseContext(context);Log.i("SceneCamProbe","Application.attachBaseContext");}
    public void onCreate(){super.onCreate();Log.i("SceneCamProbe","Application.onCreate");}
}
