"""Targeted SDK compile and host behavior checks; project query/network are fixtures."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'app/src/main/java/t/mma'
LIB = Path('/tmp/pl-kotlin-libs')
STD = next(LIB.glob('kotlin-stdlib-[0-9]*.jar'))
SDK = Path('/usr/lib/android-sdk/platforms/android-36/android.jar')
STUBS = {
 'Context.kt': 'package t.mma.hooks.core\nclass FeatureContext {fun hostContext():android.content.Context=TODO()}',
 'Storage.kt': '''package t.mma.preferences
object HchatStorage {
 lateinit var prefs:android.content.SharedPreferences
 fun preferences(context:android.content.Context,name:String)=prefs
 fun runtimePreferences(context:android.content.Context,name:String)=prefs
}''',
 'Header.kt': '''package t.mma.hooks.api.contact
object WeContactHeaderApi {
 interface Provider {fun getHeaderText(activity:android.app.Activity):String?; fun getHeaderColorSpec(activity:android.app.Activity):String?=null}
 var refreshed=0
 fun refreshCurrentProfile(group:String?,member:String){refreshed++}
 fun removeProvider(provider:Provider){}
}''',
 'Tasks.kt': '''package t.mma.hooks.api.core
object WeChatApis {val service=Tasks();fun tasks():Tasks?=service}
class Tasks {
 val background=java.util.ArrayDeque<Runnable>();val main=java.util.ArrayDeque<Runnable>()
 var reject=false
 fun runAsync(r:Runnable?) {if(reject) throw java.util.concurrent.RejectedExecutionException();background.add(r!!)}
 fun runOnMain(r:Runnable?){main.add(r!!)}
 fun drain(){while(background.isNotEmpty())background.remove().run();while(main.isNotEmpty())main.remove().run()}
}''',
 'Query.kt': '''package t.mma.hooks.items.realtail
class BeforeTransferNameQuery(c:t.mma.hooks.core.FeatureContext,l:(String,Throwable?)->Unit) {
 fun interface Callback {fun onResult(name:String)}
 var destroyed=false
 init {latest=this}
 fun ensureReady()=!destroyed
 fun query(id:String,group:String,callback:Callback):Boolean {calls++;callbacks.add(callback);return true}
 fun destroy(){destroyed=true}
 companion object {lateinit var latest:BeforeTransferNameQuery; var calls=0;val callbacks=mutableListOf<Callback>()}
}
class RealNameTailStore(c:android.content.Context) {
 fun cachedTail(id:String)=cached[id].orEmpty()
 companion object {
 val cached=mutableMapOf<String,String>()
 fun isValidWxid(id:String)=id.startsWith("wxid_")
 fun isSupportedQueryId(id:String)=isValidWxid(id)&&!id.endsWith("@openim")
 fun normalizeMaskedName(raw:String)=raw.trim()
 }
}''',
}
HOST = {
 'AndroidContext.kt': 'package android.content\nopen class Context\nclass Intent {val values=mutableMapOf<String,String>();fun getStringExtra(key:String)=values[key]}',
 'Activity.kt': 'package android.app\nclass Activity {var intent:android.content.Intent?=android.content.Intent()}',
 'Clock.kt': 'package android.os\nobject SystemClock {var now=0L;@JvmStatic fun elapsedRealtime()=now}',
}
MAIN = '''package t.mma.hooks.items.profileid
import android.content.SharedPreferences
import t.mma.hooks.core.*
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.items.realtail.*
import t.mma.preferences.HchatStorage
import t.mma.hooks.api.contact.WeContactHeaderApi
fun main(){
 val values=mutableMapOf<String,Any>("enabled" to true)
 val listeners=mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()
 val prefs=java.lang.reflect.Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,arrayOf(SharedPreferences::class.java)){_,m,a->
  when(m.name){
   "getBoolean" -> values[a!![0]] ?: a[1]
   "getString" -> values[a!![0]] ?: a[1]
   "registerOnSharedPreferenceChangeListener" -> {listeners.add(a!![0] as SharedPreferences.OnSharedPreferenceChangeListener);null}
   "unregisterOnSharedPreferenceChangeListener" -> {listeners.remove(a!![0]);null}
   else -> null
  }
 } as SharedPreferences
 HchatStorage.prefs=prefs
 val context=FeatureContext()
 val tasks=WeChatApis.service
 val activity=android.app.Activity()
 fun member(id:String){activity.intent!!.values["Contact_User"]=id}
 var errors=0
 // FeatureContext's hostContext is replaced with a concrete host fixture below.
 val provider=ProfileRealNameTailProvider(context){_,_->errors++}
 member("wxid_a")
 check(provider.getHeaderText(activity)=="实名尾字：未知")
 repeat(5){provider.getHeaderText(activity)}
 check(tasks.background.size==1)
 tasks.drain();check(BeforeTransferNameQuery.calls==1)
 BeforeTransferNameQuery.callbacks.removeAt(0).onResult("**东")
 check(provider.getHeaderText(activity)=="实名尾字：＊＊东")
 values["prefix"]="小";check(provider.getHeaderText(activity)=="实名尾字：小东")
 values["prefix"]="";check(provider.getHeaderText(activity)=="实名尾字：＊＊东")
 member("wxid_empty");provider.getHeaderText(activity);tasks.drain()
 BeforeTransferNameQuery.callbacks.removeAt(0).onResult("")
 repeat(5){provider.getHeaderText(activity)};check(tasks.background.isEmpty())
 android.os.SystemClock.now=60_001;provider.getHeaderText(activity);check(tasks.background.size==1);tasks.drain()
 val late=BeforeTransferNameQuery.callbacks.removeAt(0)
 val count=WeContactHeaderApi.refreshed
 provider.close();provider.close();late.onResult("**南")
 check(WeContactHeaderApi.refreshed==count&&BeforeTransferNameQuery.latest.destroyed)
 check(provider.getHeaderText(activity)==null)
 val p2=ProfileRealNameTailProvider(context){_,_->errors++}
 repeat(12){member("wxid_$it");p2.getHeaderText(activity)}
 check(tasks.background.size==8)
 p2.close();tasks.drain()
 val p3=ProfileRealNameTailProvider(context){_,_->errors++}
 tasks.reject=true;member("wxid_reject");check(p3.getHeaderText(activity)=="实名尾字：未知");check(errors==1)
 tasks.reject=false;repeat(3){p3.getHeaderText(activity)};check(tasks.background.isEmpty());p3.close()
 // Real owner/scope exercise off -> on -> off -> on -> destroy.
 values["enabled"]=false
 var created=0;var released=0
 val owner=PreferenceRuntimeOwner(prefs,setOf("enabled"),{values["enabled"]==true},{scope->created++;scope.register{released++}},{throw it})
 owner.start();check(created==0&&listeners.size==1)
 fun enabled(value:Boolean){values["enabled"]=value;listeners.toList().forEach{it.onSharedPreferenceChanged(prefs,"enabled")}}
 enabled(true);enabled(true);check(created==1)
 enabled(false);check(released==1)
 enabled(true);check(created==2)
 owner.close();owner.close();check(released==2&&listeners.isEmpty())
 println("PASS production provider: dedup, prefix, unknown cooldown/retry, 8-request bound, rejection, close/late callbacks; production owner: independent reopen and cleanup")
}
'''

def compile(sources, target, cp):
    p = subprocess.run(['java', '-Xmx384m', '-cp', ':'.join(map(str, LIB.glob('*.jar'))),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-jvm-target', '17',
        '-classpath', ':'.join(map(str, cp)), '-d', str(target), *map(str, sources)], capture_output=True, text=True, timeout=55)
    if p.returncode:
        raise RuntimeError(p.stdout + p.stderr)

with tempfile.TemporaryDirectory(prefix='profile-real-tail-') as td:
    temp = Path(td)
    for name, text in STUBS.items():
        (temp / name).write_text(text)
    production = [SRC / 'hooks/items/profileid' / n for n in ['ProfileRealNameTailProvider.kt', 'ProfileRealNameTailSettings.kt', 'ProfileGroupInfoSettings.kt', 'ProfileAddTimeSettings.kt']]
    production += [SRC / 'hooks/core' / n for n in ['PreferenceRuntimeOwner.kt', 'FeatureResourceScope.kt']]
    compile([*production, *temp.glob('*.kt')], temp / 'sdk.jar', [STD, SDK])
    print('PASS production provider/settings/owner/scope SDK compile; project API/query fixtures')
    for name, text in HOST.items():
        (temp / name).write_text(text)
    (temp / 'Context.kt').write_text('package t.mma.hooks.core\nclass FeatureContext {fun hostContext()=android.content.Context()}')
    (temp / 'Main.kt').write_text(MAIN)
    compile([*production, *temp.glob('*.kt')], temp / 'host.jar', [STD, SDK])
    subprocess.run(['java', '-cp', f'{temp / "host.jar"}:{STD}:{SDK}', 't.mma.hooks.items.profileid.MainKt'], check=True, timeout=15)

feature = (SRC / 'hooks/items/profileid/ProfileIdFeature.kt').read_text()
init = feature.split('override fun onFeatureInit', 1)[1].split('override fun onFeatureInstall', 1)[0]
assert 'tailOwner.start()' in init and 'trackResource { tailOwner.close() }' in init
assert 'installation.schedule' in init and 'resources.register { installation.close() }' in init
print('PASS owner initialized before install gating; owned installation retries and teardown wired')
