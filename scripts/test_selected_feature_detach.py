"""Extract feature detach and dialog tracking; execute with failure stubs only."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/selectedmessages/SelectedMessagesFeature.kt').read_text()
def method(needle):
 a=s.index(needle);b=s.index('{',a);i=b+1;depth=1
 while depth:
  depth+=(s[i]=='{')-(s[i]=='}');i+=1
 return s[a:i]
pre='''
object Looper {var isMain=true;fun myLooper()=if(isMain)1 else 2;fun getMainLooper()=1}
class Handler {var reject=false;val queue=ArrayList<()->Unit>();fun removeCallbacksAndMessages(x:Any?){queue.clear()};fun post(f:()->Unit):Boolean{if(reject)return false;queue.add(f);return true};fun drain(){val q=queue.toList();queue.clear();q.forEach{it()}}}
object VoiceForwardMiuixDialog {class DialogHandle(val fail:Boolean=false){var calls=0;fun close(){calls++;if(fail)error("dialog")};fun isShowing()=calls==0}}
object android {object app {class Dialog(val fail:Boolean=false){var calls=0;val isShowing get()=calls==0;fun dismiss(){calls++;if(fail)error("native dialog")}}}}
class Resource {var fail=false;var calls=0;fun close(){calls++;if(fail)error("scope")}}
class Sender(val fail:Boolean=false){var calls=0;fun shutdown(){calls++;if(fail)error("sender")}}
class Worker{val queue=ArrayList<Int>();var calls=0;fun shutdown(){calls++}}
object SelectedMessagesRuntimeCoordinator{var calls=0;fun detach(s:Sender){calls++;error("coordinator")}}
class Owner{val contactSession=Resource();var closed=false;val resources=Resource();val hookedMethods=mutableSetOf(1,2);val moduleSender=Sender(true);val officialSender=Sender();val customModuleSender=Sender();val contactWorker=Worker();val main=Handler();val dialogs=ArrayList<VoiceForwardMiuixDialog.DialogHandle>();val nativeDialogs=ArrayList<android.app.Dialog>();fun logger(s:String,e:Throwable){error("logger")}
'''
code=pre+'\n'.join(method(n) for n in ['    fun detach()', '    private fun releaseSafely(', '    private fun trackDialog(', '    private fun trackNativeDialog('])+'''
fun add(d:VoiceForwardMiuixDialog.DialogHandle){trackDialog(d)};fun addNative(d:android.app.Dialog){trackNativeDialog(d)}
}
fun main(){
 for(reject in listOf(false,true)) for(mainThread in listOf(true,false)){
  Looper.isMain=mainThread;val o=Owner();val a=VoiceForwardMiuixDialog.DialogHandle(true);val b=VoiceForwardMiuixDialog.DialogHandle();val c=android.app.Dialog(true);val d=android.app.Dialog();o.add(a);o.add(b);o.addNative(c);o.addNative(d);o.contactWorker.queue.add(1);o.main.post{error("stale callback")}
  o.main.reject=reject;o.resources.fail=true;o.detach();check(o.closed&&o.hookedMethods.isEmpty()&&o.resources.calls==1&&o.contactSession.calls==1);check(o.moduleSender.calls==1&&o.officialSender.calls==1&&o.customModuleSender.calls==1);check(o.contactWorker.queue.isEmpty()&&o.contactWorker.calls==1);o.main.drain();check(o.dialogs.isEmpty()&&o.nativeDialogs.isEmpty());check(a.calls==1&&b.calls==1&&c.calls==1&&d.calls==1)
  o.detach();check(o.resources.calls==1&&o.contactWorker.calls==1);val late=VoiceForwardMiuixDialog.DialogHandle();o.add(late);check(late.calls==1&&o.dialogs.isEmpty());val lateNative=android.app.Dialog();o.addNative(lateNative);check(lateNative.calls==1)
 }
 println("PASS production feature detach: sender/coordinator/dialog/logger failure isolation, worker queue cleared, main callbacks drained, late dialogs released, main/off-main and idempotence")
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=90)
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(std),'TestKt'],check=True,timeout=15)
assert 'contactWorker.execute {' in s and 'SelectedMessageContactRepository.warm()' not in s
assert 'ArrayBlockingQueue<Runnable>(4)' in s
print('PASS structural only: contact warmup uses feature-owned bounded worker; shared cache not globally cleared')
