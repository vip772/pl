"""Run production surface ownership against view fixtures; not a device UI test."""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[1];lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
files={
'Config.kt':'''package android.content.res
class Configuration {var uiMode=0;companion object{const val UI_MODE_NIGHT_MASK=48;const val UI_MODE_NIGHT_YES=32}}
class ColorStateList(val color:Int){companion object{fun valueOf(c:Int)=ColorStateList(c)}}
class Resources {val configuration=Configuration();fun getResourceEntryName(id:Int)=if(id==1)"bql" else "other"}
''',
'Graphics.kt':'''package android.graphics
object Color{const val TRANSPARENT=0};object PorterDuff{enum class Mode{SRC_IN}}
''',
'Drawable.kt':'''package android.graphics.drawable
open class Drawable;class ColorDrawable(val color:Int):Drawable()
''',
'View.kt':'''package android.view
import android.content.res.*
import android.graphics.drawable.Drawable
open class View {
 var parent:View?=null;var id=0;val resources=Resources();var writes=0
 var background:Drawable?=null;set(v){field=v;writes++}
 var foreground:Drawable?=null;var backgroundTintList:ColorStateList?=null
 var backgroundTintMode:android.graphics.PorterDuff.Mode?=null
 var paddingLeft=1;var paddingTop=2;var paddingRight=3;var paddingBottom=4
 var minimumWidth=10;var minimumHeight=11
 fun setPadding(l:Int,t:Int,r:Int,b:Int){paddingLeft=l;paddingTop=t;paddingRight=r;paddingBottom=b}
}
open class ViewGroup:View(){val children=mutableListOf<View>();val childCount get()=children.size;fun getChildAt(i:Int)=children[i];fun add(v:View){v.parent=this;children.add(v)}}
''',
'Widgets.kt':'''package android.widget
import android.view.View
open class EditText:View()
open class ImageView:View(){var drawable:android.graphics.drawable.Drawable?=null}
open class ImageButton:ImageView()
open class Button:View(){var textColors=android.content.res.ColorStateList(0);fun setTextColor(c:android.content.res.ColorStateList){textColors=c}}
''',
'Native.kt':'''package com.tencent.mm.pluginsdk.ui.chat
class ChatFooterBottom:android.view.ViewGroup()
''',
'Visuals.kt':'''package t.mma.hooks.items.floatingchat
object FloatingChatCardVisuals {val owned=java.util.IdentityHashMap<android.view.View,Boolean>();fun ownPresentation(v:android.view.View){owned[v]=true};fun releasePresentation(v:android.view.View){owned.remove(v)}}
''',
'Test.kt':'''package t.mma.hooks.items.floatingchat
import android.view.*
import android.widget.*
import android.graphics.drawable.Drawable
fun main(){
 val footer=ViewGroup();val icon=ImageButton();val glyph=Drawable();val foreground=Drawable()
 icon.drawable=glyph;icon.foreground=foreground;footer.add(icon)
 val edit=EditText();val original=Drawable();edit.background=original;footer.add(edit)
 val panel=com.tencent.mm.pluginsdk.ui.chat.ChatFooterBottom();val panelButton=ImageButton();panelButton.background=original;panel.add(panelButton);footer.add(panel)
 FloatingFooterInputSurfaces.apply(footer)
 check(icon.background==null&&icon.drawable===glyph&&icon.foreground===foreground&&icon.writes==0)
 check(edit.background!==original&&panelButton.background===original)
 val owners=FloatingChatCardVisuals.owned.size;val writes=edit.writes
 repeat(1000){FloatingFooterInputSurfaces.apply(footer);FloatingFooterInputSurfaces.maintain(footer)}
 check(FloatingChatCardVisuals.owned.size==owners&&edit.writes==writes&&icon.writes==0)
 icon.background=Drawable();FloatingFooterInputSurfaces.maintain(footer)
 check(icon.background!=null&&icon.drawable===glyph&&icon.foreground===foreground)
 check(icon.minimumWidth==10&&icon.paddingLeft==1)
 FloatingFooterInputSurfaces.restore(footer)
 check(icon.background==null&&icon.foreground===foreground&&icon.drawable===glyph)
 check(edit.background===original&&FloatingChatCardVisuals.owned.isEmpty())
 println("PASS production surfaces: icon drawable/foreground/null background preserved, native rewrite, geometry, panel exclusion, 1000 idle passes without writes, restore")
}
'''}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for n,s in files.items():(p/n).write_text(s)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),str(r/'app/src/main/java/t/mma/hooks/items/floatingchat/FloatingFooterInputSurfaces.kt'),*map(str,p.glob('*.kt'))],check=True,timeout=40)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.floatingchat.TestKt'],check=True,timeout=10)
