"""Exercise production private-chat settings/filter with isolated Android/storage stubs; no Gradle."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
LIBS = Path('/tmp/pl-kotlin-libs')
STD = next(LIBS.glob('kotlin-stdlib-[0-9]*.jar'))
SOURCES = {
    'Context.kt': 'package android.content\nopen class Context\ninterface SharedPreferences { fun getBoolean(k:String,d:Boolean):Boolean; fun getInt(k:String,d:Int):Int; fun getString(k:String,d:String):String; fun contains(k:String):Boolean }',
    'TextUtils.kt': 'package android.text\nobject TextUtils { @JvmStatic fun isEmpty(v:CharSequence?)=v.isNullOrEmpty() }',
    'Storage.kt': '''package t.mma.preferences
object HchatStorage {
 val values=mutableMapOf<String,Any>()
 fun preferences(c:android.content.Context,n:String)=object:android.content.SharedPreferences {
  override fun getBoolean(k:String,d:Boolean)=values[k] as? Boolean?:d
  override fun getInt(k:String,d:Int)=values[k] as? Int?:d
  override fun getString(k:String,d:String)=values[k] as? String?:d
  override fun contains(k:String)=values.containsKey(k)
 }
}''',
    'Rules.kt': '''package t.mma.hooks.items.payment.core
class RedPacketRuleTemplate
class RedPacketRuleBinding
object RedPacketRuleConfig { const val KEY_TEMPLATES="templates";const val KEY_BINDINGS="bindings"; fun parseTemplates(s:String)=emptyList<RedPacketRuleTemplate>();fun parseBindings(s:String)=emptyList<RedPacketRuleBinding>() }
object PaymentTemplateTimeFormatter { const val DEFAULT_PATTERN="HH:mm" }
data class RedPacketEffectiveRule(val enabled:Boolean=true,val skipSelf:Boolean=false,val listMode:Int=0,val whitelist:String="",val blacklist:String="",val keywordMode:Int=0,val keywords:String="") { fun isInQuietTime()=false }
''',
    'Parser.kt': '''package t.mma.hooks.items.payment.detect
object RedPacketParser { fun containsKeyword(c:String?,k:String?)=!k.isNullOrEmpty() && c.orEmpty().contains(k) }
''',
    'Main.kt': '''package t.mma.hooks.items.payment.detect
import t.mma.hooks.items.payment.core.*
import t.mma.preferences.HchatStorage
fun main() {
 val settings=RedPacketSettings(ClassLoader.getSystemClassLoader(),android.content.Context())
 val filter=RedPacketFilter(settings)
 fun legacy(t:String?,kw:Int=0)=filter.getRejectReason("friend",t,"红包",null,"me",false,0,kw,"指定词")
 fun template(t:String?,r:RedPacketEffectiveRule=RedPacketEffectiveRule())=filter.getRejectReason("friend",t,"红包",null,"me",r)
 val privateTalkers=listOf("friend","contact@openim")
 val groups=listOf("group@chatroom","group@im.chatroom")
 for(t in privateTalkers+groups) {check(legacy(t)==null);check(template(t)==null)}
 HchatStorage.values[RedPacketSettings.KEY_SKIP_PRIVATE]=true
 for(t in privateTalkers) {
  check(settings.shouldSkipPrivateChat(t));check(legacy(t)=="私聊不抢");check(template(t)=="私聊不抢")
  check(template(t,RedPacketEffectiveRule(listMode=1,whitelist="friend"))=="私聊不抢")
 }
 for(t in groups) {check(!settings.shouldSkipPrivateChat(t));check(legacy(t)==null);check(template(t)==null)}
 for(t in listOf(null,"")) {check(!settings.shouldSkipPrivateChat(t));check(legacy(t)==null)}
 check(legacy("group@chatroom",1)=="未包含指定关键词")
 check(template("group@chatroom",RedPacketEffectiveRule(enabled=false))=="规则已关闭")
 HchatStorage.values[RedPacketSettings.KEY_SKIP_PRIVATE]=false
 for(t in privateTalkers) {check(legacy(t)==null);check(template(t)==null)}
 check(legacy("friend",1)=="未包含指定关键词")
 println("PASS: default off; both filter overloads; private/OpenIM blocked; both group suffixes preserved; whitelist cannot bypass; live off/on/off; existing keyword/disabled rules preserved; missing talker unchanged")
}
''',
}
with tempfile.TemporaryDirectory(prefix='pl-skip-private-behavior-') as td:
    path = Path(td)
    for name, source in SOURCES.items():
        (path / name).write_text(source)
    production = ROOT / 'app/src/main/java/t/mma/hooks/items/payment'
    files = list(path.glob('*.kt')) + [production / 'core/RedPacketSettings.kt', production / 'detect/RedPacketFilter.kt']
    subprocess.run(['java', '-Xmx256m', '-cp', ':'.join(map(str, LIBS.glob('*.jar'))),
                    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-nowarn', '-no-stdlib', '-no-reflect',
                    '-classpath', str(STD), '-d', str(path / 'test.jar'), *map(str, files)], check=True, timeout=55)
    subprocess.run(['java', '-cp', f'{path / "test.jar"}:{STD}',
                    't.mma.hooks.items.payment.detect.MainKt'], check=True, timeout=15)
