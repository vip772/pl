from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];s=(r/'app/src/main/java/t/mma/hooks/items/floatingchat/FloatingChatFooterFeature.kt').read_text();a=s.index('    private fun visibleHeightExcludingPanel(');b=s.index('    private fun refreshMeasuredInputHeight(',a);method=s[a:b]
code='''open class View(var height:Int,var measuredHeight:Int,var lpHeight:Int,var visibility:Int=0){val layoutParams=Params(lpHeight);companion object{const val GONE=8}}
class Params(var height:Int)
class ViewGroup(val children:MutableList<View>):View(0,0,0){val childCount get()=children.size;fun getChildAt(i:Int)=children[i]}
'''+method+'''\nfun main(){val panel=View(0,0,100);val row=View(197,197,-2);val input=ViewGroup(mutableListOf(row,panel));check(visibleHeightExcludingPanel(input,panel)==197);row.height=354;row.measuredHeight=354;check(visibleHeightExcludingPanel(input,panel)==354);row.height=0;row.measuredHeight=0;row.layoutParams.height=240;check(visibleHeightExcludingPanel(input,panel)==240);println("PASS measured input height follows late toolbar and excludes accessory panel")}'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code);cmd=['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')];subprocess.run(cmd,check=True,timeout=45);subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True)