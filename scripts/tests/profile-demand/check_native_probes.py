"""Verify production probe signatures/fields against the supplied 8.0.78 smali.
Read-only source evidence, not a substitute for a real Xposed installation.
"""
from pathlib import Path
import re

repo = Path(__file__).resolve().parents[3]
root = Path('/sdcard/Download/wechat8078_decoded')
checks = {
    'smali_classes8/qn/w.smali': [
        '.method public doScene(Lcom/tencent/mm/network/s;Lcom/tencent/mm/modelbase/u0;)I',
        '.method public onGYNetEnd(IIILjava/lang/String;Lcom/tencent/mm/network/y0;[B)V',
        '.field public final f:Ljava/lang/String;', '.field public final g:I', '.field public m:Z',
    ],
    'smali_classes8/com/tencent/mm/storage/z2.smali': [
        '.method public P0(Ljava/lang/String;Ljava/util/List;Ljava/util/List;Ljava/util/List;Z)V',
        '.method public X0(Ljava/lang/String;Lso/a;Z)Lcom/tencent/mm/storage/z2;',
        '->n2:Ljava/util/Map;', '->l2:Lso/a;',
    ],
    'smali_classes8/b41/u1.smali': [
        '.method public static M(Lcom/tencent/mm/storage/z2;)Z',
        '.method public static N(Ljava/lang/String;Ljava/lang/String;Lpc5/zz;IIILjava/lang/String;Lso/a;ZZI)Z',
    ],
    'smali_classes8/com/tencent/mm/storage/a3.smali': [
        '.method public Q1(Lcom/tencent/mm/storage/z2;Z)Z',
    ],
    'smali_classes11/lb2/e.smali': [
        '.method public c(Lod5/e;)I', '.method public i(Ljava/lang/String;IZZ)I',
        '.method public a(Ljava/lang/String;Ljava/lang/String;ZZ)Ljava/lang/String;',
    ],
    'smali_classes11/ou5/n1.smali': ['.method public final m(Lod5/e;)I', '.method public final g(Lod5/e;)I'],
    'smali_classes4/com/tencent/mm/repairer/config/profile/RepairerConfigChatroomJoinInfo.smali': ['.method public c()Ljava/lang/Object;'],
    'smali_classes8/pc5/a00.smali': ['.field public d:Ljava/lang/String;', '.field public v:I'],
    'smali_classes8/so/b.smali': ['.field public d:Ljava/lang/String;', '.field public i:I'],
    'smali_classes8/pc5/zz.smali': ['.field public d:I', '.field public e:Ljava/util/LinkedList;'],
    'smali_classes8/pc5/di3.smali': ['.field public f:Lpc5/zz;'],
}
for name, needles in checks.items():
    text = (root / name).read_text()
    for needle in needles:
        assert needle in text, (name, needle)
source = (repo / 'app/src/main/java/t/mma/hooks/items/profileid/JoinTimeNativeDiagnostics.kt').read_text()
assert len(re.findall(r'^        probe\(', source, re.M)) == 13
assert 'param.result =' not in source and 'p.args[4] =' not in source
print('PASS: 13 native probe signatures and protocol fields match supplied 8.0.78 smali; observers do not override results')
