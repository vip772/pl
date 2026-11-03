"""Type-check the two notify components changed for resource-lifecycle item 26.

Compiles the real RedPacketAnnouncer.java / RedPacketNotifier.java against the Android SDK and
the existing cached module ABI. The only synthetic boundary is CustomFriendAvatarStore, which is
declared here as a one-method Java stub because its Kotlin body depends on modules outside this
scope; the call site being checked is unchanged by that stub.

This is not an Android/R8 build and it never invokes Gradle.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
LIBS = Path('/tmp/pl-kotlin-libs')
ANDROID_JAR = Path('/usr/lib/android-sdk/platforms/android-36/android.jar')
KOTLIN_ABI = ROOT / 'app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes'
JAVA_ABI = ROOT / 'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes'
SOURCES = [
    ROOT / 'app/src/main/java/t/mma/hooks/items/payment/notify/RedPacketAnnouncer.java',
    ROOT / 'app/src/main/java/t/mma/hooks/items/payment/notify/RedPacketNotifier.java',
]
STD = next(LIBS.glob('kotlin-stdlib-[0-9]*.jar'))

AVATAR_STUB = '''package t.mma.hooks.items.customfriendavatar;
public final class CustomFriendAvatarStore {
    public static android.graphics.Bitmap loadNotificationBitmap(android.content.Context context, String wxid) {
        return null;
    }
}
'''


def run() -> None:
    missing = [str(p) for p in [ANDROID_JAR, KOTLIN_ABI, JAVA_ABI, *SOURCES] if not p.exists()]
    assert not missing, '缺少既有编译产物/源码（不得因此调用 Gradle）: ' + ', '.join(missing)
    with tempfile.TemporaryDirectory(prefix='pl-notify-abi-') as td:
        path = Path(td)
        stub = path / 't/mma/hooks/items/customfriendavatar/CustomFriendAvatarStore.java'
        stub.parent.mkdir(parents=True, exist_ok=True)
        stub.write_text(AVATAR_STUB)
        classpath = ':'.join(str(p) for p in [ANDROID_JAR, KOTLIN_ABI, JAVA_ABI, STD])
        result = subprocess.run(
            ['javac', '-nowarn', '-source', '17', '-target', '17', '-encoding', 'UTF-8',
             '-d', str(path / 'out'), '-classpath', classpath, str(stub), *map(str, SOURCES)],
            capture_output=True, text=True, timeout=300
        )
        print(result.stdout, end='')
        print(result.stderr, end='')
        if result.returncode:
            raise SystemExit(result.returncode)
    print('PASS notify components type-check against Android SDK + cached module ABI '
          '(synthetic CustomFriendAvatarStore ABI only; not Android/R8/device)')


if __name__ == '__main__':
    run()