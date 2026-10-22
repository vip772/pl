"""Run production top-inset helpers against a small mutable layout fixture."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/t/mma/hooks/items/floatingchat/FloatingChatHeaderFeature.kt').read_text()
start = source.index('    private fun applyChatTopInset(')
end = source.index('    private fun applyChatStatusWrapper(', start)
helpers = source[start:end].replace('private fun ', 'fun ')
libs = Path('/tmp/pl-kotlin-libs')
stdlib = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
fixture = '''
class ViewGroup(var paddingLeft:Int, var paddingTop:Int, var paddingRight:Int, var paddingBottom:Int) {
    var writes = 0
    fun setPadding(l:Int,t:Int,r:Int,b:Int) {
        paddingLeft=l; paddingTop=t; paddingRight=r; paddingBottom=b; writes++
    }
}
class Runtime {
    private val chatTopPaddings = java.util.WeakHashMap<ViewGroup,Int>()
HELPERS
}
fun main() {
    val runtime = Runtime()
    val chat = ViewGroup(7,129,9,217)
    runtime.applyChatTopInset(chat)
    check(chat.paddingTop == 0 && chat.paddingBottom == 217)
    check(chat.paddingLeft == 7 && chat.paddingRight == 9)
    repeat(1000) { runtime.applyChatTopInset(chat) }
    check(chat.writes == 1)
    // A new inset dispatch/rotation and keyboard bottom padding must survive restore.
    chat.paddingTop = 84
    chat.paddingBottom = 640
    runtime.applyChatTopInset(chat)
    runtime.restoreChatTopInset(chat)
    check(chat.paddingTop == 84 && chat.paddingBottom == 640)
    runtime.restoreChatTopInset(chat)
    check(chat.writes == 3)
    val noInset = ViewGroup(0,0,0,26)
    runtime.applyChatTopInset(noInset)
    runtime.restoreChatTopInset(noInset)
    check(noInset.writes == 0 && noInset.paddingBottom == 26)
    // Independent chat layouts retain independent restore values.
    val second = ViewGroup(0,129,0,0)
    runtime.applyChatTopInset(chat)
    runtime.applyChatTopInset(second)
    runtime.restoreChatTopInset(second)
    runtime.restoreChatTopInset(chat)
    check(second.paddingTop == 129 && chat.paddingTop == 84)
    println("PASS: production top-inset helpers: 129px removal, idle no-op, redispatch, independent restore, side/bottom preservation")
}
'''.replace('HELPERS', helpers)
with tempfile.TemporaryDirectory() as temp:
    folder = Path(temp)
    kt = folder / 'Test.kt'
    kt.write_text(fixture)
    jar = folder / 'test.jar'
    subprocess.run(['java', '-Xmx512m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
                    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
                    '-classpath', str(stdlib), '-d', str(jar), str(kt)], check=True)
    subprocess.run(['java', '-cp', f'{jar}:{stdlib}', 'TestKt'], check=True)
