"""Whole production SkipGlobalMiniProgramSplashAdsFeature on a synthetic host; not device acceptance."""
from pathlib import Path
import subprocess, tempfile

r = Path(__file__).resolve().parents[1]
prod = r / 'app/src/main/java/t/mma/hooks/items/miniprogramsplashad/SkipGlobalMiniProgramSplashAdsFeature.kt'
settings = r / 'app/src/main/java/t/mma/hooks/items/miniprogramsplashad/SkipGlobalMiniProgramSplashAdsSettings.kt'
s = prod.read_text()

# Main process: shared schedule and the unowned splash hook are gone.
assert 'installedLoaders' not in s
assert 'DexInstallScheduler.schedule(' not in s
assert 'scheduleAppBrandProcessHook' not in s
assert 'fun destroyProcessHooks()' in s
assert 'val owner = appBrandHooker ?: MiniProgramSplashAdProcessOwner(' in s
assert 'if (appBrandLoader !== classLoader) destroyProcessHooks()' in s
assert 'resources.register { owner.close() }' in s
assert 'resources.register { installation.close() }' in s
assert 'installation.schedule { owner.install() }' in s
assert 'PreferenceRuntimeOwner(' in s
assert 'trackResource { runtime.close() }' in s
assert 'hook = HookRegistry.get().hook(method, object : XC_MethodHook()' in s
assert 'runCatching { HookRegistry.get().unhook(oldHook) }' in s
assert 'resources.register { context.eventBus().unsubscribe(subscription) }' in s
# The appbrand owner holds its own retry ticket instead of the shared schedule.
assert 'fun requestInstall(): Boolean' in s
assert 'private fun installWhenDescriptorReady(): Boolean' in s
assert 'DexInstallScheduler.installation(' in s
assert 'pending.close()' in s
# DexKit boundary and locator anchors must stay byte-identical.
assert 'usingEqStrings(SPLASH_AD_LOG_TAG, SPLASH_AD_RESULT_LOG)' in s
assert 'SPLASH_AD_LOG_TAG = "MicroMsg.AppBrandAdUtils[AppBrandSplashAd]"' in s
assert 'SPLASH_AD_RESULT_LOG = "isAdContact, appId:%s, canShowAd:%s"' in s
assert 'data.getMethodInstance(context.hostClassLoader())' in s
assert 'CACHE_SPLASH_AD_CHECK_METHOD = "splash_ad_check_method"' in s
assert s.count('.findMethod(') == 1

entry = (r / 'app/src/main/java/t/mma/ModuleEntry.java').read_text()
assert 'SkipGlobalMiniProgramSplashAdsFeature.destroyProcessHooks();' in entry
assert 'SkipGlobalMiniProgramSplashAdsFeature.installAppBrandProcessHook(context, classLoader)' in entry
assert 'SkipGlobalMiniProgramSplashAdsFeature.scheduleAppBrandProcessHook(' not in entry

manager = (r / 'app/src/main/java/t/mma/hooks/core/FeatureManager.kt').read_text()
assert 'SkipGlobalMiniProgramSplashAdsFeature.destroyProcessHooks()' in manager

libs = Path('/tmp/pl-kotlin-libs')
std = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
root = r / 'scripts/tests/miniprogramadsplash'
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
         't.mma.hooks.items.miniprogramsplashad.RegressionMainKt'],
        capture_output=True, text=True, timeout=180
    )
    print(ran.stdout)
    print(ran.stderr)
    raise SystemExit(ran.returncode)