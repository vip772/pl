"""Compiler-free source checks and Python reference models, NOT Kotlin/device execution."""
from pathlib import Path
import ast
import hashlib
import re
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'app/src/main/java/t/mma'
w = (BASE / 'hooks/items/homeextension/HomeWeather.kt').read_text()
c = (BASE / 'hooks/items/moments/MomentsCardFeature.kt').read_text()
assert 'private const val CACHE_TTL = 3 * 60 * 60_000L' in w
assert '6 * 60 * 60_000L' not in w
assert 'HomeWeather.card(ctx, compactCard)' in (BASE / 'hooks/items/homeextension/HomeExtensionRuntime.kt').read_text()
assert 'HomeWeather.card(activity, settings = prefs)' in (BASE / 'hooks/items/homesidepanel/HomeSidePanelDrawer.kt').read_text()
assert 'if (!inFlight.add(requestKey))' in w
assert 'failures[key] ?: 0L) < 5 * 60_000L' in w
assert 'main.postDelayed(automaticUpdate, delay.coerceIn(60_000L, CACHE_TTL))' in w
attach = w.split('override fun onViewAttachedToWindow(view: android.view.View)', 1)[1]
assert attach.index('fetch()') < attach.index('scheduleUpdate()')
detach = attach.split('override fun onViewDetachedFromWindow', 1)[1]
assert 'main.removeCallbacks(automaticUpdate)' in detach
assert 'locationCleanup?.invoke()' in detach and 'unregister()' in detach
print('PASS source: shared 3h TTL, single attached callback, detach cancellation, request dedup/backoff')
name = w.split('internal fun districtName(', 1)[1].split('private fun locatedName', 1)[0]
assert 'listOfNotNull(subAdminArea, subLocality, locality)' in name
for suffix in ('县', '区', '旗'):
    assert f'it.endsWith("{suffix}")' in name
assert 'districtName(address?.subLocality, address?.subAdminArea, address?.locality)' in w
assert 'URLEncoder.encode("$longitude,$latitude", "UTF-8")' in w
assert '/geo/v2/city/lookup?location=$coordinates&number=1&lang=zh' in w
assert 'listOfNotNull(district, address?.locality)' in w
assert 'listOf(item.getString("name"), item.optString("adm2"), item.optString("adm1"))' in w
assert 'text(name.substringBefore("·").trim()' in w
assert 'locationRequest == locatedRequest' in w and 'key == locatedKey' in w
assert 'prefs.getString("weather_lat", "") == lat && prefs.getString("weather_lon", "") == lon' in w
assert 'manager.removeUpdates(listener)' in w
assert 'main.postDelayed(timeout, 20_000L)' in w
assert 'fetch(force = true)' in w
print('PASS source: district first, coordinate reverse lookup, persisted selection and generation guards')
# Mirror the decision rules in Python; this does not execute the production Kotlin.
def district(sub, admin, city):
    names = [v.strip() for v in (admin, sub, city) if v and v.strip()]
    return next((v for v in names if v.endswith(('县', '区', '旗'))),
                next((v for v in names if v != (city or '').strip()), None))
for values, expected in [
    (('朝阳区', None, '北京市'), '朝阳区'),
    (('某某镇', '长兴县', '湖州市'), '长兴县'),
    ((None, '土默特左旗', '呼和浩特市'), '土默特左旗'),
    ((None, None, '杭州市'), None),
    ((None, None, '海淀区'), '海淀区'),
    (('  西湖区  ', '杭州市', '杭州市'), '西湖区'),
    ((None, None, None), None),
]:
    assert district(*values) == expected
TTL = 3 * 60 * 60_000
now = 50_000_000
def delay(saved, failed=None):
    expiry = saved + TTL if saved is not None else now
    retry = failed + 5 * 60_000 if failed is not None else 0
    return min(TTL, max(60_000, max(expiry, retry) - now))
assert delay(now) == TTL
assert delay(now - 2 * 60 * 60_000) == 60 * 60_000
assert delay(now - TTL) == 60_000
assert delay(None, now) == 5 * 60_000
assert delay(now + TTL * 10) == TTL
print('PASS reference models: 7 district cases; fresh/expired/failure/clock-skew schedule bounds (not Android execution)')
assert 'surround = Color.WHITE' in c
assert 'ColorDrawable(Color.WHITE)' in c
assert 'repeat(12)' in c and 'listBackgrounds.size >= 16' in c
assert 'WeakHashMap<View, WeakReference<TimelineBackground>>' in c
assert 'view.background === installed' in c and 'view.background = installed.original' in c
assert 'view.removeOnAttachStateChangeListener(rowAttachListener)' in c
assert 'listBackgrounds.clear()' in c
assert 'OnScrollListener' not in c
# Do not alter prior card geometry or unrelated avatar refresh.
assert 'const val CONTENT_INSET_DP = 12' in (BASE / 'hooks/items/moments/MomentsCardSettings.kt').read_text()
assert 'checkedAt > 6L * 60L * 60L * 1000L' in (BASE / 'hooks/items/homesidepanel/HomeSidePanelRuntime.kt').read_text()
for path in (ROOT / 'scripts/test_moments_card.py', Path(__file__)):
    ast.parse(path.read_text())
print('PASS source: bounded white timeline ownership/restore, unchanged card geometry and avatar cadence; Python syntax')
resources = Path('/sdcard/Download/wechat8078_decoded/res/layout')
if resources.is_dir():
    for filename in ('emh.xml', 'bl3.xml'):
        p = resources / filename
        tree = ET.fromstring(p.read_text())
        assert any(node.tag == 'com.tencent.mm.plugin.sns.ui.improve.view.ImproveRecyclerView' for node in tree.iter())
        print('PASS native resource', filename, 'SHA256', hashlib.sha256(p.read_bytes()).hexdigest())
else:
    print('SKIP device-decoded native layout evidence (not available)')
print('BOUNDARY: no Kotlin/SDK/Gradle compilation, no installation or device acceptance')
