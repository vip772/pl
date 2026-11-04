"""Execute production pixel geometry across density, window and layout configurations."""
from pathlib import Path
import subprocess,tempfile,hashlib
r=Path(__file__).resolve().parents[1]
src=r/'app/src/main/java/t/mma/hooks/items/messagemenuextension/MessageMenuGridGeometry.kt'
runtime=(src.parent/'MessageMenuGridRuntime.kt').read_text()
assert 'allViews.take(' not in runtime and 'allViews.drop(index * columns).take(columns)' in runtime
assert 'anchor.getWindowVisibleDisplayFrame' in runtime
assert 'MessageMenuGridGeometry.width' in runtime and 'MessageMenuGridGeometry.viewport' in runtime
assert 'setOnClickListener' not in runtime
for n in ['bij.xml','c_6.xml']:
 p=Path('/sdcard/Download/wechat8078_decoded/res/layout')/n
 print('EVIDENCE native 8.0.78',n,hashlib.sha256(p.read_bytes()).hexdigest())
fixture='''package t.mma.hooks.items.messagemenuextension
fun main(){var cases=0
 for(density in listOf(0.75,1.0,1.5,2.0,2.75,3.5,4.0))
 for(window in listOf(320,480,720,1080,1440,2200))
 for(columns in 5..8) for(rows in 3..5) {
  val gap=(12*density).toInt();val padding=(16*density).toInt()
  val available=window-2*gap
  val width=MessageMenuGridGeometry.width(available,padding,0,columns,(64*density).toInt())
  check(width.item>0 && width.row+padding<=available)
  check(width.row==width.item*columns)
  val height=(82*density).toInt()
  for(items in listOf(1,5,6,8,15,16,24,30,40,41,80)) {
   val count=(items+columns-1)/columns
   val viewport=MessageMenuGridGeometry.viewport(count*height,height,rows,0,0,430)
   check(viewport>0 && viewport<=430 && viewport<=rows*height && viewport<=count*height)
   val groups=(0 until items).chunked(columns)
   check(groups.flatten()==(0 until items).toList() && groups.size==count)
   cases++
  }
 }
 check(runCatching{MessageMenuGridGeometry.width(7,4,0,8,60)}.isFailure)
 println("PASS $cases production geometry density/window/column/row/item cases; no Android rendering or click validation")
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(fixture)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(src),str(p/'Test.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.messagemenuextension.TestKt'],check=True,timeout=15)