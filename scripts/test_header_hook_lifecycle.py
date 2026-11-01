"""Exercise production floating-header Hook ownership and per-entry cleanup."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];s=(r/'app/src/main/java/t/mma/hooks/items/floatingchat/FloatingChatHeaderFeature.kt').read_text()
a=s.index('    @Synchronized\n    private fun hookBefore');b=s.index('    private fun scheduleApply',a)
x=s.index('    private fun releaseSafely');y=s.index('    @Synchronized\n    fun destroy()',x)
code='''package t.mma.hooks.core
import java.lang.reflect.Member
open class XC_MethodHook {class MethodHookParam;open fun beforeHookedMethod(p:MethodHookParam){};open fun afterHookedMethod(p:MethodHookParam){}}
object HLog {fun e(s:String,t:Throwable){}}
class Host {fun before(){};fun after(){}}
object HookRegistry {class Handle(var active:Boolean=true);val handles=mutableListOf<Handle>();val callbacks=mutableListOf<XC_MethodHook>();var fail=false
 fun get()=this;fun hook(m:Member,c:XC_MethodHook):Handle {if(fail)error("hook failure");callbacks+=c;return Handle().also{handles+=it}}
 fun unhook(h:Handle){h.active=false;if(handles.indexOf(h)==0)error("unhook failure")}
}
internal class Runtime {val TAG="test";var closed=false;val hooked=HashSet<Member>();val resources=FeatureResourceScope{throw it};var effects=0
'''+s[a:b]+s[x:y]+'''
 fun addBefore(m:Member)=hookBefore(m){effects++};fun addAfter(m:Member)=hook(m){effects++}
 fun close(){closed=true;resources.close();hooked.clear()}
 fun release(map:MutableMap<Int,Int>,seen:MutableList<Int>){releaseEntries(map){k,v->seen+=k;if(k==1)error("restore failure")}}
}
fun main(){val r=Runtime();val before=Host::class.java.getDeclaredMethod("before");val after=Host::class.java.getDeclaredMethod("after")
 HookRegistry.fail=true;check(r.addBefore(before)==0);HookRegistry.fail=false;check(r.addBefore(before)==1);check(r.addAfter(after)==1);check(r.addBefore(before)==1&&HookRegistry.handles.size==2)
 val p=XC_MethodHook.MethodHookParam();HookRegistry.callbacks.forEach{it.beforeHookedMethod(p);it.afterHookedMethod(p)};check(r.effects==2)
 r.close();check(HookRegistry.handles.none{it.active});HookRegistry.callbacks.forEach{it.beforeHookedMethod(p);it.afterHookedMethod(p)};check(r.effects==2&&r.addBefore(before)==0)
 val map=linkedMapOf(1 to 1,2 to 2);val seen=mutableListOf<Int>();r.release(map,seen);check(seen==listOf(1,2)&&map.isEmpty())
 val fresh=Runtime();check(fresh.addBefore(before)==1);fresh.close();check(HookRegistry.handles.none{it.active})
 println("PASS production header before/after ownership, retry/dedup, stale callbacks, unhook failure isolation, per-entry restore and fresh reinstall")
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt'),str(r/'app/src/main/java/t/mma/hooks/core/FeatureResourceScope.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.core.TestKt'],check=True,timeout=15)