"""Run the exact production geometry updater with deterministic graphics fixtures.
No Android runtime, Gradle or APK. SDK compilation is a separate check.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
LIB = Path('/tmp/pl-kotlin-libs')
STD = next(LIB.glob('kotlin-stdlib-[0-9]*.jar'))
source = (ROOT / 'app/src/main/java/t/mma/hooks/items/antirecall/DynamicRecallRuntime.kt').read_text()
a = source.index('        private fun updateGeometry()')
b = source.index('        fun stop()', a)
method = source[a:b]
draw = source[source.index('        override fun draw(canvas: Canvas)', b):]
assert draw.index('updateGeometry()') < draw.index('rect.width() <= 0')
fixture = '''
class Metrics { var density=2f }
class Resources { val displayMetrics=Metrics() }
class View { var width=240; var height=100; val resources=Resources() }
class RectF {
 var l=0f;var t=0f;var r=0f;var b=0f
 fun set(a:Float,c:Float,d:Float,e:Float){l=a;t=c;r=d;b=e}
 fun width()=r-l;fun height()=b-t;fun centerX()=(l+r)/2;fun centerY()=(t+b)/2
}
class Paint { var strokeWidth=4f;var strokeCap=Cap.ROUND;enum class Cap{ROUND} }
class Path {
 var builds=0;var valid=false
 enum class Direction{CW}
 fun reset(){valid=false}
 fun addRoundRect(r:RectF,x:Float,y:Float,d:Direction){check(x>=0 && y>=0);valid=true;builds++}
}
class PathMeasure {var length=0f;fun setPath(p:Path,closed:Boolean){length=if(p.valid) 100f else 0f}}
class SweepGradient(x:Float,y:Float,c:IntArray,p:FloatArray){companion object{var builds=0};init{builds++}}
object Color {fun parseColor(s:String)=s.length}
object DynamicRecallGradient {var parses=0;fun parts(s:String):List<String>{parses++;return listOf(s,s,s)}}
class Geometry {
 val view=View();val host=View();val paint=Paint();val rect=RectF();val outline=Path();val measure=PathMeasure()
 var gradient:SweepGradient?=null;var geometryWidth=-1;var geometryHeight=-1
 var geometryStroke=-1f;var geometryDensity=-1f;var geometryColors:String?=null
 var colorSpec="red";var perimeter=0f;var dashCount=1;var boundsWidth=0;var boundsHeight=0
 fun setBounds(l:Int,t:Int,r:Int,b:Int){boundsWidth=r;boundsHeight=b}
'''+method+'''
 fun update()=updateGeometry()
}
fun main(){
 val g=Geometry();g.update();check(g.outline.builds==1 && SweepGradient.builds==1)
 repeat(10000){g.update()}
 check(g.outline.builds==1 && SweepGradient.builds==1 && DynamicRecallGradient.parses==1)
 for((w,h) in listOf(480 to 300,180 to 64,720 to 900)){
  g.view.width=w;g.view.height=h;g.update()
  check(g.rect.width()==w-4f && g.rect.height()==h-4f)
 }
 val builds=g.outline.builds;g.host.width=1000;g.host.height=1200;g.update()
 check(g.boundsWidth==1000 && g.boundsHeight==1200 && g.outline.builds==builds)
 val shaders=SweepGradient.builds;g.colorSpec="blue";g.update()
 check(g.outline.builds==builds && SweepGradient.builds==shaders+1)
 g.paint.strokeWidth=8f;g.update();check(g.rect.width()==g.view.width-8f)
 g.view.width=0;g.view.height=0;g.update();check(g.perimeter==0f && g.gradient==null)
 g.view.width=300;g.view.height=160;g.update();check(g.perimeter>0 && g.gradient!=null)
 g.view.resources.displayMetrics.density=3f;val n=g.outline.builds;g.update();check(g.outline.builds==n+1)
 println("PASS exact geometry: late size growth/shrink, host bounds, colors, stroke, density, zero-size recovery; 10000 unchanged updates create no paths/shaders/color lists")
}
'''
with tempfile.TemporaryDirectory(prefix='recall-geometry-') as directory:
    out = Path(directory)
    kt = out / 'Main.kt'
    kt.write_text(fixture)
    jar = out / 'test.jar'
    subprocess.run(['java', '-Xmx256m', '-cp', str(LIB / '*'),
                    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-nowarn', '-no-stdlib',
                    '-no-reflect', '-classpath', str(STD), '-d', str(jar), str(kt)],
                   check=True, timeout=60)
    subprocess.run(['java', '-ea', '-cp', str(jar)+':'+str(STD), 'MainKt'],
                   check=True, timeout=20)
