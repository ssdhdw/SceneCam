package studio.scenecam.probe;

import android.os.Looper;

/** Runs with app_process, before ActivityThread prepares the main looper. */
public final class EarlyLoadProbe {
    public static void main(String[] args){
        System.out.println("mainLooper="+Looper.getMainLooper());
        try{
            Class<?> entry=Class.forName("studio.scenecam.hook.CameraHook");
            entry.getConstructor().newInstance();
            System.out.println("MODULE_CONSTRUCTOR_OK");
        }catch(Throwable error){error.printStackTrace();System.out.println("MODULE_CONSTRUCTOR_FAILED");System.exit(1);}
    }
}
