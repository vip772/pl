"""Verify bounded access-order avatar cache source contract."""
from pathlib import Path
p=Path(__file__).resolve().parents[1]/'app/src/main/java/t/mma/ui/miuix/MiuixSettingsPage.kt'
s=p.read_text();a=s.index('internal object AvatarMemoryCache');b=s.index('internal object WeChatAvatarPathCache',a);block=s[a:b]
assert 'MAX_ENTRIES = 64' in block
assert 'LinkedHashMap<String, ImageBitmap?>(MAX_ENTRIES, 0.75f, true)' in block
assert 'removeEldestEntry' in block
assert 'bitmaps[value]' in block
print('PASS avatar cache: access-order LRU, max 64 entries, null-result caching preserved')
