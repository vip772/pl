"""Compile production toggle expression with PreferenceRuntimeOwner and faulted private resources.
The renderer, contact API and Dex host are synthetic boundaries.
"""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
s=(r/'app/src/main/java/t/mma/hooks/items/membertitle/MemberTitleFeature.kt').read_text()
start=s.index('            enabled = { ')+len('            enabled = { ');end=s.index(' },',start)
expression=s[start:end]
assert 'MemberTitleSettings.KEY_ENABLE' in expression
assert s.index('resources.register {') < s.index('scheduleRuntime?.invoke()')
assert 'resources.register { try { installation.close() } finally { scheduleRuntime = null } }' in s
assert 'if (resources.isClosed() || !store.isEnabled()) return@subscribe' in s
prefs='''package android.content
interface SharedPreferences {
 fun getBoolean(key:String,def:Boolean):Boolean
 fun registerOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener)
 fun unregisterOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener)
 fun interface OnSharedPreferenceChangeListener {fun onSharedPreferenceChanged(p:SharedPreferences,k:String?)}
}
'''
body='''package t.mma.hooks.core
import android.content.SharedPreferences
object MemberTitleSettings {const val KEY_ENABLE="enable";const val DEFAULT_ENABLE=false}
class Prefs:SharedPreferences {
 val listeners=mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>();var enabled=false
 override fun getBoolean(key:String,def:Boolean)=enabled
 override fun registerOnSharedPreferenceChangeListener(l:SharedPreferences.OnSharedPreferenceChangeListener){listeners+=l}
 override fun unregisterOnSharedPreferenceChangeListener(l:SharedPreferences.OnSharedPreferenceChangeListener){listeners.remove(l)}
 fun set(v:Boolean){enabled=v;listeners.toList().forEach{it.onSharedPreferenceChanged(this,"enable")}}
}
fun main(){
 val prefs=Prefs();var births=0;var tickets=0;var hooks=0;var subs=0;var errors=0;var fail=false
 val stale=mutableListOf<()->Unit>();var effects=0
 val owner=PreferenceRuntimeOwner(prefs,setOf(MemberTitleSettings.KEY_ENABLE),enabled={''' + expression + '''},create={scope->
  births++;scope.register{hooks++};scope.register{tickets++};scope.register{subs++}
  stale+={if(!scope.isClosed()) effects++}
  if(fail)error("subscription rejected")
 },onError={errors++})
 owner.start();check(births==0&&prefs.listeners.size==1)
 prefs.set(true);check(births==1&&hooks==0);stale.last()();check(effects==1)
 prefs.set(false);stale.last()();check(hooks==1&&tickets==1&&subs==1&&effects==1)
 fail=true;prefs.set(true);check(births==2&&hooks==2&&tickets==2&&subs==2&&errors==1)
 fail=false;owner.refresh();check(births==3);prefs.set(true);check(births==3)
 owner.close();owner.close();stale.last()();check(hooks==3&&tickets==3&&subs==3&&effects==1&&prefs.listeners.isEmpty())
 prefs.set(false);prefs.set(true);check(births==3)
 val fresh=PreferenceRuntimeOwner(prefs,setOf("enable"),{prefs.enabled},{scope->births++;scope.register{hooks++}},{throw it})
 fresh.start();check(births==4);fresh.close();check(hooks==4)
 println("PASS member-title enable expression: disabled startup, close/reopen, failed creation rollback, old callbacks inert, new instance")
}
'''
with tempfile.TemporaryDirectory(prefix='pl-member-toggle-') as td:
 p=Path(td);(p/'Prefs.kt').write_text(prefs);(p/'Test.kt').write_text(body)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'out'),str(p/'Prefs.kt'),str(p/'Test.kt'),str(r/'app/src/main/java/t/mma/hooks/core/PreferenceRuntimeOwner.kt'),str(r/'app/src/main/java/t/mma/hooks/core/FeatureResourceScope.kt')],check=True,timeout=90)
 subprocess.run(['java','-cp',str(p/'out')+':'+str(std),'t.mma.hooks.core.TestKt'],check=True,timeout=15)
