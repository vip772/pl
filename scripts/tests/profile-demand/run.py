from pathlib import Path
import subprocess,tempfile
repo=Path(__file__).resolve().parents[3]
lib=Path('/tmp/pl-kotlin-libs');jars=list(lib.glob('*.jar'));stdlib=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
sources={
'Creation.kt':'''package t.mma.hooks.items.profileid
class InitialGroupJoinObserver {fun start(l:ClassLoader?,f:(String,String,Set<String>)->Unit){};fun close(){}}
''',
'Bindings.kt':'''package t.mma.hooks.items.profileid
class JoinTimeBindingStore(c:android.content.Context) {class Binding(val time:Long,val source:String="history"); class Group {val creator="";val initialMembers=emptySet<String>();fun recordCreation(a:String,s:Set<String>){};fun bindInitialMembers(t:Long){};val bindings=emptyMap<String,Binding>();val floors=emptyMap<String,Long>();fun reconcile(m:Set<String>,t:Long){};fun merge(m:Map<String,Long>,s:(String)->String){}};fun contains(a:String,g:String)=false;fun read(a:String,g:String)=Group();fun write(a:String,g:String,s:Group){}}''',
'Context.kt':'''package android.content
open class Context''',
'Changes.kt':'''package t.mma.hooks.api.contact
class WeChatChatroomChangeApi {class Subscription {val isActive=true;fun unsubscribe(){}};class Change {val chatroom:Room?=null;fun mayMemberListChanged()=false;fun chatroomId()=""};class Room {val memberIds=emptyList<String>()};fun install(){};fun subscribe(f:(Change)->Unit)=Subscription()}''',
'Recovery.kt':'''package t.mma.hooks.items.profileid
object JoinTimeRecovery {@Volatile var account="fixture-account";fun accountKey(l:ClassLoader?)=account;fun read(l:ClassLoader?,g:String):Map<String,Long> = emptyMap()}''',
 'Clock.kt':'''package android.os
object SystemClock { @Volatile var now=0L;fun elapsedRealtime()=now }''',
'Activity.kt':'''package android.app
open class Activity {val intent=Intent()}
class Intent {fun getStringExtra(k:String):String?= when(k){"Contact_ChatRoomId"->"group@chatroom";"Contact_User"->"member";else->null}}
''',
'Log.kt':'''package t.mma.utils
object HLog {fun d(s:String){};fun e(s:String,t:Throwable){throw AssertionError(s,t)}}''',
'Prefs.kt':'''package t.mma.preferences
object HchatStorage {fun preferences(a:android.app.Activity,n:String)=Prefs()}
class Prefs {fun getBoolean(k:String,d:Boolean)=enabled;fun getString(k:String,d:String)=d;companion object {@Volatile var enabled=true}}
''',
'Settings.kt':'''package t.mma.hooks.items.profileid
object ProfileGroupJoinTimeSettings {const val PREFS_NAME="join";const val KEY_ENABLE="enabled";const val DEFAULT_ENABLE=true;const val KEY_COLOR="color";const val DEFAULT_COLOR=""}
object ProfileGroupInviterSettings {const val PREFS_NAME="invite";const val KEY_ENABLE="enabled";const val DEFAULT_ENABLE=true;const val KEY_COLOR="color";const val DEFAULT_COLOR=""}
''',
'Header.kt':'''package t.mma.hooks.api.contact
object WeContactHeaderApi {
 interface Provider {fun getHeaderText(a:android.app.Activity):String?;fun getHeaderColorSpec(a:android.app.Activity):String}
 val providers=mutableListOf<Provider>();@Volatile var refreshes=0
 fun addProvider(p:Provider){providers.add(p)}
 fun removeProvider(p:Provider){providers.remove(p)}
 fun refreshCurrentProfile(g:String,m:String){refreshes++}
 fun refreshCurrentGroupProfile(g:String){refreshes++}
}
''',
'Apis.kt':'''package t.mma.hooks.api.core
object WeChatApis {val api=Rooms();fun chatrooms()=api;fun chatroomChanges():t.mma.hooks.api.contact.WeChatChatroomChangeApi?=null}
class Rooms {
 class Room {val memberIds=emptyList<String>()}
 fun getChatroom(g:String):Room?=null
 fun getNativeMemberJoinTimes(g:String):Map<String,Long> = emptyMap()
 fun getMemberJoinTimes(g:String,b:Map<String,Long>,f:Map<String,Long>)=getMemberJoinTimes(g)
 fun memberJoinTimeSource(m:Map<String,Long>,i:String)="history"

 @Volatile var calls=0
 @Volatile var result:Map<String,Long> = emptyMap()
 val started=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
 @Volatile var complete=true
 @Volatile var verified=0
 fun logNativeJoinGate(loader:ClassLoader?){}
 class Result(val complete:Boolean, data:Map<String,Long>):HashMap<String,Long>(data)
 fun isJoinTimeResultComplete(times:Map<String,Long>)=times is Result && times.complete
 fun logPersistedJoinTimes(g:String,id:Long,hash:String){verified++}
 @Volatile var queryOverride: (() -> Map<String,Long>)? = null
 fun getMemberJoinTimes(g:String):Map<String,Long>{queryOverride?.let { return Result(complete,it()) };calls++;started.countDown();check(release.await(3,java.util.concurrent.TimeUnit.SECONDS));return Result(complete,result)}
 fun getMemberInviter(g:String,m:String)=""
 fun getMemberDisplayName(g:String,m:String)=""
}
''',
'Test.kt':'''import t.mma.hooks.items.profileid.ProfileGroupInfoFeature
import t.mma.hooks.api.contact.WeContactHeaderApi as Header
import t.mma.hooks.api.core.WeChatApis
fun main(){
 ProfileGroupInfoFeature.installProvider()
 check(WeChatApis.api.calls==0)
 val p=Header.providers[0];val a=android.app.Activity()
 t.mma.preferences.Prefs.enabled=false;check(p.getHeaderText(a)==null);check(WeChatApis.api.calls==0)
 t.mma.preferences.Prefs.enabled=true
 check(p.getHeaderText(a)==null)
 check(WeChatApis.api.started.await(3,java.util.concurrent.TimeUnit.SECONDS))
 repeat(20){check(p.getHeaderText(a)==null)};check(WeChatApis.api.calls==1)
 WeChatApis.api.release.countDown()
 Thread.sleep(100)
 check(p.getHeaderText(a)==null);check(WeChatApis.api.calls==1)
 WeChatApis.api.result=mapOf("member" to 1700000000000L)
 android.os.SystemClock.now=15_001
 p.getHeaderText(a)
 val retryEnd=System.nanoTime()+3000000000L
 while(Header.refreshes<2 && System.nanoTime()<retryEnd) Thread.yield()
 check(Header.refreshes==2);check(p.getHeaderText(a)?.startsWith("进群时间：")==true);check(WeChatApis.api.calls==2)
 repeat(20){p.getHeaderText(a)};check(WeChatApis.api.calls==2)
 android.os.SystemClock.now=30_002
 repeat(20){check(p.getHeaderText(a)!=null)}
 Thread.sleep(60);check(WeChatApis.api.calls==2) {"complete hit was scanned again"}
 // A partial snapshot must retry even when this member already has a historical hit.
 WeChatApis.api.complete=false
 ProfileGroupInfoFeature.nativeWriteCompleted("group@chatroom", 1L, "fixture")
 val verifiedEnd=System.nanoTime()+3000000000L
 while(WeChatApis.api.verified==0 && System.nanoTime()<verifiedEnd) Thread.yield()
 Thread.sleep(30)
 p.getHeaderText(a);Thread.sleep(60)
 check(WeChatApis.api.calls==3)
 android.os.SystemClock.now=45_003
 p.getHeaderText(a);Thread.sleep(60)
 check(WeChatApis.api.calls==4)
 ProfileGroupInfoFeature.stopJoinTime();check(Header.providers.size==2)
 p.getHeaderText(a);Thread.sleep(100);check(WeChatApis.api.calls==5)
 ProfileGroupInfoFeature.removeProvider();check(Header.providers.isEmpty())
 ProfileGroupInfoFeature.installProvider();check(Header.providers.size==2)
 Header.providers[0].getHeaderText(a);Thread.sleep(100);check(WeChatApis.api.calls==6)
 ProfileGroupInfoFeature.removeProvider();check(Header.providers.isEmpty())
 ProfileGroupInfoFeature.nativeWriteCompleted("group@chatroom", 2L, "fixture")
 check(WeChatApis.api.verified==1)
 // A host query may ignore interruption: its old result must never replace the new generation.
 val oldStarted=java.util.concurrent.CountDownLatch(1)
 val oldRelease=java.util.concurrent.CountDownLatch(1)
 val oldFinished=java.util.concurrent.CountDownLatch(1)
 val currentLoaded=java.util.concurrent.CountDownLatch(1)
 WeChatApis.api.complete=true
 WeChatApis.api.queryOverride={
   oldStarted.countDown()
   while(true) { try { oldRelease.await();break } catch(_:InterruptedException) {} }
   oldFinished.countDown()
   mapOf("member" to 1600000000000L)
 }
 ProfileGroupInfoFeature.installProvider()
 val oldProvider=Header.providers[0];oldProvider.getHeaderText(a)
 check(oldStarted.await(2,java.util.concurrent.TimeUnit.SECONDS))
 ProfileGroupInfoFeature.stopJoinTime()
 WeChatApis.api.queryOverride={currentLoaded.countDown();mapOf("member" to 1800000000000L)}
 oldProvider.getHeaderText(a)
 check(currentLoaded.await(2,java.util.concurrent.TimeUnit.SECONDS))
 Thread.sleep(50)
 val currentText=oldProvider.getHeaderText(a)
 check(currentText!=null)
 oldRelease.countDown();check(oldFinished.await(2,java.util.concurrent.TimeUnit.SECONDS))
 Thread.sleep(50)
 check(oldProvider.getHeaderText(a)==currentText) {"stale generation overwrote current snapshot"}
 t.mma.hooks.items.profileid.JoinTimeRecovery.account="account-B"
 val accountStarted=java.util.concurrent.CountDownLatch(1)
 val accountRelease=java.util.concurrent.CountDownLatch(1)
 WeChatApis.api.queryOverride={accountStarted.countDown();check(accountRelease.await(2,java.util.concurrent.TimeUnit.SECONDS));emptyMap()}
 check(oldProvider.getHeaderText(a)==null) {"previous account cache leaked"}
 check(accountStarted.await(2,java.util.concurrent.TimeUnit.SECONDS))
 accountRelease.countDown();Thread.sleep(60)
 check(oldProvider.getHeaderText(a)==null)
 ProfileGroupInfoFeature.removeProvider()
 println("PASS: no startup scan, disabled no load, background dedup, empty snapshot expiry and new-member refresh, cooldown, incomplete-hit retry, native-write invalidation/readback, complete-result metadata, stop/reopen, noninterruptible stale worker rejection, teardown")
}
'''}
with tempfile.TemporaryDirectory() as d:
 p=Path(d);files=[]
 for n,s in sources.items():
  f=p/n;f.write_text(s);files.append(str(f))
 cmd=['java','-Xmx256m','-cp',':'.join(map(str,jars)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(stdlib),'-d',str(p/'test.jar'),*files,str(repo/'app/src/main/java/t/mma/hooks/items/profileid/ProfileGroupInfoFeature.kt')]
 r=subprocess.run(cmd,capture_output=True,text=True);print(r.stderr if r.returncode else 'Kotlin JVM compile PASS');r.check_returncode()
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(stdlib),'TestKt'],check=True,timeout=10)
