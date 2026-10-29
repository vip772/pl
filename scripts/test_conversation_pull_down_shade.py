"""Compile and exercise the production hook with a native-draw fixture; no Gradle."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/t/mma/hooks/items/backgroundbeauty/BackgroundBeautyFeature.kt').read_text()
a = source.index('    private fun installConversationPullDownShadeHook(')
b = source.index('    private fun readNativeField', a)
method = source[a:b]
libs = Path('/tmp/pl-kotlin-libs')
stdlib = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
compiler = ['java', '-Xmx256m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
            'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-nowarn', '-no-stdlib', '-no-reflect']
fixture = '''import android.view.View
import android.graphics.Canvas
import java.lang.reflect.*
object BackgroundBeautySettings {enum class Slot {WECHAT,CHAT}}
class Bitmap(var isRecycled:Boolean=false)
class Loaded(val bitmap:Bitmap=Bitmap())
class State {val loadedBitmaps=mutableMapOf(BackgroundBeautySettings.Slot.WECHAT to Loaded())}
open class XC_MethodHook {
 class MethodHookParam(val thisObject:Any) {
  val extras=mutableMapOf<String,Any>();fun setObjectExtra(k:String,v:Any){extras[k]=v};fun getObjectExtra(k:String)=extras[k]
 }
 open fun beforeHookedMethod(param:MethodHookParam){}
 open fun afterHookedMethod(param:MethodHookParam){}
}
object HookRegistry {
 val hooks=mutableListOf<XC_MethodHook>();var fail=false
 fun get()=this
 fun hook(m:Method,h:XC_MethodHook):Any {if(fail)error("install failed");hooks.add(h);return h}
}
object HLog {var errors=0;fun e(s:String,t:Throwable){errors++}}
object KavaReflector {
 fun findDeclaredMethod(c:Class<*>,n:String,vararg p:Class<*>)=runCatching{c.getDeclaredMethod(n,*p)}.getOrNull()
 fun findDeclaredField(c:Class<*>,n:String)=runCatching{c.getDeclaredField(n)}.getOrNull()
 fun readField(f:Field,o:Any)=f.get(o)
 fun writeField(f:Field,o:Any,v:Any):Boolean {if(failWrite)return false;f.set(o,v);return true}
 var failWrite=false
}
class Fixture {
 val TAG="test";var version="8.0.78";var destroyed=false;var backgroundRenderingEnabled=true
 var enabled=true;var target=BackgroundBeautySettings.Slot.WECHAT
 val homeLists=mutableMapOf<View,BackgroundBeautySettings.Slot>();val activity=Any();val state=State();val states=mutableMapOf(activity to state)
 val hookedMembers=mutableSetOf<Member>();val ownedHooks=mutableListOf<Any>()
 fun chatChromeVersionName()=version
 fun findActivity(v:View):Any?=activity
 fun wechatHomepageBackdropEnabled(a:Any)=enabled
 fun resolveTarget(a:Any)=target
'''+method+'''
 fun install(c:Class<*>?=Native::class.java)=installConversationPullDownShadeHook(c)
}
// Mirrors the verified branch ordering: superclass first, o threshold, then Paint alpha overwrite.
class Native:View() {
 @JvmField var o=120
 var superDraws=0;var rectangles=0;var alpha=255;var first=0;var bottom=70;var taskbar=true
 fun onDraw(c:Canvas){superDraws++;if(taskbar && first==0 && bottom>6 && o>=30){alpha=o;rectangles++}}
}
class Missing:View(){fun onDraw(c:Canvas){}}
class Wrong:View(){@JvmField var o="wrong";fun onDraw(c:Canvas){}}
'''
main = '''fun main(){
 val f=Fixture();val n=Native();f.homeLists[n]=BackgroundBeautySettings.Slot.WECHAT
 check(f.install());check(f.install());check(HookRegistry.hooks.size==1)
 val hook=HookRegistry.hooks.single();val canvas=Canvas()
 fun draw(expect:Boolean,change:()->Unit={}) {
  val p=XC_MethodHook.MethodHookParam(n);val strength=n.o;val count=n.rectangles;val base=n.superDraws
  hook.beforeHookedMethod(p)
  try { n.onDraw(canvas);change() } finally { hook.afterHookedMethod(p) }
  check(n.o==strength);check(n.superDraws==base+1);check((n.rectangles>count)==expect)
 }
 // Multiple pull strengths and theme colors produce no rectangle; superclass is preserved.
 for(v in listOf(0,29,30,70,120,255))for(theme in listOf(0xff797979.toInt(),0xff7a7396.toInt())){n.o=v;n.alpha=theme;draw(false)}
 n.o=120
 f.enabled=false;draw(true);f.enabled=true
 f.backgroundRenderingEnabled=false;draw(true);f.backgroundRenderingEnabled=true
 f.destroyed=true;draw(true);f.destroyed=false
 f.target=BackgroundBeautySettings.Slot.CHAT;draw(true);f.target=BackgroundBeautySettings.Slot.WECHAT
 f.homeLists.remove(n);draw(true);f.homeLists[n]=BackgroundBeautySettings.Slot.WECHAT
 val bitmap=f.state.loadedBitmaps.getValue(BackgroundBeautySettings.Slot.WECHAT).bitmap
 bitmap.isRecycled=true;draw(true);bitmap.isRecycled=false
 val loaded=f.state.loadedBitmaps.remove(BackgroundBeautySettings.Slot.WECHAT)!!;draw(true);f.state.loadedBitmaps[BackgroundBeautySettings.Slot.WECHAT]=loaded
 // Unconditional restore even when gates change during draw.
 draw(false){f.destroyed=true;f.backgroundRenderingEnabled=false};f.destroyed=false;f.backgroundRenderingEnabled=true
 val p=XC_MethodHook.MethodHookParam(n);hook.beforeHookedMethod(p)
 try {check(n.o==0);error("host draw exception")}catch(e:IllegalStateException){}finally{hook.afterHookedMethod(p)}
 check(n.o==120)
 KavaReflector.failWrite=true;draw(true);KavaReflector.failWrite=false
 check(!Fixture().install(Missing::class.java));check(!Fixture().install(Wrong::class.java))
 val other=Fixture();other.version="8.0.77";check(!other.install());check(!other.install(null))
 HookRegistry.fail=true;val retry=Fixture();check(!retry.install());check(retry.hookedMembers.isEmpty());check(HLog.errors==1)
 HookRegistry.fail=false;check(retry.install())
 println("PASS production hook: pull thresholds/themes, native draw retained, disabled/other-page/missing/recycled gates, exception/mid-call restore, metadata/version guards, failed-write passthrough, install retry/dedup")
}
'''
with tempfile.TemporaryDirectory() as td:
    p = Path(td)
    (p/'Fixture.kt').write_text(fixture)
    (p/'View.kt').write_text('package android.view\nopen class View')
    (p/'Canvas.kt').write_text('package android.graphics\nclass Canvas')
    (p/'Main.kt').write_text('import android.graphics.Canvas\n'+main)
    subprocess.run(compiler+['-classpath',str(stdlib),'-d',str(p/'test.jar'),*map(str,p.glob('*.kt'))],check=True,timeout=60)
    subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(stdlib),'MainKt'],check=True,timeout=20)
    (p/'Fixture.kt').write_text(fixture[:fixture.index('// Mirrors the verified')] + '\nclass Native\n')
    subprocess.run(compiler+['-classpath',str(stdlib)+':/usr/lib/android-sdk/platforms/android-36/android.jar','-d',str(p/'sdk.jar'),str(p/'Fixture.kt')],check=True,timeout=60)
    print('PASS production hook Android SDK signature compile (runtime/registry stubs)')
assert 'installConversationPullDownShadeHook(conversationClass)' in source
assert 'param.result' not in method
