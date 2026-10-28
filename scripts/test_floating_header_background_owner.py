"""Execute production ActionBar layer ownership, native rewrites and restore with JVM host fixtures."""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[1]
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
files={
'Drawable.kt':'package android.graphics.drawable\nopen class Drawable\n',
'View.kt':'package android.view\nopen class View\n',
'Reflect.kt':'''package t.mma.utils
object KavaReflector {
 fun findMethodRecursive(c:Class<*>,n:String,vararg types:Class<*>):java.lang.reflect.Method?=runCatching{c.getMethod(n,*types)}.getOrNull()
 fun findFieldRecursive(c:Class<*>,n:String):java.lang.reflect.Field?=runCatching{c.getField(n)}.getOrNull()
}
''',
'Test.kt':'''package t.mma.hooks.items.floatingchat
import android.view.View
import android.graphics.drawable.Drawable
class Bar:View() {
 @JvmField var h:Drawable?=Drawable()
 @JvmField var i:Drawable?=Drawable()
 @JvmField var m:Drawable?=Drawable()
 var writes=0
 fun setPrimaryBackground(d:Drawable?){val suppress=FloatingChatActionBarBackgrounds.onNativeWrite(this,javaClass.getMethod("setPrimaryBackground",Drawable::class.java),d);h=if(suppress)null else d;writes++}
 fun setStackedBackground(d:Drawable?){val suppress=FloatingChatActionBarBackgrounds.onNativeWrite(this,javaClass.getMethod("setStackedBackground",Drawable::class.java),d);i=if(suppress)null else d;writes++}
 fun setSplitBackground(d:Drawable?){val suppress=FloatingChatActionBarBackgrounds.onNativeWrite(this,javaClass.getMethod("setSplitBackground",Drawable::class.java),d);m=if(suppress)null else d;writes++}
}
fun main(){
 val a=Bar();val b=Bar();val originalI=a.i;val originalM=a.m;val foreign=b.h
 FloatingChatActionBarBackgrounds.claim(a)
 check(a.h==null && a.i==null && a.m==null)
 val count=a.writes
 repeat(1000){FloatingChatActionBarBackgrounds.claim(a)}
 check(a.writes==count && b.h===foreign)
 val fresh=Drawable();val beforeWrite=a.writes;a.setPrimaryBackground(fresh);check(a.writes==beforeWrite+1)
 check(a.h==null)
 val writesBeforeBurst=a.writes
 repeat(10000){a.setPrimaryBackground(fresh)}
 check(a.h==null && a.writes==writesBeforeBurst+10000)
 FloatingChatActionBarBackgrounds.restore(a)
 check(a.h===fresh && a.i===originalI && a.m===originalM)
 check(!FloatingChatActionBarBackgrounds.contains(a))
 a.setPrimaryBackground(null);check(a.h==null)
 FloatingChatActionBarBackgrounds.claim(a);FloatingChatActionBarBackgrounds.restore(a);check(a.h==null)
 println("PASS three independent layers, 1000 idle claims without writes, native rewrite suppression/latest restore, null and unrelated host")
}
'''}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for n,s in files.items():(p/n).write_text(s)
 src=r/'app/src/main/java/t/mma/hooks/items/floatingchat/FloatingChatActionBarBackgrounds.kt'
 cmd=['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'out.jar'),str(src),*map(str,p.glob('*.kt'))]
 subprocess.run(cmd,check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"out.jar"}:{std}','t.mma.hooks.items.floatingchat.TestKt'],check=True,timeout=10)
