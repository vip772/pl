"""Production badge observers with synthetic View fault injection; no Android/device."""
from pathlib import Path
import ast, subprocess, tempfile
r=Path(__file__).resolve().parents[1]; libs=Path('/tmp/pl-kotlin-libs'); std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
tree=ast.parse((r/'scripts/tests/dynamic_badge_recall/run.py').read_text())
stubs=next(ast.literal_eval(n.value) for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='stubs' for t in n.targets))
v=stubs['View.kt']
v=v.replace(' var isAttachedToWindow=true',' var failRemove=false;var failAttach=false;var cancelCalls=0;var removeAttachCalls=0\n var isAttachedToWindow=true')
v=v.replace('attaches.add(l)}','attaches.add(l);if(failAttach)error("attach")}')
v=v.replace('attaches.remove(l)}','removeAttachCalls++;attaches.remove(l);if(failRemove)error("remove attach")}')
v=v.replace('fun removeCallbacks(r:Runnable){','fun removeCallbacks(r:Runnable){cancelCalls++;')
v=v.replace('delayed.removeAll{it===r}}','delayed.removeAll{it===r};if(failRemove)error("cancel")}')
v=v.replace(' var isAlive=true',' var failRemove=false;var failAdd=false;var removes=0;var preRemoves=0\n var isAlive=true')
v=v.replace('globals.add(l)}','globals.add(l);if(failAdd)error("add global")}')
v=v.replace('globals.remove(l)}','removes++;globals.remove(l);if(failRemove)error("remove global")}')
v=v.replace('listeners.add(l)}','listeners.add(l);if(failAdd)error("add predraw")}')
v=v.replace('listeners.remove(l)}','preRemoves++;listeners.remove(l);if(failRemove)error("remove predraw")}')
check='''package t.mma.hooks.items.dynamicbadge
import android.view.View
import android.view.ViewTreeObserver
fun main(){
 val host=View();var calls=0;var errors=0
 val recovery=BadgeListRecovery(host,onError={errors++;error("logger")}){calls++}
 val tasks=(host.queued+host.delayed).toList();val old=host.viewTreeObserver;val globals=old.globals.toList();val initialRemoves=old.removes
 host.failRemove=true;old.failRemove=true
 recovery.dispose();val cancelled=host.cancelCalls;recovery.dispose()
 check(host.cancelCalls==cancelled&&host.queued.isEmpty()&&host.delayed.isEmpty()&&host.attaches.isEmpty()&&old.globals.isEmpty())
 check(old.removes==initialRemoves+1&&host.removeAttachCalls==1&&errors==7)
 tasks.forEach{it.run()};globals.forEach{it.onGlobalLayout()};check(calls==0&&host.queued.isEmpty())
 println("PASS recovery: five cancellations, observer and attach removals continue despite each release and logger failing; duplicate dispose and stale callbacks inert")
 val view=View();var paints=0;var reported=0
 val observer=BadgeWindowObserver(view,onError={reported++;error("logger")}){paints++};observer.attach()
 val oldWindow=view.viewTreeObserver;val snapshot=oldWindow.listeners.toList();oldWindow.failRemove=true
 view.viewTreeObserver=ViewTreeObserver();val stale=oldWindow.listeners.toList();oldWindow.isAlive=false;oldWindow.listeners.clear();observer.attach()
 observer.dispose();stale.forEach{it.onPreDraw()};snapshot.forEach{it.onPreDraw()};observer.attach()
 println("PASS window: old-window removal failure cannot skip migrated-window cleanup; disposed snapshot cannot paint or attach")
 val failed=View().also{it.viewTreeObserver.failAdd=true};var thrown=false
 try{BadgeListRecovery(failed){error("must not repair")}}catch(_:IllegalStateException){thrown=true}
 check(thrown&&failed.attaches.isEmpty()&&failed.viewTreeObserver.globals.isEmpty()&&failed.queued.isEmpty()&&failed.delayed.isEmpty())
 val failAttach=View().also{it.failAttach=true};thrown=false
 try{BadgeListRecovery(failAttach){}}catch(_:IllegalStateException){thrown=true};check(thrown&&failAttach.attaches.isEmpty())
 val failWindow=View().also{it.viewTreeObserver.failAdd=true};val w=BadgeWindowObserver(failWindow){};thrown=false
 try{w.attach()}catch(_:IllegalStateException){thrown=true};check(thrown&&failWindow.viewTreeObserver.listeners.isEmpty());w.dispose()
 println("PASS partial registration failure rolls back global/predraw/attach listeners")
}
'''
with tempfile.TemporaryDirectory(prefix='pl-badge-cleanup-') as td:
 p=Path(td);(p/'View.kt').write_text(v);(p/'Test.kt').write_text(check)
 sources=[r/'app/src/main/java/t/mma/hooks/items/dynamicbadge'/n for n in ['BadgeListRecovery.kt','BadgeWindowObserver.kt']]+list(p.glob('*.kt'))
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),*map(str,sources)],check=True,timeout=60)
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(std),'t.mma.hooks.items.dynamicbadge.TestKt'],check=True,timeout=15)
