#!/usr/bin/env python3
"""JVM regression for production auto-cache chat/type policy; no Gradle required."""
from pathlib import Path
import subprocess, tempfile
repo = Path(__file__).resolve().parents[3]
lib = Path('/tmp/pl-kotlin-libs')
jars = list(lib.glob('*.jar'))
stdlib = next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
feature = repo / 'app/src/main/java/t/mma/hooks/items/autocache/AutoCacheFeature.kt'
source = feature.read_text()
policy = source[source.index('object AutoCacheSettings {'):source.index('class AutoCacheFeature :')]
with tempfile.TemporaryDirectory() as directory:
    root = Path(directory)
    (root/'Policy.kt').write_text('package t.mma.hooks.items.autocache\nimport t.mma.hooks.api.model.WeChatMessage\n'+policy)
    (root/'Message.kt').write_text('''package t.mma.hooks.api.model
class WeChatMessage(val kind:String,val ext:String="") {
 fun isImage()=kind=="image";fun isVideo()=kind=="video";fun isFile()=kind=="file"
 fun getFileMsg()=FileMessage(ext)
}
class FileMessage(val ext:String)
''')
    (root/'Test.kt').write_text('''import t.mma.hooks.items.autocache.AutoCacheSettings as S
import t.mma.hooks.api.model.WeChatMessage as M
fun main() {
 check(S.acceptsChat("friend", emptySet()));check(S.acceptsChat("group@chatroom", emptySet()))
 val selected=setOf("friend", "group@chatroom")
 check(S.acceptsChat("friend",selected));check(S.acceptsChat("group@chatroom",selected))
 check(!S.acceptsChat("other",selected));check(!S.acceptsChat("other@chatroom",selected))
 check(!S.acceptsChat("friend@chatroom",setOf("friend")))
 check(!S.acceptsChat(null,selected));check(!S.acceptsChat("",selected))
 check(!S.acceptsChat("friend",setOf("other")));check(S.acceptsChat("friend",emptySet()))
 for (type in listOf(S.TYPE_MEDIA,S.TYPE_MEDIA_PDF,S.TYPE_ALL)) {
  check(S.accepts(M("image"),type));check(S.accepts(M("video"),type));check(!S.accepts(M("link"),type))
 }
 check(!S.accepts(M("file","pdf"),S.TYPE_MEDIA))
 check(S.accepts(M("file"," .PDF "),S.TYPE_MEDIA_PDF));check(!S.accepts(M("file","zip"),S.TYPE_MEDIA_PDF))
 check(S.accepts(M("file","zip"),S.TYPE_ALL));check(S.accepts(M("file","zip"),null))
 println("PASS: 空名单全部、联系人与群聊混选、排除未选聊天、群ID隔离、名单修改/清空、缓存类型兼容")
}
''')
    subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,jars)), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(stdlib),'-d',str(root/'test.jar'),*[str(p) for p in root.glob('*.kt')]],check=True)
    subprocess.run(['java','-cp',str(root/'test.jar')+':'+str(stdlib),'TestKt'],check=True,timeout=10)
# Both admission and deferred execution must use the policy, so pending work obeys new settings.
assert source.count('AutoCacheSettings.acceptsChat(message.talker,') == 2
assert 'prefs.getStringSet(AutoCacheSettings.KEY_CHATS' in source
print('PASS: 入队与延迟执行均使用实时聊天名单')
