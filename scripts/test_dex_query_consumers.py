"""Compile production Java/Kotlin consumers against real DexKit ABI; no Gradle/native load."""
from pathlib import Path
import subprocess, tempfile
r = Path(__file__).resolve().parents[1]
libs = Path('/tmp/pl-kotlin-libs')
std = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
dex = Path('/tmp/pl-dexkit.jar')
compiler = ['java', '-Xmx256m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
            'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-nowarn', '-no-stdlib', '-no-reflect']
core = r / 'app/src/main/java/t/mma/hooks/core/DexInstallScheduler.kt'
holder = r / 'app/src/main/java/t/mma/dexkit/DexBridgeHolder.java'
script = r / 'app/src/main/java/t/mma/hooks/items/script/ScriptDexKitBridge.kt'
with tempfile.TemporaryDirectory() as td:
 p = Path(td)
 classes = p / 'classes'; classes.mkdir()
 (p/'Log.kt').write_text('''package t.mma.utils
object HLog {@JvmStatic fun d(s:String){};@JvmStatic fun i(s:String){};@JvmStatic fun w(s:String){};@JvmStatic fun e(s:String,t:Throwable?=null){}}
object KavaReflector {fun loadClass(n:String,l:ClassLoader):Class<*>?=runCatching{l.loadClass(n)}.getOrNull();fun declaredMethods(c:Class<*>)=c.declaredMethods;fun declaredConstructors(c:Class<*>)=c.declaredConstructors}
''')
 (p/'Handler.kt').write_text('''package android.os
class Looper {companion object {fun getMainLooper()=Looper()}}
class Handler(l:Looper){fun removeCallbacks(r:Runnable){};fun postDelayed(r:Runnable,d:Long)=true}
''')
 cp = f'{classes}:{std}:{dex}'
 subprocess.run(compiler+['-classpath',cp,'-d',str(classes),str(core),str(p/'Log.kt'),str(p/'Handler.kt')],check=True,timeout=45)
 (p/'DexFinder.java').write_text('package t.mma.dexkit; public class DexFinder {}')
 subprocess.run(['javac','-cp',cp,'-d',str(classes),str(p/'DexFinder.java'),str(holder)],check=True,timeout=20)
 (p/'Test.kt').write_text('''package t.mma.hooks.items.script
import t.mma.hooks.core.DexInstallScheduler as D
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
fun main(){
 val bridge=ScriptDexKitBridge(null,null,ClassLoader.getSystemClassLoader())
 check(bridge.bridge()==null && bridge.holder()==null)
 check(bridge.findClassList(listOf("anchor")).isEmpty())
 check(bridge.findMemberList(listOf("anchor")).isEmpty())
 check(bridge.findClassList(emptyList()).isEmpty())
 val active=AtomicInteger();val max=AtomicInteger();val pool=Executors.newFixedThreadPool(6)
 val jobs=(0 until 120).map{index->pool.submit{
  val task={val n=active.incrementAndGet();max.updateAndGet{maxOf(n,it)};Thread.yield();active.decrementAndGet();42}
  if(index%2==0) D.queryDexKit{task()} else D.queryDexKitTask(Callable{task()})
 }}
 jobs.forEach{it.get(5,TimeUnit.SECONDS)};pool.shutdown();check(max.get()==1)
 check(D.queryDexKit{D.queryDexKitTask(Callable{D.queryDexKit{42}})}==42)
 try{D.queryDexKitTask(Callable<Int>{error("expected")})}catch(_:IllegalStateException){}
 check(D.queryDexKit{43}==43)
 println("PASS Dex consumers: real DexKit Java/Kotlin ABI, empty/unavailable bridge, shared Java/Kotlin concurrency, nesting, exception recovery")
}
''')
 subprocess.run(compiler+['-classpath',cp,'-d',str(p/'test.jar'),str(script),str(p/'Test.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{cp}','t.mma.hooks.items.script.TestKt'],check=True,timeout=15)
# Ensure the entire helper and native-backed conversion remain inside the gate.
s = script.read_text()
for method, collector in [('findClassList','collectClassMatches'),('findMemberList','collectMemberMatches')]:
 block=s[s.index('    fun '+method+'(usingStrings: List'):]
 block=block[:block.index('    fun '+method+'(vararg')]
 assert block.index('queryDexKit {') < block.index(collector+'(') < block.index('ArrayList(out)') < block.index('}.onFailure')
s=holder.read_text()
for name in ['findClassesByStrings','findMethodsByStrings']:
 assert f'queryDexKitTask(() -> {name}Locked(anchorStrings))' in s
