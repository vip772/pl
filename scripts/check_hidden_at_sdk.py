"""Targeted compilation of hidden-mention production sources against existing SDK/ABI. No Gradle."""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[1]
libs=Path('/tmp/pl-kotlin-libs')
abi=r/'app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes'
cp=[abi,r/'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes',Path('/usr/lib/android-sdk/platforms/android-36/android.jar')]
assert all(p.exists() for p in cp),'SDK/existing ABI unavailable; do not build APK'
cp+=list(Path('/root/.gradle/caches/modules-2/files-2.1').glob('*/*/*/*/*.jar'))
cp+=list(Path('/root/.gradle/caches').glob('*/transforms/*/transformed/*/jars/classes.jar'))
cp+=list(libs.glob('*.jar'))
src=r/'app/src/main/java/t/mma'
sources=list((src/'hooks/items/hiddenat').glob('*.kt'))
sources+=list((src/'hooks/items/mentionall').glob('*.kt'))
sources+=[src/'hooks/api/message/OutgoingAtMessageHook.kt']
sources += [src/'hooks/core'/n for n in ['BaseFeature.kt','Feature.kt','FeatureContext.kt','FeatureResourceScope.kt','PreferenceRuntimeOwner.kt','DexInstallScheduler.kt','HookRegistry.kt','FeatureOwnership.kt']]
sources += [src/'dexkit/DexKitBridgeManager.java', src/'dexkit/DexMethodCache.kt']
with tempfile.TemporaryDirectory(prefix='hidden-at-sdk-') as td:
 stub=Path(td)/'FeatureToggleRegistry.kt'
 stub.write_text('package t.mma.ui\nobject FeatureToggleRegistry {fun isEnabled(c:android.content.Context,id:String)=true}\n')
 sources.append(stub)
 result=subprocess.run(['java','-Xmx384m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-jvm-target','17','-nowarn','-no-stdlib','-no-reflect','-Xfriend-paths='+str(abi),'-classpath',':'.join(map(str,cp)),'-d',td,*map(str,sources)],capture_output=True,text=True,timeout=60)
 print(result.stdout,end='');print(result.stderr,end='')
 result.check_returncode()
print('PASS hidden-at production SDK compile with shared outgoing hook and mention-all compatibility; no Gradle/APK/device sends')