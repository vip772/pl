"""Compile production toggle owner and scope; exercise failure and stale callback contracts."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
prefs='''package android.content
interface SharedPreferences {
 fun registerOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener)
 fun unregisterOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener)
 fun interface OnSharedPreferenceChangeListener{fun onSharedPreferenceChanged(p:SharedPreferences,k:String?)}
}
'''
code='''package t.mma.hooks.core
import android.content.SharedPreferences
class Prefs:SharedPreferences {
 val listeners=mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()
 var enabled=false;var failRemove=false
 override fun registerOnSharedPreferenceChangeListener(l:SharedPreferences.OnSharedPreferenceChangeListener){listeners+=l}
 override fun unregisterOnSharedPreferenceChangeListener(l:SharedPreferences.OnSharedPreferenceChangeListener){listeners-=l;if(failRemove)error("remove")}
 fun toggle(v:Boolean){enabled=v;listeners.toList().forEach{it.onSharedPreferenceChanged(this,"enable")}}
}
fun main(){
 val p=Prefs();var starts=0;var stops=0;var errors=0;var fail=false
 val callbacks=mutableListOf<()->Unit>();var effects=0
 val owner=PreferenceRuntimeOwner(p,setOf("enable"),{p.enabled},{scope->
  starts++;scope.register{stops++};callbacks+={if(!scope.isClosed())effects++}
  if(fail)error("installation")
 },{errors++})
 owner.start();owner.start();check(starts==0 && p.listeners.size==1)
 repeat(5){p.toggle(true);owner.refresh();check(starts==it+1);callbacks.last()();p.toggle(false);callbacks.last()()}
 check(starts==5 && stops==5 && effects==5)
 fail=true;p.toggle(true);check(starts==6 && stops==6 && errors==1)
 fail=false;owner.refresh();check(starts==7 && stops==6)
 val stale=p.listeners.single();p.failRemove=true;owner.close();owner.close()
 check(stops==7 && errors==2 && p.listeners.isEmpty())
 stale.onSharedPreferenceChanged(p,"enable");owner.start();check(starts==7)
 val p2=Prefs();p2.enabled=true;var births=0;var deaths=0
 val multi=PreferenceRuntimeOwner(p2,setOf("enable"),{p2.enabled},{s->births++;s.register{deaths++}},{throw it},restartOnChange=true)
 multi.start();multi.refresh();check(births==1 && deaths==0)
 p2.toggle(true);check(births==2 && deaths==1)
 p2.toggle(false);check(deaths==2);multi.close()
 val p3=Prefs();p3.enabled=true;var attempts=0;var releases=0;var saved:FeatureResourceScope?=null
 val async=PreferenceRuntimeOwner(p3,setOf("enable"),{p3.enabled},{s->attempts++;saved=s;s.register{releases++}},{throw it})
 async.start();saved!!.close();check(attempts==1&&releases==1)
 async.refresh();check(attempts==2&&releases==1);async.close();check(releases==2)
 println("PASS production preference owner: disabled startup, repeated toggles, no duplicate install, partial failure cleanup/retry, stale callback rejection, cleanup despite listener removal failure")
}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Prefs.kt').write_text(prefs);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Prefs.kt'),str(p/'Test.kt'),str(r/'app/src/main/java/t/mma/hooks/core/PreferenceRuntimeOwner.kt'),str(r/'app/src/main/java/t/mma/hooks/core/FeatureResourceScope.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.core.TestKt'],check=True,timeout=15)