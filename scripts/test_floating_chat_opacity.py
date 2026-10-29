from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];b=r/'app/src/main/java/t/mma/hooks/items/floatingchat';libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'));cmd=['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect']
code='''package t.mma.hooks.items.floatingchat
fun main(){
 val c=0xE6F7F7F7.toInt()
 check(FloatingChatOpacity.color(c,-1)==c)
 check(FloatingChatOpacity.color(c,0) ushr 24==255)
 check(FloatingChatOpacity.color(c,100) ushr 24==0)
 check(FloatingChatOpacity.color(c,50) ushr 24==128)
 check(FloatingChatOpacity.color(c,10) ushr 24==230)
 for(t in 0..100)check(FloatingChatOpacity.color(c,t) and 0xFFFFFF==c and 0xFFFFFF)
 check(FloatingChatOpacity.color(c,120) ushr 24==0)
 println("PASS independent transparency endpoints, legacy defaults, RGB preservation and clamping")
}'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(cmd+['-classpath',str(std)+':/usr/lib/android-sdk/platforms/android-36/android.jar','-d',str(p/'sdk.jar'),str(b/'FloatingChatOpacity.kt'),str(b/'FloatingChatCardVisuals.kt')],check=True,timeout=45)
 subprocess.run(cmd+['-classpath',str(std),'-d',str(p/'test.jar'),str(b/'FloatingChatOpacity.kt'),str(p/'Test.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.floatingchat.TestKt'],check=True)
for kind in ['Header']:
 s=(b/f'FloatingChat{kind}Feature.kt').read_text()
 for line in s.splitlines():
  if 'FloatingChatCardVisuals.apply(' in line:assert 'transparency = backgroundTransparency' in line
 assert f'floating_chat_{kind.lower()}_transparency' in s
print('PASS production card SDK compile; header background wiring')