"""Exercise production RealNameTailFeature dual-enable expression and color listener
against actual PreferenceRuntimeOwner/FeatureResourceScope and synthetic preferences.
No Android/WeChat host validation.
"""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
s=(r/'app/src/main/java/t/mma/hooks/items/realtail/RealNameTailFeature.kt').read_text()
expression=s[s.index('            enabled = {')+len('            enabled = {'):s.index('            }, create = createRuntime@')]
listener=s[s.index('        val colorListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener'):s.index('        subscribe(Events.DexReady::class.java)')]
assert 'colorPrefs.registerOnSharedPreferenceChangeListener(colorListener)' in listener
assert 'trackResource { colorPrefs.unregisterOnSharedPreferenceChangeListener(colorListener) }' in listener
prefs='''package android.content
interface SharedPreferences {
 fun getBoolean(key:String,def:Boolean):Boolean
 fun registerOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener)
 fun unregisterOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener)
 fun interface OnSharedPreferenceChangeListener {fun onSharedPreferenceChanged(p:SharedPreferences,key:String?)}
}
'''
test='''package t.mma.hooks.core
import android.content.SharedPreferences
object RealNameTailSettings {const val KEY_ENABLE="enable";const val DEFAULT_ENABLE=false}
object GroupNicknameColorSettings {const val KEY_ENABLE="enable";const val DEFAULT_ENABLE=false}
class Prefs:SharedPreferences {
 val listeners=mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()
 var value=false;var failRemove=false
 override fun getBoolean(key:String,def:Boolean)=value
 override fun registerOnSharedPreferenceChangeListener(l:SharedPreferences.OnSharedPreferenceChangeListener){listeners+=l}
 override fun unregisterOnSharedPreferenceChangeListener(l:SharedPreferences.OnSharedPreferenceChangeListener){listeners.remove(l);if(failRemove)error("unregister")}
 fun set(v:Boolean){value=v;listeners.toList().forEach{it.onSharedPreferenceChanged(this,"enable")}}
}
fun main(){
 val prefs=Prefs();val colorPrefs=Prefs();val resources=mutableListOf<()->Unit>()
 fun trackResource(f:()->Unit){resources+=f}
 var starts=0;var stops=0;var fail=false;var errors=0
 val owner=PreferenceRuntimeOwner(prefs,setOf(RealNameTailSettings.KEY_ENABLE),
 enabled={''' + expression + '''},create={scope->starts++;scope.register{stops++};if(fail)error("create")},onError={errors++})
 trackResource {owner.close()}
''' + listener + '''
 owner.start();check(starts==0&&prefs.listeners.size==1&&colorPrefs.listeners.size==1)
 colorPrefs.set(true);check(starts==1&&stops==0)
 prefs.set(true);check(starts==1&&stops==0)
 colorPrefs.set(false);check(starts==1&&stops==0)
 prefs.set(false);check(stops==1)
 fail=true;colorPrefs.set(true);check(starts==2&&stops==2&&errors==1)
 fail=false;owner.refresh();check(starts==3&&stops==2)
 prefs.set(true);colorPrefs.set(false);check(starts==3&&stops==2)
 val stale=colorPrefs.listeners.single();colorPrefs.failRemove=true
 resources.asReversed().forEach {try{it()}catch(_:Throwable){}}
 check(stops==3&&prefs.listeners.isEmpty()&&colorPrefs.listeners.isEmpty())
 stale.onSharedPreferenceChanged(colorPrefs,"enable");prefs.set(false);check(starts==3)
 val newOwner=PreferenceRuntimeOwner(prefs,setOf("enable"),{prefs.value || colorPrefs.value},{scope->starts++;scope.register{stops++}},{throw it})
 newOwner.start();check(starts==3);prefs.set(true);check(starts==4);newOwner.close();check(stops==4)
 println("PASS actual dual-toggle expression/listener with preference owner: OR enable, partial rollback/retry, independent switch, destroy and new owner")
}
'''
with tempfile.TemporaryDirectory(prefix='pl-realtail-toggle-') as td:
 p=Path(td);(p/'Prefs.kt').write_text(prefs);(p/'Test.kt').write_text(test)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-nowarn','-classpath',str(std),'-d',str(p/'out'),str(p/'Prefs.kt'),str(p/'Test.kt'),str(r/'app/src/main/java/t/mma/hooks/core/PreferenceRuntimeOwner.kt'),str(r/'app/src/main/java/t/mma/hooks/core/FeatureResourceScope.kt')],check=True,timeout=90)
 subprocess.run(['java','-cp',str(p/'out')+':'+str(std),'t.mma.hooks.core.TestKt'],check=True,timeout=15)
