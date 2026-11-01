"""Compile actual upload-tail runtime against synthetic Dex/API/hook fixtures."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
s=(r/'app/src/main/java/t/mma/hooks/items/moments/MomentsUploadTailFeature.kt').read_text();s=s[s.index('private class MomentsUploadTailHooker'):].replace('private class MomentsUploadTailHooker','class MomentsUploadTailHooker',1)
code='''package t.mma.hooks.core
import java.lang.reflect.Modifier
class Host {fun commit(){};fun id(){};fun appName(){}}
class Finder {var ready=false;fun resolveSnsUploadApi(){};val snsCommitMethod get()=if(ready)Host::class.java.getDeclaredMethod("commit") else null;val snsSetSdkIdMethod get()=Host::class.java.getDeclaredMethod("id");val snsSetSdkAppNameMethod get()=Host::class.java.getDeclaredMethod("appName")}
class FeatureContext {val finder=Finder();fun hostContext()=this;fun dexFinder()=finder}
class Prefs {fun getBoolean(k:String,d:Boolean)=true;fun getString(k:String,d:String):String?="value"}
object HchatStorage {fun preferences(c:FeatureContext,n:String)=Prefs()}
object MomentsUploadTailSettings {val PREFS_NAME="p";val KEY_ENABLE="enable";val DEFAULT_ENABLE=true;val KEY_SDK_ID="id";val DEFAULT_SDK_ID="id";val KEY_SDK_APP_NAME="name";val DEFAULT_SDK_APP_NAME="name"}
open class XC_MethodHook {class MethodHookParam {val thisObject=Host()};open fun beforeHookedMethod(p:MethodHookParam){}}
object KavaReflector {var calls=0;fun invoke(m:java.lang.reflect.Method,o:Any,v:String){calls++}}
object HookRegistry {class Handle {var active=true};val handles=mutableListOf<Handle>();val callbacks=mutableListOf<XC_MethodHook>();var fail=false;fun get()=this;fun hook(m:java.lang.reflect.Method,c:XC_MethodHook):Handle{if(fail)error("failure");callbacks+=c;return Handle().also{handles+=it}};fun unhook(h:Handle){h.active=false}}
'''+s+'''
fun main(){val c=FeatureContext();val owner=MomentsUploadTailHooker(c){_,_->};check(!owner.install());c.finder.ready=true;HookRegistry.fail=true;check(!owner.install());HookRegistry.fail=false;check(owner.install());check(owner.install());check(HookRegistry.handles.size==1)
 val cb=HookRegistry.callbacks.last();cb.beforeHookedMethod(XC_MethodHook.MethodHookParam());check(KavaReflector.calls==2);owner.destroy();owner.destroy();cb.beforeHookedMethod(XC_MethodHook.MethodHookParam());check(KavaReflector.calls==2);check(!owner.install());check(!HookRegistry.handles.last().active)
 val fresh=MomentsUploadTailHooker(c){_,_->};check(fresh.install());fresh.destroy();println("PASS upload tail missing-method retry, failure retry, deduplication, release, stale callback and fresh owner")}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt'),str(r/'app/src/main/java/t/mma/hooks/core/FeatureResourceScope.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.core.TestKt'],check=True,timeout=15)