"""Exercise original-upload production installation and ad constructor ownership."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'));base=r/'app/src/main/java/t/mma/hooks/items/moments'
s=(base/'OriginalMomentsUploadFeature.kt').read_text();a=s.index('    private val resources');b=s.index('    private fun patchGalleryTimelineArgs',a)
ad=(base/'RemoveMomentsAdsFeature.kt').read_text();aa=ad.index('        private fun installOwned');ab=ad.index('\n    }\n}',aa)
code='''package t.mma.hooks.core
import java.lang.reflect.Method
class Context
class Host(val value:String) {fun preview(){};fun create(){};fun convert(){}}
open class XC_MethodHook {class MethodHookParam {val args:Array<Any?> = emptyArray();var result:Any?="native"};open fun beforeHookedMethod(p:MethodHookParam){};open fun afterHookedMethod(p:MethodHookParam){}}
object HLog {fun e(s:String,t:Throwable?=null){}}
object KavaReflector {var ready=true;fun loadClass(n:String,l:ClassLoader):Class<*>?=if(ready)Host::class.java else null;fun findConstructor(c:Class<*>,p:Class<*>)=c.getDeclaredConstructor(p)}
object HookRegistry {class Handle {var active=true};val handles=mutableListOf<Handle>();val callbacks=mutableListOf<XC_MethodHook>();var fail:Method?=null
 fun get()=this;fun hook(m:java.lang.reflect.Member,c:XC_MethodHook):Handle{if(m==fail)error("hook failure");callbacks+=c;return Handle().also{handles+=it}};fun unhook(h:Handle){h.active=false}}
class Original {val TAG="test";var patched=0
'''+s[a:b]+'''
 fun isEnabled()=true
 fun locateSnsConvertImgMethod(d:Boolean)=Host::class.java.getDeclaredMethod("convert")
 fun locateImagePreviewSendMethod(d:Boolean)=Host::class.java.getDeclaredMethod("preview")
 fun locateSnsCreatePicMethod(d:Boolean)=Host::class.java.getDeclaredMethod("create")
 fun patchGalleryTimelineArgs(a:Array<Any?>?){patched++};fun patchGalleryResultIntent(a:Array<Any?>?){patched++};fun bypassImageReencode(p:XC_MethodHook.MethodHookParam){patched++}
}
internal object Ads {val TAG="test";val AD_INFO_CLASS="host";fun isEnabled(c:Context)=true
'''+ad[aa:ab].replace('private fun installOwned','fun installOwned',1)+'''
}
fun main(){
 val o=Original();HookRegistry.fail=Host::class.java.getDeclaredMethod("create");check(!o.install(false));check(HookRegistry.handles.size==1);HookRegistry.fail=null;check(o.install(true));check(HookRegistry.handles.size==2);check(o.install(true));check(HookRegistry.handles.size==2)
 val cb=HookRegistry.callbacks.first();cb.beforeHookedMethod(XC_MethodHook.MethodHookParam());check(o.patched==1);o.destroy();o.destroy();cb.beforeHookedMethod(XC_MethodHook.MethodHookParam());check(o.patched==1);check(!o.install(true))
 val fresh=Original();check(fresh.install(true));fresh.destroy();check(HookRegistry.handles.none{it.active})
 val c=Context();val loader=Host::class.java.classLoader;val scope=FeatureResourceScope{throw it};KavaReflector.ready=false;val before=HookRegistry.handles.size;Ads.installOwned(c,loader,scope);check(HookRegistry.handles.size==before);KavaReflector.ready=true;Ads.installOwned(c,loader,scope);check(HookRegistry.handles.last().active)
 val acb=HookRegistry.callbacks.last();val p=XC_MethodHook.MethodHookParam();acb.beforeHookedMethod(p);check(p.result==null);scope.close();val stale=XC_MethodHook.MethodHookParam();acb.beforeHookedMethod(stale);check(stale.result=="native")
 val next=FeatureResourceScope{throw it};Ads.installOwned(c,loader,next);check(HookRegistry.handles.last().active);next.close();check(HookRegistry.handles.none{it.active})
 println("PASS original upload partial retry/dedup/release/stale callback and ad missing class/release/fresh reinstall")
}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt'),str(r/'app/src/main/java/t/mma/hooks/core/FeatureResourceScope.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.core.TestKt'],check=True,timeout=15)