"""Production revoke matcher regression; JVM fixture, not device acceptance."""
from pathlib import Path
import subprocess
import tempfile
r = Path(__file__).resolve().parents[1]
s = (r / 'app/src/main/java/t/mma/dexkit/DexFinder.java').read_text()
a = s.index('    private Constructor<?> findRevokeMsgCtor(')
b = s.index('    private Constructor<?> findUploadDeviceStepCtor', a)
body = s[a:b]
assert 'localMessageClass ==' not in body
with tempfile.TemporaryDirectory() as td:
    p = Path(td)
    files = {
        'com/tencent/mm/storage/Message.java': 'package com.tencent.mm.storage; class Base { public long field_msgId; public int field_isSend; } public class Message extends Base {}',
        'com/tencent/mm/storage/Wrong.java': 'package com.tencent.mm.storage; public class Wrong { public int field_msgId; public int field_isSend; }',
        'KavaReflector.java': 'import java.lang.reflect.*; import java.util.*; class KavaReflector {static List<Constructor<?>> declaredConstructors(Class<?> c){return Arrays.asList(c.getDeclaredConstructors());} static Field findFieldRecursive(Class<?> c,String n){while(c!=null){try{return c.getDeclaredField(n);}catch(NoSuchFieldException e){c=c.getSuperclass();}}return null;} static <T extends AccessibleObject>T accessible(T a){a.setAccessible(true);return a;}}',
        'MatcherTest.java': 'import java.lang.reflect.*; class MatcherTest {' + body + '''
 static class Scene {Scene(com.tencent.mm.storage.Message m,String a,String b){}}
 static class Wrong {Wrong(com.tencent.mm.storage.Wrong m,String a,String b){}}
 static class Decoy {Decoy(Object m,String a,String b){}}
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] a)throws Exception {
  MatcherTest t=new MatcherTest();Constructor<?> c=t.findRevokeMsgCtor(Scene.class);
  check(c!=null);check(c.newInstance(new com.tencent.mm.storage.Message(),"revoke","") instanceof Scene);
  check(t.findRevokeMsgCtor(null)==null);check(t.findRevokeMsgCtor(Wrong.class)==null);check(t.findRevokeMsgCtor(Decoy.class)==null);
  System.out.println("PASS production revoke matcher: independent of local insert API, inherited fields, exact types, rejects invalid scene; JVM only");
 }}'''
    }
    for n, v in files.items():
        f = p / n
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(v)
    subprocess.run(['javac', '-d', td, *map(str, p.rglob('*.java'))], check=True, timeout=30)
    subprocess.run(['java', '-cp', td, 'MatcherTest'], check=True, timeout=20)
