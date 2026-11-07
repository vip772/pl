"""Exercise production binding state and JSON persistence using JVM preferences fixtures."""
from pathlib import Path
import subprocess, tempfile
r = Path(__file__).resolve().parents[3]
libs = list(Path('/tmp/pl-kotlin-libs').glob('*.jar'))
android = Path('/usr/lib/android-sdk/platforms/android-36/android.jar')
# Use a real JVM JSON implementation; Android's SDK JSON methods are stubs.
json_jars = [Path('/tmp/join-json-20240303.jar')]
if not json_jars[0].is_file():
    json_jars = list(Path('/root/.gradle/caches').rglob('json-*.jar'))
assert json_jars, 'JVM JSON library unavailable'
with tempfile.TemporaryDirectory(prefix='join-bindings-') as td:
    p = Path(td)
    (p/'Context.kt').write_text('package android.content\nopen class Context {val applicationContext:Context get()=this}\n')
    (p/'Prefs.kt').write_text('''package t.mma.preferences
object HchatStorage {
 val data=hashMapOf<String,String>()
 fun runtimePreferences(c:android.content.Context,n:String)=Prefs()
}
class Prefs {
 fun contains(k:String)=HchatStorage.data.containsKey(k)
 fun getString(k:String,d:String?):String?=HchatStorage.data[k]?:d
 fun edit()=Editor()
}
class Editor {
 var key="";var value=""
 fun putString(k:String,v:String):Editor {key=k;value=v;return this}
 fun commit():Boolean {HchatStorage.data[key]=value;return true}
}
''')
    (p/'Test.kt').write_text('''package t.mma.hooks.items.profileid
fun main() {
 val store=JoinTimeBindingStore(android.content.Context())
 val s=store.read("a","g")
 s.reconcile(setOf("one","two"),100)
 s.merge(mapOf("one" to 50L,"two" to 60L)){"history"}
 store.write("a","g",s)
 check(store.read("a","g").bindings["one"]?.time==50L)
 check(store.read("b","g").bindings.isEmpty())
 check(store.read("a","other").bindings.isEmpty())
 s.reconcile(emptySet(),200)
 check(s.bindings.size==2)
 s.reconcile(setOf("two"),200)
 check("one" !in s.bindings && s.floors["one"]==200L)
 store.write("a","g",s)
 val reloaded=store.read("a","g")
 reloaded.reconcile(setOf("one","two"),300)
 reloaded.merge(mapOf("one" to 50L)){"history"}
 check("one" !in reloaded.bindings)
 reloaded.merge(mapOf("one" to 250L)){"history"}
 check(reloaded.bindings["one"]?.time==250L)
 reloaded.merge(mapOf("one" to 240L)){"history"}
 check(reloaded.bindings["one"]?.time==250L)
 reloaded.merge(mapOf("one" to 245L)){"server"}
 check(reloaded.bindings["one"]?.source=="server" && reloaded.bindings["one"]?.time==245L)
 reloaded.merge(mapOf("one" to 0L,"stranger" to 999L)){"native"}
 check(reloaded.bindings["one"]?.time==245L && "stranger" !in reloaded.bindings)
 val created=JoinTimeBindingStore.Group()
 created.recordCreation("self",setOf("A","B","C","D"))
 created.bindInitialMembers(0)
 check(created.bindings.isEmpty())
 created.merge(mapOf("self" to 100L,"A" to 75L)){"history"}
 created.bindInitialMembers(100)
 check(listOf("A","B","C","D").all{created.bindings[it]?.time==100L})
 created.reconcile(setOf("self","A","B","C","D","E"),150)
 created.bindInitialMembers(100)
 check("E" !in created.bindings)
 store.write("self","new",created)
 val restored=store.read("self","new")
 check(restored.creator=="self" && restored.initialMembers==setOf("A","B","C","D"))
 restored.reconcile(setOf("self","B","C","D","E"),200)
 restored.reconcile(setOf("self","A","B","C","D","E"),250)
 restored.bindInitialMembers(100)
 check("A" !in restored.bindings && "A" !in restored.initialMembers)
 restored.merge(mapOf("A" to 230L)){"server"}
 restored.bindInitialMembers(100)
 check(restored.bindings["A"]?.time==230L)
 val old=JoinTimeBindingStore.Group()
 old.reconcile(setOf("self","A"),100)
 old.recordCreation("self",setOf("A"))
 old.bindInitialMembers(100)
 check(old.bindings.isEmpty())
 restored.reconcile(setOf("A","B","C","D","E"),300)
 check(restored.initialMembers.isEmpty())
 println("PASS initial creation persistence, missing-time guard, initial-only binding, old-group guard, leave/rejoin and creator departure")
 println("PASS production binding persistence/account/group isolation, empty-list guard, leave/rejoin floor and source upgrade")
}
''')
    cp=':'.join(map(str,[*json_jars,android,*libs]))
    subprocess.run(['java','-Xmx384m','-cp',':'.join(map(str,libs)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-jvm-target','17','-nowarn','-no-stdlib','-no-reflect','-classpath',cp,'-d',str(p/'out'),str(r/'app/src/main/java/t/mma/hooks/items/profileid/JoinTimeBindingStore.kt'),*map(str,p.glob('*.kt'))],check=True,timeout=55)
    subprocess.run(['java','-cp',str(p/'out')+':'+cp,'t.mma.hooks.items.profileid.TestKt'],check=True,timeout=15)
