"""SDK compile production feature and exercise its reversible maxWidth ownership."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
source=r/'app/src/main/java/t/mma/hooks/items/nicknamewidth/NicknameWidthFeature.kt'
s=source.read_text();libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
cmd=['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect']
stubs={
'Core.kt':'''package t.mma.hooks.core
open class BaseFeature {open fun featureId()="";open fun name()="";open fun onFeatureInit(c:FeatureContext){};open fun onFeatureInstall(c:FeatureContext){};open fun onFeatureDestroy(c:FeatureContext){};fun registerSettingsProvider(p:Any){}}
class FeatureContext {fun hostContext():android.content.Context=TODO();fun hostClassLoader():ClassLoader=TODO()}
object HookRegistry {fun get()=this;fun hook(m:java.lang.reflect.Method,h:de.robv.android.xposed.XC_MethodHook):de.robv.android.xposed.XC_MethodHook.Unhook?=null;fun unhook(h:de.robv.android.xposed.XC_MethodHook.Unhook?){}}
''',
'Xposed.kt':'''package de.robv.android.xposed
open class XC_MethodHook {class Unhook;class MethodHookParam(val args:Array<Any?>);open fun afterHookedMethod(p:MethodHookParam){}}
''',
'Store.kt':'''package t.mma.preferences
object HchatStorage {fun preferences(c:android.content.Context,n:String):android.content.SharedPreferences=TODO()}
''',
'Ui.kt':'''package t.mma.ui
object FeatureSettingsProvider {const val CATEGORY_ENHANCE="enhance"}
open class SimpleFeatureSettingsProvider(a:String,b:String,c:String,d:String,e:Boolean)
''',
'Log.kt':'''package t.mma.utils
object HLog {fun w(s:String){};fun e(s:String,t:Throwable){}}
'''}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for n,v in stubs.items():(p/n).write_text(v)
 subprocess.run(cmd+['-classpath',f'{std}:/usr/lib/android-sdk/platforms/android-36/android.jar','-d',str(p/'sdk.jar'),str(source),*map(str,p.glob('*.kt'))],check=True,timeout=45)
 state=s[s.index('internal class NicknameWidthState'):s.index('/** Verified 8.0.78')]
 fixture='''import java.util.WeakHashMap
class TextView(width:Int){var writes=0;var maxWidth=width;set(v){field=v;writes++}}
'''+state+'''
fun main(){val s=NicknameWidthState();val v=TextView(240)
 s.bind(v,false,true);check(v.maxWidth==240 && v.writes==0)
 s.bind(v,true,false);check(v.writes==0)
 s.bind(v,true,true);check(v.maxWidth==Int.MAX_VALUE && v.writes==1)
 repeat(10000){s.bind(v,true,true)};check(v.writes==1)
 s.bind(v,true,false);check(v.maxWidth==240 && v.writes==2)
 s.bind(v,true,true);s.restoreAll();check(v.maxWidth==240)
 s.bind(v,true,true);v.maxWidth=180;s.restoreAll();check(v.maxWidth==180)
 s.bind(v,true,true);v.maxWidth=200;s.bind(v,true,true);s.restoreAll();check(v.maxWidth==200)
 val unlimited=TextView(Int.MAX_VALUE);s.bind(unlimited,true,true);s.restoreAll();check(unlimited.writes==0)
 val ems=TextView(-1);s.bind(ems,true,true);check(ems.writes==0)
 println("PASS default off, private chat, one write across 10000 binds, recycled view, restore, external change and unlimited/ems guards")}
'''
 (p/'Test.kt').write_text(fixture)
 subprocess.run(cmd+['-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True)
assert 'CATEGORY_ENHANCE, true' in s
for forbidden in ['addOnLayoutChangeListener','OnPreDrawListener','setText','setSingleLine','setMaxLines','findViewById','dexKitBridge']:
 assert forbidden not in s,forbidden
print('PASS production feature compiles against Android SDK; no per-frame scanning/text mutation')