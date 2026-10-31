"""Extract production notification install and exercise failures with synthetic hook members."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];b=r/'app/src/main/java/t/mma/hooks/items'
s=(b/'customnotify/CustomNotificationRuntime.kt').read_text()
helper=s[s.index('    private val sharedHooks ='):s.index('    private val receiverRegistered')]
install=s[s.index('    @Synchronized\n    fun install(context:'):s.index('    private fun installEpochListener(')]
install=install.replace('t.mma.hooks.core.DexInstallScheduler','DexInstallScheduler')
filter_source=(b/'moments/MomentsKeywordBlockFeature.kt').read_text()
assert 'contacts.getBoolean(MomentsContactFilterSettings.KEY_ENABLE' in filter_source
assert 'if (!enabled) { stopRuntime(); return@post }' in filter_source
assert 'val current = runtime ?: return' in filter_source
assert 'tickets.values.forEach { it.close() }; tickets.clear()' in filter_source
home=(b/'homeextension/HomeExtensionFeature.kt').read_text()
assert 'PreferenceRuntimeOwner' in home and 'try { if (!resources.isClosed()) installed.install() }' in home
for name in ['MomentsFakeInteractionRuntime','MomentsFakeForwardRuntime']:
 text=(b/f'momentsfake/{name}.kt').read_text()
 assert 'resources.register { HookRegistry.get().unhook(handle) }' in text
 assert 'resources.close()' in text and 'shutdownNow()' not in text
code='''package t.mma.hooks.items.emojiat
import java.util.concurrent.atomic.AtomicBoolean
class XC_MethodHook { class Unhook }
class HookRegistry {
 var calls=0;var fail=false
 fun hook(m:java.lang.reflect.Member,c:XC_MethodHook):XC_MethodHook.Unhook { if(fail){fail=false;error("synthetic hook failure")};calls++;return XC_MethodHook.Unhook() }
 companion object {val instance=HookRegistry();fun get()=instance}
}
class FeatureContext {fun hostContext()=this}
object QuickMarkReadRuntime {fun install(c:FeatureContext) {}}
object HLog {fun e(s:String,e:Throwable) {}}
object DexInstallScheduler {
 var job:(()->Boolean)?=null
 fun schedule(key:String,label:String,block:()->Boolean) {job=block}
 fun attempt()=job!!()
}
class Owner {
 val installed=AtomicBoolean(false);val receiverRegistered=AtomicBoolean(false)
 val TAG="test";var receiverFails=true;var missingMarker=true
''' + helper + install + '''
 fun installEpochListener(c:FeatureContext) {}
 fun registerNotificationActionReceiver(c:FeatureContext) { if(!receiverFails)receiverRegistered.set(true) }
 fun installNotificationHooks(c:FeatureContext) {
  val member=String::class.java.getDeclaredMethod("length")
  for(role in listOf("notify","foreground_processor","foreground_sound","cleanup_processor","cleanup_method","mention","payload","marker")) {
   if(role=="marker"&&missingMarker)continue
   sharedHook(role,member,XC_MethodHook())
  }
 }
}
class Item(val epoch:Int)
fun main() {
 val o=Owner();o.install(FeatureContext());HookRegistry.instance.fail=true
 check(!DexInstallScheduler.attempt()&&!o.installed.get()&&HookRegistry.instance.calls==0)
 check(!DexInstallScheduler.attempt()&&!o.installed.get()&&HookRegistry.instance.calls==7)
 o.missingMarker=false
 check(!DexInstallScheduler.attempt()&&o.installed.get()&&HookRegistry.instance.calls==8)
 o.receiverFails=false
 check(DexInstallScheduler.attempt()&&o.receiverRegistered.get()&&HookRegistry.instance.calls==8)
 o.install(FeatureContext());check(DexInstallScheduler.attempt()&&HookRegistry.instance.calls==8)
 val q=EmojiAtChoiceQueue<Item>();val old=Item(0);val fresh=Item(2)
 q.offer(old);check(q.acquire()===old);q.offer(fresh);q.removeIf{it.epoch<1};check(q.acquire()===fresh)
 q.removeIf{it.epoch<1};check(q.acquire()==null);check(q.complete(fresh));check(q.acquire()==null)
 println("PASS production shared install: exception/partial hooks/receiver retry/idempotent roles; old emoji cleanup preserves new generation")
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt'),str(b/'emojiat/EmojiAtChoiceQueue.kt')],check=True,timeout=90)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.emojiat.TestKt'],check=True,timeout=20)