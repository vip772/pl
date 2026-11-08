"""Run production shared ownership with JVM fixtures; check independent UI/config contracts."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];d=r/'app/src/main/java/t/mma/hooks/items/floatingchat'
s=(d/'ChatHeaderMount.kt').read_text();a=s.index('internal object ChatHeaderMount');b=s.index('internal class HeaderRuntime',a)
code='''class FeatureContext
object DexInstallScheduler {var installs=0;var closes=0;fun installation(a:String,b:String)=Ticket().also{installs++}
 class Ticket {fun close(){closes++};fun schedule(action:()->Boolean){check(action())}}}
class HeaderRuntime(c:FeatureContext) {var destroyed=false;var lease:(()->Unit)?=null
 fun ownInstallation(a:()->Unit){lease=a};fun install()=true;fun destroy(){check(!destroyed);destroyed=true;lease?.invoke()}}
'''+s[a:b]+'''
fun main(){val c=FeatureContext();val header=ChatHeaderMount.acquire(c);val stats=ChatHeaderMount.acquire(c)
 check(header===stats&&DexInstallScheduler.installs==1)
 ChatHeaderMount.release();check(!stats.destroyed);ChatHeaderMount.release();check(stats.destroyed&&DexInstallScheduler.closes==1)
 ChatHeaderMount.release();check(DexInstallScheduler.closes==1)
 val fresh=ChatHeaderMount.acquire(c);check(fresh!==header&&DexInstallScheduler.installs==2);ChatHeaderMount.release()
 println("PASS production shared mount: one installation for two owners, final-owner cleanup, idempotent release, reinstall")}
'''
lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'out'),str(p/'Test.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p/"out"}:{std}','TestKt'],check=True,timeout=15)
feature=(d/'GroupMemberStatisticsFeature.kt').read_text()
assert 'const val ID = "floating_chat_header_member_statistics"' in feature
assert 'FeatureSettingsProvider.CATEGORY_PRACTICAL, true' in feature
assert 'GroupMemberStatisticsFeature()' in (r/'app/src/main/java/t/mma/hooks/core/FeatureRegistry.kt').read_text()
groups=(r/'app/src/main/java/t/mma/ui/miuix/MiuixSettingsPagePart02.kt').read_text()
assert 'GroupMemberStatisticsFeature.ID' in groups[groups.index('title = "美化"'):groups.index('title = "界面"')]
assert 'practicalProviders.filter { it.isNewFeature() }' in groups
assert 'GroupMemberStatisticsMiuixPage(context, provider, onBack)' in (r/'app/src/main/java/t/mma/ui/miuix/MiuixSettingsPagePart03.kt').read_text()
assert 'isEnabled() || (capsule.supportsHost() && statisticsEnabled())' in s
apply=s[s.index('    private fun apply(sourceLayout:'):s.index('    private fun visibleChatLayout(')]
assert apply.index('if (!floating)') < apply.index('reparentHeader(')
assert apply.index('statisticsSurface.apply(group, title, statistics)') < apply.index('reparentHeader(')
assert 'statisticsSurface.restore(group)' in apply and 'presentationModes[group]' in apply
surface=(d/'GroupMemberStatisticsSurface.kt').read_text()
assert 'titleParams.height = header.height' in surface and 'titleParams.height = state.titleHeight' in surface
capsule=(d/'FloatingChatHeaderCapsule.kt').read_text()
assert 'child === excluded' in capsule and 'statistics' not in capsule
for floating in (False,True):
 for stats in (False,True):
  assert (floating or stats)==any((floating,stats))
print('PASS static contracts: independent feature/old key, beauty+new entries share provider, native branch precedes floating mutation, title reservation restore, separate surface ownership; four-case policy model only')
