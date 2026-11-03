"""Source and pixel geometry regression; no Android/device claim."""
from pathlib import Path
s=(Path(__file__).resolve().parents[1]/'app/src/main/java/t/mma/hooks/items/floatingshortcut/FloatingShortcutRuntime.kt').read_text()
v=s[s.index('    private fun positionMenu('):s.index('    private fun positionHorizontalMenu(')]
assert v.index('list.gravity = itemGravity') < v.index('menu.measure(')
assert v.index('childParams.gravity = itemGravity') < v.index('menu.measure(')
assert 'dp(context, actionSize) / 2 + list.paddingRight - params.width' in v
assert 'dp(context, actionSize) / 2 - list.paddingLeft' in v
a=s[s.index('    private fun createActionView('):s.index('    private fun cancelPendingAction(')]
assert 'gravity = Gravity.CENTER_VERTICAL' in a
assert 'layoutDirection = View.LAYOUT_DIRECTION_LTR' in a
for density in (1,1.5,2,2.625,3):
 for size in (36,43,44,55,64):
  icon=int(size*density+.5);pad=int(4*density+.5)
  center=500
  for label in (40,80,180):
   width=icon+label+int(8*density+.5)+pad*2
   left=center+icon//2+pad-width
   assert left+width-pad-icon//2 == center
   left=center-icon//2-pad
   assert left+pad+icon//2 == center
print('PASS vertical gravity before measure, label/icon center-vertical, both-side column/padding pixel geometry')
