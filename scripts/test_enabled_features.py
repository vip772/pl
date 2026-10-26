"""Compile production enabled registry/aggregation with JVM stubs; no Gradle."""
from pathlib import Path
import re, subprocess, tempfile
r=Path(__file__).resolve().parents[1];root=r/'app/src/main/java/t/mma';lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
reg=(root/'ui/FeatureToggleRegistry.kt').read_text();page=(root/'ui/miuix/MiuixSettingsPagePart02.kt').read_text()
# Settings referenced by the production registry retain their actual simple string/bool constants.
classes={}
for p in (root/'hooks/items').rglob('*.kt'):
 text=p.read_text();pkg=re.search(r'^package (\S+)',text)
 if not pkg:continue
 for name in re.findall(r'(?:object|class) (\w+)',text):classes[pkg[1]+'.'+name]=text
refs=dict(re.findall(r'import ([\w.]+\.(\w+))',reg))
for full in classes:
 if full in reg:refs[full]=full.rsplit('.',1)[1]
used={}
for full,name in refs.items():
 if not full.startswith("t.mma.hooks.items."):continue
 attrs=set(re.findall(re.escape(name)+r'\.(\w+)',reg));text=classes.get(full,'');const=[]
 for attr in attrs:
  if attr in ['enabled','isEnabled']:const.append('fun '+attr+'(context:android.content.Context)=false');continue
  if attr=='Entry':continue
  m=re.search(r'const val '+attr+r'\s*=\s*("[^"]*"|true|false)',text)
  if m:const.append('const val '+attr+' = '+m[1])
  else:raise AssertionError((full,attr))
 if name=='MeEntryVisibility':continue
 used[full]='package '+full.rsplit('.',1)[0]+'\nobject '+name+' {\n'+'\n'.join(const)+'\n}\n'
helpers=page[page.index('internal fun enabledFeatureProviders('):page.index('internal fun practicalFeatureGroups(')]
refresh=page[page.index('internal fun refreshedFeatureGroup('):page.index('internal data class FeatureSearchResult(')]
main='''package t.mma.ui.miuix
import t.mma.ui.*
import android.content.Context
internal data class FeatureGroupEntry(val title:String,val providers:List<FeatureSettingsProvider>)
'''+helpers+refresh+'''
fun main(){
 val context=Context()
 val registry=UIRegistry.get()
 fun provider(id:String,category:String=FeatureSettingsProvider.CATEGORY_PRACTICAL)=object:FeatureSettingsProvider{
  override fun featureId()=id;override fun title()=id;override fun subtitle()="";override fun category()=category
 }
 fun put(p:String,k:String,v:Boolean){t.mma.preferences.HchatStorage.booleans[p+":"+k]=v}
'''
mappings=re.findall(r'"([^"]+)" to Toggle\("([^"]+)", "([^"]+)"(?:, (true|false))?\)',reg)
for fid,pref,key,default in mappings:
 if '"'+fid+'" ->' in reg:continue
 main+=f'check(FeatureToggleRegistry.isEnabled(context,"{fid}")=={default or "false"})\n'
 main+=f'put("{pref}","{key}",true);check(FeatureToggleRegistry.isEnabled(context,"{fid}"));put("{pref}","{key}",false);check(!FeatureToggleRegistry.isEnabled(context,"{fid}"))\n'
main+='''
 put("Hchat_global_config","monet_module_generator_enabled",true)
 check(!FeatureToggleRegistry.isEnabled(context,"monet_module_generator"))
 val items=listOf(provider("chat_toolbar"),provider("member_title",FeatureSettingsProvider.CATEGORY_ENTERTAINMENT),provider("script_plugin",FeatureSettingsProvider.CATEGORY_ENHANCE))
 for(p in items)registry.registerProvider(p){FeatureToggleRegistry.isEnabled(context,p.featureId())}
 registry.registerProvider(provider("tool"))
 val old=FeatureGroupEntry("已开启功能",emptyList())
 check(refreshedFeatureGroup(old).providers.isEmpty())
 put("Hchat_chat_toolbar_config","enable",true)
 put("Hchat_member_title_config","enable",true)
 put("Hchat_script_plugin_config","script_plugin_enable",true)
 val current=refreshedFeatureGroup(old)
 check(current.providers.map{it.featureId()}.toSet()==setOf("chat_toolbar","member_title","script_plugin"))
 check(enabledFeatureProviders(current.providers+current.providers).size==3)
 put("Hchat_member_title_config","enable",false)
 check(refreshedFeatureGroup(current).providers.size==2)
 val normal=FeatureGroupEntry("美化",items);check(refreshedFeatureGroup(normal)===normal)
 put("Hchat_background_beauty_config","me_hide_favorite",true)
 check(FeatureToggleRegistry.isEnabled(context,"me_entry_visibility"))
 put("Hchat_background_beauty_config","me_hide_favorite",false)
 check(!FeatureToggleRegistry.isEnabled(context,"me_entry_visibility"))
 put("Hchat_text_voice_config","text_voice_play_enable",true)
 check(FeatureToggleRegistry.isEnabled(context,"text_voice"))
 put("Hchat_text_voice_config","text_voice_send_enable",true)
 check(FeatureToggleRegistry.isEnabled(context,"text_voice"))
 println("PASS real toggle mappings/default/on/off; cross-category/script aggregation; dedup; return refresh; composite toggles; unknown tools excluded")
}
'''
assert 'val enabledProviders = enabledFeatureProviders(UIRegistry.get().getAllProviders())' in page
assert 'DetailPage.FeatureGroup(refreshedFeatureGroup(page.sourceGroup))' in page
assert 'val groups = remember(practicalProviders' not in page
assert 'featureGroupListStates.getOrPut(targetPage.group.title)' in (root/'ui/miuix/MiuixSettingsPage.kt').read_text()
with tempfile.TemporaryDirectory() as td:
 p=Path(td);sources=[]
 def write(name,text):
  path=p/name;path.write_text(text);sources.append(str(path))
 write('Context.kt','package android.content\nclass Context')
 write('Storage.kt','''package t.mma.preferences
object HchatStorage {
 val booleans=mutableMapOf<String,Boolean>();val strings=mutableMapOf<String,String>()
 fun preferences(c:android.content.Context,n:String)=Prefs(n)
 class Prefs(val n:String){fun getBoolean(k:String,d:Boolean)=booleans[n+":"+k]?:d;fun getString(k:String,d:String?)=strings[n+":"+k]?:d}
}
''')
 for i,text in enumerate(used.values()):write(f'Dep{i}.kt',text)
 write('Main.kt',main)
 sources += [str(root/'ui/FeatureToggleRegistry.kt'),str(root/'ui/UIRegistry.kt'),str(root/'ui/FeatureSettingsProvider.kt'),str(root/'hooks/items/backgroundbeauty/MeEntryVisibility.kt')]
 jar=p/'test.jar'
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(jar),*sources],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{jar}:{std}','t.mma.ui.miuix.MainKt'],check=True,timeout=20)
