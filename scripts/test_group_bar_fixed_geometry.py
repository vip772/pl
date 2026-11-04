"""Verify the 8.0.78 footer contract and execute production fixed-bar helpers."""
from pathlib import Path
import hashlib
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
runtime = (root / 'app/src/main/java/t/mma/hooks/items/groupbar/GroupBarRuntime.kt').read_text()
source_path = Path('/sdcard/Download/wechat8078_decoded/smali_classes16/com/tencent/mm/ui/conversation/ConversationListView.smali')
source = source_path.read_text()

def native_method(signature):
    return next(part.split('.end method', 1)[0] for part in source.split('.method ')[1:]
                if part.splitlines()[0].endswith(signature))

space = native_method('g(IIZ)F')
footer = native_method('h()V')
assert 'getMeasuredHeight()I' in space
assert 'Lcom/tencent/mm/ui/gl;->a(Landroid/content/Context;)I' in space
assert 'ConversationListView;->E:I' in space
assert 'ConversationListView;->s:I' not in space
assert 'ConversationListView;->g(IIZ)F' in footer
assert footer.index('->g(IIZ)F') < footer.index('iput v6, v0, Landroid/widget/LinearLayout$LayoutParams;->height:I')
assert 'cmpg-float' in footer and 'if-gez v3' in footer
assert 'session.availableFooterSpace(space)' in runtime
assert 'sessions[param.thisObject as? View] ?: return' in runtime
recycler_path = source_path.parent / 'recycler/ConversationRecyclerView.smali'
recycler = recycler_path.read_text()
def recycler_method(signature):
    return next(part.split('.end method', 1)[0] for part in recycler.split('.method ')[1:]
                if part.splitlines()[0].endswith(signature))
calculate = recycler_method('j1(IIZ)F')
assert 'getMeasuredHeight()I' in calculate
assert 'Lcom/tencent/mm/ui/gl;->a(Landroid/content/Context;)I' in calculate
assert 'ConversationRecyclerView;->G2:I' in calculate
assert 'ConversationRecyclerView;->v2:I' not in calculate
assert '->j1(IIZ)F' in recycler_method('k1()F')
assert '->k1()F' in recycler_method('m1()V')
assert '->i1(FZ)V' in recycler_method('m1()V')
assert 'cmpg-float v3, p1, v3' in recycler_method('i1(FZ)V')
assert 'hookFooterSpace(list, "g")' in runtime
assert 'hookFooterSpace(recycler, "j1")' in runtime
for path in (source_path, recycler_path):
    print('PASS native footer contract ' + path.name + '; SHA256=' + hashlib.sha256(path.read_bytes()).hexdigest())

start = runtime.index('        private fun syncFixedPosition()')
end = runtime.index('        private fun safePosition()', start)
methods = runtime[start:end]
for banned in ('layoutParams', 'requestLayout', 'setSelection', 'KavaReflector', 'IntArray(', 'post('):
    assert banned not in methods, banned
assert 'syncFixedPosition()' in runtime[runtime.index('private val preDrawListener'):runtime.index('private val titlePosition')]

fixture = '''
class View {
    var visibility = VISIBLE
    var baseTop = 0
    var parentShift = 0
    var writes = 0
    var reads = 0
    var translationY = 0f
        set(value) { field = value; writes++ }
    fun getLocationInWindow(out: IntArray) {
        reads++; out[1] = kotlin.math.round(baseTop + parentShift + translationY).toInt()
    }
    companion object { const val VISIBLE = 0; const val INVISIBLE = 4 }
}
class Session {
    val bar = View()
    var stableWindowTop: Int? = 120
    var floatingInset = 2
    val barPosition = IntArray(2)
    var appliedScrollInset = 46
METHODS
    fun frame() = syncFixedPosition()
}
fun main() {
    val s = Session()
    s.bar.baseTop = 122
    // Same/different ancestor branches, upward/downward displacement and return to rest.
    for (shift in listOf(0, -110, -230, -110, 0, 80, 170, 0)) {
        s.bar.parentShift = shift
        s.frame()
        check(s.bar.baseTop + shift + s.bar.translationY == 122f)
    }
    val writes = s.bar.writes
    repeat(5000) { s.frame() }
    check(s.bar.writes == writes && s.bar.translationY == 0f)
    s.bar.visibility = View.INVISIBLE
    val reads = s.bar.reads
    s.bar.parentShift = -200; s.frame()
    check(s.bar.reads == reads)
    s.bar.visibility = View.VISIBLE; s.frame()
    check(s.bar.baseTop + s.bar.parentShift + s.bar.translationY == 122f)
    s.stableWindowTop = null
    val coldReads = s.bar.reads; s.frame(); check(s.bar.reads == coldReads)
    s.stableWindowTop = 190; s.bar.baseTop = 192; s.bar.parentShift = 0; s.frame()
    check(s.bar.baseTop + s.bar.translationY == 192f)

    // Native g: viewport - row heights - action bar - status bar.
    // Native h: full result -> bottom bar; short result -> max(extra, bottom bar).
    // This catches the old short-result bottom alignment losing exactly our reserve.
    var cases = 0
    for (viewport in listOf(600, 832, 1200))
    for (base in listOf(80, 120, 160))
    for (reserve in listOf(44, 46, 66))
    for (rows in listOf(0, 1, 2, 4, 8, 16, 40)) {
        val bottom = 56
        val content = rows * 64
        val original = (viewport - content - base).toFloat()
        s.appliedScrollInset = reserve
        val adjusted = s.availableFooterSpace(original)
        check(adjusted == original - reserve)
        val nativeFooter = if (adjusted < 0f) bottom else maxOf(adjusted.toInt(), bottom)
        val atBottom = viewport - bottom - content - nativeFooter
        // With short content, h's footer plus the bottom-bar range leaves first row
        // at least at base + reserve; it used to settle at base.
        if (adjusted >= bottom) {
            val settledFirst = atBottom + bottom
            check(settledFirst == base + reserve)
            val brokenFirst = viewport - content - maxOf(original.toInt(), bottom)
            check(brokenFirst == base && brokenFirst < base + reserve)
        } else {
            check(nativeFooter == bottom)
        }
        cases++
    }
    s.appliedScrollInset = 0
    for (space in listOf(-300f, -1f, 0f, 20f, 900f)) check(s.availableFooterSpace(space) == space)
    println("PASS $cases footer cases; fixed upward/downward/return frames; 5000 stable frames without writes; hidden/cold/inset/disable gates")
}
'''.replace('METHODS', methods)
libs = Path('/tmp/pl-kotlin-libs')
stdlib = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as directory:
    path = Path(directory)
    (path / 'Test.kt').write_text(fixture)
    subprocess.run(['java', '-Xmx256m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
                    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-nowarn', '-no-stdlib', '-no-reflect',
                    '-classpath', str(stdlib), '-d', str(path / 'test.jar'), str(path / 'Test.kt')],
                   check=True, timeout=45)
    subprocess.run(['java', '-cp', f'{path / "test.jar"}:{stdlib}', 'TestKt'], check=True, timeout=10)
