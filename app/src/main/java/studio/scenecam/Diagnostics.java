package studio.scenecam;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Diagnostics {
    private static final Map<String,String> entries=new LinkedHashMap<>();
    private Diagnostics(){}
    public static synchronized void record(String caller,String message){
        if(message==null)message="";
        if(message.length()>600)message=message.substring(0,600);
        entries.remove(caller);entries.put(caller,message);
        while(entries.size()>32)entries.remove(entries.keySet().iterator().next());
    }
    public static synchronized String text(){
        if(entries.isEmpty())return "Нет подключений модуля. Проверьте область действия LSPosed и полностью перезапустите целевое приложение.";
        StringBuilder text=new StringBuilder();for(Map.Entry<String,String> e:entries.entrySet())text.append(e.getKey()).append(": ").append(e.getValue()).append('\n');return text.toString();
    }
}
