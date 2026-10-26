"""Exercise production rendering gate and installation transitions with JVM stubs."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
s=(r/'app/src/main/java/t/mma/hooks/items/backgroundbeauty/BackgroundBeautyFeature.kt').read_text();a=s.index('    private fun updateBackgroundHooks()');z=s.index('    fun destroy()',a)
main='''package t.mma.hooks.items.backgroundbeauty
object BackgroundBeautyFeature {const val ID="background_beauty"}
object BackgroundBeautyStore {val images=mutableSetOf<BackgroundBeautySettings.Slot>();fun hasImage(c:Any,s:BackgroundBeautySettings.Slot)=s in images}
object HLog {fun e(s:String){}}
object DexInstallScheduler {fun schedule(k:String,n:String,f:()->Boolean){f()}}
class Prefs {var enabled=true;fun getBoolean(k:String,d:Boolean)=enabled}
class Ctx {fun hostContext()=this}
class Runtime {
 val prefs=Prefs();val context=Ctx();val TAG="test"
 var backgroundRenderingEnabled=false;var backgroundHooksInstalled=false;var installs=0
 var configuredBackgroundSlots=emptySet<BackgroundBeautySettings.Slot>()
 fun installActivityHooks():Boolean {installs++;return true}
 fun installTabHooks()=true
 fun installFragmentHooks()=true
 fun installSurfaceHooks()=true
 fun installBackgroundWriteHook()=true
 fun installWechatVerifiedPaths()=true
 fun installChatChromeLifecycle(){}
 fun installChatBackgroundComponentHook(allowDexSearch:Boolean)=true
 fun update()=updateBackgroundHooks()
'''+s[a:z]+'''}
fun main(){
 for(enabled in listOf(false,true)) for(mask in 0 until 32){
  var reads=0
  val actual=BackgroundBeautySettings.needsRendering(enabled){slot->reads++;mask and (1 shl slot.ordinal)!=0}
  check(actual==(enabled && mask!=0));if(!enabled)check(reads==0)
 }
 val runtime=Runtime();runtime.update();check(runtime.installs==0 && !runtime.backgroundRenderingEnabled)
 BackgroundBeautyStore.images.add(BackgroundBeautySettings.Slot.ME);runtime.update();check(runtime.installs==1 && runtime.backgroundRenderingEnabled)
 runtime.update();check(runtime.installs==1)
 BackgroundBeautyStore.images.clear();runtime.update();check(!runtime.backgroundRenderingEnabled)
 BackgroundBeautyStore.images.add(BackgroundBeautySettings.Slot.CONTACTS);runtime.update();check(runtime.installs==1 && runtime.backgroundRenderingEnabled)
 runtime.prefs.enabled=false;runtime.update();check(!runtime.backgroundRenderingEnabled)
 println("PASS 64 rendering combinations; hidden-only skips background hooks; first image installs once; remove/restore/disable transitions")
}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Main.kt').write_text(main);jar=p/'test.jar'
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(jar),str(p/'Main.kt'),str(r/'app/src/main/java/t/mma/hooks/items/backgroundbeauty/BackgroundBeautySettings.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{jar}:{std}','t.mma.hooks.items.backgroundbeauty.MainKt'],check=True,timeout=30)