"""Whole production FakeMiniProgramBaseLib file on a synthetic host; not device acceptance."""
from pathlib import Path
import subprocess, tempfile

r = Path(__file__).resolve().parents[1]
prod = r / 'app/src/main/java/t/mma/hooks/items/miniprogrambaselib/FakeMiniProgramBaseLibFeature.kt'
settings = r / 'app/src/main/java/t/mma/hooks/items/miniprogrambaselib/FakeMiniProgramBaseLibSettings.kt'
s = prod.read_text()

# Structural constraints that must hold in the production source.
assert 'DexInstallScheduler.schedule(' not in s
assert 'appBrandProcessHookInstalled' not in s
assert 'fun destroyProcessHooks()' in s
assert 'val owner = appBrandHooker ?: MiniProgramAppBrandHooker(' in s
assert 'if (appBrandLoader !== classLoader) destroyProcessHooks()' in s
assert 'resources.register { owner.close() }' in s
assert 'resources.register { installation.close() }' in s
assert 'PreferenceRuntimeOwner(' in s
assert 'hook = HookRegistry.get().hook(' in s
assert 'runCatching { HookRegistry.get().unhook(oldHook) }' in s
# The protocol constants are device semantics and must stay untouched.
assert 'LIB_VERSION_ARG_INDEX = 6' in s
assert 'SPOOFED_LIB_VERSION = 9999' in s
assert 'private const val PRIVATE_OPEN_URL_API = "private_openUrl"' in s

libs = Path('/tmp/pl-kotlin-libs')
std = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
root = r / 'scripts/tests/miniprogram_baselib'
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
    compiled = subprocess.run(cmd, capture_output=True, text=True, timeout=240)
    if compiled.returncode:
        print(compiled.stdout)
        print(compiled.stderr)
        raise SystemExit(compiled.returncode)
    ran = subprocess.run(
        ['java', '-cp', str(p / 'test.jar') + ':' + str(std),
         't.mma.hooks.items.miniprogrambaselib.RegressionMainKt'],
        capture_output=True, text=True, timeout=120
    )
    print(ran.stdout)
    print(ran.stderr)
    raise SystemExit(ran.returncode)
