#!/usr/bin/env python3
"""Mutation harness: revert one ownership guarantee at a time and prove the suite catches it."""
from pathlib import Path
import subprocess
import sys
import shutil

ROOT = Path('/root/pl')
BASE = Path('/tmp/hchat-mut')
HCHAT = ROOT / 'app/src/main/java/t/mma/hooks/items/hchatextra'
FEATURE = HCHAT / 'HchatExtraFeature.kt'
LABELS = HCHAT / 'ForwardedRecordTypeLabels.kt'
PENDING = HCHAT / 'MessageDetailsPendingLayoutObserver.kt'
SUITE = ROOT / 'scripts/test_hchat_extra_scope.py'

MUTATIONS = [
    (
        'pending: 去掉 posted 归位（投递被拒后永久早退）',
        PENDING,
        '            if (!accepted) posted = false\n',
        '            if (false && !accepted) posted = false\n',
        'phase C',
    ),
    (
        'labels: install() 永远报告成功（失败态被记成已安装）',
        LABELS,
        '            installed = handle != null\n            installed\n',
        '            installed = true\n            true\n',
        'phase B',
    ),
    (
        'feature: 独占 Hook 在登记表清理前就关闭',
        FEATURE,
        '        releaseSafely("独占Hook") { ownedHooks.close() }\n',
        '',
        'phase D',
    ),
    (
        'feature: 尾部修复超时投递不再判返回值',
        FEATURE,
        '        if (!list.postDelayed(timeout, 1550L)) finish()\n',
        '        list.postDelayed(timeout, 1550L)\n',
        'phase D',
    ),
    (
        'feature: 头像详情间距恢复去掉逐项隔离',
        FEATURE,
        '                try {\n                    restoreAvatarDetailsSpacing(parent, spacing)\n                } catch (error: Throwable) {\n',
        '                run {\n                    restoreAvatarDetailsSpacing(parent, spacing)\n                }\n                if (false) {\n                }\n',
        'phase D',
    ),
    (
        'labels: 摘除投递不再判返回值',
        LABELS,
        '        } else if (!detachHandler.post(detach)) {\n',
        '        } else if (detachHandler.post(detach) && false) {\n',
        'phase D',
    ),
    (
        'labels: destroy() 先关 main 再卸句柄',
        LABELS,
        '        ownedHooks.close()\n        // 撤销本代全部未执行投递；此后本类不再接受新投递。\n        main.close()\n',
        '        main.close()\n        ownedHooks.close()\n',
        'phase B',
    ),
    (
        'runtime: close() 先销毁 Hooker 再停重试',
        FEATURE,
        '        snapshot.second.forEach(::closeTicket)\n        snapshot.first?.let(::destroyHooker)\n',
        '        snapshot.first?.let(::destroyHooker)\n        snapshot.second.forEach(::closeTicket)\n',
        'phase D',
    ),
]


def restore():
    for name in ('HchatExtraFeature.kt', 'ForwardedRecordTypeLabels.kt', 'MessageDetailsPendingLayoutObserver.kt'):
        shutil.copy2(BASE / name, HCHAT / name)


def run():
    proc = subprocess.run([sys.executable, str(SUITE)], capture_output=True, text=True)
    return proc.returncode, (proc.stdout + proc.stderr)


def main():
    restore()
    code, out = run()
    print('BASELINE exit=' + str(code))
    if code != 0:
        print(out[-3000:])
        raise SystemExit('baseline must be green before mutating')
    caught = 0
    for name, path, old, new, expect in MUTATIONS:
        text = path.read_text()
        if text.count(old) != 1:
            print('SKIP ' + name + ': anchor count=' + str(text.count(old)))
            restore()
            continue
        path.write_text(text.replace(old, new))
        code, out = run()
        restore()
        hit = code != 0 and (expect in out or 'kompile failed' in out)
        caught += 1 if hit else 0
        flag = 'CAUGHT' if hit else 'MISSED'
        marker = ''
        for line in out.splitlines():
            if (line.startswith(('LEAK', 'ORDER', 'MISSING', 'UNCHECKED'))
                    or 'AssertionError' in line or 'Exception in thread' in line):
                marker = line.strip()[:160]
                break
        print(flag + ' [' + expect + '] ' + name + ' exit=' + str(code) + ' :: ' + marker)
    print('mutations caught: ' + str(caught) + '/' + str(len(MUTATIONS)))
    code, out = run()
    print('FINAL exit=' + str(code) + ' (restored)')
    if code != 0:
        print(out[-2000:])
        raise SystemExit('restore verification failed')


if __name__ == '__main__':
    main()
