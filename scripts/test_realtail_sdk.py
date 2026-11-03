"""Compile full real-tail production runtime against Android SDK and cached ABI; no Gradle/device."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs')
abi=r/'app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes'
cp=[abi,r/'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes',Path('/usr/lib/android-sdk/platforms/android-36/android.jar')]
assert all(p.exists() for p in cp),'Existing SDK/ABI required; do not invoke Gradle'
cp+=list(Path('/root/.gradle/caches/modules-2/files-2.1').glob('*/*/*/*/*.jar'))+list(Path('/root/.gradle/caches').glob('*/transforms/*/transformed/*/jars/classes.jar'))+list(libs.glob('*.jar'))
sources=[r/'app/src/main/java/t/mma/hooks/core'/n for n in ['FeatureOwnership.kt','FeatureResourceScope.kt','PreferenceRuntimeOwner.kt','BaseFeature.kt','Feature.kt','FeatureContext.kt','HookRegistry.kt','DexInstallScheduler.kt']]
sources += list((r/'app/src/main/java/t/mma/hooks/items/realtail').glob('*.kt'))
sources += [r/'app/src/main/java/t/mma/dexkit/DexKitBridgeManager.java']
sources += [r/'app/src/main/java'/n for n in ['t/mma/hooks/api/runtime/WeChatTaskApi.kt','t/mma/hooks/api/net/WeChatNetworkApi.kt','t/mma/hooks/api/media/WeChatInternalServices.kt','t/mma/hooks/items/chatname/NativeGroupNicknameGate.kt','t/mma/hooks/items/chatname/ChatNameDecorationLayout.kt']]
with tempfile.TemporaryDirectory(prefix='pl-realtail-sdk-') as td:
 # Settings aggregation is unrelated to the badge runtime; explicitly synthetic boundary.
 toggle=Path(td)/'FeatureToggleRegistry.kt'
 toggle.write_text('package t.mma.ui'+chr(10)+'object FeatureToggleRegistry {fun isEnabled(c:android.content.Context,id:String)=true}')
 sources.append(toggle)
 cmd=['java','-Xmx384m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-jvm-target','17','-Xfriend-paths='+str(abi),'-nowarn','-no-stdlib','-no-reflect','-classpath',':'.join(map(str,cp)),'-d',td,*map(str,sources)]
 result=subprocess.run(cmd,cwd=r,capture_output=True,text=True,timeout=120)
 print(result.stdout,end='');print(result.stderr,end='')
 if result.returncode:raise SystemExit(result.returncode)
print('PASS full real-tail production runtime SDK + cached ABI compilation; synthetic settings-toggle ABI only (not Android/R8/device)')
