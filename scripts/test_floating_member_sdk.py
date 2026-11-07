"""Compile floating header production Kotlin and contact Java against SDK/existing ABI. No Gradle."""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[1]
libs=Path('/tmp/pl-kotlin-libs')
cp=[r/'app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes',r/'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes',Path('/usr/lib/android-sdk/platforms/android-36/android.jar')]
assert all(p.exists() for p in cp), 'Existing SDK/ABI required; do not invoke Gradle'
cp += list(Path('/root/.gradle/caches/modules-2/files-2.1').glob('*/*/*/*/*.jar'))
cp += list(Path('/root/.gradle/caches').glob('*/transforms/*/transformed/*/jars/classes.jar')) + list(libs.glob('*.jar'))
java=[r/'app/src/main/java/t/mma/hooks/api/contact/WeChatContactApi.java']
sources=list((r/'app/src/main/java/t/mma/hooks/items/floatingchat').glob('*.kt'))+java
sources += [r/'app/src/main/java/t/mma/hooks/core'/n for n in ['BaseFeature.kt','Feature.kt','FeatureContext.kt','FeatureResourceScope.kt','DexInstallScheduler.kt','HookRegistry.kt','IdleSerialExecutor.kt']]
sources += [r/'app/src/main/java/t/mma/dexkit/DexKitBridgeManager.java', r/'app/src/main/java/t/mma/hooks/api/media/WeChatInternalServices.kt']
with tempfile.TemporaryDirectory() as td:
    stub=Path(td)/'FeatureToggleRegistry.kt'
    stub.write_text('package t.mma.ui\nobject FeatureToggleRegistry {fun isEnabled(c:android.content.Context,id:String)=true}\n')
    sources.append(stub)
    cmd=['java','-Xmx512m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-jvm-target','17','-Xfriend-paths='+str(cp[0]),'-nowarn','-no-stdlib','-no-reflect','-classpath',':'.join(map(str,cp)),'-d',td,*map(str,sources)]
    subprocess.run(cmd,check=True,timeout=180)
    subprocess.run(['javac','-encoding','UTF-8','-cp',td+':'+':'.join(map(str,cp)),'-d',td,*map(str,java)],check=True,timeout=90)
print('PASS floating header all production Kotlin + contact API Java SDK direct compile (not Android/R8)')
