package studio.scenecam.probe;
public final class ApiProbe {public static void main(String[] args)throws Exception{Class<?> type=Class.forName("android.media.AudioRecord");for(java.lang.reflect.Method m:type.getDeclaredMethods())if(m.getName().startsWith("native_")||m.getName().equals("read"))System.out.println(m);}}
