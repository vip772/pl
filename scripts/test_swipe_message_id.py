"""JVM comparison of production accessor and original ID reader; no Gradle."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
feature=subprocess.check_output(['git','show','f9d6f00:app/src/main/java/t/mma/hooks/items/swipequote/SwipeQuoteFeature.kt'],cwd=r,text=True)
a=feature.index('    private fun messageId(msg: Any): Long {');z=feature.index('    private fun resolveNativeMessage(source: Any)',a)
old=feature[a:z].replace('private fun messageId','fun legacy')
a=feature.index('    private fun parseLong(');z=feature.index('    private fun findChatFooterInView',a);old+=feature[a:z]
stub='''package t.mma.utils
import java.lang.reflect.*
object KavaReflector {
 var searches=0
 val calls=mutableListOf<String>()
 fun findMethod(c:Class<*>,n:String):Method? { searches++;return runCatching{c.getDeclaredMethod(n).apply{isAccessible=true}}.getOrNull() ?: runCatching{c.getMethod(n).apply{isAccessible=true}}.getOrNull() }
 fun findDeclaredField(c:Class<*>,n:String):Field? { searches++;return runCatching{c.getDeclaredField(n).apply{isAccessible=true}}.getOrNull() }
 fun invoke(m:Method?,o:Any):Any? {if(m==null)return null;calls.add(m.name);return runCatching{m.invoke(o)}.getOrNull()}
 fun readField(f:Field?,o:Any):Any? {if(f==null)return null;calls.add(f.name);return runCatching{f.get(o)}.getOrNull()}
 fun readField(o:Any,n:String):Any? { var c:Class<*>?=o.javaClass;while(c!=null && c!=Any::class.java){val f=findDeclaredField(c,n);if(f!=null)return readField(f,o);c=c.superclass};return null }
}
'''
main='''package t.mma.hooks.items.swipequote
import t.mma.utils.KavaReflector
class Mixed(var first:Any?,var second:Any?,var third:Any?, @JvmField var field_msgId:Any?, @JvmField var msgId:Any?, @JvmField var msgID:Any?, @JvmField var id:Any?) {
 fun getMsgId():Any? {if(first=="throw")error("getter");return first}
 fun getMsgID()=second
 fun getId()=third
}
open class Parent { @JvmField var msgId:Any?=" 17 ";fun getMsgID():Long=0 }
class Child:Parent()
class Positive(var value:Long){fun getMsgId()=value}
class Empty
'''+old+'''
fun main(){
 val reader=MessageIdAccessors();val values=listOf<Any?>(null,0L,-1L,1L,19,2.5," 42 ","bad","throw")
 var count=0
 for(i in 0 until 2000){
  val m=Mixed(values[i%values.size],values[(i/3)%values.size],values[(i/7)%values.size],values[(i/11)%values.size],values[(i/13)%values.size],values[(i/17)%values.size],values[(i/19)%values.size])
  KavaReflector.calls.clear();val expected=legacy(m);val calls=KavaReflector.calls.toList()
  KavaReflector.calls.clear();check(reader.read(m)==expected);check(KavaReflector.calls==calls);count++
 }
 for(m in listOf(Child(),Empty(),Positive(91),Positive(0))){check(reader.read(m)==legacy(m));count++}
 val p=Positive(9);val hot=MessageIdAccessors();check(hot.read(p)==9L);val searches=KavaReflector.searches
 repeat(1000){p.value=it.toLong()+1;check(hot.read(p)==p.value)}
 check(KavaReflector.searches==searches)
 println("PASS $count legacy results and invocation-order comparisons; 1000 hot reads without new lookup; IDs remain live")
}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Main.kt').write_text(main);(p/'Reflector.kt').write_text(stub);jar=p/'test.jar'
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(jar),str(p/'Main.kt'),str(p/'Reflector.kt'),str(r/'app/src/main/java/t/mma/hooks/items/swipequote/MessageIdAccessors.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{jar}:{std}','t.mma.hooks.items.swipequote.MainKt'],check=True,timeout=30)