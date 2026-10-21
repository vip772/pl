#!/usr/bin/env python3
"""Check production header bindings against user-supplied 8.0.78 decoded evidence.
Usage: python3 scripts/check_floating_header_8078.py /path/to/wechat8078_decoded
Static evidence checks only; not an Android runtime/visual test.
"""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[1]
decoded = Path(sys.argv[1])
android = '{http://schemas.android.com/apk/res/android}'
xml = ET.parse(decoded / 'res/layout/ba.xml').getroot()
views = {v.get(android + 'id'): v for v in xml.iter()}
assert views['@id/obn'].tag == 'TextView'
assert views['@id/obr'].tag == 'TextView'
assert views['@id/coy'].tag == 'LinearLayout'
assert views['@id/actionbar_up_indicator'].get(android + 'clickable') == 'true'
assert '@android:id/text1' not in views
fg = (decoded / 'smali_classes11/com/tencent/mm/ui/chatting/component/fg.smali').read_text()
start = fg.index('.method public final I0(')
end = fg.index('.end method', start)
assert '0x7f0e0068' in fg[start:end]
ha = (decoded / 'smali_classes16/com/tencent/mm/ui/ha.smali').read_text()
assert '0x7f0e006a' in ha and 'Lcom/tencent/mm/ui/widget/WXActionMenuView;->a(' in ha
menu = ET.parse(decoded / 'res/layout/bc.xml').getroot()
assert menu.tag == 'com.tencent.mm.ui.widget.WXActionMenuView'
source = (root / 'app/src/main/java/t/mma/hooks/items/floatingchat/FloatingChatHeaderCapsule.kt').read_text()
assert 'val nativeTitle = named("obn") as? TextView' in source
assert 'header.findViewById<TextView>(android.R.id.text1)' not in source
assert 'named("obr")' in source and 'collectMenuActions(menu, actions)' in source
assert 'view.javaClass.name == "com.tencent.mm.ui.widget.WXActionMenuView"' in source
print('PASS: fg.I0 -> ba.xml; obn/obr/back/coy bindings; bc.xml WXActionMenuView menu; obsolete text1 lookup removed')
for feature in ['hiddenat/HiddenAtFeature.kt', 'emojiat/EmojiAtRuntime.kt']:
    code = (root / 'app/src/main/java/t/mma/hooks/items' / feature).read_text()
    assert 'HLog.d(' not in code
    assert 'HLog.e(' in code
hidden = (root / 'app/src/main/java/t/mma/hooks/items/hiddenat/HiddenAtFeature.kt').read_text()
assert '原生消息读取方法缺失' not in hidden and 'private var observed' not in hidden
assert 'pending.put(talker, id, scope.users)' in hidden
assert 'KavaReflector.invokeOrThrow(setter, message, merged)' in hidden
assert 'HiddenAtSource.merge(param.result as? String, users)' in hidden
print('PASS: temporary at-feature diagnostics removed; native source setter, pending association and uplink merge retained')
