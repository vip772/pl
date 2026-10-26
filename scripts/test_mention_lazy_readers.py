"""Compare production readers/classification with c28717d; no Gradle."""
from pathlib import Path
import subprocess, tempfile, re
root=Path(__file__).resolve().parents[1]; libs=Path('/tmp/pl-kotlin-libs'); std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
rel='app/src/main/java/t/mma/hooks/api/model/WeChatMessage.kt'; model=(root/rel).read_text()
legacy=subprocess.check_output(['git','show','c28717d:'+rel],cwd=root,text=True)
for name in re.findall(r'^(?:data )?class (WeChat\w+)', legacy, re.M):
 legacy=re.sub(r'\b'+name+r'\b','Legacy'+name,legacy)
legacy=legacy.replace('LegacyWeChatMessage', 'LegacyMessage')
# Instrument only temporary compilation copies, not production source.
model=model.replace('fun msgSourceValue(source: String?, key: String): String {','fun msgSourceValue(source: String?, key: String): String {\n Counts.reads++')
start=model.index('fun msgSourceValue(');end=model.index('fun extractXml(',start)
chunk=model[start:end]
chunk=chunk.replace('val match1 = Regex(', 'Counts.regexes++; val match1 = Regex(').replace('val match2 = Regex(', 'Counts.regexes++; val match2 = Regex(').replace('val match3 = Regex(', 'Counts.regexes++; val match3 = Regex(')
model=model[:start]+chunk+model[end:]
main=r'''import t.mma.hooks.api.model.*
fun main(){
 val values=listOf("", " ", "me", "alice;me|bob", "notify@all", "announcement@all", "all", "@all", "<![CDATA[me]]>", "&amp;", "&#109;e", "'quoted'", "\"double\"", "\nme\t")
 val keys=listOf("atuserlist", ".msgsource.atuserlist", "atall", ".msgsource.atall", "custom.key", "", "a+b")
 val sources=mutableListOf<String?>(null,"", " ", "<refermsg><atuserlist>me</atuserlist></refermsg>")
 for(v in values) for(k in keys){
  sources.addAll(listOf("<$k>$v</$k>","<$k><![CDATA[$v]]></$k>","$k='$v'", "$k=\"$v\"", "$k=<![CDATA[$v]]>", "$k=$v", "$k='$v'; $k=other", "<ATUSERLIST>$v</ATUSERLIST>"))
 }
 sources.addAll(listOf("<atuserlist>alice</atuserlist> .msgsource.atuserlist='me'", "<atall>0</atall> .msgsource.atall='1'", "<refermsg><atuserlist>notify@all</atuserlist></refermsg><atuserlist>me</atuserlist>", "&lt;atuserlist&gt;me&lt;/atuserlist&gt;", "<refermsg><atuserlist>me", "<!-- <refermsg> --> .msgsource.atuserlist='alice'"))
 var count=0
 for(s in sources){
  check(WeChatMessage.parseAtUserList(s)==LegacyMessage.parseAtUserList(s)){"list: $s"};count++
  for(k in keys){check(WeChatMessage.msgSourceValue(s,k)==LegacyMessage.msgSourceValue(s,k)){"key $k: $s"};count++}
  for(body in listOf("", "@所有人\u2005hello", "@me hi", "<refermsg>@所有人\u2005</refermsg>reply")) for(self in listOf("", "me", "alice")){
   check(WeChatMessage.classifyAtMention(s,body,self).name==LegacyMessage.classifyAtMention(s,body,self).name){"classification: $s"}; count++
  }
 }
 Counts.reads=0;Counts.regexes=0
 check(WeChatMessage.parseAtUserList("<atuserlist>me</atuserlist>")==listOf("me"))
 check(Counts.reads==0 && Counts.regexes==0)
 Counts.reads=0;Counts.regexes=0
 check(WeChatMessage.msgSourceValue("atuserlist='me'", "atuserlist")=="me")
 check(Counts.reads==1 && Counts.regexes==1)
 Counts.reads=0;Counts.regexes=0
 check(WeChatMessage.msgSourceValue("atuserlist=<![CDATA[me]]>", "atuserlist")=="me");check(Counts.regexes==2)
 Counts.reads=0;Counts.regexes=0
 check(WeChatMessage.msgSourceValue("atuserlist=me", "atuserlist")=="me");check(Counts.regexes==3)
 Counts.reads=0;Counts.regexes=0
 check(WeChatMessage.classifyAtMention("<atuserlist>me</atuserlist><atall>1</atall>","", "me")==WeChatMessage.AtMentionType.AT_ALL)
 check(Counts.reads==0 && Counts.regexes==0)
 println("PASS $count legacy comparisons; XML zero fallback; quoted/CDATA/bare compile 1/2/3 regexes")
}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for n,s in [('WeChatMessage.kt',model),('LegacyMessage.kt',legacy),('Main.kt',main),('Counts.kt','package t.mma.hooks.api.model\nobject Counts { var reads=0; var regexes=0 }')]: (p/n).write_text(s)
 files=list(p.glob('*.kt'))+[root/rel.replace('WeChatMessage.kt',n) for n in ['WeChatMessageTypes.kt','WeChatParsedMessage.kt']]
 jar=p/'test.jar'
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(jar),*map(str,files)],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{jar}:{std}','MainKt'],check=True,timeout=60)
