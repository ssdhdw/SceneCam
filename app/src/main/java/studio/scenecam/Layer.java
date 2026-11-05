package studio.scenecam;

import android.graphics.Bitmap;
import org.json.JSONObject;
import java.util.UUID;

public final class Layer {
    public final String id;
    public String kind, name, uri;
    public String scaleMode="inherit";
    public float x=0, y=0, width=1, height=1, alpha=1;
    public int rotation=0;
    public boolean visible=true, mirror=false;
    public volatile Bitmap frame;
    public volatile String error="";
    public Layer(String kind, String name, String uri) { this(UUID.randomUUID().toString(),kind,name,uri); }
    private Layer(String id,String kind,String name,String uri) {this.id=id;this.kind=kind;this.name=name;this.uri=uri;}
    public JSONObject json() throws Exception {
        return new JSONObject().put("id",id).put("kind",kind).put("name",name).put("uri",uri)
            .put("x",x).put("y",y).put("w",width).put("h",height).put("alpha",alpha)
            .put("scaleMode",scaleMode).put("rotation",rotation).put("visible",visible).put("mirror",mirror);
    }
    public static Layer read(JSONObject j) throws Exception {
        Layer l=new Layer(j.getString("id"),j.getString("kind"),j.getString("name"),j.getString("uri"));
        l.x=(float)j.optDouble("x",0); l.y=(float)j.optDouble("y",0);
        l.width=(float)j.optDouble("w",1); l.height=(float)j.optDouble("h",1);
        l.scaleMode=j.optString("scaleMode","inherit");l.alpha=(float)j.optDouble("alpha",1);l.rotation=j.optInt("rotation",0);
        l.visible=j.optBoolean("visible",true);l.mirror=j.optBoolean("mirror",false);return l;
    }
}
