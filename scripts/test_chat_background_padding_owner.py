"""Compile and exercise production background geometry restoration."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/backgroundbeauty/BackgroundBeautyFeature.kt').read_text()
a=s.index('    private fun restoreSurfaceGeometry(');b=s.index('    private fun applyWechatStatusMasks(',a);method=s[a:b]
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
cmd=['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect']
shared="""object BackgroundBeautySettings {enum class Slot {CHAT,WECHAT}}
class SurfaceSnapshot {var slot=BackgroundBeautySettings.Slot.CHAT;val originalPaddingLeft=0;val originalPaddingTop=0;val originalPaddingRight=0;val originalPaddingBottom=20;val originalMinimumWidth=0;val originalMinimumHeight=0}
"""
fixture="""class View(val className:String){var paddingLeft=0;var paddingTop=129;var paddingRight=0;var paddingBottom=400;var minimumWidth=0;var minimumHeight=0;var writes=0;fun setPadding(l:Int,t:Int,r:Int,b:Int){paddingLeft=l;paddingTop=t;paddingRight=r;paddingBottom=b;writes++}}
fun main(){for(n in listOf("ChattingRecyclerView","ScrollControlRecyclerView")){val v=View("com.tencent.mm.pluginsdk.ui.tools."+n);val s=SurfaceSnapshot();repeat(1000){restoreSurfaceGeometry(v,s)};check(v.paddingBottom==400 && v.paddingTop==129 && v.writes==0);s.slot=BackgroundBeautySettings.Slot.WECHAT;restoreSurfaceGeometry(v,s);check(v.paddingBottom==20)};val v=View("other");restoreSurfaceGeometry(v,SurfaceSnapshot());check(v.paddingBottom==20);println("PASS background keeps live chat top/bottom padding across 1000 repairs; other surfaces still restore")}
"""
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'SDK.kt').write_text('import android.view.View'+chr(10)+shared+method)
 subprocess.run(cmd+['-classpath',str(std)+':/usr/lib/android-sdk/platforms/android-36/android.jar','-d',str(p/'sdk.jar'),str(p/'SDK.kt')],check=True,timeout=45)
 (p/'Test.kt').write_text(shared+method.replace('view.javaClass.name','view.className')+fixture)
 subprocess.run(cmd+['-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True)
