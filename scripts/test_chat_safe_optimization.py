from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];b=r/'app/src/main/java/t/mma/hooks/items';lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'));cmd=['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect']
s=(b/'chatname/ChatNameDecorationLayout.kt').read_text();cache=s[s.index('private class NameGradientCache'):]
fixture='''class Shader{enum class TileMode{CLAMP}}
class LinearGradient(val x:Float,val y:Float,val end:Float,val z:Float,val a:Int,val b:Int,val mode:Shader.TileMode)
'''+cache+'''
fun main(){val c=NameGradientCache();val first=c.get(1f,10f,1,2);repeat(1000){check(c.get(1f,10f,1,2)===first)};check(c.get(2f,10f,1,2)!==first);val second=c.get(2f,10f,1,2);check(c.get(2f,11f,1,2)!==second);val third=c.get(2f,11f,1,2);check(c.get(2f,11f,3,2)!==third);println("PASS shader identity reuse and x/width/color invalidation")}
'''
time='''package t.mma.hooks.items.chattime
import java.util.*
import java.text.SimpleDateFormat
fun main(){val f=ChatTimeFormatter();val original=TimeZone.getDefault();val locale=Locale.getDefault();try{
 val t=1800000123456L
 for(z in listOf("UTC","Asia/Shanghai","America/New_York")){TimeZone.setDefault(TimeZone.getTimeZone(z));for(l in listOf(Locale.US,Locale.CHINA)){Locale.setDefault(l);for(p in listOf("yyyy-MM-dd HH:mm:ss","HH:mm","EEEE")){repeat(10){check(f.format(t,p,"HH:mm")==SimpleDateFormat(p,l).format(Date(t)))}}}}
 check(f.format(t,"'","HH:mm")==SimpleDateFormat("HH:mm").format(Date(t)))
 val threads=(1..8).map{Thread{repeat(100){check(f.format(t,"HH:mm","HH:mm")==SimpleDateFormat("HH:mm").format(Date(t)))}}};threads.forEach{it.start()};threads.forEach{it.join()}
 println("PASS formatter matches native output across pattern/locale/timezone, invalid format fallback and concurrent callers")
 }finally{TimeZone.setDefault(original);Locale.setDefault(locale)}}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'ShaderTest.kt').write_text(fixture);(p/'TimeTest.kt').write_text(time)
 subprocess.run(cmd+['-classpath',str(std),'-d',str(p/'test.jar'),str(p/'ShaderTest.kt'),str(p/'TimeTest.kt'),str(b/'chattime/ChatTimeFormatter.kt')],check=True)
 for main in ['ShaderTestKt','t.mma.hooks.items.chattime.TimeTestKt']:subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}',main],check=True)
 (p/'Store.kt').write_text('''package t.mma.hooks.items.membertitle
class MemberTitleStore{data class ColorSpec(val startColor:Int,val endColor:Int){val isGradient get()=startColor!=endColor}}''')
 subprocess.run(cmd+['-classpath',str(std)+':/usr/lib/android-sdk/platforms/android-36/android.jar','-d',str(p/'sdk.jar'),str(b/'chatname/ChatNameDecorationLayout.kt'),str(p/'Store.kt')],check=True)
print('PASS complete production nickname decoration compiles against Android SDK')