"""Run production chrome ownership predicates against a shared launcher/chat tree."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/backgroundbeauty/BackgroundBeautyFeature.kt').read_text()
a=s.index('                    state.surfaces.entries.toList().forEach');a=s.index('                        if (',a);b=s.index(' {',a)
tab=s[a:b].strip()[4:-1]
a=s.index('        val cached = state.surfaces.keys.filter',s.index('    private fun applyChromeTransparency'));a=s.index('            view !== host',a);b=s.index('\n        }',a);cached=s[a:b]
a=s.index('            if (slot != BackgroundBeautySettings.Slot.CHAT &&',s.index('    private fun applyChromeTransparency'));b=s.index(' continue',a);skip=s[a:b].strip()[4:-1]
code='''
class View(val parent:View?=null,var isAttachedToWindow:Boolean=true,var chrome:Boolean=false,var background:Any?=null)
object BackgroundBeautySettings{enum class Slot{CHAT,WECHAT,CONTACTS}}
class Snapshot(var slot:BackgroundBeautySettings.Slot,var appliedBackground:Any?=null)
class State{val surfaces=linkedMapOf<View,Snapshot>()}
fun isChromeSurface(view:View)=view.chrome
fun isDescendantOf(view:View,root:View):Boolean {var v:View?=view;while(v!=null){if(v===root)return true;v=v.parent};return false}
fun tab(view:View,snapshot:Snapshot,slot:BackgroundBeautySettings.Slot,visibleChatHost:View?) =
'''+tab+'''
fun cached(view:View,state:State,host:View,chatHost:View?,visibleChatHost:View?) =
'''+cached+'''
fun skip(view:View,state:State,slot:BackgroundBeautySettings.Slot,visibleChatHost:View?) =
'''+skip+'''
fun main(){
 val home=BackgroundBeautySettings.Slot.WECHAT;val chat=BackgroundBeautySettings.Slot.CHAT
 val decor=View();val homeToolbar=View(decor,chrome=true);val chatRoot=View(decor);val chatToolbar=View(chatRoot,chrome=true)
 val state=State();state.surfaces[homeToolbar]=Snapshot(home);state.surfaces[chatToolbar]=Snapshot(chat)
 repeat(10000){
  check(!tab(chatToolbar,state.surfaces[chatToolbar]!!,home,chatRoot))
  check(!cached(chatToolbar,state,decor,null,chatRoot))
  check(cached(chatToolbar,state,decor,chatRoot,chatRoot))
  check(!cached(homeToolbar,state,decor,chatRoot,chatRoot))
  check(skip(chatRoot,state,home,chatRoot));check(skip(chatToolbar,state,home,chatRoot))
 }
 // Real external homepage background replacement is still updated.
 homeToolbar.background=Any();check(tab(homeToolbar,state.surfaces[homeToolbar]!!,home,chatRoot))
 check(cached(homeToolbar,state,decor,null,chatRoot))
 // Hidden chat retains ownership; closing cleanup removes it separately.
 check(!tab(chatToolbar,state.surfaces[chatToolbar]!!,home,null))
 check(!cached(chatToolbar,state,decor,null,null))
 state.surfaces.remove(chatToolbar)
 check(!skip(chatToolbar,state,home,null))
 // A newly cached child inside the visible chat is excluded even with an old home tag.
 state.surfaces[chatToolbar]=Snapshot(home)
 check(!tab(chatToolbar,state.surfaces[chatToolbar]!!,home,chatRoot))
 check(!cached(chatToolbar,state,decor,null,chatRoot))
 check(!skip(homeToolbar,state,chat,chatRoot))
 println("PASS production predicates: chat scope, home scope, visible/hidden chat, external home replacement, cleanup, stale tag and 10000 repeats")
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 d=Path(td);(d/'Test.kt').write_text(code)
 x=subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(d/'out.jar'),str(d/'Test.kt')],capture_output=True,text=True)
 assert x.returncode==0,x.stdout+x.stderr
 subprocess.run(['java','-cp',str(d/'out.jar')+':'+str(std),'TestKt'],check=True)
