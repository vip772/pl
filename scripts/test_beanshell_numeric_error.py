"""Compile production BeanShell with SDK dependencies and run synthetic JVM regressions.
No Gradle, APK installation, real plugins, or device acceptance is performed.
"""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ.get('ANDROID_JAR', '/usr/lib/android-sdk/platforms/android-37.0/android.jar'))
DX_ROOT = Path.home() / '.gradle/caches/modules-2/files-2.1/com.jakewharton.android.repackaged/dalvik-dx'
DX = Path(os.environ['DALVIK_DX_JAR']) if 'DALVIK_DX_JAR' in os.environ else next(DX_ROOT.glob('*/*/*.jar'), None)
if not SDK.is_file() or DX is None or not DX.is_file():
    raise SystemExit('BLOCKED: set ANDROID_JAR and DALVIK_DX_JAR to existing SDK/dx dependency files')
with tempfile.TemporaryDirectory(prefix='pl-numeric-error-') as directory:
    classes = Path(directory) / 'classes'
    classes.mkdir()
    sources = sorted((ROOT / 'app/src/main/java/bsh').rglob('*.java'))
    fixture = ROOT / 'scripts/tests/beanshell_numeric_error/NumericErrorTest.java'
    subprocess.run(['javac', '-encoding', 'UTF-8', '--release', '17', '-cp',
                    os.pathsep.join(map(str, [SDK, DX])), '-d', str(classes),
                    *map(str, sources), str(fixture)], check=True, timeout=120)
    print(f'PASS production BeanShell SDK targeted compile: {len(sources)} sources; NOT Android/R8 build', flush=True)
    subprocess.run(['java', '-ea',
                    '--add-opens=java.base/java.lang=ALL-UNNAMED',
                    '--add-opens=java.base/java.util=ALL-UNNAMED',
                    '--add-opens=java.base/java.math=ALL-UNNAMED',
                    '-cp', str(classes), 'NumericErrorTest'], check=True, timeout=45)