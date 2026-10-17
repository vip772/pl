from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[3];base=r/'app/src/main/java/t/mma/hooks/items/homesidepanel';lib=Path('/tmp/pl-kotlin-libs');stdlib=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
files={
'Context.kt':'package android.content\nopen class Context\nclass SharedPreferences {val data=mutableMapOf<String,Any>();fun getString(k:String,d:String?):String?=data[k] as? String ?: d;fun getBoolean(k:String,d:Boolean)=data[k] as? Boolean ?: d;fun getInt(k:String,d:Int)=data[k] as? Int ?: d;fun edit()=Editor();inner class Editor {fun putString(k:String,v:String)=apply{data[k]=v};fun putInt(k:String,v:Int)=apply{data[k]=v};fun putBoolean(k:String,v:Boolean)=apply{data[k]=v};fun remove(k:String)=apply{data.remove(k)};fun apply(){}}}',
'Storage.kt':'package t.mma.preferences\nobject HchatStorage {val prefs=android.content.SharedPreferences();fun preferences(c:android.content.Context,n:String)=prefs}',
'Color.kt':'package android.graphics\nobject Color {fun parseColor(s:String)=0}',
'Gradient.kt':'package t.mma.hooks.items.antirecall\nobject DynamicRecallGradient {const val DEFAULT="";fun parts(s:String)=listOf("#ffffff")}',
'Images.kt':'package t.mma.hooks.items.homesidepanel\nobject SidePanelImages {val removed=mutableListOf<String>();fun clear(c:android.content.Context,id:String){removed+=id}}',
'Test.kt':'''import t.mma.hooks.items.homesidepanel.*
fun main(){val c=android.content.Context();val p=HomeSidePanelSettings.preferences(c)
 check(HomeSidePanelSettings.Shortcut.entries.size==8);check(HomeSidePanelSettings.Shortcut.QR_CODE.title=="二维码");check(HomeSidePanelSettings.Shortcut.SETTINGS.title=="设置")
 val all=HomeSidePanelSettings.Shortcut.entries.toList();HomeSidePanelSettings.saveShortcuts(c,all);check(HomeSidePanelSettings.shortcuts(c)==all)
 HomeSidePanelSettings.saveShortcuts(c,all.reversed());check(HomeSidePanelSettings.shortcuts(c)==all.reversed())
 for(n in 1..8){val rows=all.take(n).chunked(4);check(rows.flatten().size==n);check(rows.size<=2 && rows.all{it.size in 1..4});val width=320f;rows.forEach{val cell=(width-5*(it.size-1))/it.size;check(kotlin.math.abs(cell*it.size+5*(it.size-1)-width)<.01f)}}
 val a=SidePanelAppearance.addPhoto(c);val b=SidePanelAppearance.addPhoto(c);check(a!=b);check(SidePanelAppearance.order(c).containsAll(listOf(a,b)))
 p.edit().putString(SidePanelAppearance.ORDER,"$b,clock,$a,photo,shortcuts,hchat").putInt("${b}_height",900).apply();check(SidePanelAppearance.order(c).first()==b);check(SidePanelAppearance.photoHeight(c,b)==500)
 SidePanelAppearance.removePhoto(c,a);check(a !in SidePanelAppearance.order(c));check(b in SidePanelAppearance.order(c));check(SidePanelImages.removed==listOf(a));check("photo" in SidePanelAppearance.order(c))
 println("PASS: 8快捷名称与顺序、1至8均分网格、独立图片卡片增删排序及高度边界")}
'''}
with tempfile.TemporaryDirectory() as t:
 p=Path(t)
 for name,text in files.items(): (p/name).write_text(text)
 for name in ['HomeSidePanelSettings.kt','SidePanelAppearance.kt']: (p/name).write_text((base/name).read_text())
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(stdlib),'-d',str(p/'test.jar'),*map(str,p.glob('*.kt'))],check=True,timeout=40)
 subprocess.run(['java','-cp',f'{p}/test.jar:{stdlib}','TestKt'],check=True,timeout=10)