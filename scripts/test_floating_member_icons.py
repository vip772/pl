"""Execute production native icon formatting against loader/span fixtures, not host rendering."""
from pathlib import Path
import subprocess
import tempfile
r = Path(__file__).resolve().parents[1]
s = (r/'app/src/main/java/t/mma/hooks/items/floatingchat/FloatingChatHeaderCapsule.kt').read_text()
method = s[s.index('    private fun statisticsText('):s.index('    private fun fail(')]
code = '''
class Context {val classLoader=javaClass.classLoader}
class ViewGroup {val context=Context()}
class Drawable {fun mutate()=this;fun setBounds(a:Int,b:Int,c:Int,d:Int){check(c==14&&d==14)}}
class State {var iconsLoaded=false;var maleIcon:Drawable?=null;var femaleIcon:Drawable?=null}
class SpannableString(val value:String):CharSequence by value {
 val spans=mutableListOf<Pair<Int,Int>>()
 fun setSpan(span:ImageSpan,start:Int,end:Int,flags:Int){spans.add(start to end)}
 override fun toString()=value
}
class ImageSpan(d:Drawable,a:Int){companion object {const val ALIGN_CENTER=2}}
object Spanned {const val SPAN_EXCLUSIVE_EXCLUSIVE=33}
object HLog {var errors=0;fun e(s:String,t:Throwable){errors++}}
object KavaReflector {
 var calls=0;var fail=false
 fun loadClass(s:String,c:ClassLoader):Class<*> {check(s=="ke5.a");return Context::class.java}
 fun findDeclaredMethod(c:Class<*>,s:String,vararg p:Class<*>):String {check(s=="i");check(p.toList()==listOf(Context::class.java,Int::class.javaPrimitiveType));return s}
 fun invokeOrThrow(m:String,r:Any?,c:Context,id:Int):Drawable {calls++;if(fail)error("unavailable");check(id==1||id==2);return Drawable()}
}
class Runtime {
 fun dp(h:ViewGroup,v:Int)=v
 fun resourceId(h:ViewGroup,n:String,t:String):Int {check(t=="raw");return when(n){"ic_sex_male"->1;"ic_sex_female"->2;else->error(n)}}
 fun format(h:ViewGroup,s:State,t:String)=statisticsText(h,s,t)
METHOD
}
fun main(){
 val r=Runtime();val h=ViewGroup();val s=State();val text="男 12 · 女 9 · 未知 2 · 好友 5"
 val formatted=r.format(h,s,text) as SpannableString
 check(formatted.toString()==text);check(formatted.spans==listOf(1 to 2,8 to 9))
 repeat(1000){r.format(h,s,text)};check(KavaReflector.calls==2)
 check(r.format(h,s,"群员统计暂不可用")=="群员统计暂不可用")
 KavaReflector.fail=true;val failed=State();repeat(3){check(r.format(h,failed,text).toString()==text)}
 check(HLog.errors==1&&KavaReflector.calls==3)
 println("PASS production native icon spans: male/female placement, host loader signature, two loads per owner, no repeated load over 1000 formats, failure text and one error")
}
'''.replace('METHOD',method)
lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'out'),str(p/'Test.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"out"}:{std}','TestKt'],check=True,timeout=15)
