#!/usr/bin/env python3
"""Read-only evidence assertions; no compilation or Android behavior test."""
from pathlib import Path
import argparse
import hashlib

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--evidence-dir', type=Path, required=True)
p.add_argument('--installed-apk', type=Path, required=True)
a = p.parse_args()

def method(text, signature):
    start = text.index('.method ' + signature)
    return text[start:text.index('.end method', start)]

def read(relative):
    path = a.evidence_dir / relative
    data = path.read_bytes()
    print('SHA256', relative, hashlib.sha256(data).hexdigest())
    return data.decode()

assert hashlib.sha256(a.installed_apk.read_bytes()).hexdigest() == 'c0b7f2dd579c8c65a0fdd5f9a266d26c1731b3f0fe63ac48d89700f17bdc56a6', 'Different installed artifact: re-establish class mapping before checking'
ha = read('native/com/tencent/mm/ui/ha.smali')
y0 = read('native/androidx/appcompat/app/y0.smali')
draw = read('native/androidx/appcompat/widget/c.smali')
color = method(ha, 'public p0(I)V')
assert 'Landroid/graphics/drawable/ColorDrawable;' in color
assert 'Landroidx/appcompat/app/b;->w(Landroid/graphics/drawable/Drawable;)V' in color
assert 'Landroidx/appcompat/widget/ActionBarContainer;->setPrimaryBackground(Landroid/graphics/drawable/Drawable;)V' in method(y0, 'public w(Landroid/graphics/drawable/Drawable;)V')
for field in ('h', 'i', 'm'):
    assert 'ActionBarContainer;->' + field + ':Landroid/graphics/drawable/Drawable;' in method(draw, 'public draw(Landroid/graphics/Canvas;)V')
print('PASS native evidence: controller color -> primary setter; wrapper reads h/i/m')

owner = read('installed/smali/gj/a.smali')
callback = read('installed/smali/gj/c0.smali')
lambda_ = read('installed/smali/ak/d.smali')
install = read('installed/smali/gj/n.smali')
bridge = read('installed/smali/gj/e0.smali')
for setter in ('setPrimaryBackground', 'setStackedBackground', 'setSplitBackground'):
    assert '"' + setter + '"' in install
assert 'Lgj/e0;->k(Ljava/lang/reflect/Method;Lak/d;)I' in install
assert 'Lgj/c0;-><init>(Lgj/e0;Lak/d;)V' in method(bridge, 'public final declared-synchronized k(Ljava/lang/reflect/Method;Lak/d;)I')
assert 'Lak/d;->invoke(Ljava/lang/Object;)Ljava/lang/Object;' in method(callback, 'public final beforeHookedMethod(Lde/robv/android/xposed/XC_MethodHook$MethodHookParam;)V')
assert 'Lgj/a;->a(Landroid/view/View;Ljava/lang/reflect/Method;Landroid/graphics/drawable/Drawable;)Z' in lambda_
assert 'aput-object v2, p1, v4' in lambda_
owned = method(owner, 'public static a(Landroid/view/View;Ljava/lang/reflect/Method;Landroid/graphics/drawable/Drawable;)Z')
assert 'Ljava/util/Map;->containsKey(Ljava/lang/Object;)Z' in owned
assert 'Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;' in owned
print('PASS installed artifact: all three setters registered through BEFORE callback; ownership lookup/latest-value save and null argument branch present')
print('BOUNDARY: proves artifact structure only; does not prove hook execution, observed gray writer, UI correctness or runtime performance')
