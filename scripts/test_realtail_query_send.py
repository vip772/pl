"""Extract production query method, test failed-send callback rollback and close race.
Synthetic network/constructor only; not host or Android business validation.
"""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
s=(r/'app/src/main/java/t/mma/hooks/items/realtail/BeforeTransferNameQuery.kt').read_text()
a=s.index('    fun query(wxid: String, roomId: String, callback: Callback): Boolean {');b=s.index('    private fun locateScene()',a)
method=s[a:b].rstrip()
code='''import java.util.Collections
import java.util.WeakHashMap
import java.lang.reflect.Constructor
class Scene(val id:String,val room:String)
object KavaReflector{fun newInstance(c:Constructor<*>,a:String,b:String):Any?=c.newInstance(a,b)}
object RealNameTailStore{fun isSupportedQueryId(id:String)=id.isNotBlank()}
class Network {var fail=false;var reject=false;var during:(()->Unit)?=null
 fun sendRequest(scene:Any):Boolean {during?.invoke();if(fail)error("network throw");return !reject}
}
object WeChatApis{val api=Network();fun network():Network?=api}
class Query{
 @Volatile var closed=false;var ready=true;var events=0
 var initialized=true
 var sceneCtor:Constructor<*>?=Scene::class.java.getDeclaredConstructor(String::class.java,String::class.java)
 val pending=Collections.synchronizedMap(WeakHashMap<Any,Callback>())
 val logger:(String,Throwable?)->Unit={_,_->events++;error("logger fault")}
 fun interface Callback {fun onResult(name:String)}
 fun ensureReady():Boolean {if(!ready)return false; synchronized(this) {return !closed}}
 fun destroy(){synchronized(this){closed=true;pending.clear()}}
''' +method+'''
}
fun main(){
 val q=Query();val cb=Query.Callback{error("late callback")}
 WeChatApis.api.fail=true;check(!q.query("friend","room",cb));check(q.pending.isEmpty()&&q.events==1)
 WeChatApis.api.fail=false;WeChatApis.api.reject=true;check(!q.query("friend","room",cb));check(q.pending.isEmpty())
 WeChatApis.api.reject=false;check(q.query("friend","room",cb));check(q.pending.size==1)
 q.destroy();check(q.pending.isEmpty()&&!q.query("friend","room",cb))
 val race=Query();race.ready=false;check(!race.query("friend","room",cb));race.ready=true
 val hook=Thread{race.destroy()};hook.start();hook.join();check(!race.query("friend","room",cb))
 println("PASS extracted production query: thrown/rejected sends roll back callbacks, logger failure isolated, closed query cannot enqueue")
}
'''
with tempfile.TemporaryDirectory(prefix='pl-realtail-query-') as td:
 p=Path(td);(p/'Check.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-nowarn','-classpath',str(std),'-d',str(p/'out'),str(p/'Check.kt')],check=True,timeout=90)
 subprocess.run(['java','-cp',str(p/'out')+':'+str(std),'CheckKt'],check=True,timeout=15)
