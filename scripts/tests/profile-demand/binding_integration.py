"""Production provider + production store; simulated host membership/database APIs."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[3]
ns={"__file__":str(Path(__file__).with_name('run.py'))}
source=Path(__file__).with_name('run.py').read_text().split('with tempfile.TemporaryDirectory() as d:')[0]
exec(source,ns)
sources=ns['sources']
sources.pop('Bindings.kt')
sources['Creation.kt']='''package t.mma.hooks.items.profileid
class InitialGroupJoinObserver {
 fun start(l:ClassLoader?,f:(String,String,Set<String>)->Unit){callback=f}
 fun close(){callback=null}
 companion object {var callback:((String,String,Set<String>)->Unit)?=null}
}'''

sources['Context.kt']='package android.content\nopen class Context {val applicationContext:Context get()=this}'
sources['Activity.kt']=sources['Activity.kt'].replace('open class Activity {','open class Activity:android.content.Context() {')
sources['Prefs.kt']='''package t.mma.preferences
object HchatStorage {
 val data=java.util.concurrent.ConcurrentHashMap<String,String>()
 fun preferences(a:android.app.Activity,n:String)=Prefs()
 fun runtimePreferences(a:android.content.Context,n:String)=Prefs()
}
class Prefs {
 fun getBoolean(k:String,d:Boolean)=true
 fun getString(k:String,d:String?):String?=HchatStorage.data[k]?:d
 fun contains(k:String)=HchatStorage.data.containsKey(k)
 fun edit()=Editor()
}
class Editor {
 var k="";var v=""
 fun putString(key:String,value:String):Editor {k=key;v=value;return this}
 fun commit():Boolean {HchatStorage.data[k]=v;return true}
}
'''
sources['Changes.kt']='''package t.mma.hooks.api.contact
class WeChatChatroomChangeApi {
 var callback:((Change)->Unit)?=null
 class Subscription(val close:()->Unit) {var isActive=true;fun unsubscribe(){isActive=false;close()}}
 class Room(val memberIds:List<String>)
 class Change(val chatroom:Room) {fun mayMemberListChanged()=true;fun chatroomId()="group@chatroom"}
 fun install(){}
 fun subscribe(f:(Change)->Unit):Subscription {callback=f;return Subscription{callback=null}}
 fun emit(m:List<String>){callback?.invoke(Change(Room(m)))}
}
'''
sources['Apis.kt']='''package t.mma.hooks.api.core
object WeChatApis {
 val api=Rooms();val changes=t.mma.hooks.api.contact.WeChatChatroomChangeApi()
 fun chatrooms()=api;fun chatroomChanges()=changes
}
class Rooms {
 @Volatile var members=listOf("member","other")
 @Volatile var calls=0
 @Volatile var result:Map<String,Long> = mapOf("member" to 1700000000000L)
 class Room(val memberIds:List<String>)
 fun getChatroom(g:String)=Room(members)
 @Volatile var native:Map<String,Long> = emptyMap()
 fun getNativeMemberJoinTimes(g:String):Map<String,Long> = native
 fun getMemberJoinTimes(g:String)=getMemberJoinTimes(g,emptyMap(),emptyMap())
 fun getMemberJoinTimes(g:String,b:Map<String,Long>,f:Map<String,Long>):Map<String,Long>{calls++;return result.filter{it.value>(f[it.key]?:0)}}
 fun memberJoinTimeSource(m:Map<String,Long>,i:String)="history"
 fun isJoinTimeResultComplete(m:Map<String,Long>)=true
 fun logPersistedJoinTimes(g:String,e:Long,h:String){}
 fun getMemberInviter(g:String,m:String)=""
 fun getMemberDisplayName(g:String,m:String)=""
}
'''
sources['Test.kt']='''import t.mma.hooks.items.profileid.ProfileGroupInfoFeature as Feature
import t.mma.hooks.api.core.WeChatApis as Api
import t.mma.hooks.api.contact.WeContactHeaderApi as Header
fun waitFor(f:()->Boolean){val end=System.nanoTime()+3_000_000_000;while(!f()){check(System.nanoTime()<end);Thread.sleep(10)}}
fun main(){
 val a=android.app.Activity()
 fun start(){Feature.installProvider();Feature.startBindings(a)}
 start()
 Api.api.members=listOf("fixture-account","member","other")
 Api.api.native=mapOf("fixture-account" to 1700000000000L)
 t.mma.hooks.items.profileid.InitialGroupJoinObserver.callback!!.invoke("fixture-account","group@chatroom",setOf("member","other"))
 waitFor{t.mma.preferences.HchatStorage.data.isNotEmpty()}
 val created=t.mma.hooks.items.profileid.JoinTimeBindingStore(a).read("fixture-account","group@chatroom")
 check(created.bindings["member"]?.time==1700000000000L && created.bindings["member"]?.source=="creation")
 check(created.bindings["other"]?.time==1700000000000L)
 var provider=Header.providers[0]
 provider.getHeaderText(a)
 waitFor{provider.getHeaderText(a)!=null}
 check(Api.api.calls==0){"creation binding scanned history"}
 Feature.removeProvider()
 check(t.mma.hooks.items.profileid.InitialGroupJoinObserver.callback==null)
 t.mma.preferences.HchatStorage.data.clear()
 Api.api.native=emptyMap()
 Api.api.members=listOf("member","other")
 start();provider=Header.providers[0]
 provider.getHeaderText(a)
 waitFor{provider.getHeaderText(a)!=null}
 check(Api.api.calls==1)
 Feature.removeProvider()
 Api.api.result=emptyMap()
 start();provider=Header.providers[0]
 provider.getHeaderText(a)
 waitFor{provider.getHeaderText(a)!=null}
 check(Api.api.calls==1){"persisted binding rescanned history"}
 // Persisted group remains monitored after restart, before another profile has been opened.
 Api.api.members=listOf("other")
 Api.changes.emit(Api.api.members)
 waitFor{t.mma.preferences.HchatStorage.data.values.any{it.contains("floors") && !it.contains("1700000000000")}}
 check(provider.getHeaderText(a)==null)
 Api.api.members=listOf("member","other")
 Api.changes.emit(Api.api.members)
 Thread.sleep(60)
 Api.api.result=mapOf("member" to 1700000000000L)
 android.os.SystemClock.now=20_000
 provider.getHeaderText(a)
 Thread.sleep(100)
 check(provider.getHeaderText(a)==null){"old membership time revived"}
 Api.api.result=mapOf("member" to System.currentTimeMillis()+2000)
 android.os.SystemClock.now=40_000
 provider.getHeaderText(a)
 waitFor{provider.getHeaderText(a)!=null}
 Feature.removeProvider()
 check(Api.changes.callback==null)
 println("PASS production creation/provider/store integration: initial-member binding from native creator time, no history scan and shutdown")
 println("PASS production provider/store integration: restart without history, departure cleanup, stale-time rejection, rejoin binding and unsubscribe")
}
'''
libs=ns['jars'];stdlib=ns['stdlib'];json=Path('/tmp/join-json-20240303.jar')
assert json.is_file(),'Run bindings.py prerequisites first'
with tempfile.TemporaryDirectory(prefix='join-binding-integration-') as td:
 p=Path(td);files=[]
 for name,text in sources.items():
  f=p/name;f.write_text(text);files.append(str(f))
 cp=str(json)+':'+str(stdlib)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',cp,'-d',str(p/'out'),*files,str(r/'app/src/main/java/t/mma/hooks/items/profileid/ProfileGroupInfoFeature.kt'),str(r/'app/src/main/java/t/mma/hooks/items/profileid/JoinTimeBindingStore.kt')],check=True,timeout=55)
 subprocess.run(['java','-cp',str(p/'out')+':'+cp,'TestKt'],check=True,timeout=15)
