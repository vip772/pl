"""Exercise production background hook registration and late close on the JVM."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
s=(r/'app/src/main/java/t/mma/hooks/items/backgroundbeauty/BackgroundBeautyFeature.kt').read_text()
a=s.index('    private fun hookOnce(');b=s.index('    private val framePageSlots',a)
method=s[a:b].replace('private fun hookOnce(', 'fun hookOnce(',1)
a=s.index('    private fun ownHook(');b=s.index('\n    }',a)+6
own=s[a:b].replace('private fun ownHook(', 'fun ownHook(',1)
code='''package t.mma.hooks.core
import java.lang.reflect.Member
open class XC_MethodHook {
 class MethodHookParam
 class Unhook {var active=true}
 open fun beforeHookedMethod(param:MethodHookParam){}
 open fun afterHookedMethod(param:MethodHookParam){}
}
object HLog {fun e(s:String,t:Throwable){}}
object HookRegistry {
 val handles=mutableListOf<XC_MethodHook.Unhook>();var fail=false;var late:(()->Unit)?=null
 fun get()=this
 fun hook(m:Member,c:XC_MethodHook):XC_MethodHook.Unhook {
  if(fail){fail=false;error("install failure")}
  return XC_MethodHook.Unhook().also {handles+=it;late?.invoke()}
 }
 fun unhook(h:XC_MethodHook.Unhook){h.active=false}
}
internal class Owner {
 val hookResources=FeatureResourceScope {throw it};val TAG="test"
 var destroyed=false;var backgroundRenderingEnabled=true
 val hookedMembers=mutableSetOf<Member>()
'''+method+own+'''
 fun close(){destroyed=true;hookResources.close()}
}
fun main(){
 val m=String::class.java.getDeclaredMethod("length")
 val o=Owner();HookRegistry.fail=true;check(!o.hookOnce(m));check(o.hookedMembers.isEmpty())
 check(o.hookOnce(m));val first=HookRegistry.handles.single();check(o.hookOnce(m));check(HookRegistry.handles.size==1)
 o.close();o.close();check(!first.active);check(!o.hookOnce(m))
 val late=Owner();HookRegistry.late={late.close()};check(late.hookOnce(m));check(!HookRegistry.handles.last().active);HookRegistry.late=null
 val direct=XC_MethodHook.Unhook();late.ownHook(direct);check(!direct.active)
 println("PASS background production hook retry, dedup, close, post-close gate and late direct/standard handle release")
}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt'),str(r/'app/src/main/java/t/mma/hooks/core/FeatureResourceScope.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.core.TestKt'],check=True,timeout=15)
