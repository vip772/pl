"""Run actual memory callback: registration, pressure gates, coalescing and failures."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
files={
'Android.kt':'''package android.content
import android.content.res.Configuration
interface ComponentCallbacks2 {fun onTrimMemory(level:Int);fun onLowMemory();fun onConfigurationChanged(newConfig:Configuration)
 companion object {const val TRIM_MEMORY_UI_HIDDEN=20;const val TRIM_MEMORY_RUNNING_LOW=10}}
open class Context {var registrations=0;val applicationContext:Context? get()=this;fun registerComponentCallbacks(c:ComponentCallbacks2){registrations++}}
''',
'Config.kt':'package android.content.res\nclass Configuration',
'Background.kt':'''package t.mma.hooks.items.backgroundbeauty
import java.util.concurrent.*
object BackgroundBeautyStore {
 var entered=CountDownLatch(1);var release=CountDownLatch(1);var calls=0;var fail=false
 fun trimMemory(){calls++;entered.countDown();check(release.await(3,TimeUnit.SECONDS));if(fail)error("decode lock failure")}
}
''',
'Avatar.kt':'package t.mma.ui.miuix\nobject AvatarMemoryCache {var calls=0;fun trimMemory(){calls++}}',
'Log.kt':'package t.mma.utils\nobject HLog {var errors=0;fun e(s:String,t:Throwable){errors++}}',
'Test.kt':'''import t.mma.hooks.core.ResourceMemoryCallbacks as C
import t.mma.hooks.items.backgroundbeauty.BackgroundBeautyStore as B
import t.mma.ui.miuix.AvatarMemoryCache as A
import t.mma.utils.HLog
import android.content.Context
import java.util.concurrent.*
fun worker():ThreadPoolExecutor {val f=C.javaClass.getDeclaredField("cleaner");f.isAccessible=true;return f.get(C) as ThreadPoolExecutor}
fun flush(){worker().submit{}.get(3,TimeUnit.SECONDS)}
fun main(){
 val context=Context();C.install(context);C.install(context);check(context.registrations==1)
 C.onTrimMemory(5);C.onTrimMemory(20);check(worker().poolSize==0)
 C.onTrimMemory(10);check(B.entered.await(3,TimeUnit.SECONDS))
 repeat(100){C.onLowMemory()};check(worker().queue.isEmpty())
 B.release.countDown();flush();check(B.calls==1 && A.calls==1)
 B.fail=true;C.onLowMemory();flush();check(HLog.errors==1)
 B.fail=false;C.onLowMemory();flush();check(B.calls==3 && A.calls==2)
 worker().shutdownNow()
 println("PASS production memory pressure callback: one registration, UI-hidden/moderate gates, asynchronous coalescing, error reset, retry")
}
'''}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for name,content in files.items():(p/name).write_text(content)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),*map(str,p.glob('*.kt')),str(r/'app/src/main/java/t/mma/hooks/core/ResourceMemoryCallbacks.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=15)