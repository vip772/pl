"""Execute production HomeExtension destroy with failing resource and main-thread stubs."""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/homeextension/HomeExtensionRuntime.kt').read_text()
a=s.index('    private fun releaseSafely(');b=s.index('    private fun requestAvatar',a)
code='''object Looper {var main=true;fun myLooper()=if(main)1 else 0;fun getMainLooper()=1}
object HLog {var errors=0;fun e(s:String,t:Throwable){errors++;if(errors==1)error("logger failure")}}
class Handler {val jobs=mutableListOf<()->Unit>();var removes=0;fun post(f:()->Unit){jobs+=f};fun removeCallbacksAndMessages(x:Any?){jobs.clear();removes++};fun drain(){val copy=jobs.toList();jobs.clear();copy.forEach{it()}}}
class Subscription {var calls=0;fun unsubscribe(){calls++;error("unsubscribe failure")}}
class Prefs {var calls=0;fun unregisterOnSharedPreferenceChangeListener(x:Any){calls++;error("preference failure")}}
object HookRegistry {val removed=mutableListOf<Int>();fun get()=this;fun unhook(h:Int){removed+=h;if(h==1)error("unhook failure")}}
class ListView {var calls=0;fun removeHeaderView(x:Any){calls++;if(calls==1)error("header failure")}}
class Session(val fail:Boolean) {val root=Any();var calls=0;fun dispose(){calls++;if(fail)error("session failure")}}
class Worker {val queue=mutableListOf(1,2);var stopped=false;fun shutdownNow(){stopped=true}}
class Cache {var evicted=false;fun evictAll(){evicted=true}}
class Runtime {
 val main=Handler();var destroyed=false;var changeSubscription:Subscription?=Subscription();val prefs=Prefs();val listener=Any();val hooks=mutableListOf(1,2);val guardedAdapters=mutableSetOf(1,2)
 val first=ListView();val second=ListView();val firstSession=Session(true);val secondSession=Session(false);val sessions=linkedMapOf(first to firstSession,second to secondSession)
 val worker=Worker();val avatarWorker=Worker();val pendingAttach=mutableSetOf(1);val avatarRequests=mutableMapOf("a" to 1);val avatars=Cache()
'''+s[a:b]+'''
}
fun main(){val r=Runtime();val subscription=r.changeSubscription!!
 Looper.main=false;r.destroy();check(!r.destroyed&&r.main.jobs.size==1);Looper.main=true;r.main.drain()
 check(r.destroyed&&r.changeSubscription==null&&subscription.calls==1&&r.prefs.calls==1)
 check(HookRegistry.removed==listOf(1,2)&&r.hooks.isEmpty()&&r.guardedAdapters.isEmpty())
 check(r.firstSession.calls==1&&r.secondSession.calls==1&&r.first.calls==1&&r.second.calls==1&&r.sessions.isEmpty())
 check(r.worker.stopped&&r.avatarWorker.stopped&&r.worker.queue.isEmpty()&&r.avatarWorker.queue.isEmpty())
 check(r.pendingAttach.isEmpty()&&r.avatarRequests.isEmpty()&&r.avatars.evicted&&r.main.jobs.isEmpty())
 r.destroy();check(subscription.calls==1&&r.prefs.calls==1&&r.main.removes==1)
 println("PASS actual destroy main dispatch/idempotence and subscription/preference/hook/session/logger failure isolation")
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=15)
