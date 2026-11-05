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
 fun registerOnSharedPreferenceChangeListener(listener:OnSharedPreferenceChangeListener){}
 fun interface OnSharedPreferenceChangeListener {fun onSharedPreferenceChanged(prefs:SharedPreferences,key:String?)}
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
 val listeners=mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()
 override fun registerOnSharedPreferenceChangeListener(listener:SharedPreferences.OnSharedPreferenceChangeListener){listeners.add(listener)}
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
 override fun commit():Boolean{if(failOnce&&name==failName){failOnce=false;return false};val changed=(values.keys+next.keys).filter{values[it]!=next[it]};values.clear();values.putAll(next);for(k in changed)for(listener in listeners.toList())listener.onSharedPreferenceChanged(this@FastKV,k);return true}
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
 val mentionAll=Store.preferences(a,"Hchat_mention_all_config")
 mentionAll.edit().putBoolean("enable",true).putString("command","#notify").commit()
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
 check(Store.backupPreferenceNames(a).containsAll(listOf("Hchat_brand_new_feature","Hchat_unopened","Hchat_mention_all_config")))
 check("Hchat_custom_locator" !in Store.backupPreferenceNames(a))
 val backup=File(base,"test.zip");val result=HchatConfigBackup.exportToUri(a,Uri(backup))
 check(result.stores==4 && result.resources==1)
 val restored=HchatConfigBackup.importFromUri(b,Uri(backup));check(restored.items==9)
 val restoredMentionAll=Store.preferences(b,"Hchat_mention_all_config")
 check(restoredMentionAll.getBoolean("enable",false) && restoredMentionAll.getString("command","")=="#notify")
 val out=Store.preferences(b,"Hchat_brand_new_feature")
 check(out.getInt("i",0)==42 && out.getLong("l",0)==1234567890123L && out.getFloat("f",0f)==1.25f)
 check(File(out.getString("path","")!!).readText()=="image-data")
 check(Store.preferences(b,"Hchat_conversation_analysis_config").getString("api_key","")=="legacy-test")
 check(out.getString("path","")!!.startsWith(Store.storageDir(b).absolutePath))
 val only=Context(File(base,"only"),"com.only")
 File(Store.resourceDirectory(only,"new"),"icon").writeText("icon")
 val onlyZip=File(base,"only.zip");check(HchatConfigBackup.exportToUri(only,Uri(onlyZip)).items==0)
 check(HchatConfigBackup.importFromUri(b,Uri(onlyZip)).resources==1)
 // Mention-all settings remain independent of hidden-mention and survive enabled/disabled restore.
 val allContext=Context(File(base,"mention-all"),"com.mention.all")
 val allRestored=Context(File(base,"mention-all-restored"),"com.mention.all.restored")
 val allConfig=Store.preferences(allContext,"Hchat_mention_all_config")
 val hiddenConfig=Store.preferences(allContext,"Hchat_hidden_at_config")
 Store.runtimePreferences(allContext,"Hchat_mention_all_runtime").edit().putString("members","not-user-config").commit()
 for (allOn in listOf(false,true)) for (hiddenOn in listOf(false,true)) {
  allConfig.edit().putBoolean("enable",allOn).putString("command",if(allOn) "#notify" else "#aa").commit()
  hiddenConfig.edit().putBoolean("enable",hiddenOn).commit()
  val allZip=File(base,"mention-all-$allOn-$hiddenOn.zip")
  check(HchatConfigBackup.exportToUri(allContext,Uri(allZip)).items==3)
  HchatConfigBackup.importFromUri(allRestored,Uri(allZip))
  val allResult=Store.preferences(allRestored,"Hchat_mention_all_config")
  check(allResult.getBoolean("enable",!allOn)==allOn)
  check(allResult.getString("command","")==if(allOn) "#notify" else "#aa")
  check(Store.preferences(allRestored,"Hchat_hidden_at_config").getBoolean("enable",!hiddenOn)==hiddenOn)
  check("Hchat_mention_all_runtime" !in Store.backupPreferenceNames(allContext))
 }
 println("PASS mention-all: all four independent switch combinations and custom/default command archive round trip; runtime excluded")
 // Message menu layout values use the existing automatically discovered config store.
 val menu=Context(File(base,"menu"),"com.menu")
 val menuOut=Context(File(base,"menu-out"),"com.menu.out")
 for (columns in 5..8) for (rows in 3..5) {
  val menuPrefs=Store.preferences(menu,"Hchat_message_menu_extension_config")
  menuPrefs.edit().putBoolean("message_menu_extension_enable",true)
   .putInt("message_menu_extension_columns",columns).putInt("message_menu_extension_max_rows",rows).commit()
  val menuZip=File(base,"menu-$columns-$rows.zip")
  check(HchatConfigBackup.exportToUri(menu,Uri(menuZip)).items==3)
  HchatConfigBackup.importFromUri(menuOut,Uri(menuZip))
  val restoredMenu=Store.preferences(menuOut,"Hchat_message_menu_extension_config")
  check(restoredMenu.getBoolean("message_menu_extension_enable",false))
  check(restoredMenu.getInt("message_menu_extension_columns",0)==columns)
  check(restoredMenu.getInt("message_menu_extension_max_rows",0)==rows)
 }
 println("PASS: message menu all 12 column/row configurations production archive round trip")
 // Global private-chat switch: absent defaults off; true and false survive archive restore.
 val hb=Context(File(base,"redpacket"),"com.redpacket")
 val hbOut=Context(File(base,"redpacket-restored"),"com.redpacket.restored")
 val hbPrefs=Store.preferences(hb,"Hchat_redpacket_config")
 check(!hbPrefs.getBoolean("hb_skip_private",false))
 for (enabled in listOf(true,false)) {
  hbPrefs.edit().putBoolean("hb_skip_private",enabled).commit()
  Store.runtimePreferences(hb,"Hchat_redpacket_runtime_test").edit().putString("pending","packet").commit()
  val hbZip=File(base,"redpacket-$enabled.zip")
  check(HchatConfigBackup.exportToUri(hb,Uri(hbZip)).stores==1)
  HchatConfigBackup.importFromUri(hbOut,Uri(hbZip))
  val restoredHb=Store.preferences(hbOut,"Hchat_redpacket_config")
  check(restoredHb.contains("hb_skip_private") && restoredHb.getBoolean("hb_skip_private",!enabled)==enabled)
  check("Hchat_redpacket_runtime_test" !in Store.backupPreferenceNames(hbOut))
 }
 println("PASS: private-chat skip default off, true/false archive round trip, runtime excluded")
 // Card settings are normal user configuration; drawing state is never persisted.
 val card=Context(File(base,"moments-card"),"com.card")
 val cardOut=Context(File(base,"moments-card-restored"),"com.card.restored")
 val cardPrefs=Store.preferences(card,"Hchat_moments_card_config")
 check(!cardPrefs.getBoolean("enable",false) && !cardPrefs.getBoolean("border_enable",false))
 for (enabled in listOf(true,false)) {
  cardPrefs.edit().putBoolean("enable",enabled).putBoolean("border_enable",enabled)
   .putString("border_colors","#123456,#789ABC,#DEF012").commit()
  Store.runtimePreferences(card,"Hchat_moments_card_test_runtime").edit().putInt("view",12).commit()
  val cardZip=File(base,"card-$enabled.zip")
  check(HchatConfigBackup.exportToUri(card,Uri(cardZip)).stores==1)
  HchatConfigBackup.importFromUri(cardOut,Uri(cardZip))
  val restoredCard=Store.preferences(cardOut,"Hchat_moments_card_config")
  check(restoredCard.contains("enable") && restoredCard.getBoolean("enable",!enabled)==enabled)
  check(restoredCard.contains("border_enable") && restoredCard.getBoolean("border_enable",!enabled)==enabled)
  check(restoredCard.getString("border_colors","")=="#123456,#789ABC,#DEF012")
  check("Hchat_moments_card_test_runtime" !in Store.backupPreferenceNames(cardOut))
 }
 println("PASS: moments card two default-off toggles and three colors true/false round trip; runtime excluded")
 // Shortcut label background is a user preference, independent of text/icon colors.
 val label=Context(File(base,"shortcut-label"),"com.shortcut.label")
 val labelOut=Context(File(base,"shortcut-label-out"),"com.shortcut.label.out")
 val labelPrefs=Store.preferences(label,"floating_shortcut_menu")
 check(!Store.validBackupName("unmanaged_other_store"))
 val labelIcon=File(Store.storageDir(label),"FloatingShortcut/icons/label-test.png")
 labelIcon.parentFile.mkdirs();labelIcon.writeBytes(byteArrayOf(7,8,9))
 labelPrefs.edit().putString("bubble_icon",labelIcon.absolutePath).commit()
 for (color in listOf("", "#112233", "#112233,#445566,#778899", "#80112233,#90445566,#A0778899")) {
  labelPrefs.edit().putString("label_background_color",color).putString("label_color","#123456").putString("action_color","#654321").commit()
  Store.runtimePreferences(label,"Hchat_shortcut_label_test_runtime").edit().putInt("view",1).commit()
  val labelZip=File(base,"shortcut-label.zip")
  val labelExport=HchatConfigBackup.exportToUri(label,Uri(labelZip))
  check(labelExport.stores==1 && labelExport.resources==1)
  HchatConfigBackup.importFromUri(labelOut,Uri(labelZip))
  val restoredLabel=Store.preferences(labelOut,"floating_shortcut_menu")
  check(restoredLabel.getString("label_background_color",null)==color)
  check(restoredLabel.getString("label_color",null)=="#123456")
  check(restoredLabel.getString("action_color",null)=="#654321")
  val restoredIcon=File(Store.storageDir(labelOut),"FloatingShortcut/icons/label-test.png")
  check(restoredIcon.readBytes().contentEquals(labelIcon.readBytes()))
  check(restoredLabel.getString("bubble_icon",null)==restoredIcon.absolutePath)
  check("Hchat_shortcut_label_test_runtime" !in Store.backupPreferenceNames(labelOut))
 }
 println("PASS: shortcut label background blank/single/three/alpha colors round trip; text/icon unchanged; runtime excluded")
 // Calendar uses a fixed resource path, so restore never needs the source URI.
 val cal=Context(File(base,"calendar"),"com.calendar")
 val calOut=Context(File(base,"calendar-restored"),"com.calendar.restored")
 Store.preferences(cal,"Hchat_home_extension_config").edit().putInt("calendar_alpha",61).putLong("calendar_background_revision",9L).commit()
 val calImage=File(Store.resourceDirectory(cal,"home_calendar"),"background.jpg")
 calImage.writeBytes(byteArrayOf(1,2,3,4,5))
 File(calImage.parentFile,"background.import").writeText("partial")
 File(calImage.parentFile,"background.tmp").writeText("partial")
 Store.preferences(cal,"Hchat_home_photo_transform").edit().putFloat("home_calendar_background_9_z",2f).putFloat("home_calendar_background_9_x",.3f).putFloat("home_calendar_background_9_y",.8f).commit()
 val calZip=File(base,"calendar.zip")
 check(HchatConfigBackup.exportToUri(cal,Uri(calZip)).resources==1)
 check(HchatConfigBackup.importFromUri(calOut,Uri(calZip)).resources==1)
 check(File(Store.resourceDirectory(calOut,"home_calendar"),"background.jpg").readBytes().contentEquals(calImage.readBytes()))
 check(Store.preferences(calOut,"Hchat_home_extension_config").getInt("calendar_alpha",0)==61)
 check(!File(Store.resourceDirectory(calOut,"home_calendar"),"background.import").exists())
 val calTransform=Store.preferences(calOut,"Hchat_home_photo_transform")
 check(calTransform.getFloat("home_calendar_background_9_z",0f)==2f && calTransform.getFloat("home_calendar_background_9_x",0f)==.3f && calTransform.getFloat("home_calendar_background_9_y",0f)==.8f)
 println("PASS: calendar resource, opacity, zoom and offsets round trip; temporary files excluded")
 // Music style2 background and transform survive changed file timestamps/root paths.
 val music=Context(File(base,"music"),"com.music")
 val musicOut=Context(File(base,"music-out"),"com.music.out")
 val musicImage=File(Store.resourceDirectory(music,"home_music"),"style2_background.jpg");musicImage.writeBytes(byteArrayOf(4,3,2,1))
 File(musicImage.parentFile,"style2_background.tmp").writeText("temporary")
 Store.preferences(music,"Hchat_home_extension_config").edit().putLong("music_style2_background_revision",17L).putInt("music_alpha",63).commit()
 Store.preferences(music,"Hchat_home_photo_transform").edit().putFloat("music_style2_17_z",2f).putFloat("music_style2_17_x",.2f).putFloat("music_style2_17_y",.8f).commit()
 val musicZip=File(base,"music.zip");HchatConfigBackup.exportToUri(music,Uri(musicZip));HchatConfigBackup.importFromUri(musicOut,Uri(musicZip))
 check(File(Store.resourceDirectory(musicOut,"home_music"),"style2_background.jpg").readBytes().contentEquals(musicImage.readBytes()))
 check(!File(Store.resourceDirectory(musicOut,"home_music"),"style2_background.tmp").exists())
 check(Store.preferences(musicOut,"Hchat_home_extension_config").getLong("music_style2_background_revision",0L)==17L)
 check(Store.preferences(musicOut,"Hchat_home_extension_config").getInt("music_alpha",0)==63)
 check(Store.preferences(musicOut,"Hchat_home_photo_transform").getFloat("music_style2_17_x",0f)==.2f)
 println("PASS: music background, opacity, persistent revision and transform round trip; temporary excluded")
 // Me switches use the automatically discovered background preference store.
 val me=Context(File(base,"me"),"com.me")
 val meOut=Context(File(base,"me-restored"),"com.me.restored")
 val mePrefs=Store.preferences(me,"Hchat_background_beauty_config")
 val meKeys=listOf("me_hide_favorite","me_hide_moments","me_hide_works","me_hide_cards","me_hide_emoji")
 meKeys.forEachIndexed { i,k -> mePrefs.edit().putBoolean(k,i%2==0).commit() }
 Store.runtimePreferences(me,"Hchat_background_beauty_method_cache").edit().putString("locator","runtime").commit()
 val meZip=File(base,"me.zip")
 check(HchatConfigBackup.exportToUri(me,Uri(meZip)).stores==1)
 HchatConfigBackup.importFromUri(meOut,Uri(meZip))
 val meRestored=Store.preferences(meOut,"Hchat_background_beauty_config")
 meKeys.forEachIndexed { i,k -> check(meRestored.contains(k) && meRestored.getBoolean(k,false)==(i%2==0)) }
 check("Hchat_background_beauty_method_cache" !in Store.backupPreferenceNames(meOut))
 println("PASS: five Me switches true/false round trip, runtime cache excluded")
  // New switches are independent, default off, and share the existing user store.
  for (mask in 0..3) {
   val extra=Context(File(base,"extra-$mask"),"com.extra.$mask")
   val extraOut=Context(File(base,"extra-restored-$mask"),"com.extra.restored.$mask")
   val extraPrefs=Store.preferences(extra,"Hchat_background_beauty_config")
   val extraKeys=listOf("hide_home_computer_login","hide_input_voice_icon")
   extraKeys.forEach { check(!extraPrefs.getBoolean(it,false)) }
   extraKeys.forEachIndexed { i,k -> extraPrefs.edit().putBoolean(k,(mask and (1 shl i))!=0).commit() }
   Store.runtimePreferences(extra,"Hchat_background_beauty_method_cache").edit().putString("locator","not-user-config").commit()
   val extraZip=File(base,"extra-$mask.zip")
   check(HchatConfigBackup.exportToUri(extra,Uri(extraZip)).stores==1)
   HchatConfigBackup.importFromUri(extraOut,Uri(extraZip))
   val extraRestored=Store.preferences(extraOut,"Hchat_background_beauty_config")
   extraKeys.forEachIndexed { i,k -> check(extraRestored.contains(k) && extraRestored.getBoolean(k,false)==((mask and (1 shl i))!=0)) }
   check("Hchat_background_beauty_method_cache" !in Store.backupPreferenceNames(extraOut))
  }
  println("PASS extra visibility: default off, four independent combinations archive round trip; runtime excluded")
 val voice=Context(File(base,"voice-source"),"com.voice.source");val voicePrefs=Store.preferences(voice,"Hchat_voice_to_text_config")
 check(!voicePrefs.getBoolean("enable",false))
 voicePrefs.edit().putBoolean("enable",true).commit()
 Store.runtimePreferences(voice,"Hchat_voice_to_text_method_cache").edit().putString("runtime","exclude").commit()
 val voiceZip=File(base,"voice.zip");HchatConfigBackup.exportToUri(voice,Uri(voiceZip))
 val voiceOut=Context(File(base,"voice-dest"),"com.voice.dest");HchatConfigBackup.importFromUri(voiceOut,Uri(voiceZip))
 check(Store.preferences(voiceOut,"Hchat_voice_to_text_config").getBoolean("enable",false))
 check("Hchat_voice_to_text_method_cache" !in Store.backupPreferenceNames(voiceOut))
 voicePrefs.edit().putBoolean("enable",false).commit();HchatConfigBackup.exportToUri(voice,Uri(voiceZip));HchatConfigBackup.importFromUri(voiceOut,Uri(voiceZip))
 check(!Store.preferences(voiceOut,"Hchat_voice_to_text_config").getBoolean("enable",true))
 println("PASS: voice-to-text toggle true/false round trip and runtime exclusion")
 val nick=Context(File(base,"nick-source"),"com.nick.source")
 val nickPrefs=Store.preferences(nick,"Hchat_group_nickname_width_config")
 check(!nickPrefs.getBoolean("enable",false))
 val nickOut=Context(File(base,"nick-dest"),"com.nick.dest");val nickZip=File(base,"nick.zip")
 Store.runtimePreferences(nick,"Hchat_group_nickname_width_method_cache").edit().putString("runtime","exclude").commit()
 for(value in listOf(true,false)) {
 nickPrefs.edit().putBoolean("enable",value).commit()
 HchatConfigBackup.exportToUri(nick,Uri(nickZip));HchatConfigBackup.importFromUri(nickOut,Uri(nickZip))
 check(Store.preferences(nickOut,"Hchat_group_nickname_width_config").getBoolean("enable",!value)==value)
 check("Hchat_group_nickname_width_method_cache" !in Store.backupPreferenceNames(nickOut))
 }
 println("PASS nickname width default-off and true/false round trip, runtime exclusion")

 // Auto merge is a new ordinary store: actual settings constants, on/off ZIP round trip.
 val merge=Context(File(base,"merge"),"com.merge.source")
 val mergeOut=Context(File(base,"merge-out"),"com.merge.dest")
 val mergePrefs=t.mma.hooks.items.automerge.AutoMergeSettings.preferences(merge)
 val mergeDest=t.mma.hooks.items.automerge.AutoMergeSettings.preferences(mergeOut)
 val mergeKey=t.mma.hooks.items.automerge.AutoMergeSettings.KEY_ENABLE
 check(!mergePrefs.getBoolean(mergeKey,t.mma.hooks.items.automerge.AutoMergeSettings.DEFAULT_ENABLE))
 Store.runtimePreferences(merge,"Hchat_auto_merge_method_cache").edit().putString("descriptor","exclude").commit()
 val mergeZip=File(base,"merge.zip")
 for (enabled in listOf(true,false)) {
  mergePrefs.edit().putBoolean(mergeKey,enabled).commit()
  check(HchatConfigBackup.exportToUri(merge,Uri(mergeZip)).stores==1)
  ZipFile(mergeZip).use { z ->
   val manifest=JSONObject(z.getInputStream(z.getEntry("config.json")).bufferedReader().readText())
   check(!manifest.getJSONObject("prefs").has("Hchat_auto_merge_method_cache"))
  }
  HchatConfigBackup.importFromUri(mergeOut,Uri(mergeZip))
  check(mergeDest.getBoolean(mergeKey,!enabled)==enabled)
 }
 check(!Store.backupPreferenceNames(mergeOut).contains("Hchat_auto_merge_method_cache"))
 println("PASS auto merge actual settings: default-off, true/false export-import ZIP round trip and runtime cache exclusion")
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
 // Group bar user configuration is automatically discovered; projection caches stay in memory.
 val bar=Context(File(base,"bar"),"com.bar")
 val barOut=Context(File(base,"bar-out"),"com.bar.out")
 val barPrefs=Store.preferences(bar,"Hchat_conversation_group_bar_config")
 check(!barPrefs.getBoolean("enable",false))
 val groupJson="""[{"id":"custom_1","name":"家人","enabled":true,"members":["friend","room@chatroom"]},{"id":"all","name":"全部","enabled":false}]"""
 barPrefs.edit().putBoolean("enable",true).putInt("style",1).putInt("transparency",37).putString("groups",groupJson).putInt("next_number",8).commit()
 Store.runtimePreferences(bar,"Hchat_group_bar_method_cache").edit().putString("method","runtime").commit()
 val barZip=File(base,"bar.zip");check(HchatConfigBackup.exportToUri(bar,Uri(barZip)).stores==2)
 HchatConfigBackup.importFromUri(barOut,Uri(barZip))
 val restoredBar=Store.preferences(barOut,"Hchat_conversation_group_bar_config")
 check(restoredBar.getBoolean("enable",false)&&restoredBar.getInt("style",0)==1)
 check(restoredBar.getInt("transparency",0)==37&&restoredBar.getString("groups","")==groupJson&&restoredBar.getInt("next_number",0)==8)
 barPrefs.edit().putBoolean("enable",false).commit()
 HchatConfigBackup.exportToUri(bar,Uri(barZip));HchatConfigBackup.importFromUri(barOut,Uri(barZip))
 check(!restoredBar.getBoolean("enable",true))
 check("Hchat_group_bar_method_cache" !in Store.backupPreferenceNames(bar))
 println("PASS: group bar default/off/on, style, transparency, ordered group/member JSON, next number round trip; runtime cache excluded")
 // Direct settings writes, quick toggles and import use these same production preference listeners.
 val grouped=Store.preferences(bar,"Hchat_conversation_groups")
 grouped.edit().putString("groups_v1","preserved").putBoolean("enabled",true).commit()
 check(!barPrefs.getBoolean("enable",false))
 barPrefs.edit().putBoolean("enable",true).commit()
 check(!grouped.getBoolean("enabled",true)&&grouped.getString("groups_v1","")=="preserved")
 grouped.edit().putBoolean("enabled",true).commit()
 check(!barPrefs.getBoolean("enable",true)&&barPrefs.getString("groups","")==groupJson)
 // Importing the enabled-bar backup must disable an already enabled chat-group feature.
 HchatConfigBackup.importFromUri(bar,Uri(barZip))
 check(!barPrefs.getBoolean("enable",true)) // latest backup explicitly saved bar off
 barPrefs.edit().putBoolean("enable",true).commit()
 HchatConfigBackup.exportToUri(bar,Uri(barZip))
 grouped.edit().putBoolean("enabled",true).commit()
 HchatConfigBackup.importFromUri(bar,Uri(barZip))
 check(!(barPrefs.getBoolean("enable",false)&&grouped.getBoolean("enabled",false)))
 println("PASS bidirectional grouping exclusion for direct/quick preference writes; both group configurations preserved")
 // Removed input settings cannot round-trip; independent header settings remain.
 val retired=Context(File(base,"retired"),"com.retired")
 Store.preferences(retired,"Hchat_global_config").edit().putBoolean("floating_chat_header_enabled",true).putInt("floating_chat_header_transparency",23).putBoolean("floating_chat_footer_enabled",true).putInt("floating_chat_footer_transparency",80).commit()
 Store.preferences(retired,"Hchat_feature_floating_chat_footer").edit().putBoolean("enabled",true).commit()
 val retiredZip=File(base,"retired.zip");HchatConfigBackup.exportToUri(retired,Uri(retiredZip))
 java.util.zip.ZipFile(retiredZip).use { archive ->
  val manifest=JSONObject(archive.getInputStream(archive.getEntry("config.json")).bufferedReader().readText())
  val exported=manifest.getJSONObject("prefs")
  check(!exported.has("Hchat_feature_floating_chat_footer"))
  val global=exported.getJSONObject("Hchat_global_config")
  check(!global.has("floating_chat_footer_enabled")&&!global.has("floating_chat_footer_transparency"))
  check(global.has("floating_chat_header_enabled")&&global.has("floating_chat_header_transparency"))
 }
 val oldGlobal=JSONObject().put("floating_chat_footer_enabled",JSONObject().put("type","boolean").put("value",true)).put("floating_chat_footer_transparency",JSONObject().put("type","int").put("value",80)).put("floating_chat_header_enabled",JSONObject().put("type","boolean").put("value",true))
 val oldPrefs=JSONObject().put("Hchat_global_config",oldGlobal).put("Hchat_feature_floating_chat_footer",JSONObject().put("enabled",JSONObject().put("type","boolean").put("value",true)))
 val oldFile=File(base,"retired-old.json");oldFile.writeText(JSONObject().put("format","HchatConfigBackup").put("version",1).put("prefs",oldPrefs).toString())
 val retiredOut=Context(File(base,"retired-out"),"com.retired.out")
 HchatConfigBackup.importFromUri(retiredOut,Uri(oldFile))
 val importedGlobal=Store.preferences(retiredOut,"Hchat_global_config")
 check(importedGlobal.getBoolean("floating_chat_header_enabled",false))
 check(!importedGlobal.contains("floating_chat_footer_enabled")&&!importedGlobal.contains("floating_chat_footer_transparency"))
 check(Store.preferences(retiredOut,"Hchat_feature_floating_chat_footer").all.isEmpty())
 println("PASS removed input settings excluded from export/legacy import; header settings preserved")
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
 production=[repo/'app/src/main/java/t/mma/hooks/items/automerge/AutoMergeSettings.kt',repo/'app/src/main/java/t/mma/preferences/HchatStorage.kt',repo/'app/src/main/java/t/mma/preferences/ConversationGroupingExclusion.kt',repo/'app/src/main/java/t/mma/ui/miuix/HchatConfigBackup.kt']
 subprocess.run(['java','-Xmx384m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',f'{stdlib}:{jsonjar}','-d',str(jar),*[str(p/n) for n in sources],*map(str,production)],check=True,timeout=60)
 subprocess.run(['java','-cp',f'{jar}:{stdlib}:{jsonjar}','t.mma.ui.miuix.MainKt',str(p/'data')],check=True,timeout=30)
