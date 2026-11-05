package studio.scenecam;

import android.content.*;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.util.Log;
import java.util.HashMap;
import java.util.Map;

/** User-approved local access: URI grants also establish Android package visibility. */
public final class SceneAccess {
    public static final Uri FRAME=Uri.parse("content://studio.scenecam.frames/frame");
    private final Context context;
    private final Map<String,Long> granted=new HashMap<>();
    private boolean closed;
    public SceneAccess(Context context){this.context=context;}
    public synchronized void refresh(){
        if(closed)return;
        for(PackageInfo app:context.getPackageManager().getInstalledPackages(0)){
            Long update=granted.get(app.packageName);if(update!=null&&update.longValue()==app.lastUpdateTime)continue;
            try{context.grantUriPermission(app.packageName,FRAME,Intent.FLAG_GRANT_READ_URI_PERMISSION);granted.put(app.packageName,app.lastUpdateTime);}
            catch(RuntimeException error){Log.w("SceneCamAccess","URI grant failed for "+app.packageName,error);}
        }
    }
    public synchronized void close(){closed=true;context.revokeUriPermission(FRAME,Intent.FLAG_GRANT_READ_URI_PERMISSION);granted.clear();}
}
