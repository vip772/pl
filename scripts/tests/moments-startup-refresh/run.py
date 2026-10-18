from pathlib import Path
r = Path(__file__).resolve().parents[3]
f = (r / 'app/src/main/java/t/mma/hooks/items/momentsfake/MomentsFakeForwardRuntime.kt').read_text()
i = (r / 'app/src/main/java/t/mma/hooks/items/momentsfake/MomentsFakeInteractionRuntime.kt').read_text()
for name in ['reloadCurrentTimeline', 'reloadTimelineAdapter', 'refreshImproveMvvmList']:
 start = f.index('fun '+name, f.index('internal class MomentsFakeForwardRuntime'))
 body = f[start:]
 pos = body.index('{'); depth = 1; n = pos + 1
 while depth:
  depth += (body[n] == '{') - (body[n] == '}'); n += 1
 body = body[:n]
 for forbidden in ['locateTimelineLocalReloadMethod(', 'locateImproveTimelineRefreshMethod(', 'installTimelineAdapterTracker(', 'findMethod(']:
  assert forbidden not in body, (name, forbidden)
assert 'timelineAdapterTrackerInstalled || improveTimelineRefreshMethod != null' in f
assert 'if (!isSnsActivity(activity)) return' in i
assert 'if (ids.isEmpty()) return@submit' in i
def refresh(ids, hierarchy):
 return bool(ids) and any(x.startswith('com.tencent.mm.plugin.sns.') for x in hierarchy)
assert not refresh([], ['com.tencent.mm.plugin.sns.ui.SnsTimeLineUI'])
assert not refresh(['saved'], ['com.tencent.mm.ui.LauncherUI'])
assert refresh(['saved'], ['com.tencent.mm.plugin.sns.ui.SnsTimeLineUI'])
assert refresh(['saved'], ['wrapper.Activity', 'com.tencent.mm.plugin.sns.ui.SnsUserUI'])
print('PASS: 首页隔离、空数据跳过、朋友圈继承页面、刷新路径无同步定位及有限补装')
