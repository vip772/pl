"""Mutation harness for scripts/test_script_plugin_scope.py.

Each mutant rewrites one production file in a copy of the repository, then runs the
phase suite against that copy. A mutant is caught when the suite fails; an uncaught
mutant means the suite is blind to that regression and must be strengthened.

The working tree is never touched: every mutant is applied inside a throwaway copy
under the system temp directory, and the production tree is restored from a pristine
snapshot taken before the first mutant runs.

None of this is an Android/R8 build and none of it touches a real WeChat session.
"""
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]

FEATURE = 'app/src/main/java/t/mma/hooks/items/script/ScriptPluginFeature.kt'
NEWFRIEND = 'app/src/main/java/t/mma/hooks/items/script/ScriptNewFriendHook.kt'
MESSAGE = 'app/src/main/java/t/mma/hooks/items/script/ScriptMessageHook.kt'
MEMBER = 'app/src/main/java/t/mma/hooks/items/script/ScriptMemberChangeHook.kt'
AUTOREPLY = 'app/src/main/java/t/mma/hooks/items/autoreply/AutoReplyFeature.kt'
LEASE = 'app/src/main/java/t/mma/hooks/core/SharedHookLease.kt'

COPY_DIRS = ('app/src', 'scripts')

# Long runs get their own log file so a dropped terminal never loses the verdict.
LOG_DIR = Path('/tmp/pl-stage1-20261103-scriptplugin')


# Each mutant: (name, relative path, old fragment, new fragment, expected failure hint).
MUTANTS = [
    (
        'lease releases on every owner departure',
        LEASE,
        'if (entry.owners.isNotEmpty() || !entry.active) return',
        'if (!entry.active) return',
        'shared hook lease',
    ),
    (
        'lease keeps a ghost owner after a failed install',
        LEASE,
        'if (!installed) {\n                entry.owners.remove(owner)\n                return false\n            }',
        'if (!installed) {\n                return false\n            }',
        'shared hook lease',
    ),
    (
        'lease close leaves the host hook installed',
        LEASE,
        'pending.forEach(::runUninstall)',
        'Unit',
        'shared hook lease',
    ),
    (
        'lease leaves the hook installed when closed during acquire',
        LEASE,
        'entry.owners.clear()\n                entry.active = false\n                runUninstall(entry)\n                return false',
        'entry.owners.clear()\n                entry.active = false\n                return false',
        'shared hook lease',
    ),
    (
        'message hook drops the listener before the handle',
        MESSAGE,
        'hostSubscriptions += AutoCloseable {\n            changeApi.unsubscribe(listener)\n            subscription.unsubscribe()\n        }',
        'hostSubscriptions += AutoCloseable {\n            changeApi.unsubscribe(listener)\n        }',
        'script hook subscriptions',
    ),
    (
        'message hook uninstall forgets to clear the store',
        MESSAGE,
        'val pending = hostSubscriptions.toList().also { hostSubscriptions.clear() }',
        'val pending = hostSubscriptions.toList()',
        'script hook subscriptions',
    ),
    (
        'member hook uninstall keeps stale snapshots',
        MEMBER,
        'memberSnapshots.clear()\n        recentEvents.clear()',
        'recentEvents.clear()',
        'script hook subscriptions',
    ),
    (
        'member hook uninstall drops the observe handle',
        MEMBER,
        'hostSubscriptions += AutoCloseable {\n                    observeApi.unsubscribe(observeListener)\n                    observeSubscription.unsubscribe()\n                }',
        'hostSubscriptions += AutoCloseable {\n                    observeApi.unsubscribe(observeListener)\n                }',
        'script hook subscriptions',
    ),
    (
        'generation close runs the runtime before the subscriptions',
        FEATURE,
        'uninstallOwnedHooks()\n        try {\n            ScriptPluginRuntime.close()',
        'try {\n            ScriptPluginRuntime.close()',
        'script hook generation',
    ),
    (
        'generation close releases the lease before the runtime',
        FEATURE,
        'if (taken.second) {\n            try {\n                ScriptNewFriendHook.release(ScriptNewFriendHook.OWNER_SCRIPT)',
        'if (taken.second) {\n            try {\n                ScriptNewFriendHook.release(ScriptNewFriendHook.OWNER_SCRIPT)\n                ScriptPluginRuntime.close()',
        'script hook generation',
    ),
    (
        'generation keeps its tickets on close',
        FEATURE,
        'taken.first.forEach(::closeTicket)',
        'Unit',
        'script hook generation',
    ),
    (
        'generation installs hooks without the WARMUP ticket',
        FEATURE,
        'DexInstallScheduler.installation("$ID:message", "$NAME-消息监听", DexInstallScheduler.Stage.WARMUP)',
        'DexInstallScheduler.installation("$ID:message", "$NAME-消息监听", DexInstallScheduler.Stage.EARLY)',
        'script hook generation',
    ),
    (
        'generation closes a runtime it never started',
        FEATURE,
        'if (!taken.third) return',
        'Unit',
        'script hook generation',
    ),
    (
        'generation forgets to release the lease while closed during acquire',
        FEATURE,
        'ScriptNewFriendHook.release(ScriptNewFriendHook.OWNER_SCRIPT)\n            return false',
        'return false',
        'script hook generation',
    ),
    (
        'friend hook falls back to a process-wide installed flag',
        NEWFRIEND,
        'fun acquire(context: FeatureContext, owner: String): Boolean {',
        'private var installed = false\n\n    fun acquire(context: FeatureContext, owner: String): Boolean {',
        'source invariants',
    ),
    (
        'auto reply goes back to the global friend hook install',
        AUTOREPLY,
        'ScriptNewFriendHook.acquire(context, ScriptNewFriendHook.OWNER_AUTO_REPLY)',
        'ScriptNewFriendHook.install(context)',
        'source invariants',
    ),
    (
        'auto reply forgets to return its lease',
        AUTOREPLY,
        'resources.register { ScriptNewFriendHook.release(ScriptNewFriendHook.OWNER_AUTO_REPLY) }',
        'Unit',
        'source invariants',
    ),
    (
        'generation stops using a ticket for the menu hook',
        FEATURE,
        'DexInstallScheduler.installation("$ID:menus", "$NAME-菜单", DexInstallScheduler.Stage.WARMUP)',
        'DexInstallScheduler.installation("shared:menus", "$NAME-菜单", DexInstallScheduler.Stage.WARMUP)',
        'source invariants',
    ),
    (
        'message hook subscribes with a bare lambda',
        MESSAGE,
        'val subscription = changeApi.subscribe(listener)',
        'val subscription = changeApi.subscribe { change -> val unused = change; Unit }',
        'script hook subscriptions',
    ),
    (
        "friend hook builds the ampersand via Char.single()",
        NEWFRIEND,
        "return value\n            .replace(\"&lt;\", \"<\")",
        "val amp = '&'.single()\n            .replace(amp + \"lt;\", \"<\")",
        "real compile",
    ),
]


