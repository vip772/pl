"""Compile complete automatic-cache feature and exercise per-install ownership without native/network."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
sdk=Path('/usr/lib/android-sdk/platforms/android-36/android.jar')
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 stubs={
 'Core.kt':'''package t.mma.hooks.core
class FeatureContext{fun hostContext():android.content.Context=error("unused")}
open class BaseFeature {private val scope=FeatureResourceScope{};open fun featureId()="";open fun name()="";open fun onFeatureInit(c:FeatureContext){};open fun onFeatureInstall(c:FeatureContext){};fun registerSettingsProvider(p:Any){};fun trackResource(r:()->Unit){scope.register(r)};fun subscriptionRegistrar():(Any)->Unit={s->scope.register{(s as t.mma.hooks.api.core.Subscription).unsubscribe()}}}
''',
 'Log.kt':'''package t.mma.utils
object HLog {fun d(s:String){};fun i(s:String){};fun w(s:String){};fun e(s:String,t:Throwable?=null){}}
''',
 'Handler.kt':'''package android.os
class Looper {companion object{fun getMainLooper()=Looper()}}
class Handler(l:Looper){fun removeCallbacks(r:Runnable){};fun postDelayed(r:Runnable,d:Long)=true}
''',
 'UI.kt':'''package t.mma.ui
interface FeatureSettingsProvider{companion object{const val CATEGORY_ENHANCE="enhance"}}
open class SimpleFeatureSettingsProvider(a:String,b:String,c:String,d:String,e:Boolean):FeatureSettingsProvider
''',
 'Storage.kt':'''package t.mma.preferences
object HchatStorage {fun preferences(c:android.content.Context,n:String):android.content.SharedPreferences=error("unused")}
''',
 'Message.kt':'''package t.mma.hooks.api.model
class FileMsg(val ext:String="pdf")
class WeChatMessage(val msgSvrId:Long=1,val msgId:Long=1,val talker:String="fixture",val type:Int=3){fun isImage()=type==3;fun isVideo()=type==43;fun isFile()=type==49;fun isIncoming()=true;fun getFileMsg()=FileMsg()}
''',
 'API.kt':'''package t.mma.hooks.api.core
import t.mma.hooks.api.model.WeChatMessage
class Subscription {fun unsubscribe(){}}
class Event {val outgoing=false;val storedMessage:WeChatMessage?=null;val message:WeChatMessage?=null}
class Observer{val isAvailable=true;fun install(){};fun subscribe(f:(Event)->Unit)=Subscription()}
class Store {fun getMessageById(id:Long):WeChatMessage?=null;fun getMessageBySvrId(t:String,id:Long):WeChatMessage?=null}
class MessageApi {fun observe():Observer?=null;fun store():Store?=null}
class Account {fun selfWxId()="fixture"}
object WeChatApis {fun message()=MessageApi();fun account():Account?=Account()}
''',
 'Native.kt':'''package t.mma.hooks.items.autocache
class NativeMediaCache(c:t.mma.hooks.core.FeatureContext){fun install()=false;fun complete(m:t.mma.hooks.api.model.WeChatMessage)=false;fun submit(m:t.mma.hooks.api.model.WeChatMessage)=false}
''',
 'Test.kt':'''package t.mma.hooks.items.autocache
import t.mma.hooks.core.*
import java.util.concurrent.*
fun field(o:Any,n:String):java.lang.reflect.Field=o.javaClass.getDeclaredField(n).apply{isAccessible=true}
fun main(){
 val c=FeatureContext()
 repeat(30){
  val runtime=AutoCacheRuntime(c){}
  val worker=field(runtime,"worker").get(runtime) as ScheduledExecutorService
  worker.schedule({error("closed task executed")},1,TimeUnit.DAYS)
  val pending=field(runtime,"pending").get(runtime) as MutableSet<String>
  val submitted=field(runtime,"lastSubmitted").get(runtime) as MutableMap<String,Long>
  pending.add("old");submitted["old"]=1L
  runtime.shutdown();runtime.shutdown();runtime.start()
  check(worker.isShutdown && worker.awaitTermination(3,TimeUnit.SECONDS));check(pending.isEmpty()&&submitted.isEmpty())
  val ticket=field(runtime,"installation").get(runtime)
  val f=ticket.javaClass.getDeclaredField("closed").apply{isAccessible=true}
  check(f.getBoolean(ticket))
 }
 val old=AutoCacheRuntime(c){};old.shutdown();val fresh=AutoCacheRuntime(c){}
 check(!(field(fresh,"worker").get(fresh) as ScheduledExecutorService).isShutdown)
 fresh.shutdown()
 println("PASS full automatic-cache feature compile; per-install fresh workers, delayed-task cancellation, credential close, maps cleared, idempotence and closed start")
}
'''
 }
 for n,s in stubs.items():(p/n).write_text(s)
 source=r/'app/src/main/java/t/mma/hooks/items/autocache/AutoCacheFeature.kt'
 s=source.read_text();assert 'subscriptionRegistrar()' in s and 'registerSubscription(observer.subscribe' in s
 assert s.index('trackResource { runtime.shutdown() }')<s.index('runtime.start()')
 assert 'catch (error: java.util.concurrent.RejectedExecutionException)' in s
 sources=[source,r/'app/src/main/java/t/mma/hooks/core/DexInstallScheduler.kt',r/'app/src/main/java/t/mma/hooks/core/FeatureResourceScope.kt']
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',f'{std}:{sdk}','-d',str(p/'test.jar'),*map(str,p.glob('*.kt')),*map(str,sources)],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}:{sdk}','t.mma.hooks.items.autocache.TestKt'],check=True,timeout=15)