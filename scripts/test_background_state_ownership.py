"""Exercise production weak indices and page-owned state without Android runtime."""
from pathlib import Path
import subprocess, tempfile
r = Path(__file__).resolve().parents[1]
s = (r/'app/src/main/java/t/mma/hooks/items/backgroundbeauty/BackgroundBeautyFeature.kt').read_text()
a=s.index('    private class WeakValueMap<');b=s.index('    private class HostState', a)
weak=s[a:b].replace('private class WeakValueMap','class WeakValueMap')
assert 'WeakValueMap<Activity, HostState>()' in s
assert 'WeakValueMap<View, HostState>()' in s
assert 'Pair<WeakReference<HostState>, Int>' in s
assert s.count('setTag(t.mma.R.id.hchat_background_host_state, null)')==2
assert 'setTag(t.mma.R.id.hchat_background_host_state, it)' in s
code='''import java.lang.ref.WeakReference
import java.util.WeakHashMap
'''+weak+'''
class Page { var state: State? = null }
class State(val page: Page)
fun abandoned(index: WeakValueMap<Page, State>): Pair<WeakReference<Page>,WeakReference<State>> {
 val page=Page(); val state=State(page); page.state=state; index[page]=state
 check(index[page]===state)
 return WeakReference(page) to WeakReference(state)
}
fun main() {
 val index=WeakValueMap<Page,State>();val page=Page();val state=State(page)
 page.state=state;index[page]=state
 repeat(3){System.gc()};check(index[page]===state)
 val lost=abandoned(index)
 repeat(80){ if(lost.first.get()!=null || lost.second.get()!=null){System.gc();Thread.sleep(10)} }
 check(lost.first.get()==null && lost.second.get()==null){"Runtime index retained page/state cycle"}
 check(index.remove(page)===state);check(index[page]==null)
 println("PASS production weak state ownership: active page retains state, abandoned page cycle collectible, explicit removal")
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=15)
