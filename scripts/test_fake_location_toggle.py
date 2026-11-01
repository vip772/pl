"""Production feature install and preference/scope ownership with synthetic host, not device."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/fakelocation/FakeLocationFeature.kt').read_text()
a=s.index('    override fun onFeatureInstall(');b=s.index('    private fun logFeatureError',a)
method=s[a:b]
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
prefs="""package android.content
interface SharedPreferences {
 fun registerOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener)
 fun unregisterOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener)
 fun interface OnSharedPreferenceChangeListener{fun onSharedPreferenceChanged(p:SharedPreferences,k:String?)}
}
"""
code="""package t.mma.hooks.core
import android.content.SharedPreferences
class Prefs:SharedPreferences {
 val listeners=mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>();var enabled=false
 override fun registerOnSharedPreferenceChangeListener(l:SharedPreferences.OnSharedPreferenceChangeListener){listeners.add(l)}
 override fun unregisterOnSharedPreferenceChangeListener(l:SharedPreferences.OnSharedPreferenceChangeListener){listeners.remove(l)}
 fun toggle(v:Boolean){enabled=v;listeners.toList().forEach{it.onSharedPreferenceChanged(this,FakeLocationSettings.KEY_ENABLE)}}
}
object HchatStorage {val prefs=Prefs();fun preferences(c:Any,n:String)=prefs}
object FakeLocationSettings {const val PREFS_NAME="config";const val KEY_ENABLE="enable"}
object Events {class DexReady}
class Bus {
 val callbacks=mutableListOf<(Events.DexReady)->Unit>()
 fun subscribe(c:Class<Events.DexReady>,f:(Events.DexReady)->Unit):Any{callbacks.add(f);f(Events.DexReady());return f}
 fun unsubscribe(s:Any){callbacks.remove(s)}
}
class FeatureContext {val bus=Bus();fun hostContext()=Any();fun eventBus()=bus}
object DexInstallScheduler {
 val tickets=mutableListOf<Installation>();val pending=mutableListOf<()->Boolean>()
 class Installation {var closed=false;fun schedule(f:()->Boolean){if(!closed)pending.add(f)};fun close(){closed=true}}
 fun installation(id:String,n:String)=Installation().also{tickets.add(it)}
}
class FakeLocationHooker(c:FeatureContext,logger:(String,Throwable?)->Unit){
 var closed=false;var effects=0
 init{all.add(this)}
 fun install():Boolean{if(closed)return false;effects++;return true}
 fun close(){closed=true}
 companion object{val all=mutableListOf<FakeLocationHooker>()}
}
open class Base {open fun onFeatureInstall(c:FeatureContext){} }
internal class Feature:Base(){
 val scope=FeatureResourceScope{throw it}
 fun trackResource(f:()->Unit){scope.register(f)}
 fun name()="FakeLocation"
 fun isEnabled(c:Any)=HchatStorage.prefs.enabled
 fun logFeatureError(m:String,t:Throwable?){error(m)}
 val ID="fake_location"
"""+method+"""
}
fun main(){
 val p=HchatStorage.prefs;val f=Feature();val c=FeatureContext();f.onFeatureInstall(c)
 check(p.listeners.size==1&&FakeLocationHooker.all.isEmpty()&&c.bus.callbacks.isEmpty())
 repeat(100){
  p.toggle(true);val owner=FakeLocationHooker.all.last();val ticket=DexInstallScheduler.tickets.last()
  check(c.bus.callbacks.size==1);val old=c.bus.callbacks.single()
  DexInstallScheduler.pending.toList().forEach{it()};DexInstallScheduler.pending.clear()
  check(owner.effects==2);p.toggle(false)
  check(owner.closed&&ticket.closed&&c.bus.callbacks.isEmpty());val before=owner.effects
  old(Events.DexReady());check(DexInstallScheduler.pending.isEmpty()&&owner.effects==before)
 }
 p.toggle(true);val last=FakeLocationHooker.all.last();val pending=DexInstallScheduler.pending.toList()
 f.scope.close();check(last.closed&&p.listeners.isEmpty()&&c.bus.callbacks.isEmpty())
 pending.forEach{check(!it())};p.toggle(true);check(FakeLocationHooker.all.size==101)
 println("PASS production FakeLocation install: disabled startup; 100 toggle generations; subscription/ticket/hooks release; sticky/late DexReady; queued install after close")
}
"""
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Prefs.kt').write_text(prefs);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Prefs.kt'),str(p/'Test.kt'),str(r/'app/src/main/java/t/mma/hooks/core/PreferenceRuntimeOwner.kt'),str(r/'app/src/main/java/t/mma/hooks/core/FeatureResourceScope.kt')],check=True,timeout=90)
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(std),'t.mma.hooks.core.TestKt'],check=True,timeout=30)
