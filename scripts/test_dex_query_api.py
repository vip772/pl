"""Compile the new detached-result DexKit query API against the real ABI."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
libs = Path('/tmp/pl-kotlin-libs')
stdlib = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
dexkit = Path('/tmp/pl-dexkit.jar')

with tempfile.TemporaryDirectory() as td:
    p = Path(td)
    classes = p / 'classes'
    classes.mkdir()
    (p / 'Log.kt').write_text(
        'package t.mma.utils\n'
        'object HLog { fun d(s:String){}; fun i(s:String){}; fun w(s:String){}; '
        'fun e(s:String,t:Throwable?=null){} }\n'
        'object KavaReflector { fun loadClass(n:String,l:ClassLoader):Class<*>?=null }\n'
    )
    (p / 'Handler.kt').write_text(
        'package android.os\n'
        'class Looper { companion object { fun getMainLooper()=Looper() } }\n'
        'class Handler(l:Looper) { fun removeCallbacks(r:Runnable){}; '
        'fun postDelayed(r:Runnable,d:Long)=true }\n'
    )
    cp = f'{classes}:{stdlib}:{dexkit}'
    compiler = [
        'java', '-Xmx256m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-nowarn', '-no-stdlib',
        '-no-reflect', '-classpath', cp, '-d', str(classes)
    ]
    subprocess.run(
        compiler + [
            str(root / 'app/src/main/java/t/mma/hooks/core/DexInstallScheduler.kt'),
            str(root / 'app/src/main/java/t/mma/hooks/core/DexKitQueryApi.kt'),
            str(root / 'app/src/main/java/t/mma/dexkit/DexKitBridgeManager.java'),
            str(p / 'Log.kt'), str(p / 'Handler.kt')
        ],
        check=True,
        timeout=60,
    )
    print('PASS new detached-result DexKit query API compiles with real DexKit ABI')