from pathlib import Path
import subprocess,tempfile
r=Path('/root/pl');libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'));sdk=Path('/usr/lib/android-sdk/platforms/android-36/android.jar')
s=(r/'app/src/main/java/t/mma/hooks/items/backgroundbeauty/BackgroundBeautyFeature.kt').read_text();a=s.index('        // Track actual native measurement,');b=s.index('        listOf("com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout"',a)
fixture='''package t.mma.hooks.items.backgroundbeauty
import android.app.Activity
import android.view.*
import t.mma.utils.*
class Param(val thisObject:Any,val args:Array<Any?>)
class Context {fun hostClassLoader()=javaClass.classLoader!!;fun hostContext():android.content.Context=TODO()}
internal class Host {val inputUnderlay=ChatInputMessageUnderlay()}
internal class Fixture {
val context=Context();val states=mutableMapOf<Activity,Host>();val destroyed=false;val TAG="test"
val prefs:android.content.SharedPreferences=TODO()
fun findWechatVerifiedMethod(c:Class<*>?,n:String,vararg types:Class<*>)=c?.getDeclaredMethod(n,*types)
fun hookOnce(m:java.lang.reflect.Method,before:((Param)->Unit)?=null,after:((Param)->Unit)?=null){}
fun findActivity(v:View):Activity?=null
fun chatChromeVersionName()="8.0.78"
fun floatingFooterEnabled()=false
fun hasBackgroundImage(s:BackgroundBeautySettings.Slot)=true
fun install(){
'''+s[a:b]+'''}}
object BackgroundBeautySettings {const val KEY_ENABLE="enabled";const val DEFAULT_ENABLE=false;enum class Slot {CHAT}}
object BackgroundBeautyStore {fun hasImage(c:android.content.Context,s:BackgroundBeautySettings.Slot)=true}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Fixture.kt').write_text(fixture)
 (p/'Reflect.kt').write_text('''package t.mma.utils
object KavaReflector {
fun loadClass(n:String,c:ClassLoader):Class<*>?=null
fun findMethodRecursive(c:Class<*>,n:String):java.lang.reflect.Method?=null
fun invokeOrThrow(m:java.lang.reflect.Method,o:Any):Any?=null
}
object HLog {fun e(s:String,e:Throwable){}}
''')
 src=r/'app/src/main/java/t/mma/hooks/items/hchatextra/HchatExtraFeature.kt';text=src.read_text();a=text.index('    private fun applyMessageDetailsColors(');b=text.index('    private fun rememberMessageDetailsLabel',a)
 (p/'Time.kt').write_text('''import android.widget.TextView
import android.graphics.drawable.GradientDrawable
class Time {
fun messageDetailsResolvedTextColor(c:android.content.Context)=0
fun messageDetailsResolvedBgColor(c:android.content.Context)=0
'''+text[a:b]+'}')
 cmd=['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',f'{std}:{sdk}','-d',str(p/'check.jar'),str(r/'app/src/main/java/t/mma/hooks/items/backgroundbeauty/ChatInputMessageUnderlay.kt'),*[str(f) for f in p.glob('*.kt')]]
 result=subprocess.run(cmd,capture_output=True,text=True,timeout=60)
 if result.returncode:print(result.stderr)
 result.check_returncode();print('PASS: full production controller, exact measurement/layout/touch hook integration and production time background method compile against Android SDK (dependencies stubbed)')