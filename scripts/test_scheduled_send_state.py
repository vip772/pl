"""Compile production reload/completion paths and check concurrent send ownership."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/scheduledtask/ScheduledTaskFeature.kt').read_text()
a=s.index('    @Synchronized\n    fun reload()');b=s.index('    @Synchronized\n    fun executeNow',a)
reload=s[a:b]
a=s.index('    @Synchronized\n    private fun finishTask');b=s.index('    private fun displayName',a)
finish=s[a:b]
code='''import java.util.concurrent.ConcurrentHashMap
data class ScheduledTaskItem(val id:String,val planTime:Long,val planTimes:List<Long> = listOf(planTime),val status:Int=1,val repeatType:Int=0,val repeatDays:List<Int> = emptyList(),val sendOnTimeout:Boolean=true,val lastExecutedTime:Long=0,val lastSuccessCount:Int=0,val lastFailCount:Int=0)
object ScheduledTaskSettings {
 const val STATUS_RUNNING=1;const val STATUS_PENDING=0;const val REPEAT_NONE=0
 fun normalizedPlanTimes(t:ScheduledTaskItem)=t.planTimes
 fun normalizeForSave(t:ScheduledTaskItem)=t
 fun resolveNextPlanTime(p:Long,r:Int,d:List<Int>,now:Long)=now+60000
}
class Settings {
 val data=linkedMapOf<String,ScheduledTaskItem>();fun isEnabled()=true
 fun tasks()=data.values.toList();fun saveTasks(t:List<ScheduledTaskItem>){data.clear();t.forEach{data[it.id]=it}}
 fun findTask(id:String)=data[id];fun saveTask(t:ScheduledTaskItem){data[t.id]=t};fun deleteTask(id:String){data.remove(id)}
}
class Fixture {
 val settings=Settings();var started=true;val acceptedSends=mutableSetOf<String>();val uncertainSends=ConcurrentHashMap<String,Long>()
 val scheduled=mutableSetOf<String>();val SINGLE_TIMEOUT_WINDOW_MS=600000L
 fun cancelAll(){scheduled.clear()};fun schedule(t:ScheduledTaskItem,delay:Long){scheduled+=t.id}
'''+reload+finish+'''
 fun finish(t:ScheduledTaskItem){finishTask(t,1,0);acceptedSends.remove(t.id);reload()}
 fun immediate(t:ScheduledTaskItem){finishImmediateTask(t,1,0);acceptedSends.remove(t.id);reload()}
}
fun main(){
 val f=Fixture();val now=System.currentTimeMillis()
 val a=ScheduledTaskItem("a",now-1000);val b=ScheduledTaskItem("b",now-1000)
 f.settings.saveTask(a);f.settings.saveTask(b);f.acceptedSends+=listOf("a","b")
 f.reload();check(f.scheduled.isEmpty() && f.settings.findTask("b")!!.status==1)
 f.finish(a);check(f.settings.findTask("a")==null && f.settings.findTask("b")!!.status==1 && "b" !in f.scheduled)
 f.finish(b);check(f.settings.tasks().isEmpty())
 val repeat=ScheduledTaskItem("repeat",now-1000,repeatType=1)
 f.settings.saveTask(repeat);f.acceptedSends+="repeat";f.finish(repeat)
 check(f.settings.findTask("repeat")!!.planTime>now && "repeat" in f.scheduled)
 val uncertain=ScheduledTaskItem("uncertain",now-1000)
 f.settings.saveTask(uncertain);f.uncertainSends[uncertain.id]=uncertain.planTime;f.reload()
 check("uncertain" !in f.scheduled && f.settings.findTask("uncertain")!!.status==1)
 f.settings.saveTask(uncertain.copy(planTime=now+60000,planTimes=listOf(now+60000)));f.reload()
 check("uncertain" in f.scheduled && !f.uncertainSends.containsKey("uncertain"))
 val manual=ScheduledTaskItem("manual",now+120000)
 f.settings.saveTask(manual);f.acceptedSends+="manual";f.immediate(manual)
 check("manual" in f.scheduled && f.settings.findTask("manual")!!.lastSuccessCount==1)
 val stale=ScheduledTaskItem("stale",now+100000);f.settings.saveTask(stale);f.reload();check(f.settings.findTask("stale")!!.status==0 && "stale" in f.scheduled)
 println("PASS production send state: reload preserves in-flight ownership, no cross-task resend, one-shot deletion, repeat advancement, uncertain occurrence held, edited time recovery, manual send plan retained, crash-stale reset")
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=15)