def build_copy(target: Path):
    for name in COPY_DIRS:
        shutil.copytree(ROOT / name, target / name)


def apply_mutant(target: Path, relative: str, old: str, new: str):
    path = target / relative
    text = path.read_text(encoding='utf-8')
    count = text.count(old)
    if count != 1:
        return False, 'anchor matched ' + str(count) + ' times in ' + relative
    path.write_text(text.replace(old, new, 1), encoding='utf-8')
    return True, ''


def run_suite(target: Path):
    return subprocess.run(
        [sys.executable, 'scripts/test_script_plugin_scope.py'],
        cwd=str(target), capture_output=True, text=True, timeout=1800,
    )


def log(line: str):
    """Flush every line: a killed run must still show how far it got."""
    sys.stdout.write(line + '\n')
    sys.stdout.flush()
    try:
        LOG_DIR.mkdir(parents=True, exist_ok=True)
        with (LOG_DIR / 'mutate.log').open('a', encoding='utf-8') as handle:
            handle.write(line + '\n')
    except OSError:
        pass


def main():
    if not (ROOT / 'scripts/test_script_plugin_scope.py').is_file():
        raise SystemExit('missing scripts/test_script_plugin_scope.py')

    log('=== baseline ' + str(len(MUTANTS)) + ' mutants ===')
    baseline = run_suite(ROOT)
    log('BASELINE exit=' + str(baseline.returncode))
    if baseline.returncode != 0:
        sys.stdout.write(baseline.stdout)
        sys.stderr.write(baseline.stderr)
        raise SystemExit('baseline suite is not green; refuse to mutate')

    caught = 0
    missed = []
    with tempfile.TemporaryDirectory(prefix='mutate-script-plugin-') as raw:
        base = Path(raw)
        for index, (name, relative, old, new, hint) in enumerate(MUTANTS, 1):
            target = base / ('m' + str(index))
            target.mkdir(parents=True)
            build_copy(target)
            applied, error = apply_mutant(target, relative, old, new)
            if not applied:
                missed.append((name, error))
                log('SKIP ' + str(index) + ': ' + name + ' -> ' + error)
                shutil.rmtree(target, ignore_errors=True)
                continue
            result = run_suite(target)
            shutil.rmtree(target, ignore_errors=True)
            if result.returncode == 0:
                missed.append((name, 'suite stayed green'))
                log('MISS ' + str(index) + ': ' + name)
            else:
                caught += 1
                detail = ''
                for stream in (result.stdout, result.stderr):
                    for line in stream.splitlines():
                        if ('Exception' in line or 'AssertionError' in line or 'MISSING' in line
                                or 'error:' in line or 'kompile failed' in line or 'compile failed' in line):
                            detail = line.strip()[:200]
                            break
                    if detail:
                        break
                log('CAUGHT ' + str(index) + ': ' + name + ' :: ' + detail)

    total = len(MUTANTS)
    log('mutations caught: ' + str(caught) + '/' + str(total))
    if missed:
        for name, reason in missed:
            log('uncaught: ' + name + ' (' + reason + ')')
        raise SystemExit('mutation coverage incomplete: ' + str(len(missed)) + ' uncaught')
    log('ALL MUTANTS CAUGHT')


if __name__ == '__main__':
    main()
