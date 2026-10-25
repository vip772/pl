"""Run production archive and storage with file-backed host stubs, no Gradle/device."""
from pathlib import Path
import subprocess,tempfile,os
repo=Path(__file__).resolve().parents[3]
lib=Path('/tmp/pl-kotlin-libs');stdlib=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
jsonjar=Path(os.environ.get('JSON_JAR','/tmp/backup-json.jar'))
assert jsonjar.exists(), 'Set JSON_JAR to org.json:json:20240303 (Maven Central)'
sources={
'Context.kt':'''package android.content
import java.io.File
import android.net.Uri
open class Context(val dataDir:File,val packageName:String="com.test") {
 val applicationContext:Context get()=this
 val filesDir=File(dataDir,"files").apply{mkdirs()}
 val cacheDir=File(dataDir,"cache").apply{mkdirs()}
 companion object {const val MODE_PRIVATE=0}
 val legacy=mutableMapOf<String,SharedPreferences>()
 fun getSharedPreferences(n:String,m:Int):SharedPreferences=legacy.getOrPut(n){io.fastkv.FastKV(n)}
 val contentResolver=Resolver()
}
class Resolver{fun openOutputStream(u:Uri,m:String)=u.file.outputStream();fun openInputStream(u:Uri)=u.file.inputStream()}
interface SharedPreferences {
 val all:Map<String,Any?>
 fun edit():Editor
 fun contains(k:String)=all.containsKey(k)
 fun getString(k:String,d:String?):String?=all[k] as? String?:d
 fun getBoolean(k:String,d:Boolean):Boolean=all[k] as? Boolean?:d
 fun getInt(k:String,d:Int):Int=all[k] as? Int?:d
 fun getLong(k:String,d:Long):Long=all[k] as? Long?:d
 fun getFloat(k:String,d:Float):Float=all[k] as? Float?:d
 interface Editor {
 fun clear():Editor
 fun putBoolean(k:String,v:Boolean):Editor
 fun putInt(k:String,v:Int):Editor
 fun putLong(k:String,v:Long):Editor
 fun putFloat(k:String,v:Float):Editor
 fun putString(k:String,v:String?):Editor
 fun putStringSet(k:String,v:Set<String>?):Editor
 fun commit():Boolean
 fun apply(){commit()}
 }
}
''',
'Uri.kt':'''package android.net
class Uri(val file:java.io.File)
''',
'Log.kt':'''package t.mma.utils
object HLog{fun e(s:String,t:Throwable?=null){}}
''',
'FastKV.kt':'''package io.fastkv
import android.content.SharedPreferences
import java.io.File
class FastKV(val name:String):SharedPreferences {
 companion object{val stores=mutableMapOf<String,FastKV>();var failName="";var failOnce=false}
 val values=linkedMapOf<String,Any?>()
 override val all get()=values.toMap()
 fun close(){}
 class Builder(val path:String,val name:String){fun build():FastKV {
 File(path,"$name.kva").apply{parentFile.mkdirs();createNewFile()}
 return stores.getOrPut("$path/$name"){FastKV(name)}
 }}
 override fun edit()=object:SharedPreferences.Editor {
 val next=values.toMutableMap()
 override fun clear()=apply{next.clear()}
 override fun putBoolean(k:String,v:Boolean)=apply{next[k]=v}
 override fun putInt(k:String,v:Int)=apply{next[k]=v}
 override fun putLong(k:String,v:Long)=apply{next[k]=v}
 override fun putFloat(k:String,v:Float)=apply{next[k]=v}
 override fun putString(k:String,v:String?)=apply{next[k]=v}
 override fun putStringSet(k:String,v:Set<String>?)=apply{next[k]=v}
 override fun commit():Boolean{if(failOnce&&name==failName){failOnce=false;return false};values.clear();values.putAll(next);return true}
 }
}
''',
'Main.kt':'''package t.mma.ui.miuix
import android.content.Context
import android.net.Uri
import t.mma.preferences.HchatStorage as Store
import io.fastkv.FastKV
import java.io.File
import java.util.zip.*
import org.json.*
fun main(args:Array<String>){
 val base=File(args[0]);val a=Context(File(base,"a"));val b=Context(File(base,"b"),"com.test.b")
 val p=Store.preferences(a,"Hchat_brand_new_feature")
 p.edit().putBoolean("enabled",true).putInt("i",42).putLong("l",1234567890123L).putFloat("f",1.25f).putStringSet("s",setOf("a","b")).commit()
 // Legacy AI values are exported without opening the AI settings page.
 a.getSharedPreferences("Hchat_conversation_analysis_config",0).edit().putString("api_key","legacy-test").commit()
 val image=File(Store.resourceDirectory(a,"future"),"image.png");image.writeText("image-data")
 p.edit().putString("path",image.absolutePath).commit()
 File(Store.storageDir(a),"Hchat_unopened.kvb").writeText("")
 Store.preferences(a,"Hchat_weather_cache").edit().putString("data","runtime").commit()
 Store.runtimePreferences(a,"Hchat_custom_locator").edit().putInt("dex",5).commit()
 Store.preferences(a,"Hchat_mention_notice_config_seen").edit().putBoolean("id",true).commit()
 check(Store.backupPreferenceNames(a).containsAll(listOf("Hchat_brand_new_feature","Hchat_unopened")))
 check("Hchat_custom_locator" !in Store.backupPreferenceNames(a))
 val backup=File(base,"test.zip");val result=HchatConfigBackup.exportToUri(a,Uri(backup))
 check(result.stores==3 && result.resources==1)
 val restored=HchatConfigBackup.importFromUri(b,Uri(backup));check(restored.items==7)
 val out=Store.preferences(b,"Hchat_brand_new_feature")
 check(out.getInt("i",0)==42 && out.getLong("l",0)==1234567890123L && out.getFloat("f",0f)==1.25f)
 check(File(out.getString("path","")!!).readText()=="image-data")
 check(Store.preferences(b,"Hchat_conversation_analysis_config").getString("api_key","")=="legacy-test")
 check(out.getString("path","")!!.startsWith(Store.storageDir(b).absolutePath))
 val only=Context(File(base,"only"),"com.only")
 File(Store.resourceDirectory(only,"new"),"icon").writeText("icon")
 val onlyZip=File(base,"only.zip");check(HchatConfigBackup.exportToUri(only,Uri(onlyZip)).items==0)
 check(HchatConfigBackup.importFromUri(b,Uri(onlyZip)).resources==1)
 // Calendar uses a fixed resource path, so restore never needs the source URI.
 val cal=Context(File(base,"calendar"),"com.calendar")
 val calOut=Context(File(base,"calendar-restored"),"com.calendar.restored")
 Store.preferences(cal,"Hchat_home_extension_config").edit().putInt("calendar_alpha",61).putLong("calendar_background_revision",9L).commit()
 val calImage=File(Store.resourceDirectory(cal,"home_calendar"),"background.jpg")
 calImage.writeBytes(byteArrayOf(1,2,3,4,5))
 File(calImage.parentFile,"background.import").writeText("partial")
 File(calImage.parentFile,"background.tmp").writeText("partial")
 val calZip=File(base,"calendar.zip")
 check(HchatConfigBackup.exportToUri(cal,Uri(calZip)).resources==1)
 check(HchatConfigBackup.importFromUri(calOut,Uri(calZip)).resources==1)
 check(File(Store.resourceDirectory(calOut,"home_calendar"),"background.jpg").readBytes().contentEquals(calImage.readBytes()))
 check(Store.preferences(calOut,"Hchat_home_extension_config").getInt("calendar_alpha",0)==61)
 check(!File(Store.resourceDirectory(calOut,"home_calendar"),"background.import").exists())
 println("PASS: calendar background resource + opacity round trip; temporary files excluded")
 // Empty stores also round trip.
 val c=Context(File(base,"c"),"com.test.c");HchatConfigBackup.importFromUri(c,Uri(backup))
 fun legacy(value:JSONObject):File {
 val f=File(base,"old.json");f.writeText(JSONObject().put("format","HchatConfigBackup").put("version",1)
 .put("prefs",JSONObject().put("Hchat_future_unknown",JSONObject().put("key",value))).toString());return f
 }
 HchatConfigBackup.importFromUri(b,Uri(legacy(JSONObject().put("type","string").put("value","old"))))
 check(Store.preferences(b,"Hchat_future_unknown").getString("key","")=="old")
 val before=out.all
 val invalid=legacy(JSONObject().put("type","int").put("value",1)).readText()
 val forbidden=File(base,"forbidden.json");forbidden.writeText(invalid.replace("Hchat_future_unknown","Hchat_weather_cache"))
 check(runCatching{HchatConfigBackup.importFromUri(b,Uri(forbidden))}.isFailure)

 check(runCatching{HchatConfigBackup.importFromUri(b,Uri(legacy(JSONObject().put("type","int").put("value","bad"))))}.isFailure)
 check(out.all==before)
 // Commit failure after resource replacement must restore both preferences and resource.
 val target=File(Store.resourceDirectory(b,"future"),"image.png");target.writeText("before")
 out.edit().putInt("i",99).commit();FastKV.failName="Hchat_brand_new_feature";FastKV.failOnce=true
 check(runCatching{HchatConfigBackup.importFromUri(b,Uri(backup))}.isFailure)
 check(out.getInt("i",0)==99 && target.readText()=="before")
 val evil=File(base,"evil.zip")
 ZipOutputStream(evil.outputStream()).use{it.putNextEntry(ZipEntry("resources/resources/../escape"));it.write(byteArrayOf(1));it.closeEntry()}
 check(runCatching{HchatConfigBackup.importFromUri(b,Uri(evil))}.isFailure)
 // Symbolic links must not escape a managed directory.
 val external=File(base,"outside").apply{mkdirs()}
 java.nio.file.Files.createSymbolicLink(File(Store.resourceDirectory(b,"future"),"link").toPath(),external.toPath())
 val bad=File(base,"link.zip")
 ZipOutputStream(bad.outputStream()).use{it.putNextEntry(ZipEntry("resources/resources/future/link/bad"));it.write(byteArrayOf(1));it.closeEntry()}
 check(runCatching{HchatConfigBackup.importFromUri(b,Uri(bad))}.isFailure)
 println("PASS: automatic new/unopened stores; runtime exclusion; all value types; resources; path relocation; legacy JSON; validation; commit rollback; traversal and links")
}
'''}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for n,s in sources.items():(p/n).write_text(s)
 jar=p/'test.jar'
 production=[repo/'app/src/main/java/t/mma/preferences/HchatStorage.kt',repo/'app/src/main/java/t/mma/ui/miuix/HchatConfigBackup.kt']
 subprocess.run(['java','-Xmx384m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',f'{stdlib}:{jsonjar}','-d',str(jar),*[str(p/n) for n in sources],*map(str,production)],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{jar}:{stdlib}:{jsonjar}','t.mma.ui.miuix.MainKt',str(p/'data')],check=True,timeout=30)
