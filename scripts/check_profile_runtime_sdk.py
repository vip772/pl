from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]; libs=Path('/tmp/pl-kotlin-libs')
abi=r/'app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes'
cp=[abi,r/'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes',Path('/usr/lib/android-sdk/platforms/android-36/android.jar')]
assert all(p.exists() for p in cp),'Missing SDK/ABI; never run Gradle'
cp+=list(Path('/root/.gradle/caches/modules-2/files-2.1').glob('*/*/*/*/*.jar'))+list(Path('/root/.gradle/caches').glob('*/transforms/*/transformed/*/jars/classes.jar'))+list(libs.glob('*.jar'))
src=r/'app/src/main/java/t/mma'
sources=list((src/'hooks/items/profileid').glob('*.kt'))
sources += [src/'hooks/api/contact/WeChatChatroomApi.java', src/'hooks/api/runtime/WeChatDatabaseApi.java']
sources += [src/'hooks/core'/n for n in ['BaseFeature.kt','Feature.kt','FeatureContext.kt','FeatureResourceScope.kt','PreferenceRuntimeOwner.kt','DexInstallScheduler.kt','HookRegistry.kt']]
sources += [src/'hooks/api/contact/WeContactHeaderApi.kt', src/'dexkit/DexKitBridgeManager.java',src/'dexkit/DexMethodCache.kt']
sources += [src/'hooks/items/realtail/BeforeTransferNameQuery.kt',src/'hooks/core/FeatureOwnership.kt',src/'hooks/api/runtime/WeChatTaskApi.kt',src/'hooks/api/net/WeChatNetworkApi.kt']
with tempfile.TemporaryDirectory(prefix='profile-runtime-sdk-') as td:
 p=Path(td)
 stub=p/'FeatureToggleRegistry.kt';stub.write_text('package t.mma.ui\nobject FeatureToggleRegistry {fun isEnabled(c:android.content.Context,id:String)=true}\n')
 sources.append(stub)
 cmd=['java','-Xmx384m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-jvm-target','17','-Xfriend-paths='+str(abi),'-nowarn','-no-stdlib','-no-reflect','-classpath',':'.join(map(str,cp)),'-d',str(p/'out'),*map(str,sources)]
 result=subprocess.run(cmd,capture_output=True,text=True,timeout=55)
 print(result.stdout,end='');print(result.stderr,end='')
 if result.returncode:raise SystemExit(result.returncode)
 # Compile modified Java against current Kotlin output without compiling unrelated sources.
 java_cmd=['javac','-proc:none','--release','17','-sourcepath',str(p/'empty-source'),'-classpath',str(p/'out')+':'+':'.join(map(str,cp)),'-d',str(p/'java-out'),str(src/'hooks/api/contact/WeChatChatroomApi.java'),str(src/'hooks/api/runtime/WeChatDatabaseApi.java')]
 result=subprocess.run(java_cmd,capture_output=True,text=True,timeout=55)
 print(result.stdout,end='');print(result.stderr,end='')
 if result.returncode:raise SystemExit(result.returncode)
 # Run native protobuf parser on JVM, replacing only Android TextUtils.
 textutils=p/'TextUtils.java'
 textutils.write_text('package android.text; public class TextUtils { public static boolean isEmpty(CharSequence s) {return s==null || s.length()==0;} }')
 test=r/'scripts/tests/profile-demand/NativeJoinTimeTest.java'
 testcp=str(p/'java-out')+':'+str(p/'out')+':'+':'.join(map(str,cp))
 result=subprocess.run(['javac','-proc:none','--release','17','-classpath',testcp,'-d',str(p/'test-out'),str(textutils),str(test)],capture_output=True,text=True,timeout=55)
 print(result.stdout,end='');print(result.stderr,end='')
 if result.returncode:raise SystemExit(result.returncode)
 subprocess.run(['java','-cp',str(p/'test-out')+':'+testcp,'NativeJoinTimeTest'],check=True,timeout=15)
print('PASS profile Kotlin + chatroom Java SDK compile; existing ABI and UI registry fixture; no Gradle/APK/device')
