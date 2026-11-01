"""Execute production registry with synthetic Xposed handles; no host hooks."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
fixture='''package de.robv.android.xposed
open class XC_MethodHook { class Unhook(val action:()->Unit = {}) {fun unhook()=action()} }
object XposedBridge { fun hookMethod(m:java.lang.reflect.Member,c:XC_MethodHook)=XC_MethodHook.Unhook() }
'''
test='''import t.mma.hooks.core.HookRegistry
import de.robv.android.xposed.XC_MethodHook.Unhook
fun main() {
 val registry=HookRegistry.get();val bad=Unhook{error("native")};registry.add(bad);registry.add(bad)
 check(runCatching{registry.unhook(bad)}.isFailure);check(registry.size()==0)
 val late=Unhook();val first=Unhook{registry.add(late)};registry.add(first);registry.add(bad)
 registry.unhookAll();check(registry.size()==1);registry.unhook(late);check(registry.size()==0)
 registry.unhook(null);registry.unhookAll();check(registry.size()==0)
 val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
 val active=Unhook{entered.countDown();release.await()};registry.add(active)
 val thread=Thread{registry.unhook(active)};thread.start();check(entered.await(3,java.util.concurrent.TimeUnit.SECONDS))
 registry.add(late);release.countDown();thread.join(3000);check(!thread.isAlive);check(registry.size()==1);registry.unhookAll()
 println("PASS production HookRegistry: throwing handle, duplicate identity, concurrent additions and bulk isolation")
}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Xposed.kt').write_text(fixture);(p/'Test.kt').write_text(test)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-nowarn','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Xposed.kt'),str(p/'Test.kt'),str(r/'app/src/main/java/t/mma/hooks/core/HookRegistry.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(std),'TestKt'],check=True,timeout=20)
