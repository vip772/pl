"""Execute shared production transform with deterministic gesture stubs, not Android gesture recognition."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
files={
'Context.kt':'package android.content\nclass Context\n',
'Matrix.kt':'''package android.graphics
class Matrix {var scale=1f;var x=0f;var y=0f;fun setScale(a:Float,b:Float){scale=a;x=0f;y=0f};fun postTranslate(a:Float,b:Float){x+=a;y+=b}}
''',
'View.kt':'''package android.view
open class View {val context=android.content.Context();var width=200;var height=100;val parent=Parent();fun performHapticFeedback(i:Int)=true}
class Parent {var blocked=false;fun requestDisallowInterceptTouchEvent(b:Boolean){blocked=b}}
object HapticFeedbackConstants {const val LONG_PRESS=1}
class MotionEvent(val actionMasked:Int,val x:Float=0f,val y:Float=0f,val pointerCount:Int=1,val actionIndex:Int=0){fun getX(i:Int)=x;fun getY(i:Int)=y;companion object{const val ACTION_DOWN=0;const val ACTION_UP=1;const val ACTION_MOVE=2;const val ACTION_CANCEL=3;const val ACTION_POINTER_UP=6}}
class GestureDetector(c:android.content.Context,val listener:SimpleOnGestureListener){open class SimpleOnGestureListener{open fun onDown(e:MotionEvent)=false;open fun onLongPress(e:MotionEvent){}};fun onTouchEvent(e:MotionEvent){if(e.actionMasked==0)listener.onDown(e);if(e.actionMasked==99)listener.onLongPress(e)}}
class ScaleGestureDetector(c:android.content.Context,val listener:SimpleOnScaleGestureListener){var scaleFactor=2f;val focusX=100f;val focusY=50f;var isInProgress=false;open class SimpleOnScaleGestureListener{open fun onScaleBegin(d:ScaleGestureDetector)=true;open fun onScale(d:ScaleGestureDetector)=false};fun onTouchEvent(e:MotionEvent){isInProgress=e.pointerCount>1;if(isInProgress&&listener.onScaleBegin(this))listener.onScale(this)}}
''',
'Storage.kt':'''package t.mma.preferences
object HchatStorage {val p=Prefs();fun preferences(c:android.content.Context,n:String)=p}
class Prefs {val values=mutableMapOf<String,Float>();var saves=0;fun getFloat(k:String,d:Float)=values[k]?:d;fun edit()=Editor(this)}
class Editor(val p:Prefs){val values=mutableMapOf<String,Float>();fun putFloat(k:String,v:Float)=apply{values[k]=v};fun apply(){p.values.putAll(values);p.saves++}}
''',
'Test.kt':'''package t.mma.hooks.items.homeextension
import android.view.*
import android.graphics.Matrix
import t.mma.preferences.HchatStorage
fun main(){
 val v=View();var latest=Matrix();val c=PhotoTransformController(v,"calendar_9",{400 to 100},{latest=it})
 c.updateMatrix();check(latest.scale==1f&&latest.x== -100f)
 c.onTouchEvent(MotionEvent(0,100f,50f));c.onTouchEvent(MotionEvent(2,140f,50f));c.onTouchEvent(MotionEvent(1))
 check(latest.x== -100f&&HchatStorage.p.saves==0&&!v.parent.blocked)
 c.onTouchEvent(MotionEvent(0,100f,50f));c.onTouchEvent(MotionEvent(99,100f,50f));check(v.parent.blocked)
 c.onTouchEvent(MotionEvent(2,140f,50f));check(kotlin.math.abs(latest.x + 60f)<.001f)
 c.onTouchEvent(MotionEvent(2,140f,50f,2));check(latest.scale==2f)
 c.onTouchEvent(MotionEvent(1));check(!v.parent.blocked&&HchatStorage.p.saves==1)
 var restored=Matrix();PhotoTransformController(v,"calendar_9",{400 to 100},{restored=it}).updateMatrix()
 check(restored.scale==latest.scale&&restored.x==latest.x&&restored.y==latest.y)
 var fresh=Matrix();PhotoTransformController(v,"calendar_10",{400 to 100},{fresh=it}).updateMatrix();check(fresh.scale==1f&&fresh.x== -100f)
 c.onTouchEvent(MotionEvent(0));c.onTouchEvent(MotionEvent(99));c.onTouchEvent(MotionEvent(2,-10000f,-10000f))
 check(latest.x>= -600f&&latest.y>= -100f)
 c.onTouchEvent(MotionEvent(3));check(!v.parent.blocked)
 c.onTouchEvent(MotionEvent(0));c.onTouchEvent(MotionEvent(99));c.detach();check(!v.parent.blocked)
 v.width=100;v.height=200;c.updateMatrix();check(latest.scale>=2f)
 println("PASS: non-edit scroll passthrough, long press drag, pinch, clamping, save/reload, replacement reset, cancel/detach and resize")
}
'''}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for n,s in files.items():(p/n).write_text(s)
 q=subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),*[str(p/n) for n in files],str(r/'app/src/main/java/t/mma/hooks/items/homeextension/PhotoTransformController.kt')],capture_output=True,text=True,timeout=60)
 if q.returncode:print(q.stderr)
 q.check_returncode()
 subprocess.run(['java','-cp',f'{p}/test.jar:{std}','t.mma.hooks.items.homeextension.TestKt'],check=True,timeout=20)
