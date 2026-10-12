#!/usr/bin/env python3
"""Compile actual tint policy; check color/alpha and runtime wiring without an APK."""
from pathlib import Path
import tempfile, subprocess
repo=Path(__file__).resolve().parents[3]
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-*.jar'))
with tempfile.TemporaryDirectory() as d:
 p=Path(d)
 (p/'Color.kt').write_text('''package android.graphics
object Color {
 fun parseColor(v:String):Int { val n=v.drop(1).toLong(16);return (if(v.length==7) n or 0xff000000L else n).toInt() }
 fun alpha(c:Int)=c ushr 24
 fun red(c:Int)=(c ushr 16) and 255
 fun green(c:Int)=(c ushr 8) and 255
 fun blue(c:Int)=c and 255
 fun argb(a:Int,r:Int,g:Int,b:Int)=(a shl 24) or (r shl 16) or (g shl 8) or b
}
''')
 (p/'Test.kt').write_text('''package t.mma.hooks.items.antirecall
import android.graphics.Color
fun main(){
 check(DynamicRecallTint.normalize(null)=="#808080")
 check(DynamicRecallTint.normalize("bad")=="#808080")
 check(DynamicRecallTint.normalize("#fff,#000")=="#808080")
 check(DynamicRecallTint.normalize(" #aabbcc ")=="#AABBCC")
 val gray=DynamicRecallTint.color(null,35);check(Color.alpha(gray)==89);check(Color.red(gray)==128)
 check(Color.alpha(DynamicRecallTint.color("#FF0000",0))==0)
 check(Color.alpha(DynamicRecallTint.color("#80FF0000",50))==64)
 check(Color.alpha(DynamicRecallTint.color("#FF0000",100))==204)
 check(DynamicRecallTint.opacity(-5)==0);check(DynamicRecallTint.opacity(99)==80)
 println("PASS gray default, invalid preference fallback, custom RGB/ARGB, coverage bounds and transparent mode")
}
''')
 args=['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'tint.jar'),str(p/'Color.kt'),str(p/'Test.kt'),str(repo/'app/src/main/java/t/mma/hooks/items/antirecall/DynamicRecallTint.kt')]
 subprocess.run(args,check=True)
 subprocess.run(['java','-cp',str(p/'tint.jar')+':'+str(std),'t.mma.hooks.items.antirecall.TestKt'],check=True)
runtime=(repo/'app/src/main/java/t/mma/hooks/items/antirecall/DynamicRecallRuntime.kt').read_text()
assert 'tintPaint' not in runtime and 'tintRect' not in runtime
assert 'stroke?.stop()' in runtime
assert 'key.startsWith("dynamic_recall_")' in runtime
assert 'root.removeOnAttachStateChangeListener(this)' in runtime
assert 'Bitmap' not in runtime and 'setLayerType' not in runtime
ui=(repo/'app/src/main/java/t/mma/ui/miuix/MiuixSettingsPagePart16.kt').read_text()
assert 'SwitchRow(recallMarkerEnabled, "撤回标记"' in ui
assert '.putBoolean(AntiRecallSettings.KEY_MARKER_ENABLE' in ui
assert 'recallMessageColor' not in ui and 'recallMessageOpacity' not in ui
assert 'view.alpha = 0.5f' in runtime
assert 'originalAlpha = view.alpha' in runtime
assert 'restoreContent()' in runtime
assert 'marker?.update(next, isSelf)' in runtime
assert 'top + content.height - iconSize' in runtime
assert 'left - 4f * density - iconSize' in runtime
assert '(borderEnabled() || markerEnabled())' in runtime
assert 'fadedContent?.alpha = originalAlpha' in runtime
assert 'setTextColor' not in runtime and 'imageAlpha' not in runtime
print('PASS marker persistence, removed color editor, 50% container alpha and original alpha restoration, independent border and bottom-corner wiring')
