"""Production renderer install/destroy methods, real OwnedHooks, synthetic UI/gate host.
No native nickname gate/network/device validation. Includes rejected/late hook and cleanup faults.
"""
import ast, subprocess, tempfile
from pathlib import Path
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs')
s=(r/'app/src/main/java/t/mma/hooks/items/realtail/RealNameTailRenderer.kt').read_text()
def method(needle):
 a=s.index(needle);a=s.rfind(chr(10),0,a)+1;b=s.index('{',a);depth=1;i=b+1
 while depth:depth+=(s[i]=='{')-(s[i]=='}');i+=1
 return s[a:i]
stubs={}
for n in ast.parse((r/'scripts/test_round_avatar_owner.py').read_text()).body:
 if isinstance(n,ast.Assign) and any(isinstance(x,ast.Name) and x.id=='stubs' for x in n.targets):stubs=ast.literal_eval(n.value)
stubs['Xposed.kt'] = stubs['Xposed.kt'].replace(' open fun beforeHookedMethod', ' open fun afterHookedMethod(param:MethodHookParam) {}'+chr(10)+' open fun beforeHookedMethod')
owned=(r/'app/src/main/java/t/mma/hooks/core/FeatureOwnership.kt').read_text()
owned=owned[owned.index('private fun reportOwnershipError'):owned.index('/** 本功能独占的主线程投递')]
code='''package t.mma.hooks.items.realtail
import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.core.*
import java.util.concurrent.atomic.AtomicBoolean
private class View {var removals=0;fun removeCallbacks(r:Runnable){removals++;error("remove fault")}}
private class Gate {var releases=0;fun release(){releases++;error("gate release fault")}}
private object NativeGroupNicknameGate {
 var last:Gate?=null
 fun acquire(c:FeatureContext,m:java.lang.reflect.Method,l:(String,Throwable?)->Unit):Gate=Gate().also{last=it}
}
private class Tasks {
 var cancels=0
 fun cancel(k:String){cancels++;error("cancel fault")}
 fun runOnMain(f:()->Unit){f()}
}
private object WeChatApis {val t=Tasks();fun tasks():Tasks?=t}
private class Renderer {
 val active=AtomicBoolean(true)
 var installed=false
 val logger:(String,Throwable?)->Unit={_,_->error("logger fault")}
 val hooks=OwnedHooks {try{logger("hook",it)}catch(_:Throwable){}}
 var nativeNicknameGate:Gate?=null
 var profilePrefetcher:Any?=Any()
 val context=FeatureContext()
 val boundViews=mutableMapOf<View,String>()
 val colorRebinds=mutableMapOf<View,Runnable>()
 val msgSenderCache=mutableMapOf<Any,String>()
 val holderTextFieldCache=mutableMapOf<Class<*>,Any>()
 val likelyMsgCache=mutableMapOf<Class<*>,Boolean>()
 val inSetText=ThreadLocal<Boolean>()
 var hidden=0;var effects=0
 fun hideTail(v:View){hidden++;error("hide tail fault")}
 fun hideNicknameColor(v:View){hidden++}
 fun locateUsernameBindMethod()=Fixture::class.java.getDeclaredMethod("bind")
 fun handleUsernameBind(p:XC_MethodHook.MethodHookParam){if(active.get())effects++}
'''+chr(10).join(method(x) for x in ['fun release(', 'fun destroy()', 'fun install():', 'fun installUsernameBindHook()'])+'''
}
private object Fixture {@JvmStatic fun bind() {}}
fun main(){
 val registry=HookRegistry.get()
 registry.reset();registry.failAt=1
 val failed=Renderer();check(!failed.install());val first=NativeGroupNicknameGate.last!!
 check(first.releases==1);check(failed.nativeNicknameGate==null)
 registry.failAt=0;check(failed.install());val handle=registry.installed.single();handle.fail=true
 handle.callback.afterHookedMethod(XC_MethodHook.MethodHookParam(emptyArray()));check(failed.effects==1)
 val view=View();failed.boundViews[view]="sender";failed.colorRebinds[view]=Runnable{}
 failed.msgSenderCache[Any()]="sender";failed.holderTextFieldCache[Any::class.java]=Any();failed.likelyMsgCache[Any::class.java]=true
 failed.destroy();failed.destroy()
 check(handle.releases==1&&NativeGroupNicknameGate.last!!.releases==1)
 check(view.removals==1&&failed.hidden==2&&WeChatApis.t.cancels==1)
 check(failed.boundViews.isEmpty()&&failed.colorRebinds.isEmpty()&&failed.msgSenderCache.isEmpty())
 check(failed.holderTextFieldCache.isEmpty()&&failed.likelyMsgCache.isEmpty()&&failed.profilePrefetcher==null)
 check(!failed.installed&&!failed.install())
 handle.callback.afterHookedMethod(XC_MethodHook.MethodHookParam(emptyArray()));check(failed.effects==1)
 println("PASS renderer rejected hook releases gate; retry works; unhook/gate/cancel/remove/UI/logger failures isolated; stale callback inert")
 registry.reset();val late=Renderer();registry.beforeHook={registry.beforeHook=null;late.destroy()}
 check(!late.install());check(registry.installed.single().releases==1)
 check(late.nativeNicknameGate==null&&!late.installed)
 println("PASS renderer destroy during registration releases late hook and gate; closed renderer cannot resurrect")
}
'''
with tempfile.TemporaryDirectory(prefix='pl-realtail-owner-') as td:
 t=Path(td)
 for n,b in stubs.items():(t/n).write_text(b)
 (t/'Ownership.kt').write_text('package t.mma.hooks.core\nimport de.robv.android.xposed.XC_MethodHook\nimport java.lang.reflect.Member\n'+owned)
 (t/'Check.kt').write_text(code)
 std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-nowarn','-classpath',str(std),'-d',str(t/'out'),*map(str,t.glob('*.kt'))],check=True,timeout=100)
 subprocess.run(['java','-cp',str(t/'out')+':'+str(std),'t.mma.hooks.items.realtail.CheckKt'],check=True,timeout=20)