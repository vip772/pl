from pathlib import Path
import tempfile,subprocess,re
r=Path(__file__).resolve().parents[1];b=r/'app/src/main/java/t/mma/hooks/items/chattoolbar';lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'));cmd=['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect']
s=(b/'ChatToolbarGlyph.kt').read_text();settings=(b/'ChatToolbarSettings.kt').read_text();ids=re.findall(r'Tool\("([a-z_]+)"',settings)+['quick_reply','system_camera','voice_call']
assert len(set(ids))==16
for id in ids:assert '"'+id+'"' in s,id
runtime=(b/'ChatToolbarRuntime.kt').read_text();assert 'ChatToolbarGlyph(id, foreground)' in runtime and 'ChatToolbarIcon' not in runtime and 'copyFor' not in runtime
assert 'nativeById[config.id]?.let' in runtime and 'album.invokeLongClick()' in runtime
# Run actual geometry initializer and draw methods against counted graphics fixtures.
geometry=s[s.index('    private val path = Path()'):s.index('    override fun setAlpha')]
fixture='''class Paint
class Path {var operations=0;enum class Direction{CW};fun moveTo(x:Float,y:Float){operations++};fun lineTo(x:Float,y:Float){operations++};fun cubicTo(a:Float,b:Float,c:Float,d:Float,e:Float,f:Float){operations++};fun quadTo(a:Float,b:Float,c:Float,d:Float){operations++};fun close(){operations++};fun addRoundRect(a:Float,b:Float,c:Float,d:Float,e:Float,f:Float,g:Direction){operations++};fun addCircle(a:Float,b:Float,c:Float,d:Direction){operations++};fun addOval(a:Float,b:Float,c:Float,d:Float,e:Direction){operations++}}
class Bounds{val isEmpty=false;fun width()=66;fun height()=66;fun exactCenterX()=33f;fun exactCenterY()=33f}
class Canvas{var draws=0;fun save()=1;fun translate(x:Float,y:Float){};fun scale(x:Float,y:Float){};fun drawPath(p:Path,paint:Paint){check(p.operations>0);draws++};fun restoreToCount(n:Int){}}
class Glyph(id:String){val paint=Paint();val bounds=Bounds()
'''+geometry.replace('override fun draw','fun draw')+'''fun count()=path.operations
}
fun main(){val ids=listOf('''+','.join('"'+id+'"' for id in ids)+''');for(id in ids){val g=Glyph(id);val before=g.count();val c=Canvas();repeat(1000){g.draw(c)};check(g.count()==before&&c.draws==1000)};println("PASS all 16 tool geometries nonempty; 1000 draws each do not rebuild paths; native actions retained")}
'''
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(fixture)
 subprocess.run(cmd+['-classpath',str(std)+':/usr/lib/android-sdk/platforms/android-36/android.jar','-d',str(p/'sdk.jar'),str(b/'ChatToolbarGlyph.kt')],check=True,timeout=45)
 subprocess.run(cmd+['-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True)
print('PASS production Canvas Drawable SDK compile')