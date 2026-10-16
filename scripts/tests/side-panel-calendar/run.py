from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[3];lib=Path('/tmp/pl-kotlin-libs');stdlib=next(lib.glob('kotlin-stdlib-*.jar'))
code='''package t.mma.hooks.items.homesidepanel
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
private fun month(y:Int,m:Int,d:Int)=SidePanelCalendarMonth.from(GregorianCalendar(TimeZone.getTimeZone("Asia/Shanghai")).apply {clear();set(y,m-1,d,12,0,0)})
fun main(){
 for(y in 1999..2101) for(m in 1..12){
  val c=GregorianCalendar(y,m-1,1);val days=c.getActualMaximum(Calendar.DAY_OF_MONTH)
  for(d in listOf(1,days)){
   val v=month(y,m,d);check(v.year==y && v.month==m && v.today==d)
   check(v.cells.size in listOf(28,35,42));check(v.cells.filterNotNull()==(1..days).toList())
   check(v.cells.indexOf(1)==(c.get(Calendar.DAY_OF_WEEK)+5)%7)
   check(v.cells.count{it==v.today}==1)
  }
 }
 check(month(2024,2,29).cells.filterNotNull().size==29)
 check(month(2100,2,28).cells.filterNotNull().size==28)
 check(month(2021,2,1).cells.size==28)
 check(month(2026,3,1).cells.size==42)
 check(month(2026,12,31).today==31);check(month(2027,1,1).today==1)
 println("PASS: 103年完整月份、周一对齐、4/5/6行、闰年/世纪年、跨月跨年、今天唯一标记")
}
'''
with tempfile.TemporaryDirectory() as d:
 p=Path(d);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(stdlib),'-d',str(p/'test.jar'),str(r/'app/src/main/java/t/mma/hooks/items/homesidepanel/SidePanelCalendarMonth.kt'),str(p/'Test.kt')],check=True,timeout=30)
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(stdlib),'t.mma.hooks.items.homesidepanel.TestKt'],check=True,timeout=10)