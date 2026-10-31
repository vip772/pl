"""Source-boundary regression check for the splash-ad locator."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/t/mma/hooks/items/miniprogramsplashad/SkipGlobalMiniProgramSplashAdsFeature.kt').read_text()
start = source.index('context.dexKitManager().withCurrentBridge { bridge ->')
opening = source.index('{', start)
depth = 1
end = opening + 1
while depth:
    depth += (source[end] == '{') - (source[end] == '}')
    end += 1
scope = source[opening:end]
assert 'bridge.findMethod(' in scope
assert 'usingEqStrings(SPLASH_AD_LOG_TAG, SPLASH_AD_RESULT_LOG)' in scope
assert 'data.getMethodInstance(context.hostClassLoader())' in scope
assert 'isConstructor' in scope and 'isSplashAdCheckMethod' in scope
assert 'distinctBy { it.toGenericString() }' in scope
assert 'context.dexKitBridge()' not in source
assert source.count('.findMethod(') == 1
assert source.index('DexMethodCache.load(') < start
assert source.index('DexMethodCache.save(', end) > end
assert source.index('DexMethodCache.clear(', end) > end
print('PASS splash ad source boundary: native result conversion and filtering remain inside manager scope; cache semantics preserved')