#!/usr/bin/env python3
"""JVM regression for production edge gesture and background feedback drawable; no Gradle."""
from pathlib import Path
import os
import subprocess
import tempfile

repo = Path(__file__).resolve().parents[3]
lib = Path(os.environ.get('KOTLIN_LIB_DIR', '/tmp/pl-kotlin-libs'))
stdlib = next(lib.glob('kotlin-stdlib-*.jar'))
feature = repo / 'app/src/main/java/t/mma/hooks/items/backgroundbeauty/BackgroundBeautyFeature.kt'
text = feature.read_text()
start = text.index('private class BackgroundBeautyFeedbackDrawable')
end = text.index('private class ScreenAlignedBitmapDrawable', start)
stubs = {
    'R.kt': '''package android
object R { object attr { const val state_enabled=4; const val state_pressed=1; const val state_focused=2; const val state_selected=3 } }
''',
    'Test.kt': '''import android.graphics.*
import android.graphics.drawable.*
import t.mma.hooks.items.homesidepanel.SidePanelEdgeGesture
import java.lang.ref.WeakReference
import android.view.View
import kotlin.math.max
object BackgroundBeautySettings { fun opacity(v:Float)=v.coerceIn(.1f,1f) }
object HLog {fun i(s:String){} }

class PaintDrawable:Drawable() {
 var draws=0
 override fun draw(c:Canvas){draws++}
 override fun setAlpha(a:Int){}
 override fun setColorFilter(c:ColorFilter?){}
 override fun getOpacity()=0
}
fun main(){
 val renderCanvas=Canvas()
 val render=ScreenAlignedBitmapDrawable(Bitmap(),View(),.5f,0xffeeeeee.toInt())
 render.bounds=Rect()
 render.onBoundsForTest()
 render.draw(renderCanvas)
 check(renderCanvas.operations=="base,image,")
 check(renderCanvas.imageAlpha==127)
 check(render.opacity==PixelFormat.OPAQUE)
 renderCanvas.operations=""
 render.draw(renderCanvas)
 check(renderCanvas.operations=="base,image,")
 val chatRender=ScreenAlignedBitmapDrawable(Bitmap(),View(),.5f)
 chatRender.onBoundsForTest();renderCanvas.operations="";chatRender.draw(renderCanvas)
 check(renderCanvas.operations=="image," && chatRender.opacity==PixelFormat.TRANSLUCENT)
 val g=SidePanelEdgeGesture(24f,8f)
 g.down(10f,200f,true);check(!g.move(12f,202f));g.reset()
 g.down(10f,200f,true);check(!g.move(12f,240f));check(!g.move(90f,241f));g.reset()
 g.down(60f,200f,true);check(!g.move(120f,200f));g.reset()
 g.down(10f,200f,false);check(!g.move(80f,200f));g.reset()
 g.down(10f,200f,true);check(g.move(40f,202f));check(g.move(20f,203f));g.reset();check(!g.move(100f,203f))
 g.down(10f,200f,true);check(!g.move(-5f,200f));check(!g.move(100f,200f));g.reset()
 repeat(1000){g.down(5f,5f,true);check(!g.move(6f,6f));g.reset()}
 val wide=SidePanelEdgeGesture(270f,30f)
 wide.down(116.71875f,1536.3867f,true);check(!wide.move(116.71875f,1536.3867f));check(wide.move(960.46875f,1571.4258f));wide.reset()
 wide.down(116.71875f,1536.3867f,true);check(!wide.move(120f,1600f));check(!wide.move(960f,1601f));wide.reset()
 wide.down(271f,1536f,true);check(!wide.move(960f,1536f))
 val native=PaintDrawable();val wrapper=BackgroundBeautyFeedbackDrawable();val canvas=Canvas()
 wrapper.draw(canvas);check(native.draws==0)
 wrapper.state=intArrayOf(android.R.attr.state_pressed);wrapper.draw(canvas);check(native.draws==0)
 wrapper.state=intArrayOf(android.R.attr.state_focused,android.R.attr.state_selected);wrapper.draw(canvas);check(native.draws==0)
 wrapper.state=intArrayOf();wrapper.draw(canvas);check(native.draws==0)
 check(wrapper.isStateful())
 wrapper.setTouchPressed(true)
 val field=wrapper.javaClass.getDeclaredField("ripple");field.isAccessible=true
 val inner=field.get(wrapper) as Drawable
 check(inner.state.contains(android.R.attr.state_pressed) && inner.state.contains(android.R.attr.state_enabled))
 wrapper.state=intArrayOf(android.R.attr.state_enabled)
 check(inner.state.contains(android.R.attr.state_pressed))
 wrapper.setTouchPressed(false)
 check(!inner.state.contains(android.R.attr.state_pressed))
 val drawer=DrawerHarness()
 check(!drawer.interceptEdgeTouch(MotionEvent(MotionEvent.ACTION_DOWN,5f,50f)))
 check(drawer.interceptEdgeTouch(MotionEvent(MotionEvent.ACTION_MOVE,40f,50f)))
 check(drawer.cancelCount==1 && drawer.opened)
 check(drawer.interceptEdgeTouch(MotionEvent(MotionEvent.ACTION_MOVE,240f,50f)))
 check(drawer.interceptEdgeTouch(MotionEvent(MotionEvent.ACTION_UP,240f,50f)))
 check(drawer.opened)
 drawer.close(false)
 check(!drawer.interceptEdgeTouch(MotionEvent(MotionEvent.ACTION_DOWN,5f,50f)))
 check(!drawer.interceptEdgeTouch(MotionEvent(MotionEvent.ACTION_UP,5f,50f)))
 check(!drawer.opened)
 println("PASS: tap/jitter/vertical/outside/disabled/leftward pass-through, horizontal takeover and reset, production CANCEL reentry; original solid background never drawn; ripple-only content and state forwarding")
}
'''
}
drawer_source = (repo/'app/src/main/java/t/mma/hooks/items/homesidepanel/HomeSidePanelDrawer.kt').read_text()
method_start = drawer_source.index('    fun interceptEdgeTouch(')
method_end = drawer_source.index('    private fun onPanelTouch(', method_start)
harness = """
class MotionEvent(val actionMasked:Int,val x:Float,val y:Float) {
 var action=actionMasked
 fun recycle(){}
 companion object {const val ACTION_DOWN=0;const val ACTION_UP=1;const val ACTION_MOVE=2;const val ACTION_CANCEL=3;const val ACTION_POINTER_DOWN=5
 fun obtain(e:MotionEvent)=MotionEvent(e.actionMasked,e.x,e.y)}
}
class Surface {var translationX=0f;var alpha=0f}
internal class DrawerHarness {
 val panelWidth=300;val panel=Surface();val scrim=Surface()
 val edgeGesture=SidePanelEdgeGesture(24f,8f)
 var enabled=true;var opened=false;var edgeDragging=false;var edgeStartX=0f;var dispatchingHostCancel=false;var cancelCount=0
 val activity=Host()
 inner class Host {val window=Win()}
 inner class Win {val decorView=Decor()}
 inner class Decor {fun dispatchTouchEvent(e:MotionEvent){cancelCount++;interceptEdgeTouch(MotionEvent(e.action,e.x,e.y))}}
 fun show(animated:Boolean){opened=true}
 fun settleOpen(){opened=true}
 fun close(animated:Boolean){opened=false;edgeDragging=false;edgeGesture.reset()}
""" + drawer_source[method_start:method_end] + "}"
# Java Drawable stub avoids JVM setter collisions and matches Android's Java API.
with tempfile.TemporaryDirectory(prefix='background-regression-') as directory:
    work=Path(directory)
    java=work/'android/graphics/drawable/Drawable.java'
    java.parent.mkdir(parents=True)
    java.write_text('''package android.graphics.drawable;
public abstract class Drawable {
 public interface Callback {void invalidateDrawable(Drawable who);void scheduleDrawable(Drawable who,Runnable what,long when);void unscheduleDrawable(Drawable who,Runnable what);}
 public Callback callback; public android.graphics.Rect bounds=new android.graphics.Rect(); public int[] state=new int[0];
 public abstract void draw(android.graphics.Canvas c); public abstract void setAlpha(int a);public abstract void setColorFilter(android.graphics.ColorFilter c);public abstract int getOpacity();
 public boolean isStateful(){return false;} protected boolean onStateChange(int[] s){return false;}
 public Callback getCallback(){return callback;} public void setCallback(Callback c){callback=c;}
 public android.graphics.Rect getBounds(){return bounds;} public void setBounds(android.graphics.Rect r){bounds=r;}
 public int[] getState(){return state;} public boolean setState(int[] s){state=s;return onStateChange(s);}
 public void setHotspot(float x,float y){}public void setHotspotBounds(int l,int t,int r,int b){}public void jumpToCurrentState(){}protected void onBoundsChange(android.graphics.Rect r){}
 public void invalidateSelf(){}public void scheduleSelf(Runnable r,long t){}public void unscheduleSelf(Runnable r){}
}
''')
    for name in ['Canvas','ColorFilter','Rect']:
        p=work/('android/graphics/'+name+'.java');p.parent.mkdir(parents=True,exist_ok=True);p.write_text('package android.graphics; public class '+name+' {}')
    render_stubs = {
      'android/graphics/Canvas.java': 'package android.graphics; public class Canvas {public String operations="";public int imageAlpha; public void drawColor(int c){operations+="base,";}public void drawBitmap(Bitmap b,Rect src,RectF dst,Paint p){operations+="image,";imageAlpha=p.alpha;}public Rect getClipBounds(){return new Rect();}public boolean isHardwareAccelerated(){return true;}}',
      'android/graphics/Bitmap.java': 'package android.graphics;public class Bitmap {public int getWidth(){return 100;}public int getHeight(){return 200;}public boolean isRecycled(){return false;}}',
      'android/graphics/Rect.java': 'package android.graphics;public class Rect {public int width(){return 100;}public int height(){return 200;}}',
      'android/graphics/RectF.java': 'package android.graphics;public class RectF {boolean empty=true;public void set(float l,float t,float r,float b){empty=false;}public boolean isEmpty(){return empty;}}',
      'android/graphics/Paint.java': 'package android.graphics;public class Paint {public static final int ANTI_ALIAS_FLAG=1,FILTER_BITMAP_FLAG=2,DITHER_FLAG=4;public int alpha;public ColorFilter colorFilter;public Paint(int f){}public int getAlpha(){return alpha;}public void setAlpha(int a){alpha=a;}public ColorFilter getColorFilter(){return colorFilter;}public void setColorFilter(ColorFilter c){colorFilter=c;}}',
      'android/view/View.java': 'package android.view;public class View {public int getWidth(){return 100;}public int getHeight(){return 200;}public View getRootView(){return this;}public void getLocationOnScreen(int[] a){a[0]=0;a[1]=0;}}'
    }
    for path, content in render_stubs.items():
        target=work/path;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(content)
    # Production calls setters via Kotlin Java synthetic properties.
    j=java.read_text().replace('public abstract int getOpacity();','public abstract int getOpacity(); public int getAlpha(){return 255;}public android.graphics.ColorFilter getColorFilter(){return null;}')
    java.write_text(j)
    extra = {
      'android/content/res/ColorStateList.java': 'package android.content.res; public class ColorStateList {public static ColorStateList valueOf(int c){return new ColorStateList();}}',
      'android/graphics/Color.java': 'package android.graphics; public class Color {public static final int WHITE=-1;}',
      'android/graphics/drawable/ColorDrawable.java': 'package android.graphics.drawable; public class ColorDrawable extends Drawable {public ColorDrawable(int c){} public void draw(android.graphics.Canvas c){}public void setAlpha(int a){}public void setColorFilter(android.graphics.ColorFilter c){}public int getOpacity(){return 0;}}',
      'android/graphics/drawable/RippleDrawable.java': 'package android.graphics.drawable; public class RippleDrawable extends ColorDrawable {public RippleDrawable(android.content.res.ColorStateList c,Drawable content,Drawable mask){super(0);if(content!=null)throw new AssertionError("solid ripple content");}}'
    }
    for path, content in extra.items():
        target=work/path;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(content)
    subprocess.run(['javac','-d',str(work/'classes'),*map(str,work.rglob('*.java'))],check=True)
    files=[]
    for name in ['R.kt','Test.kt']:
        p=work/name;p.write_text(stubs[name] + (chr(10) + text[start:end].replace('ColorStateList.valueOf', 'android.content.res.ColorStateList.valueOf') + chr(10) + text[text.index('private class ScreenAlignedBitmapDrawable'):text.index('private class BackgroundBeautyRuntime')].replace('    override fun onBoundsChange(bounds: Rect) {', '    fun onBoundsForTest() { updateDestination() }\n    override fun onBoundsChange(bounds: Rect) {') + chr(10) + harness if name == 'Test.kt' else ''));files.append(str(p))
    (work/'PixelFormat.kt').write_text('package android.graphics\nobject PixelFormat { const val TRANSLUCENT=-3;const val OPAQUE=-1 }')

    prod=repo/'app/src/main/java/t/mma/hooks/items/homesidepanel/SidePanelEdgeGesture.kt'
    compiler=['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(stdlib)+':'+str(work/'classes'),'-d',str(work/'tests.jar')]
    subprocess.run(compiler+files+[str(work/'PixelFormat.kt'),str(prod)],check=True)
    subprocess.run(['java','-cp',str(work/'tests.jar')+':'+str(work/'classes')+':'+str(stdlib),'TestKt'],check=True)
