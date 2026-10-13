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
object R { object attr { const val state_pressed=1; const val state_focused=2; const val state_selected=3 } }
''',
    'Test.kt': '''import android.graphics.*
import android.graphics.drawable.Drawable
import t.mma.hooks.items.homesidepanel.SidePanelEdgeGesture
class PaintDrawable:Drawable() {
 var draws=0
 override fun draw(c:Canvas){draws++}
 override fun setAlpha(a:Int){}
 override fun setColorFilter(c:ColorFilter?){}
 override fun getOpacity()=0
}
fun main(){
 val g=SidePanelEdgeGesture(24f,8f)
 g.down(10f,200f,true);check(!g.move(12f,202f));g.reset()
 g.down(10f,200f,true);check(!g.move(12f,240f));check(!g.move(90f,241f));g.reset()
 g.down(60f,200f,true);check(!g.move(120f,200f));g.reset()
 g.down(10f,200f,false);check(!g.move(80f,200f));g.reset()
 g.down(10f,200f,true);check(g.move(40f,202f));check(g.move(20f,203f));g.reset();check(!g.move(100f,203f))
 g.down(10f,200f,true);check(!g.move(-5f,200f));check(!g.move(100f,200f));g.reset()
 repeat(1000){g.down(5f,5f,true);check(!g.move(6f,6f));g.reset()}
 val native=PaintDrawable();val wrapper=BackgroundBeautyFeedbackDrawable(native);val canvas=Canvas()
 wrapper.draw(canvas);check(native.draws==0)
 wrapper.state=intArrayOf(android.R.attr.state_pressed);wrapper.draw(canvas);check(native.draws==1)
 wrapper.state=intArrayOf();wrapper.draw(canvas);check(native.draws==1)
 check(native.state.isEmpty());check(wrapper.isStateful())
 println("PASS: tap/jitter/vertical/outside/disabled/leftward pass-through, horizontal takeover and reset; normal background hidden, pressed feedback retained and released")
}
'''
}
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
 public void invalidateSelf(){}public void scheduleSelf(Runnable r,long t){}public void unscheduleSelf(Runnable r){}
}
''')
    for name in ['Canvas','ColorFilter','Rect']:
        p=work/('android/graphics/'+name+'.java');p.parent.mkdir(parents=True,exist_ok=True);p.write_text('package android.graphics; public class '+name+' {}')
    # Production calls setters via Kotlin Java synthetic properties.
    j=java.read_text().replace('public abstract int getOpacity();','public abstract int getOpacity(); public int getAlpha(){return 255;}public android.graphics.ColorFilter getColorFilter(){return null;}')
    java.write_text(j)
    subprocess.run(['javac','-d',str(work/'classes'),*map(str,work.rglob('*.java'))],check=True)
    files=[]
    for name in ['R.kt','Test.kt']:
        p=work/name;p.write_text(stubs[name] + (chr(10) + text[start:end] if name == 'Test.kt' else ''));files.append(str(p))
    (work/'PixelFormat.kt').write_text('package android.graphics\nobject PixelFormat { const val TRANSLUCENT=-3 }')

    prod=repo/'app/src/main/java/t/mma/hooks/items/homesidepanel/SidePanelEdgeGesture.kt'
    compiler=['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(stdlib)+':'+str(work/'classes'),'-d',str(work/'tests.jar')]
    subprocess.run(compiler+files+[str(work/'PixelFormat.kt'),str(prod)],check=True)
    subprocess.run(['java','-cp',str(work/'tests.jar')+':'+str(work/'classes')+':'+str(stdlib),'TestKt'],check=True)
