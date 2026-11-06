from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]; libs=Path('/tmp/pl-kotlin-libs')
abi=r/'app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes'
cp=[abi,r/'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes',Path('/usr/lib/android-sdk/platforms/android-36/android.jar')]
assert all(p.exists() for p in cp),'Missing SDK/ABI; never run Gradle'
cp+=list(Path('/root/.gradle/caches/modules-2/files-2.1').glob('*/*/*/*/*.jar'))+list(Path('/root/.gradle/caches').glob('*/transforms/*/transformed/*/jars/classes.jar'))+list(libs.glob('*.jar'))
src=r/'app/src/main/java/t/mma'
sources=list((src/'hooks/items/antirecall').glob('*.kt'))
sources+=list((src/'hooks/items/hchatextra').glob('*.kt'))
sources += [src/'hooks/core/FeatureOwnership.kt',src/'hooks/core/PreferenceRuntimeOwner.kt']
sources+=[src/'hooks/core'/n for n in ['BaseFeature.kt','Feature.kt','FeatureContext.kt','FeatureResourceScope.kt','DexInstallScheduler.kt','HookRegistry.kt']]
sources += [src/'hooks/api/media/WeChatInternalServices.kt',src/'hooks/items/mentionextension/MentionNoticeFeature.kt',src/'hooks/items/mentionextension/MentionNoticeTouchGuard.kt']
sources+=[src/'dexkit/DexKitBridgeManager.java',src/'dexkit/DexMethodCache.kt',src/'hooks/items/dynamicbadge/SelectableBorderEffect.kt',src/'hooks/api/message/WeChatLocalMessageApi.kt']
with tempfile.TemporaryDirectory(prefix='antirecall-sdk-audit-') as td:
 p=Path(td)
 stub=p/'FeatureToggleRegistry.kt';stub.write_text('package t.mma.ui\nobject FeatureToggleRegistry {fun isEnabled(c:android.content.Context,id:String)=true}\n')
 sources.append(stub)
 cmd=['java','-Xmx384m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-jvm-target','17','-Xfriend-paths='+str(abi),'-nowarn','-no-stdlib','-no-reflect','-classpath',':'.join(map(str,cp)),'-d',str(p/'out'),*map(str,sources)]
 result=subprocess.run(cmd,capture_output=True,text=True,timeout=55)
 print(result.stdout,end='');print(result.stderr,end='')
 if result.returncode:raise SystemExit(result.returncode)
print('PASS full antirecall package + current core/Dex cache + real Android SDK; existing project ABI and synthetic UI toggle boundary; no Gradle/APK/device')
