"""Production real-tail scheduler close gate with synthetic tasks; no host/network calls."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
stubs={
'Api.kt':'''package t.mma.hooks.api.core
object WeChatApis {val task=Tasks();fun tasks():Tasks?=task;fun account():Account?=Account()}
class Account {fun selfWxId()="self"}
class Tasks {val delayed=mutableMapOf<String,()->Unit>();val async=mutableListOf<()->Unit>();val cancelled=mutableListOf<String>();fun runOnMainDelayed(k:String,d:Long,f:()->Unit){delayed[k]=f};fun runAsync(f:()->Unit):Any{async+=f;return this};fun runOnMain(r:Runnable){async+={r.run()}};fun cancel(k:String){cancelled+=k;delayed.remove(k)}}
''',
'Store.kt':'''package t.mma.hooks.items.realtail
class RealNameTailStore {fun isEnabled()=true;fun hasTail(id:String)=false;fun saveTail(id:String,v:String){saves++};var saves=0;companion object {fun isSupportedQueryId(id:String)=id.isNotBlank()}}
class BeforeTransferNameQuery {var calls=0;fun ensureReady()=false;fun query(id:String,room:String,callback:(String)->Unit):Boolean{calls++;return true}}
''',
'Test.kt':'''package t.mma.hooks.items.realtail
import t.mma.hooks.api.core.WeChatApis
fun main(){
 val store=RealNameTailStore();val query=BeforeTransferNameQuery();val s=RealNameTailScheduler(store,query,{_,_->},{error("late saved callback")})
 s.onVisibleSoon("room@chatroom","friend")
 val stale=WeChatApis.task.delayed.values.single()
 s.onMessage("room@chatroom","friend2")
 val queued=WeChatApis.task.async.toList()
 s.destroy();check(WeChatApis.task.delayed.isEmpty())
 stale();queued.forEach{it()}
 s.onMessage("room@chatroom","friend3");s.onVisible("room@chatroom","friend4");s.onVisibleSoon("room@chatroom","friend5")
 check(WeChatApis.task.delayed.isEmpty());check(query.calls==0&&store.saves==0)
 val f=s.javaClass.getDeclaredField("queue").apply{isAccessible=true};check((f.get(s) as java.util.ArrayDeque<*>).isEmpty())
 s.destroy()
 println("PASS actual real-tail scheduler: close cancels visible/resume tasks; stale delayed and queued callbacks inert; no query/save after close")
}
'''}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for n,s in stubs.items():(p/n).write_text(s)
 sources=list(p.glob('*.kt'))+[r/'app/src/main/java/t/mma/hooks/items/realtail/RealNameTailScheduler.kt']
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),*map(str,sources)],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.realtail.TestKt'],check=True,timeout=15)
