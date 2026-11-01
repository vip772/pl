"""Whole production SkipMiniProgramVideoAdsFeature on a synthetic host; not device acceptance."""
from pathlib import Path
import subprocess, tempfile

r = Path(__file__).resolve().parents[1]
prod = r / 'app/src/main/java/t/mma/hooks/items/miniprogramvideoad/SkipMiniProgramVideoAdsFeature.kt'
settings = r / 'app/src/main/java/t/mma/hooks/items/miniprogramvideoad/SkipMiniProgramVideoAdsSettings.kt'
s = prod.read_text()

# Structural constraints that must hold in the production source.
assert 'installedLoaders' not in s
assert 'DexInstallScheduler.schedule(' not in s
assert 'fun destroyProcessHooks()' in s
assert 'val owner = appBrandHooker ?: MiniProgramVideoAdsHooker(' in s
assert 'if (appBrandLoader !== classLoader) destroyProcessHooks()' in s
assert 'existing.isInstalled(classLoader)' in s
assert 'resources.register { owner.close() }' in s
assert 'resources.register { installation.close() }' in s
assert 'installation.schedule { owner.install() }' in s
assert 'PreferenceRuntimeOwner(' in s
assert 'trackResource { runtime.close() }' in s
assert 'hook = HookRegistry.get().hook(' in s
assert 'runCatching { HookRegistry.get().unhook(oldHook) }' in s
assert 'resources.register { context.eventBus().unsubscribe(subscription) }' in s
# Device-side protocol constants and payload rewrite must stay untouched.
assert 'const val BRIDGE_CLASS =\n            "com.tencent.mm.appbrand.commonjni.AppBrandJsBridgeBinding"' in s
assert 'EVENT_VIDEO_TIME_UPDATE = "onVideoTimeUpdate"' in s
assert 'json.put("position", 60)' in s
assert 'json.put("duration", 1)' in s
assert 'KavaReflector.findMethod(\n                bridgeClass,\n                "subscribeHandler",' in s
assert 'String::class.java,\n                String::class.java,\n                Integer.TYPE,\n                String::class.java' in s

entry = (r / 'app/src/main/java/t/mma/ModuleEntry.java').read_text()
assert 'SkipMiniProgramVideoAdsFeature.destroyProcessHooks();' in entry
assert 'SkipMiniProgramVideoAdsFeature.installAppBrandProcessHook(context, classLoader)' in entry

manager = (r / 'app/src/main/java/t/mma/hooks/core/FeatureManager.kt').read_text()
assert 'SkipMiniProgramVideoAdsFeature.destroyProcessHooks()' in manager

libs = Path('/tmp/pl-kotlin-libs')
std = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
root = r / 'scripts/tests/miniprogramvideoad'
core = r / 'app/src/main/java/t/mma/hooks/core'
hosts = sorted((root / 'host').glob('*.kt'))
with tempfile.TemporaryDirectory() as td:
    p = Path(td)
    cmd = [
        'java', '-Xmx320m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
        '-no-stdlib', '-no-reflect', '-nowarn',
        '-classpath', str(std), '-d', str(p / 'test.jar'),
        str(root / 'RegressionMain.kt'), str(prod), str(settings),
        str(core / 'PreferenceRuntimeOwner.kt'), str(core / 'FeatureResourceScope.kt'),
    ]
    cmd += [str(h) for h in hosts]
    compiled = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
    if compiled.returncode:
        print(compiled.stdout)
        print(compiled.stderr)
        raise SystemExit(compiled.returncode)
    ran = subprocess.run(
        ['java', '-cp', str(p / 'test.jar') + ':' + str(std),
         't.mma.hooks.items.miniprogramvideoad.RegressionMainKt'],
        capture_output=True, text=True, timeout=180
    )
    print(ran.stdout)
    print(ran.stderr)
    raise SystemExit(ran.returncode)