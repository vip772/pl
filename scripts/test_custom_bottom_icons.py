"""Compile the production bitmap loading function with an instrumented decoder; synthetic only."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/custombottombar/CustomBottomBarIconStore.kt').read_text()
a=s.index('    fun loadBitmap(');b=s.index('    private fun decodeScaled',a)
code='''import java.io.File
import kotlin.math.max
class Bitmap(val width:Int,val height:Int){var recycled=false;fun recycle(){recycled=true};companion object {var fail=false;fun createScaledBitmap(b:Bitmap,w:Int,h:Int,f:Boolean):Bitmap{if(fail)error("scale failed");return Bitmap(w,h)}}}
object BitmapFactory {class Options {var inJustDecodeBounds=false;var outWidth=0;var outHeight=0;var inSampleSize=1};var width=4096;var height=2048;var sample=0;var last:Bitmap?=null;fun decodeFile(p:String,o:Options):Bitmap? {if(o.inJustDecodeBounds){o.outWidth=width;o.outHeight=height;return null};sample=o.inSampleSize;return Bitmap(width/sample,height/sample).also{last=it}}}
object Store {const val MAX_ICON_EDGE=256
'''+s[a:b]+'''}
fun main(){
 val file=File.createTempFile("bottom-icon",".png");file.writeText("fixture")
 try {
  check(Store.loadBitmap(null)==null);check(Store.loadBitmap("")==null)
  val large=Store.loadBitmap(file.path)!!;check(large.width==256&&large.height==128&&!large.recycled);check(BitmapFactory.sample==8&&BitmapFactory.last!!.recycled)
  BitmapFactory.width=64;BitmapFactory.height=32;val small=Store.loadBitmap(file.path)!!;check(small===BitmapFactory.last&&!small.recycled)
  BitmapFactory.width=4096;BitmapFactory.height=2048;Bitmap.fail=true;check(Store.loadBitmap(file.path)==null&&BitmapFactory.last!!.recycled)
  Bitmap.fail=false;BitmapFactory.width=0;check(Store.loadBitmap(file.path)==null)
 }finally {file.delete()}
 println("PASS production icon loader: invalid path/bounds; sampled 4096px source; 256px output; source release after scaling and scaling exception; small bitmap retained")
}
'''
lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-nowarn','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=90)
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(std),'TestKt'],check=True,timeout=30)
