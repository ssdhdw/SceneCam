package studio.scenecam;
import org.json.JSONObject;
import java.util.UUID;
public final class AudioLayer {
 public final String id;public String kind,name,uri;public volatile boolean enabled=true;public volatile float gain=1,level;public volatile String error="";
 public AudioLayer(String kind,String name,String uri){this(UUID.randomUUID().toString(),kind,name,uri);}
 private AudioLayer(String id,String kind,String name,String uri){this.id=id;this.kind=kind;this.name=name;this.uri=uri;}
 public JSONObject json()throws Exception{return new JSONObject().put("id",id).put("kind",kind).put("name",name).put("uri",uri).put("enabled",enabled).put("gain",gain);}
 public static AudioLayer read(JSONObject j)throws Exception{AudioLayer layer=new AudioLayer(j.getString("id"),j.getString("kind"),j.getString("name"),j.optString("uri",""));layer.enabled=j.optBoolean("enabled",true);layer.gain=(float)j.optDouble("gain",1);return layer;}
}
