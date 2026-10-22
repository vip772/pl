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
assert 'named("obr")' in source and 'collectMenuActions(menu, candidates)' in source
assert 'icon?.constantState == settingIcon.constantState' in source
assert 'settingRaw = header.resources.getIdentifier("actionbar_setting_icon", "raw"' in source
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

# Actual 8.0.78 icon-menu path: fq -> controller.U -> original listener/description.
assert 'const v13, 0x7f0a0109' in ha
assert 'iput-object v3, v0, Lcom/tencent/mm/ui/ha;->U:Lcom/tencent/mm/ui/widget/imageview/WeImageView;' in ha
start = ha.index('    :cond_37\n    :goto_11')
end = ha.index('    iget v9, v8, Lcom/tencent/mm/ui/fb;->b:I', start)
binding = ha[start:end]
assert 'setOnClickListener' in binding and 'setContentDescription' in binding
assert 'Lcom/tencent/mm/ui/fb;->h:Ljava/lang/String;' in binding
assert '0x7f1000b6' in fg and '0x7f11001f' in fg
assert 'view.id == fq' in source and 'view.hasOnClickListeners()' in source
assert 'view.javaClass.name == "com.tencent.mm.ui.widget.imageview.WeImageView"' in source
assert '(view as? ImageView)?.drawable' in source
assert 'state.more.setOnClickListener { nativeMore.performClick() }' in source
print('PASS: fq native icon-menu listener/description evidence and original click delegation')

# fb.b is the icon resource, not the menu ID: controller.i receives icon as p2.
start = ha.index('.method public final i(IILjava/lang/String;Z')
end = ha.index('.end method', start)
assert 'iput p2, v0, Lcom/tencent/mm/ui/fb;->b:I' in ha[start:end]
start = ha.index('    :cond_37\n    :goto_11')
end = ha.index('    :cond_38', start)
tag_binding = ha[start:end]
assert 'Lcom/tencent/mm/ui/fb;->b:I' in tag_binding
assert '0x7f0a6816' in tag_binding and 'setTag(ILjava/lang/Object;)' in tag_binding
assert 'name="nwr" id="0x7f0a6816"' in (decoded / 'res/values/public.xml').read_text()
assert 'view.getTag(iconTagId) == settingRaw' in source
assert 'nativeSettingTag ||' in source
print('PASS: native icon resource tag nwr validates settings independently of drawable wrappers')

# Real 13:10 runtime path: chatroom MORE icon, not the settings menu branch.
assert 'name="actionbar_icon_dark_more" id="0x7f110012"' in (decoded / 'res/values/public.xml').read_text()
chatroom_literal = fg.index('    const-string v10, "chatroom"')
start = fg.rfind('    :goto_9', 0, chatroom_literal)
end = fg.index('    :goto_a', chatroom_literal)
chatroom_more = fg[start:end]
assert '0x7f110012' in chatroom_more and '"chatroom"' in chatroom_more
assert 'Lcom/tencent/mm/ui/chatting/component/fg;->Z0(IIILjava/lang/String;' in chatroom_more
assert 'moreRaw = header.resources.getIdentifier("actionbar_icon_dark_more", "raw"' in source
assert 'moreRaw != 0 && view.getTag(iconTagId) == moreRaw' in source
assert 'HEADER_SPLIT_APPLIED' in source
print('PASS: runtime chatroom dark_more branch accepted alongside settings; applied marker present')
