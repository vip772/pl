from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[3]
s=(r/'app/src/main/java/t/mma/hooks/items/backgroundbeauty/BackgroundBeautyFeature.kt').read_text()
a=s.index('                    val state = surfaceOwners[view] ?: run {',s.index('    private fun installBackgroundWriteHook'))
b=s.index('                    val snapshot = state.surfaces[view]',a)
body=s[a:b].replace('val state = surfaceOwners[view]','surfaceOwners[view]').replace('return@hookOnce','return')
setter=s[s.index('    private fun installBackgroundWriteHook'):s.index('        installed = installDecorBackgroundHook()')]
for forbidden in ['resolveTarget(', 'hasConfiguredBackground(', 'BackgroundBeautyStore.hasImage(', 'currentActivity()']:
 assert forbidden not in setter,forbidden
fixture="""
class View(var parent:View?=null)
object BackgroundBeautySettings {enum class Slot {CHAT,CONTACTS}}
class Runtime {
 val surfaceOwners=HashMap<View,Any>()
 val framePageSlots=HashMap<Int,BackgroundBeautySettings.Slot>()
 val homeLists=HashMap<View,BackgroundBeautySettings.Slot>()
 val dirtyBoundRows=HashMap<View,Boolean>()
 fun write(view:View) { BODY }
}
fun main(){
 val r=Runtime();r.framePageSlots[0]=BackgroundBeautySettings.Slot.CHAT
 val list=View();val row=View(list);val icon=View(row)
 repeat(100000){r.write(icon)};check(r.dirtyBoundRows.isEmpty())
 r.homeLists[list]=BackgroundBeautySettings.Slot.CONTACTS
 r.framePageSlots[0]=BackgroundBeautySettings.Slot.CONTACTS
 r.write(icon);check(r.dirtyBoundRows[row]==true);r.dirtyBoundRows.clear()
 val chatRow=View(View());val bubble=View(chatRow)
 repeat(100000){r.write(bubble)};check(r.dirtyBoundRows.isEmpty())
 r.surfaceOwners[icon]=Any();r.write(icon);check(r.dirtyBoundRows.isEmpty())
 println("PASS: production chat writes, contacts late binding, mixed lists, owned bypass; no page scan or file query")
}
""".replace('BODY',body)
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 d=Path(td);kt=d/'Test.kt';kt.write_text(fixture);jar=d/'test.jar'
 p=subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(jar),str(kt)],capture_output=True,text=True)
 if p.returncode:raise SystemExit(p.stdout+p.stderr)
 subprocess.run(['java','-cp',f'{jar}:{std}','TestKt'],check=True)
