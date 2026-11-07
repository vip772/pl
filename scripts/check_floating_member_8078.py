"""Check the supplied host title/tips layout evidence; no host mutation."""
from pathlib import Path
import xml.etree.ElementTree as ET
import sys,hashlib
root=Path(sys.argv[1]);a='{http://schemas.android.com/apk/res/android}'
files=['res/layout/ba.xml','res/layout/s3.xml','smali_classes11/com/tencent/mm/ui/chatting/component/fg.smali','smali_classes3/com/tencent/mm/ui/tipsbar/ChatTipsBarGroup.smali']
views={v.get(a+'id'):v for v in ET.parse(root/files[0]).getroot().iter()}
assert views['@id/obn'].tag=='TextView' and views['@id/obr'].tag=='TextView'
assert views['@id/coy'].tag=='LinearLayout'
fg=(root/files[2]).read_text();start=fg.index('.method public final I0(')
assert '0x7f0e0068' in fg[start:fg.index('.end method',start)]
assert '0x7f0e02f4' in (root/files[3]).read_text()
assert 'name="s3" id="0x7f0e02f4"' in (root/'res/values/public.xml').read_text()
ids={v.get(a+'id') for v in ET.parse(root/files[1]).getroot().iter()}
assert {'@id/hyi','@id/m98','@id/ow1'}.issubset(ids)
print('PASS 8.0.78 title bindings and ChatTipsBarGroup -> s3 layout IDs')
for name in files:print(hashlib.sha256((root/name).read_bytes()).hexdigest(),name)
profile="smali_classes16/com/tencent/mm/plugin/profile/ui/tab/m5.smali"
source=(root/profile).read_text()
for rid in ("0x7f1104c9", "0x7f1104c8"):
 start=source.index("const v2, "+rid)
 assert "Lke5/a;->i(Landroid/content/Context;I)Landroid/graphics/drawable/Drawable;" in source[start:start+350]
public=(root/"res/values/public.xml").read_text()
assert 'name="ic_sex_male" id="0x7f1104c9"' in public
assert 'name="ic_sex_female" id="0x7f1104c8"' in public
print("PASS native male/female raw resources and actual profile loader calls")
print(hashlib.sha256((root/profile).read_bytes()).hexdigest(),profile)
