"""Run production overlay ownership and background restore methods on JVM stubs."""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/floatingchat/ChatHeaderMount.kt').read_text()
a=s.index('    private fun enableWindowOverlay(');b=s.index('    private fun createState(',a)
c=s.index('    private fun releaseSafely(');d=s.index('    @Synchronized',c)
bg=(r/'app/src/main/java/t/mma/hooks/items/floatingchat/FloatingChatActionBarBackgrounds.kt').read_text()
a2=bg.index('    fun restore(view: View)');b2=bg.rindex('}')
start=s.index('        private val pendingRestores =');end=s.index('        private const val TAG',start)
barrier=s[start:end]
code="""
import android.view.View
import androidx.appcompat.widget.ActionBarOverlayLayout
import java.lang.reflect.Method
object HLog {fun e(s:String,t:Throwable){error("logger")}}
class Runtime {val TAG="test";var closed=false;val windowOverlayStates=linkedMapOf<View,Boolean>()
"""+s[a:b]+s[c:d]+"""
fun apply(v:View)=enableWindowOverlay(v);fun restore()=restoreWindowOverlays()
}
class Background {val originals=linkedMapOf<View,MutableMap<Method,Any?>>();var writing=false
"""+bg[a2:b2]+"""
}
class HeaderRuntime(val fail:Boolean=false){var restored=0;fun releaseSafely(action:()->Unit){try{action()}catch(_:Throwable){}};fun restoreOwnedViews(){restored++;if(fail)error("restore")};fun retire(){synchronized(pendingRestores){pendingRestores.add(this);hasPendingRestores=true}}
companion object {
"""+barrier+"""
fun drain()=drainPendingRestores()
}}
class Layers:View(){var attempts=0;fun first(v:Any?){attempts++;error("layer")};fun second(v:Any?){attempts++};fun third(v:Any?){attempts++}}
fun main(){
val retired=HeaderRuntime(true);val next=HeaderRuntime();retired.retire();next.retire();HeaderRuntime.drain();check(retired.restored==1&&next.restored==1);HeaderRuntime.drain();check(retired.restored==1&&next.restored==1)
for(original in listOf(false,true)){val rt=Runtime();val overlay=ActionBarOverlayLayout(original);val header=View();header.parent=overlay;rt.apply(header);check(overlay.mode);check(rt.windowOverlayStates[overlay]==original);rt.apply(header);check(overlay.writes==1);rt.restore();check(overlay.mode==original&&rt.windowOverlayStates.isEmpty());rt.restore();check(overlay.writes==2)}
val rt=Runtime();val fail=ActionBarOverlayLayout(false);fail.fail=true;val ok=ActionBarOverlayLayout(true);rt.windowOverlayStates[fail]=false;rt.windowOverlayStates[ok]=true;rt.restore();check(fail.writes==1&&ok.writes==1&&rt.windowOverlayStates.isEmpty())
val partial=ActionBarOverlayLayout(false);partial.fail=true;val h=View();h.parent=partial;rt.apply(h);check(rt.windowOverlayStates[partial]==false&&partial.mode);partial.fail=false;rt.restore();check(!partial.mode)
val closed=Runtime();closed.closed=true;val other=ActionBarOverlayLayout(false);h.parent=other;closed.apply(h);check(!other.mode&&other.writes==0)
val bg=Background();val layers=Layers();bg.originals[layers]=linkedMapOf(*listOf("first","second","third").map{layers.javaClass.getMethod(it,Any::class.java) to null}.toTypedArray());bg.restore(layers);check(layers.attempts==3&&!bg.writing&&bg.originals.isEmpty());bg.restore(layers);check(layers.attempts==3)
println("PASS production overlay original false/true, repeated claim, partial setter failure, restore isolation, closed gate; three background layers and writing reset")}
"""
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 (p/'View.kt').write_text('package android.view; open class View {var parent:Any?=null}')
 (p/'Overlay.kt').write_text('package androidx.appcompat.widget; class ActionBarOverlayLayout(var mode:Boolean):android.view.View(){var writes=0;var fail=false;fun isInOverlayMode()=mode;fun setOverlayMode(v:Boolean){writes++;mode=v;if(fail)error("setter")}}')
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),*map(str,p.glob('*.kt'))],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=15)
