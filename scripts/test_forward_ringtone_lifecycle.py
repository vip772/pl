"""Exercise production forward/ringtone hook registrars against failures and late close."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
def method(s,marker):
 a=s.index(marker);start=s.index('{',a);depth=1;b=start+1
 while depth:
  if s[b]=='{':depth+=1
  elif s[b]=='}':depth-=1
  b+=1
 return s[a:b].replace('private fun','fun',1)
f=(r/'app/src/main/java/t/mma/hooks/items/forwardlimit/RemoveForwardLimitFeature.kt').read_text();g=(r/'app/src/main/java/t/mma/hooks/items/callmedialimit/CallRingtoneBlockFeature.kt').read_text()
code='''package t.mma.hooks.core
import java.lang.reflect.Method
open class XC_MethodHook {class Unhook {var active=true};class MethodHookParam;open fun beforeHookedMethod(param:MethodHookParam){}}
object HookRegistry {
 val handles=mutableListOf<XC_MethodHook.Unhook>();val callbacks=mutableListOf<XC_MethodHook>();var fail=false;var late:(()->Unit)?=null
 fun get()=this
 fun hook(m:Method,c:XC_MethodHook):XC_MethodHook.Unhook {
  if(fail){fail=false;error("failure")};callbacks+=c
  return XC_MethodHook.Unhook().also{handles+=it;late?.invoke()}
 }
 fun unhook(h:XC_MethodHook.Unhook){h.active=false}
}
internal class Forward {
 val resources=FeatureResourceScope{throw it};val hookedMethods=mutableSetOf<Method>()
 fun logger(s:String,t:Throwable){}
'''+method(f,'    private fun hook(')+'''
}
internal class Ring {
 val resources=FeatureResourceScope{throw it};val hookedMethods=mutableSetOf<Method>()
'''+method(g,'    private fun hook(')+'''
}
fun main(){
 val a=String::class.java.getDeclaredMethod("length");val b=String::class.java.getDeclaredMethod("isEmpty")
 val f=Forward();var hits=0;HookRegistry.fail=true;check(!f.hook(a,"test"){hits++});check(f.hookedMethods.isEmpty())
 check(f.hook(a,"test"){hits++});val first=HookRegistry.handles.last();check(f.hook(a,"test"){hits++});check(HookRegistry.handles.last()===first)
 val cb=HookRegistry.callbacks.last();cb.beforeHookedMethod(XC_MethodHook.MethodHookParam());check(hits==1)
 f.resources.close();check(!first.active);cb.beforeHookedMethod(XC_MethodHook.MethodHookParam());check(hits==1)
 val late=Forward();HookRegistry.late={late.resources.close()};check(late.hook(a,"late"){});check(!HookRegistry.handles.last().active);HookRegistry.late=null
 val ring=Ring();ring.hook(a,XC_MethodHook());val existing=HookRegistry.handles.last()
 HookRegistry.fail=true;check(runCatching{ring.hook(b,XC_MethodHook())}.isFailure);check(b !in ring.hookedMethods)
 ring.hook(a,XC_MethodHook());check(HookRegistry.handles.last()===existing)
 ring.hook(b,XC_MethodHook());ring.resources.close();check(HookRegistry.handles.none{it.active})
 val old=Ring();HookRegistry.late={old.resources.close()};old.hook(a,XC_MethodHook());check(!HookRegistry.handles.last().active)
 println("PASS production forward/ringtone registrars: retry, partial install dedup, release, stale forward callback and late handles")
}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt'),str(r/'app/src/main/java/t/mma/hooks/core/FeatureResourceScope.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.core.TestKt'],check=True,timeout=15)