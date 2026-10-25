"""Production cancellable file readiness protocol, no Gradle/device."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'));cor=next(lib.glob('kotlinx-coroutines-core-jvm*.jar'))
main='''package t.mma.hooks.api.media
import kotlinx.coroutines.*
fun main()=runBlocking {
 var downloads=0;var loading=0;var resolved=0
 check(awaitFavoriteVoiceFile(resolve={"local"},startDownload={downloads++;true},downloading={loading++})=="local")
 check(downloads==0&&loading==0)
 check(awaitFavoriteVoiceFile(resolve={if(++resolved>=3)"remote" else null},startDownload={downloads++;true},downloading={loading++},attempts=5,pollMillis=1)=="remote")
 check(downloads==1&&loading==1)
 check(runCatching{awaitFavoriteVoiceFile<String>({null},{false},{},attempts=1,pollMillis=1)}.isFailure)
 check(runCatching{awaitFavoriteVoiceFile<String>({null},{true},{},attempts=2,pollMillis=1)}.exceptionOrNull()?.message?.contains("超时")==true)
 var callbacks=0
 val j=launch {awaitFavoriteVoiceFile<String>({null},{true},{},pollMillis=1000);callbacks++}
 yield();j.cancelAndJoin();check(callbacks==0)
 val j2=launch(start=CoroutineStart.LAZY){awaitFavoriteVoiceFile({"ready"},{true},{});callbacks++}
 j2.cancel();j2.join();check(callbacks==0)
 // Cancelling the actual download-start suspension must not dispatch playback/send.
 val j3=launch {awaitFavoriteVoiceFile<String>({null},{delay(1000);true},{},pollMillis=1);callbacks++}
 yield();j3.cancelAndJoin();check(callbacks==0)
 println("PASS: local/no download; remote readiness; download rejection; timeout; cancel while waiting, before start and during download dispatch; no late action")
}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Main.kt').write_text(main)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',f'{std}:{cor}','-d',str(p/'test.jar'),str(p/'Main.kt'),str(r/'app/src/main/java/t/mma/hooks/api/media/FavoriteVoiceFilePreparation.kt')],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{p}/test.jar:{std}:{cor}','t.mma.hooks.api.media.MainKt'],check=True,timeout=15)
s=(r/'app/src/main/java/t/mma/hooks/items/voiceforward/VoiceForwardFeature.kt').read_text();a=s.index('    private fun handleFavoriteSelectClick(');b=s.index('    private fun handleMultiNativeVoiceMenuClick',a);s=s[a:b]
assert '收藏语音文件不存在' not in s
assert 'awaitFavoriteVoiceFile(' in s and 'requestDownloadAwait' in s
print('PASS selector opens confirmation without a local-file precondition')