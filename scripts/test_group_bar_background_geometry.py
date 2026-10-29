"""Execute production geometry ownership and title coordinate helpers with host stubs."""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/backgroundbeauty/BackgroundBeautyFeature.kt').read_text()
a=s.index('    private fun restoreSurfaceGeometry('); b=s.index('    private fun applyWechatStatusMasks',a)
geometry=s[a:b]
g=(r/'app/src/main/java/t/mma/hooks/items/groupbar/GroupBarRuntime.kt').read_text()
a=g.index('        private fun titleTranslation(');b=g.index('        private fun ensureBarOnTop()',a)
translation=g[a:b]
code='''open class View(var parent:View?=null,var translationY:Float=0f) {
 var paddingLeft=1;var paddingTop=90;var paddingRight=3;var paddingBottom=4
 var minimumWidth=0;var minimumHeight=0
 fun setPadding(l:Int,t:Int,r:Int,b:Int){paddingLeft=l;paddingTop=t;paddingRight=r;paddingBottom=b}
}
class SurfaceSnapshot {
 val originalPaddingLeft=1;val originalPaddingTop=2;val originalPaddingRight=3;val originalPaddingBottom=4
 val originalMinimumWidth=0;val originalMinimumHeight=0
}
'''+geometry+translation+'''
fun main(){
 val snapshot=SurfaceSnapshot()
 for(v in listOf(com.tencent.mm.ui.conversation.ConversationListView(),com.tencent.mm.ui.conversation.recycler.ConversationRecyclerView())) {
  repeat(1000){restoreSurfaceGeometry(v,snapshot);check(v.paddingTop==90)}
  v.paddingTop=2;restoreSurfaceGeometry(v,snapshot);check(v.paddingTop==2)
 }
 val ordinary=View();restoreSurfaceGeometry(ordinary,snapshot);check(ordinary.paddingTop==2)
 val root=View();val parent=View(root);val titleHost=View(root);val title=View(titleHost)
 for(common in listOf(-80f,0f,90f))for(pull in listOf(-60f,0f,130f)) {
  root.translationY=common;parent.translationY=pull;titleHost.translationY=17f
  val anchorY=200+titleTranslation(title);val parentY=100+titleTranslation(parent)
  check(anchorY+50-titleTranslation(title)-parentY+titleTranslation(parent)==150)
 }
 println("PASS production geometry: background repair preserves live home padding, ordinary surfaces restore, relative title coordinates ignore parent/shared bounce")
}
'''
lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Main.kt').write_text(code)
 # Default-package View cannot be imported from named packages: use a named base.
 (p/'Main.kt').write_text('import fixture.View\n'+code[code.index('class SurfaceSnapshot'):])
 (p/'View.kt').write_text('package fixture\n'+code[:code.index('class SurfaceSnapshot')])
 (p/'List.kt').write_text('package com.tencent.mm.ui.conversation\nclass ConversationListView:fixture.View()')
 (p/'Recycler.kt').write_text('package com.tencent.mm.ui.conversation.recycler\nclass ConversationRecyclerView:fixture.View()')
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),*map(str,p.glob('*.kt'))],check=True,timeout=45)
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(std),'MainKt'],check=True,timeout=10)
assert '+ titleTranslation(parent)' in g
