"""Production GameEmoji onFeatureInstall with synthetic host; not device acceptance."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/gameemoji/GameEmojiFeature.kt').read_text()
a=s.index('    override fun onFeatureInstall(');b=s.index('    private fun logRuntimeError',a)
method=s[a:b]
assert 'restartOnChange = false' in method
assert 'DexInstallScheduler.installation(' in method
assert 'resources.register { installation.close() }' in method
assert 'resources.register { owner.close() }' in method
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
prefs="""package android.content
interface SharedPreferences {
 fun registerOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener)
 fun unregisterOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener)
 fun interface OnSharedPreferenceChangeListener{fun onSharedPreferenceChanged(p:SharedPreferences,k:String?)}
}
"""
code="""package t.mma.hooks.items.gameemoji
import android.content.SharedPreferences
import t.mma.hooks.core.FeatureResourceScope
import t.mma.hooks.core.PreferenceRuntimeOwner
class Prefs:SharedPreferences {
 val listeners=mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()
 var fixed=false;var pick=false
 override fun registerOnSharedPreferenceChangeListener(l:SharedPreferences.OnSharedPreferenceChangeListener){listeners.add(l)}
 override fun unregisterOnSharedPreferenceChangeListener(l:SharedPreferences.OnSharedPreferenceChangeListener){listeners.remove(l)}
 fun change(k:String){listeners.toList().forEach{it.onSharedPreferenceChanged(this,k)}}
}
object HchatStorage {val prefs=Prefs();fun preferences(c:Any,n:String)=prefs}
object GameEmojiSettings {
 const val PREFS_NAME="Hchat_game_emoji_config"
 const val KEY_FIXED_RESULT="game_emoji_fixed_result"
 const val KEY_PICK_BEFORE_SEND="game_emoji_pick_before_send"
 fun fixedResultEnabled(c:Any)=HchatStorage.prefs.fixed
 fun pickBeforeSendEnabled(c:Any)=HchatStorage.prefs.pick
}
object Events {class DexReady}
class Bus {
 val callbacks=mutableListOf<(Events.DexReady)->Unit>()
 fun subscribe(c:Class<Events.DexReady>,f:(Events.DexReady)->Unit):Any{callbacks.add(f);return f}
 fun unsubscribe(s:Any){callbacks.remove(s)}
 fun post(){callbacks.toList().forEach{it(Events.DexReady())}}
}
class FeatureContext {val bus=Bus();fun hostContext()=Any();fun eventBus()=bus}
object DexInstallScheduler {
 enum class Stage{EARLY,BRIDGE,WARMUP}
 class Installation(val key:String){
  var closed=false;var scheduled=0
  fun schedule(f:()->Boolean){if(closed)return;scheduled++;pending.add(this to f)}
  fun close(){closed=true}
 }
 val tickets=mutableListOf<Installation>()
 val pending=mutableListOf<Pair<Installation,()->Boolean>>()
 fun installation(id:String,n:String,stage:Stage)=Installation(id).also{tickets.add(it)}
 fun drain():List<Boolean>{val p=pending.toList();pending.clear();return p.map{it.second()}}
}
class GameEmojiRuntime(val c:FeatureContext,val logger:(String,Throwable?)->Unit){
 var closed=false;var installed=false;var installs=0
 init{all.add(this)}
 fun install():Boolean{if(closed)return false;if(installed)return true;installed=true;installs++;return true}
 fun close(){closed=true}
 companion object{val all=mutableListOf<GameEmojiRuntime>()}
}
val errors=mutableListOf<String>()
open class Base {open fun onFeatureInstall(c:FeatureContext){} }
internal class Feature:Base(){
 val scope=FeatureResourceScope{errors.add(it.message?:"release")}
 fun trackResource(f:()->Unit){scope.register(f)}
 fun name()="指定骰子猜拳"
 fun logRuntimeError(m:String,t:Throwable?){errors.add(m)}
 val ID="game_emoji_result"
"""+method+"""
}
fun main(){
 val p=HchatStorage.prefs;val f=Feature();val c=FeatureContext();f.onFeatureInstall(c)
 check(p.listeners.size==1)
 check(GameEmojiRuntime.all.isEmpty()) { "disabled startup must not create runtime" }
 check(DexInstallScheduler.tickets.isEmpty()&&c.bus.callbacks.isEmpty())
 var generations=0
 repeat(100){
  p.fixed=true;p.change(GameEmojiSettings.KEY_FIXED_RESULT)
  generations++
  val owner=GameEmojiRuntime.all.last()
  check(GameEmojiRuntime.all.size==generations&&!owner.closed)
  check(c.bus.callbacks.size==1)
  check(DexInstallScheduler.tickets.size==generations)
  val ticket=DexInstallScheduler.tickets.last()
  // 同代内两个开关互相切换不重建：旧 owner 与旧票根都不换
  p.fixed=false;p.pick=true;p.change(GameEmojiSettings.KEY_PICK_BEFORE_SEND)
  check(GameEmojiRuntime.all.size==generations)
  check(GameEmojiRuntime.all.last()===owner&&!owner.closed)
  check(DexInstallScheduler.tickets.last()===ticket&&!ticket.closed)
  DexInstallScheduler.drain().forEach{check(it)}
  check(owner.installs==1)
  // 重复 DexReady 不会重复安装
  c.bus.post();DexInstallScheduler.drain().forEach{check(it)}
  check(owner.installs==1)
  // 关闭：runtime、票根、订阅一起释放
  p.pick=false;p.change(GameEmojiSettings.KEY_PICK_BEFORE_SEND)
  check(owner.closed&&ticket.closed&&c.bus.callbacks.isEmpty())
  // 迟到的排队 installer 不能复活已关闭的 runtime
  val queued=DexInstallScheduler.pending.toList();DexInstallScheduler.pending.clear()
  queued.forEach{check(!it.second())}
  check(owner.installs==1)
 }
 // 关闭后旧订阅闭包也不得再调度
 p.fixed=true;p.change(GameEmojiSettings.KEY_FIXED_RESULT)
 val stale=c.bus.callbacks.toList();val staleOwner=GameEmojiRuntime.all.last()
 val staleTicket=DexInstallScheduler.tickets.last()
 p.fixed=false;p.change(GameEmojiSettings.KEY_FIXED_RESULT)
 check(staleOwner.closed&&staleTicket.closed&&c.bus.callbacks.isEmpty())
 DexInstallScheduler.pending.clear()
 stale.forEach{it(Events.DexReady())}
 check(DexInstallScheduler.pending.isEmpty()) { "stale DexReady must not schedule" }
 // 双开关同时开启时任一 key 变更都不换代
 p.fixed=true;p.pick=true;p.change(GameEmojiSettings.KEY_FIXED_RESULT)
 val both=GameEmojiRuntime.all.last()
 p.change(GameEmojiSettings.KEY_PICK_BEFORE_SEND);p.change(GameEmojiSettings.KEY_FIXED_RESULT)
 check(GameEmojiRuntime.all.last()===both&&!both.closed)
 // feature scope 关闭即释放全部代
 val pending=DexInstallScheduler.pending.toList();DexInstallScheduler.pending.clear()
 f.scope.close()
 check(both.closed&&p.listeners.isEmpty()&&c.bus.callbacks.isEmpty())
 pending.forEach{check(!it.second())}
 check(errors.isEmpty()) { "unexpected release errors: "+errors }
 println("PASS production GameEmoji install: disabled startup; 100 toggle generations; both-key alternation without rebuild; DexReady dedupe; stale subscription/queued installer after close; feature scope release")
}
"""
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Prefs.kt').write_text(prefs);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Prefs.kt'),str(p/'Test.kt'),str(r/'app/src/main/java/t/mma/hooks/core/PreferenceRuntimeOwner.kt'),str(r/'app/src/main/java/t/mma/hooks/core/FeatureResourceScope.kt')],check=True,timeout=120)
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(std),'t.mma.hooks.items.gameemoji.TestKt'],check=True,timeout=60)
