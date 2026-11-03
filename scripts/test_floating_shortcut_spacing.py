"""验证悬浮快捷菜单纵向间距，不运行 Gradle 或实机。"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / 'app/src/main/java/t/mma/hooks/items/floatingshortcut/FloatingShortcutRuntime.kt').read_text()
assert 'if (horizontal) leftMargin = dp(activity, 5) else topMargin = dp(activity, 12)' in source
assert 'setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4))' in source
vertical = source[source.index('    private fun positionMenu('):source.index('    private fun positionHorizontalMenu(')]
horizontal = source[source.index('    private fun positionHorizontalMenu('):source.index('    private fun orientActionViews(')]
assert 'val gap = dp(context, 8)' in vertical
assert 'val gap = dp(context, 10)' in horizontal
assert 'margin + openMenu.height + dp(view.context, 8)' in source
assert 'decor.height - margin - openMenu.height - dp(view.context, 8) - view.height' in source
assert 'margin + openMenu.width + dp(view.context, 10)' in source
assert 'decor.width - margin - openMenu.width - dp(view.context, 10) - view.width' in source
assert 4 + 8 == 12
print('通过：纵向相邻图标空隙为12dp；主按钮间距为4dp内边距加8dp外间距；拖动约束同步；横向间距保持不变。')
