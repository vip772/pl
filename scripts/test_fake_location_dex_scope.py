"""Source-boundary regression checks; not an Android/native runtime test."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/t/mma/hooks/items/fakelocation/FakeLocationFeature.kt').read_text()
start = source.index('context.dexKitManager().withCurrentBridge { bridge ->')
opening = source.index('{', start)
depth = 1
end = opening + 1
while depth:
    depth += (source[end] == '{') - (source[end] == '}')
    end += 1
scope = source[opening:end]
assert 'bridge.findMethod(' in scope
assert 'usingEqStrings(*anchors)' in scope
assert ').mapNotNull { data ->' in scope
assert 'data.getMethodInstance(context.hostClassLoader())' in scope
assert '?.takeIf(::isLocationCallback)' in scope
assert 'context.dexKitBridge()' not in source
assert source.count('.findMethod(') == 1
assert source.index('DexMethodCache.loadList(') < start
assert source.index('methods.addAll(candidates)', end) > end
assert source.index('DexMethodCache.saveList(', end) > end
assert 'DexMethodCache.loadListCrossProcess(' in source
assert 'val methods = LinkedHashSet<Method>()' in source
assert 'methods.distinctBy { it.toGenericString() }' in source
assert source.count('arrayOf("MicroMsg.') == 3
print('PASS fake location source boundary: eager conversion inside manager scope; cache fast path, anchors, deduplication and cross-process cache preserved')
