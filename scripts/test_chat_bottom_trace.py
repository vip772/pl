"""Compile production trace against Android SDK and exercise its rate gate."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
trace=r/'app/src/main/java/t/mma/hooks/items/floatingchat/ChatBottomTrace.kt'
cmd=['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect']
s=trace.read_text();a=s.index('        val now =');b=s.index('        val chain =',a);gate=s[a:b]
code='''
import java.util.WeakHashMap
object SystemClock {var time=0L;fun uptimeMillis()=time}
class View
class Gate(val start:Long){var last = -1000L;var count=0}
val gates=WeakHashMap<View,MutableMap<String,Gate>>()
var built=0
fun event(view:View,phase:String,detail:()->String){
'''+gate+'''
 detail()
}
fun main(){val v=View();repeat(1000){event(v,"layout"){built++;""}};check(built==1)
 SystemClock.time=500;event(v,"layout"){built++;""};check(built==2)
 for(i in 2..100){SystemClock.time=i*500L;event(v,"layout"){built++;""}};check(built==80)
 SystemClock.time=120001;event(v,"layout"){error("expired")}
 println("PASS lazy trace limit: repeated events, 500ms throttle, 80 records and 120s expiry")}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'HLog.kt').write_text('package t.mma.utils\nobject HLog {fun i(s:String){}}')
 subprocess.run(cmd+['-classpath',str(std)+':/usr/lib/android-sdk/platforms/android-36/android.jar','-d',str(p/'sdk.jar'),str(trace),str(p/'HLog.kt')],check=True,timeout=45)
 (p/'Test.kt').write_text(code)
 subprocess.run(cmd+['-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True)
bg=(r/'app/src/main/java/t/mma/hooks/items/backgroundbeauty/BackgroundBeautyFeature.kt').read_text()
assert 'ChatInputOutline' not in bg and 'chatInputStrokeWidth' not in bg and 'inputOutlineStyles' not in bg
assert 'view.javaClass.name != "com.tencent.mm.ui.FrostedContentView"' not in bg[bg.index('private fun applyNativeChrome'):bg.index('private fun setTransparentSurface')]
print('PASS editor outline and sampling removed; previous chat chrome scope retained')