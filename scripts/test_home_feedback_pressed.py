"""Exercise production drawable pressed-state propagation and strict row hit boundaries."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/backgroundbeauty/BackgroundBeautyFeature.kt').read_text()
a=s.index('private class BackgroundBeautyFeedbackDrawable(');b=s.index('private class ScreenAlignedBitmapDrawable(',a)
drawable=s[a:b]
a=drawable.index('    private fun applyRippleState()');b=drawable.index('    override fun onBoundsChange(',a)
state=drawable[a:b].replace('override fun onStateChange','fun onStateChange')
eligibility_start=s.index('    private fun isConversationFeedbackRow(')
eligibility=s[eligibility_start:s.index('    private fun updateListRipple(',eligibility_start)]
code='''object android {object R {object attr {const val state_pressed=1;const val state_enabled=2}}}
class View(var height:Int=200,var visibility:Int=0,val task:Boolean=false,val extension:Boolean=false){
 var title:View?=null
 fun <T:View> findViewById(id:Int):T?=title as? T
 companion object{const val VISIBLE=0}
}
object t {object mma {object hooks {object items {object homeextension {object HomeExtensionSurface {fun contains(v:View)=v.extension}}}}}}
fun resourceId(n:String)=1
fun isWechatTaskSurface(v:View)=v.task
ELIGIBILITY
class Ripple {var state=intArrayOf()}
class Feedback(val manualOnly:Boolean){
 val ripple=Ripple();var hostState=intArrayOf();var touchPressed=false
 fun invalidateSelf(){}
 STATE
 fun press(v:Boolean){touchPressed=v;applyRippleState()}
}
fun main(){
 val normal=View().also{it.title=View()};check(isConversationFeedbackRow(normal))
 check(!isConversationFeedbackRow(View(2400)))
 check(!isConversationFeedbackRow(View(80)))
 check(!isConversationFeedbackRow(View(task=true).also{it.title=View()}))
 check(!isConversationFeedbackRow(View(extension=true).also{it.title=View()}))
 check(!isConversationFeedbackRow(View().also{it.title=View(visibility=8)}))
 val f=Feedback(true)
 repeat(1000){f.onStateChange(intArrayOf(2,1));check(1 !in f.ripple.state)}
 f.press(true);check(1 in f.ripple.state)
 f.onStateChange(intArrayOf(2));check(1 in f.ripple.state)
 f.press(false);check(1 !in f.ripple.state)
 repeat(1000){f.onStateChange(intArrayOf(2,1));check(1 !in f.ripple.state)}
 val native=Feedback(false);native.onStateChange(intArrayOf(2,1));check(1 in native.ripple.state)
 println("PASS production manual-only state ignores 2000 propagated presses, explicit row press/release works, other-slot native feedback retained")
}
'''.replace('STATE',state).replace('ELIGIBILITY',eligibility)
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
compiler=['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect']
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Main.kt').write_text(code)
 x=subprocess.run(compiler+['-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Main.kt')],capture_output=True,text=True,timeout=45)
 assert x.returncode==0,x.stdout+x.stderr
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','MainKt'],check=True,timeout=10)
 (p/'Drawable.kt').write_text('''import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.ColorDrawable
'''+drawable)
 x=subprocess.run(compiler+['-classpath',f'{std}:/usr/lib/android-sdk/platforms/android-36/android.jar','-d',str(p/'sdk.jar'),str(p/'Drawable.kt')],capture_output=True,text=True,timeout=45)
 assert x.returncode==0,x.stdout+x.stderr
print('PASS complete production feedback Drawable compiles against Android SDK')