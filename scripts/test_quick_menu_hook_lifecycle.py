"""Exercise production menu hook registrars with failing and late synthetic handles."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
paths=['quickcontactedit/QuickContactEditFeature.kt','quickmoments/QuickMomentsFeature.kt','grouplabel/QuickGroupChatLabelFeature.kt','conversationanalysis/ConversationAnalysisMessageMenuRuntime.kt']
blocks=[]
for i,name in enumerate(paths):
 s=(r/'app/src/main/java/t/mma/hooks/items'/name).read_text();a=s.index('    private fun hook(method: Method, callback: XC_MethodHook): Boolean {');pos=a;level=0;started=False
 for pos in range(a,len(s)):
  if s[pos]=='{':level+=1;started=True
  elif s[pos]=='}':
   level-=1
   if started and level==0:break
 method=s[a:pos+1].replace('private fun hook(','fun hook(',1)
 blocks.append('internal class Owner'+str(i)+''' {
 val resources=FeatureResourceScope { errors++ };var errors=0
 val hookedMethods=mutableSetOf<Method>()
 fun logger(s:String,t:Throwable?){}
'''+method+'\n}\n')
code='''package t.mma.hooks.core
import java.lang.reflect.Method
import java.lang.reflect.Modifier
class XC_MethodHook {class Unhook {var active=true}}
object KavaReflector {fun accessible(m:Method):Method?=m}
object HookRegistry {
 val handles=mutableListOf<XC_MethodHook.Unhook>();var fail=false;var late:(()->Unit)?=null
 fun get()=this
 fun hook(m:Method,c:XC_MethodHook):XC_MethodHook.Unhook {
  if(fail){fail=false;error("hook failure")};val h=XC_MethodHook.Unhook();handles+=h;late?.invoke();return h
 }
 fun unhook(h:XC_MethodHook.Unhook){h.active=false}
}
'''+''.join(blocks)+'''fun main(){
 val m=String::class.java.getDeclaredMethod("length")
'''
for i in range(len(paths)):
 code+='''run {
 val o=OwnerN();HookRegistry.fail=true
 check(!o.hook(m,XC_MethodHook()));check(o.hookedMethods.isEmpty())
 check(o.hook(m,XC_MethodHook()));val old=HookRegistry.handles.last()
 check(o.hook(m,XC_MethodHook()));check(HookRegistry.handles.last()===old)
 o.resources.close();check(!old.active)
 val late=OwnerN();HookRegistry.late={late.resources.close()}
 check(late.hook(m,XC_MethodHook()));check(!HookRegistry.handles.last().active)
 HookRegistry.late=null
}
'''.replace('OwnerN','Owner'+str(i))
code+='println("PASS four production menu hook registrars: failure retry, deduplication, close unhook, late handle release")\n}'
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt'),str(r/'app/src/main/java/t/mma/hooks/core/FeatureResourceScope.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.core.TestKt'],check=True,timeout=15)
