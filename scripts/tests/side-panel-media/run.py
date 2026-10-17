from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[3];s=(r/'app/src/main/java/t/mma/hooks/items/homesidepanel/SidePanelMediaView.kt').read_text()
a=s.index('    fun setPlayback(');b=s.index('    private fun startVideo()',a)
code='''class Animation {var starts=0;var stops=0;fun start(){starts++};fun stop(){stops++}}
class Gif {var playing=false;var resets=0;fun restart(){resets++}}
class Harness {
 var disposed=false;var active=false;var restartPending=false;var decodePending=false
 val animated:Animation?=Animation();val movieView:Gif?=Gif();val texture:Any?=Any();var starts=0;var stops=0
 fun startVideo(){starts++};fun stopVideo(){stops++}
'''+s[a:b]+'''}
fun main(){val h=Harness();h.setPlayback(true);check(h.animated!!.starts==1 && h.movieView!!.resets==1);h.setPlayback(true);check(h.animated.starts==1);h.setPlayback(false);check(h.stops==1 && !h.movieView.playing);h.setPlayback(true);check(h.movieView.resets==2);h.setPlayback(false);h.decodePending=true;h.setPlayback(true);check(h.restartPending);check(h.animated.starts==2);h.setPlayback(false);check(!h.restartPending);println("PASS: 打开从头播放、重复刷新不重启、关闭停止、解码等待与关闭竞态")}
'''
lib=Path('/tmp/pl-kotlin-libs');stdlib=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as t:
 p=Path(t);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(stdlib),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=40)
 subprocess.run(['java','-cp',f'{p}/test.jar:{stdlib}','TestKt'],check=True,timeout=10)
