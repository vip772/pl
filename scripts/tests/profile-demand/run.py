from pathlib import Path
import subprocess,tempfile
repo=Path(__file__).resolve().parents[3]
lib=Path('/tmp/pl-kotlin-libs');jars=list(lib.glob('*.jar'));stdlib=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
sources={
'Activity.kt':'''package android.app
open class Activity {val intent=Intent()}
class Intent {fun getStringExtra(k:String):String?= when(k){"Contact_ChatRoomId"->"group@chatroom";"Contact_User"->"member";else->null}}
''',
'Log.kt':'''package t.mma.utils
object HLog {fun e(s:String,t:Throwable){throw AssertionError(s,t)}}''',
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
}
''',
'Apis.kt':'''package t.mma.hooks.api.core
object WeChatApis {val api=Rooms();fun chatrooms()=api}
class Rooms {
 @Volatile var calls=0
 val started=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
 fun getMemberJoinTimes(g:String):Map<String,Long>{calls++;started.countDown();check(release.await(3,java.util.concurrent.TimeUnit.SECONDS));return mapOf("member" to 1700000000000L)}
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
 val end=System.nanoTime()+3000000000L
 while(Header.refreshes==0 && System.nanoTime()<end) Thread.yield()
 check(Header.refreshes==1);check(p.getHeaderText(a)?.startsWith("进群时间：")==true);check(WeChatApis.api.calls==1)
 ProfileGroupInfoFeature.removeProvider();check(Header.providers.isEmpty())
 println("PASS: 注册不查群、关闭不加载、当前群后台加载、并发去重、完成刷新与缓存命中、销毁清理")
}
'''}
with tempfile.TemporaryDirectory() as d:
 p=Path(d);files=[]
 for n,s in sources.items():
  f=p/n;f.write_text(s);files.append(str(f))
 cmd=['java','-Xmx256m','-cp',':'.join(map(str,jars)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(stdlib),'-d',str(p/'test.jar'),*files,str(repo/'app/src/main/java/t/mma/hooks/items/profileid/ProfileGroupInfoFeature.kt')]
 r=subprocess.run(cmd,capture_output=True,text=True);print(r.stderr if r.returncode else 'Kotlin JVM compile PASS');r.check_returncode()
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(stdlib),'TestKt'],check=True,timeout=10)
