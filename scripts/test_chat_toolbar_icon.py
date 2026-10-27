from pathlib import Path
import tempfile,subprocess
r=Path(__file__).resolve().parents[1];src=r/'app/src/main/java/t/mma/hooks/items/chattoolbar/ChatToolbarIcon.kt';libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'));compiler=['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect']
s=src.read_text();method=s[s.index('    internal fun visibleBounds'):s.rfind('}')]
code='object Bounds {\n'+method+'}\n'+'''fun main(){
 fun rect(w:Int,h:Int,l:Int,t:Int,r:Int,b:Int):IntArray {val p=IntArray(w*h);for(y in t..b)for(x in l..r)p[y*w+x]=0xff000000.toInt();return p}
 check(Bounds.visibleBounds(rect(96,96,24,24,71,71),96,96)!!.contentEquals(intArrayOf(24,24,48,48)))
 check(Bounds.visibleBounds(rect(96,96,0,0,95,95),96,96)!!.contentEquals(intArrayOf(0,0,96,96)))
 check(Bounds.visibleBounds(rect(96,96,12,32,83,63),96,96)!!.contentEquals(intArrayOf(12,32,72,32)))
 check(Bounds.visibleBounds(IntArray(96*96),96,96)==null)
 check(Bounds.visibleBounds(intArrayOf(0x08000000,0xff000000.toInt()),2,1)!!.contentEquals(intArrayOf(1,0,1,1)))
 println("PASS transparent padding, full-size icon, aspect ratio, empty image and alpha threshold")
}'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(compiler+['-classpath',str(std),'-d',str(p/'sdk.jar'),str(src)],env=None,check=False) if False else None
 subprocess.run(compiler+['-classpath',str(std)+':/usr/lib/android-sdk/platforms/android-36/android.jar','-d',str(p/'sdk.jar'),str(src)],check=True,timeout=45)
 subprocess.run(compiler+['-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True)
print('PASS production drawable helper compiles against Android SDK')