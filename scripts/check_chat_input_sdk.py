#!/usr/bin/env python3
"""Compile the production input underlay against real Android API types.

Usage: python3 scripts/check_chat_input_sdk.py [path/to/android.jar]
Only project logging/reflection helpers are stubbed; Android classes are not.
"""
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
libs = Path('/tmp/pl-kotlin-libs')
sdk = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('/usr/lib/android-sdk/platforms/android-36/android.jar')
if not sdk.is_file():
    raise SystemExit(f'Android SDK jar missing: {sdk}; pass its path explicitly')
stdlib = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
    folder = Path(td)
    helpers = folder / 'Helpers.kt'
    helpers.write_text('''package t.mma.utils
object HLog { fun i(message: String) {} }
object ChatScrollDiagnostic {fun begin()=0L;fun end(n:String,t:Long){};fun hit(n:String){};fun geometry(a:Int,b:Int,c:Int){}}
object KavaReflector {
 fun findMethodRecursive(type: Class<*>, name: String): java.lang.reflect.Method? = null
 fun invokeOrThrow(method: java.lang.reflect.Method, owner: Any): Any? = null
}
''')
    command = [
        'java', '-Xmx256m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
        '-classpath', f'{stdlib}:{sdk}', '-d', str(folder / 'check.jar'),
        str(helpers),
        str(root / 'app/src/main/java/t/mma/hooks/items/backgroundbeauty/ChatInputMessageUnderlay.kt'),
    ]
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode:
        print(result.stdout, end='')
        print(result.stderr, end='', file=sys.stderr)
        raise SystemExit(result.returncode)
print(f'PASS: production ChatInputMessageUnderlay compiles against real Android SDK: {sdk}')
