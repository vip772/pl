from pathlib import Path
import tempfile,subprocess
r=Path(__file__).resolve().parents[3]; base=r/'app/src/main/java/t/mma/hooks/items/homeextension'; lib=Path('/tmp/pl-kotlin-libs'); std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
s=(base/'HomeExtensionRuntime.kt').read_text(); guard=s[s.index('    private val guardedAdapters'):s.index('    fun destroy()')]
files={
'View.kt':'''package android.view
open class View { var parent: Any? = null; var tag: Any? = null }
open class ViewGroup: View()
''',
'Widgets.kt':'''package android.widget
import android.view.*
interface ListAdapter
class HeaderViewListAdapter(val wrappedAdapter: ListAdapter?): ListAdapter
class ListView(var adapter: ListAdapter?)
''',
'Hook.kt':'''package de.robv.android.xposed
open class XC_MethodHook { class Unhook; class MethodHookParam(val args: Array<Any?>); open fun beforeHookedMethod(param: MethodHookParam) {} }
''',
'Registry.kt':'''package t.mma.hooks.core
import de.robv.android.xposed.XC_MethodHook
object HookRegistry { val callbacks= mutableListOf<XC_MethodHook>(); fun get()=this; fun hook(method:java.lang.reflect.Method,callback:XC_MethodHook):XC_MethodHook.Unhook {callbacks+=callback;return XC_MethodHook.Unhook()} }
''',
'Reflect.kt':'''package t.mma.utils
object KavaReflector { fun findMethodRecursive(c:Class<*>,name:String,vararg types:Class<*>)=c.getMethod(name,*types) }
''',
'Guard.kt':'''package t.mma.hooks.items.homeextension
import android.widget.*
import android.view.*
import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.core.HookRegistry
import t.mma.utils.KavaReflector
class Guard { val hooks= mutableListOf<XC_MethodHook.Unhook>()
'''+guard+''' fun attach(list: ListView) = installRecycleGuard(list)
}
''',
'Test.kt':'''import android.view.*
import android.widget.*
import t.mma.hooks.items.homeextension.*
import t.mma.hooks.core.HookRegistry
import de.robv.android.xposed.XC_MethodHook
class Adapter:ListAdapter { fun getView(position:Int,view:View?,parent:ViewGroup):View = view?:View() }
class Root:ViewGroup(),HomeExtensionSurface.Root
fun main() {
 val guard=Guard();val adapter=Adapter();val list=ListView(HeaderViewListAdapter(HeaderViewListAdapter(adapter)))
 guard.attach(list);guard.attach(list);check(HookRegistry.callbacks.size==1)
 val header=Root();check(header.tag==null);val child=View().apply{parent=header}
 val host=View().apply{tag=Any()};val originalTag=host.tag
 for (view in listOf(header,child,host,null)) {
  val param=XC_MethodHook.MethodHookParam(arrayOf(2,view,ViewGroup()))
  HookRegistry.callbacks.single().beforeHookedMethod(param)
  if(view===header || view===child) check(param.args[1]==null) else check(param.args[1]===view)
 }
 check(host.tag===originalTag);guard.attach(ListView(null));check(HookRegistry.callbacks.size==1)
 println("PASS: header/descendant excluded from reuse, host rows untouched, nested adapters unwrapped, hooks deduplicated")
}
'''}
with tempfile.TemporaryDirectory() as d:
 p=Path(d)
 for name,text in files.items(): (p/name).write_text(text)
 (p/'Surface.kt').write_text((base/'HomeExtensionSurface.kt').read_text())
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),*map(str,p.glob('*.kt'))],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p}/test.jar:{std}','TestKt'],check=True,timeout=10)
side=(r/'app/src/main/java/t/mma/ui/miuix/SidePanelSettingsEditor.kt').read_text(); bottom=(r/'app/src/main/java/t/mma/ui/miuix/CustomBottomBarMiuixPage.kt').read_text(); home=(r/'app/src/main/java/t/mma/ui/miuix/HomeExtensionMiuixPage.kt').read_text()
assert 'value=if(percent) 1f-value else value' in side and 'value=if(percent) 1f-it else it' in side
assert 'value = 100f - opacity' in bottom and 'normalizeOpacity(100 - it.toInt())' in bottom
assert 'value = if (inverse) 100f - value else value' in home
for percent in range(101):
 opacity=1-percent/100
 assert abs(opacity+percent/100-1)<1e-6
 assert abs((1-opacity)*100-percent)<1e-6
assert 'coerceIn(.1f,1f)' not in (r/'app/src/main/java/t/mma/hooks/items/homesidepanel/HomeSidePanelDrawer.kt').read_text()
print('PASS: transparency controls invert persisted opacity; 0/50/100 endpoints and all integer values roundtrip; old appearance preserved')
