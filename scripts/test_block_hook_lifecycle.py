"""Compile production typing/pat install and destroy methods with synthetic hook handles."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
blocks=[]
for i,(folder,name,locate) in enumerate([('typingreport','TypingReportBlockFeature','locateSendTypingMethod'),('patblock','PatBlockFeature','locateAvatarDoubleClickMethod')]):
 s=(r/f'app/src/main/java/t/mma/hooks/items/{folder}/{name}.kt').read_text()
 a=s.index('    private var installed = false');b=s.index('    private fun '+locate,a)
 blocks.append('internal class Owner'+str(i)+''' {
 var failures=0;fun logger(s:String,t:Throwable?){failures++}
 fun isEnabled()=true
 fun '''+locate+'''(allowDexSearch:Boolean)=String::class.java.getDeclaredMethod("length")
'''+s[a:b]+'\n}\n')
code='''package t.mma.hooks.core
open class XC_MethodHook {
 class MethodHookParam {var changed=false;var result:Any?=null;set(value){field=value;changed=true}}
 class Unhook {var active=true}
 open fun beforeHookedMethod(param:MethodHookParam){}
}
object HookRegistry {
 val handles=mutableListOf<XC_MethodHook.Unhook>();val callbacks=mutableListOf<XC_MethodHook>();var fail=false
 fun get()=this
 fun hook(m:java.lang.reflect.Method,c:XC_MethodHook):XC_MethodHook.Unhook {
  if(fail){fail=false;error("install failure")}
  callbacks+=c;return XC_MethodHook.Unhook().also {handles+=it}
 }
 fun unhook(h:XC_MethodHook.Unhook){h.active=false}
}
'''+''.join(blocks)+'fun main(){\n'
for i in range(2):
 code+='''run {
 val o=OwnerN();HookRegistry.fail=true;check(!o.install(false));check(o.failures==1)
 check(o.install(false));val h=HookRegistry.handles.last();val count=HookRegistry.handles.size
 check(o.install(true));check(HookRegistry.handles.size==count)
 val cb=HookRegistry.callbacks.last();val before=XC_MethodHook.MethodHookParam();cb.beforeHookedMethod(before);check(before.changed)
 o.destroy();o.destroy();check(!h.active);check(!o.install(true))
 val stale=XC_MethodHook.MethodHookParam();cb.beforeHookedMethod(stale);check(!stale.changed)
 val fresh=OwnerN();check(fresh.install(true));check(HookRegistry.handles.last().active);fresh.destroy()
}
'''.replace('OwnerN','Owner'+str(i))
code+='println("PASS production typing and pat: failure retry, dedup, destroy unhook, stale callback gate, fresh runtime reinstall")\n}'
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt'),str(r/'app/src/main/java/t/mma/hooks/core/FeatureResourceScope.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.core.TestKt'],check=True,timeout=15)