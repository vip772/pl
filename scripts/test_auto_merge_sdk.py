"""Compile auto-merge production sources against SDK and existing dependency ABI, without Gradle."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');s=r/'app/src/main/java/t/mma'
abi=r/'app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes'
cp=[abi,r/'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes',Path('/usr/lib/android-sdk/platforms/android-36/android.jar')]
cp+=list(Path('/root/.gradle/caches/modules-2/files-2.1').glob('*/*/*/*/*.jar'))+list(Path('/root/.gradle/caches').glob('*/transforms/*/transformed/*/jars/classes.jar'))+list(libs.glob('*.jar'))
sources=list((s/'hooks/items/automerge').glob('*.kt'))+[s/'hooks/core'/n for n in ['BaseFeature.kt','Feature.kt','FeatureContext.kt','FeatureResourceScope.kt','PreferenceRuntimeOwner.kt','HookRegistry.kt']]+[s/'ui/SimpleFeatureSettingsProvider.kt',s/'dexkit/DexKitBridgeManager.java']
with tempfile.TemporaryDirectory() as td:
 p=Path(td)/'Toggle.kt'
 p.write_text('package t.mma.ui\nobject FeatureToggleRegistry {fun isEnabled(c:android.content.Context,id:String)=false}')
 sources.append(p)
 result=subprocess.run(['java','-Xmx512m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-jvm-target','17','-Xfriend-paths='+str(abi),'-nowarn','-no-stdlib','-no-reflect','-classpath',':'.join(map(str,cp)),'-d',td,*map(str,sources)],capture_output=True,text=True,timeout=90)
 print(result.stdout,end='');print(result.stderr,end='');
 if result.returncode: raise SystemExit(result.returncode)
print('PASS complete auto merge feature/runtime/settings/provider and production owner/scope/hook registry/BaseFeature SDK36 + cached dependency ABI compile; synthetic enabled-aggregation boundary; UI/Gradle/R8/device excluded')
