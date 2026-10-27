from pathlib import Path
import tempfile,subprocess
r=Path(__file__).resolve().parents[1]
code='''package t.mma.dexkit;
import android.content.SharedPreferences;import java.util.*;import java.lang.reflect.*;
public class SnapshotTest {
 public static void main(String[] args){Map<String,Object> stored=new HashMap<>();int[] writes={0};
 SharedPreferences prefs=(SharedPreferences)Proxy.newProxyInstance(SnapshotTest.class.getClassLoader(),new Class[]{SharedPreferences.class},(p,m,a)->{
 if(m.getName().equals("getAll"))return new HashMap<>(stored);
 if(m.getName().equals("edit")){writes[0]++;Map<String,Object> next=new HashMap<>(stored);
 return Proxy.newProxyInstance(SnapshotTest.class.getClassLoader(),new Class[]{SharedPreferences.Editor.class},(e,n,b)->{
 String k=n.getName();if(k.equals("clear")){next.clear();return e;}if(k.startsWith("put")){next.put((String)b[0],b[1]);return e;}if(k.equals("remove")){next.remove(b[0]);return e;}
 if(k.equals("apply")||k.equals("commit")){stored.clear();stored.putAll(next);return k.equals("commit")?true:null;}return null;});}return null;});
 CacheSnapshotEditor first=new CacheSnapshotEditor(prefs);first.putString("runtime","a").putBoolean("complete",true).apply();if(writes[0]!=1)throw new AssertionError();
 new CacheSnapshotEditor(prefs).putString("runtime","a").putBoolean("complete",true).apply();if(writes[0]!=1)throw new AssertionError();
 new CacheSnapshotEditor(prefs).putString("runtime","b").putBoolean("complete",true).apply();if(writes[0]!=2||!stored.get("runtime").equals("b"))throw new AssertionError();
 new CacheSnapshotEditor(prefs).putString("runtime","b").commit();if(writes[0]!=3||stored.containsKey("complete"))throw new AssertionError();
 System.out.println("PASS unchanged snapshot skips editor; runtime change persists; stale keys removed; commit preserved");}}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'SnapshotTest.java').write_text(code);sdk='/usr/lib/android-sdk/platforms/android-36/android.jar'
 subprocess.run(['javac','-cp',sdk,'-d',td,str(r/'app/src/main/java/t/mma/dexkit/CacheSnapshotEditor.java'),str(p/'SnapshotTest.java')],check=True)
 subprocess.run(['java','-cp',td+':'+sdk,'t.mma.dexkit.SnapshotTest'],check=True)
