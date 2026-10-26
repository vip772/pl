"""Run production favorites selection and ensure shared UI actions do not consume search queues."""
from pathlib import Path
import tempfile,subprocess
r=Path(__file__).resolve().parents[1];base=r/'app/src/main/java/t/mma/hooks/items/homeextension';lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
s=(base/'HomeMusic.kt').read_text()
assert 'queue' not in s
assert 'private fun previous(c:Context) = stepFavorite(c, -1)' in s
assert 'private fun next(c:Context) = stepFavorite(c, 1)' in s
assert 'val list=favorites(c)' in s[s.index('private fun stepFavorite'):]
code="""package t.mma.hooks.items.homeextension
fun main(){
 check(favoriteStepIndex(1,0,1,false)==0)
 check(favoriteStepIndex(3,0,-1,false)==2)
 check(favoriteStepIndex(3,2,1,false)==0)
 check(favoriteStepIndex(3,-1,1,false)==0)
 check(favoriteStepIndex(3,-1,-1,false)==2)
 repeat(100){check(favoriteStepIndex(3,1,1,true) in listOf(0,2))}
 check(runCatching{favoriteStepIndex(0,-1,1,false)}.isFailure)
 println("PASS live favorites wrap/missing current/one/empty/shuffle; all styles use shared previous/next")
}
"""
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(base/'HomeMusicFavoriteNavigation.kt'),str(p/'Test.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.homeextension.TestKt'],check=True,timeout=10)
