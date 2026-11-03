"""Exercise production card style/restore/draw with synthetic host APIs; no Gradle/device.
Hook installation is covered separately by SDK compilation and decoded-host evidence.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'app/src/main/java/t/mma'
LIBS = Path('/tmp/pl-kotlin-libs')
STD = next(LIBS.glob('kotlin-stdlib-[0-9]*.jar'))
source = (BASE / 'hooks/items/moments/MomentsCardFeature.kt').read_text()
# Execute the production style, restoration and drawable bodies unchanged.
start = source.index('internal class MomentsCardRuntime(')
body = source[start:]
a = body.index('    @Synchronized fun install()')
b = body.index('    private fun style(', a)
body = body[:a] + body[b:]
imports = '''package t.mma.hooks.items.moments
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.*
import android.os.*
import android.view.*
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import t.mma.hooks.core.*
'''
SOURCES = {
    'Runtime.kt': imports + body,
    'Resources.kt': '''package android.content.res
class Configuration { var uiMode=0; companion object { const val UI_MODE_NIGHT_MASK=48;const val UI_MODE_NIGHT_YES=32 } }
class Metrics { var density=2f }
class Resources { val configuration=Configuration();val displayMetrics=Metrics() }
''',
    'Graphics.kt': '''package android.graphics
object Color { const val WHITE=-1;const val TRANSPARENT=0 }
object PixelFormat { const val TRANSLUCENT=-3 }
class Rect(val left:Int,val top:Int,val right:Int,val bottom:Int)
class RectF {var left=0f;var top=0f;var right=0f;var bottom=0f
 fun set(l:Float,t:Float,r:Float,b:Float){left=l;top=t;right=r;bottom=b}
 fun width()=right-left; fun height()=bottom-top; val isEmpty get()=right<=left || bottom<=top
}
open class Shader { enum class TileMode {CLAMP} }
class LinearGradient(l:Float,t:Float,r:Float,b:Float,c:IntArray,p:FloatArray?,m:Shader.TileMode):Shader(){init{allocations++};companion object{var allocations=0}}
class ColorFilter
class Paint(f:Int) { var color=0
 set(v){field=v;alpha=v ushr 24}
 var style=Style.FILL;var strokeWidth=0f;var alpha=255;var colorFilter:ColorFilter?=null;var shader:Shader?=null
 enum class Style{FILL,STROKE};companion object{const val ANTI_ALIAS_FLAG=1}
}
class Canvas {var draws=0;var lastAlpha=255;var lastRadius=0f;var last:List<Float> = emptyList();val ops=mutableListOf<Pair<String,Int>>()
 fun drawRect(r:RectF,p:Paint){draws++;ops+="rect" to p.color}
 fun drawRoundRect(r:RectF,x:Float,y:Float,p:Paint){draws++;lastAlpha=p.alpha;lastRadius=x;ops+="round" to p.color;last=listOf(r.left,r.top,r.right,r.bottom)}}
''',
    'Drawable.kt': '''package android.graphics.drawable
import android.graphics.*
abstract class Drawable {var callback:Any?=null
 open fun onBoundsChange(b:Rect){};abstract fun draw(c:Canvas)
 open fun setAlpha(a:Int){};open fun setColorFilter(f:ColorFilter?){};open fun getOpacity()=0
 fun invalidateSelf(){};fun bounds(l:Int,t:Int,r:Int,b:Int){onBoundsChange(Rect(l,t,r,b))}
}
class LayerDrawable(val children:Array<Drawable>):Drawable(){override fun draw(c:Canvas){children.forEach{it.draw(c)}}}
class HostDrawable:Drawable(){override fun draw(c:Canvas){}}
''',
    'View.kt': '''package android.view
import android.content.res.Resources
import android.graphics.drawable.Drawable
open class View {val resources=Resources();var foreground:Drawable?=null
 var background:Drawable?=null
 set(v){field=v;v?.callback=this}
 var visibility=VISIBLE;var paddingLeft=0;var paddingTop=0;var paddingRight=0;var paddingBottom=0
 fun setPadding(l:Int,t:Int,r:Int,b:Int){paddingLeft=l;paddingTop=t;paddingRight=r;paddingBottom=b}
 companion object {const val GONE=8;const val VISIBLE=0}
}
open class ViewGroup:View()
''',
    'Os.kt': '''package android.os
class Looper {companion object {private val main=Looper();var current:Looper?=main;fun getMainLooper()=main;fun myLooper()=current}}
class Handler(l:Looper) {fun post(r:Runnable):Boolean {queue+=r;return true};companion object{val queue=mutableListOf<Runnable>();fun drain(){val q=queue.toList();queue.clear();q.forEach{it.run()}}}}
''',
    'Core.kt': '''package t.mma.hooks.core
class FeatureContext
class OwnedHooks(r:(Throwable)->Unit):AutoCloseable{var closed=false;override fun close(){closed=true}}
''',
    'Main.kt': '''package t.mma.hooks.items.moments
import android.view.*
import android.graphics.*
import android.graphics.drawable.*
import android.os.*
import java.lang.ref.WeakReference
import t.mma.hooks.core.FeatureContext
private fun runtime(border:Boolean=true)=MomentsCardRuntime(FeatureContext(),border,intArrayOf(1,2,3)){throw it}
private val style=MomentsCardRuntime::class.java.getDeclaredMethod("style",View::class.java,View::class.java).apply{isAccessible=true}
private fun apply(r:MomentsCardRuntime,v:View,d:View?=null){style.invoke(r,v,d)}
private fun padding(v:View)=listOf(v.paddingLeft,v.paddingTop,v.paddingRight,v.paddingBottom)
private fun abandoned(r:MomentsCardRuntime):WeakReference<View>{val v=View();v.background=HostDrawable();apply(r,v);return WeakReference(v)}
fun main(){
 val r=runtime();val v=View();val bg=HostDrawable();val fg=HostDrawable();val divider=View()
 v.background=bg;v.foreground=fg;v.setPadding(1,2,3,4)
 apply(r,v,divider);val card=v.background
 check(card is MomentsCardDrawable && v.foreground is LayerDrawable)
 check(padding(v)==listOf(13,2,15,16) && divider.visibility==View.GONE)
 repeat(1000){apply(r,v,divider)}
 check(v.background===card && padding(v)==listOf(13,2,15,16))
 r.close();r.close();check(v.background===bg && v.foreground===fg)
 check(padding(v)==listOf(1,2,3,4) && divider.visibility==View.VISIBLE)
 apply(r,v);check(v.background===bg)
 val next=runtime(false);apply(next,v);check(v.foreground===fg);next.close();check(v.background===bg && padding(v)==listOf(1,2,3,4))
 val offMain=runtime();apply(offMain,v);Looper.current=Looper();offMain.close();check(Handler.queue.size==1)
 Looper.current=Looper.getMainLooper();Handler.drain();check(v.background===bg && padding(v)==listOf(1,2,3,4))
 val oldGeneration=runtime();apply(oldGeneration,v)
 Looper.current=Looper();oldGeneration.close();Looper.current=Looper.getMainLooper()
 val newGeneration=runtime();apply(newGeneration,v);val currentCard=v.background
 Handler.drain();check(v.background===currentCard);newGeneration.close()
 val guarded=runtime();apply(guarded,v);val replacement=HostDrawable();v.background=replacement;guarded.close();check(v.background===replacement)
 val bounded=runtime(false);val many=List(193){View()};many.forEach{apply(bounded,it)}
 check(many.take(192).all{it.background is MomentsCardDrawable} && many.last().background==null);bounded.close();check(many.all{it.background==null})
 val gc=runtime();val weak=abandoned(gc)
 repeat(60){if(weak.get()!=null){System.gc();Thread.sleep(10)}}
 check(weak.get()==null){"Card index retained host Drawable.Callback/View cycle"};gc.close()
 // No border is required to distinguish the card from its gutters/gap.
 val surface=Canvas();val outlined=MomentsCardDrawable(2f,12,12,-1,null,surround=0xFFF2F2F2.toInt())
 outlined.bounds(0,0,200,100);outlined.draw(surface)
 check(surface.ops==listOf("rect" to 0xFFF2F2F2.toInt(),"round" to -1))
 val dark=Canvas();val darkCard=MomentsCardDrawable(2f,12,12,0xFF242424.toInt(),null,surround=0xFF191919.toInt())
 darkCard.bounds(0,0,200,100);darkCard.draw(dark)
 check(dark.ops==listOf("rect" to 0xFF191919.toInt(),"round" to 0xFF242424.toInt()))
 val canvas=Canvas();val fill=MomentsCardDrawable(2f,12,12,-1,null)
 fill.bounds(0,0,200,100);fill.draw(canvas);check(canvas.last==listOf(12f,0f,188f,88f))
 val stroke=MomentsCardDrawable(2f,12,12,0,intArrayOf(1,2,3));val before=LinearGradient.allocations
 stroke.bounds(0,0,200,100);check(LinearGradient.allocations==before+1)
 repeat(1000){stroke.draw(canvas)};check(LinearGradient.allocations==before+1)
 check(canvas.last==listOf(14f,2f,186f,86f))
 check(canvas.lastAlpha==255 && canvas.lastRadius==42f)
 stroke.setAlpha(128);stroke.draw(canvas);check(canvas.lastAlpha==128)
 stroke.setAlpha(255)
 stroke.bounds(0,0,220,100);check(LinearGradient.allocations==before+2)
 println("PASS production style/restore/draw bodies: 6dp gap, 2dp inset stroke, repeated bind, off/on, stale guard, main-thread cleanup, host replacement, 192-view cap, collectible callback cycle, no gradient allocations during draw")
}
''',
}
# Integration checks complement execution; they do not establish live WeChat compatibility.
registry = (BASE / 'hooks/core/FeatureRegistry.kt').read_text()
groups = (BASE / 'ui/miuix/MiuixSettingsPagePart02.kt').read_text()
route = (BASE / 'ui/miuix/MiuixSettingsPagePart03.kt').read_text()
toggles = (BASE / 'ui/FeatureToggleRegistry.kt').read_text()
assert 'MomentsCardFeature()' in registry
assert 't.mma.hooks.items.moments.MomentsCardFeature.ID' in groups
assert 'MomentsCardMiuixPage' in route
assert '"moments_card_mode" to Toggle("Hchat_moments_card_config", "enable")' in toggles
assert 'FeatureSettingsProvider.CATEGORY_PRACTICAL, true' in source
assert 'PreferenceRuntimeOwner(' in source and 'trackResource { owner.close() }' in source
assert 'restartOnChange = true' in source and 'private val hooks = OwnedHooks(report)' in source
assert 'OnScrollListener' not in source and 'ValueAnimator' not in source
with tempfile.TemporaryDirectory(prefix='pl-moments-card-behavior-') as td:
    path = Path(td)
    for name, value in SOURCES.items():
        (path / name).write_text(value)
    jar = path / 'test.jar'
    subprocess.run(['java', '-Xmx256m', '-cp', ':'.join(map(str, LIBS.glob('*.jar'))),
                    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-nowarn', '-no-stdlib', '-no-reflect',
                    '-classpath', str(STD), '-d', str(jar), *map(str, path.glob('*.kt')),
                    str(BASE / 'hooks/items/moments/MomentsCardSettings.kt')], check=True, timeout=60)
    subprocess.run(['java', '-cp', f'{jar}:{STD}', 't.mma.hooks.items.moments.MainKt'], check=True, timeout=20)
print('PASS static integration: both categories, one route/config, new-feature flag, owned preference runtime/hooks; no scroll listener/animation')
