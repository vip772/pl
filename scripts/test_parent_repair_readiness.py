"""Execute production repair callback and readiness logging with deterministic stubs; no Gradle."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/conversationgroup/ConversationGroupRuntime.kt').read_text()
repair=s[s.index('    internal fun repairMissingOriginalParents('):s.index('    private fun loadOriginalParentRefs(')]
s=(r/'app/src/main/java/t/mma/hooks/items/quickread/QuickMarkReadRuntime.kt').read_text()
log=s[s.index('    private fun preparationFailed('):s.index('    private fun observeNativeStorage(')]
code='''import java.util.concurrent.atomic.AtomicBoolean
class Context {val applicationContext get()=this}
class Immediate {fun execute(f:()->Unit)=f();fun post(f:()->Unit)=f()}
class Group(val id:String="g",val conversationIds:List<String> = listOf("room"))
object ConversationGroupStore {const val PREFS_NAME="prefs";var account="a";fun accountKey()=account;fun load(c:Context)=listOf(Group())}
class Database {var parent="wxid_hchat_group_g";var ready=true;var duringPrepare:()->Unit={};fun prepare():Boolean{duringPrepare();return ready}}
object WeChatApis {val db=Database();fun database()=db}
object HchatStorage {fun preferences(c:Context,n:String)=Unit}
object HLog {var d=0;var w=0;var e=0;fun d(s:String){d++};fun w(s:String){w++};fun e(s:String,t:Throwable){e++}}
class State(val parents:Map<String,String>)
class Runtime {
 val active=AtomicBoolean(true);val executor=Immediate();val main=Immediate();val parentRestoreFailures=mutableSetOf<String>();val TAG="test"
 var originals=mapOf<String,String>();var saves=0;var syncs=0;var failSave=false
 fun virtualTalker(id:String)="wxid_hchat_group_"+id
 fun loadConversationParents(d:Database,t:Set<String>)=State(mapOf("room" to d.parent))
 fun loadOriginalParentRefs(p:Unit,a:String)=originals
 fun saveOriginalParentRefs(p:Unit,a:String,v:Map<String,String>){if(failSave)error("disk");saves++;originals=v}
 fun syncAsync(c:Context){syncs++}
'''+repair+'''}
class Readiness {val preparationFailures=mutableSetOf<String>();val TAG="test"
 fun call(s:String)=preparationFailed(s)
'''+log+'''}
fun main(){
 val c=Context();val r=Runtime();var count=-1;var failure:String?=null
 fun run(){count=-1;failure=null;r.repairMissingOriginalParents(c,"a","g",setOf("room")){n,e->count=n;failure=e}}
 run();check(count==1&&failure==null&&r.originals["room"]==""&&r.syncs==1)
 run();check(count==0&&r.saves==1) // repeated repair does not overwrite trusted state
 r.originals=mapOf("room" to "native");run();check(count==0&&r.originals["room"]=="native")
 r.originals=emptyMap();WeChatApis.db.parent="native";run();check(failure!=null&&r.saves==1)
 WeChatApis.db.parent="wxid_hchat_group_g";ConversationGroupStore.account="other";run();check(failure!=null&&r.saves==1)
 ConversationGroupStore.account="a";WeChatApis.db.duringPrepare={ConversationGroupStore.account="other"};run();check(failure!=null&&r.saves==1)
 ConversationGroupStore.account="a";WeChatApis.db.duringPrepare={};r.failSave=true;run();check(failure!=null&&r.syncs==1)
 val q=Readiness();check(!q.call("native_storage_not_ready:l4"));q.call("native_storage_not_ready:l4");q.call("database_api_not_ready");check(HLog.d==2&&HLog.w==0)
 q.call("method_not_found");check(HLog.w==1)
 println("PASS explicit target only, trusted backup retained, idempotency, changed parent/account rejected, disk failure prevents sync, transient debug deduplicated, real warning retained")
}
'''
lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=10)
