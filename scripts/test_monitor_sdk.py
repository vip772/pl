"""Direct Kotlin/SDK compile of changed features against existing ABI; never invokes Gradle.
Requires already present Android/JDK/Kotlin/dependency caches; not a full Android/R8 build."""
from pathlib import Path
import subprocess,tempfile,re
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs')
cp=[r/'app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes',r/'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes',Path('/usr/lib/android-sdk/platforms/android-36/android.jar')]
assert all(p.exists() for p in cp), 'Existing SDK/ABI fixtures required; do not invoke Gradle'
cp += [r/'app/build/intermediates/compile_r_class_jar/debug/generateDebugRFile/R.jar']
cp+=list(Path('/root/.gradle/caches/modules-2/files-2.1').glob('*/*/*/*/*.jar'))+list(Path('/root/.gradle/caches').glob('*/transforms/*/transformed/*/jars/classes.jar'))+list(libs.glob('*.jar'))
sources=[r/'app/src/main/java/t/mma/hooks/core'/n for n in ['FeatureManager.kt','BaseFeature.kt','Feature.kt','FeatureContext.kt','FeatureResourceScope.kt','PreferenceRuntimeOwner.kt','IdleSerialExecutor.kt','PendingNoticeTasks.kt','DexInstallScheduler.kt','HookRegistry.kt']]
sources+=[r/'app/src/main/java/t/mma/dexkit/DexKitBridgeManager.java']
for folder in ['grouprename','groupleave','voicetotext','stepmultiplier','protobuf','hidemenu','messagemenuextension','groupbar','nicknamewidth','mentionextension','swipequote','backgroundbeauty','typingreport','patblock','inputhint','textspeech','forwardlimit','callmedialimit','statuslimit','quickread','fakevoiceduration','quoteclear']:
 sources+=list((r/'app/src/main/java/t/mma/hooks/items'/folder).glob('*.kt'))
java_sources=[r/'app/src/main/java/t/mma/hooks/items/protobuf'/n for n in ['ProtobufPacketHook.java','ProtobufPacketRuntime.java']]
sources += [r/'app/src/main/java/t/mma/hooks/items/antirecall/DynamicRecallGradient.kt']
sources += [r/'app/src/main/java/t/mma/hooks/items/conversationanalysis'/n for n in ['ConversationAnalysisFeature.kt','ConversationAnalysisRuntime.kt','ConversationAnalysisMessageMenuRuntime.kt']]
sources += list((r/'app/src/main/java/t/mma/hooks/items/realtail').glob('*.kt'))
sources += [r/'app/src/main/java/t/mma/hooks/items/chatname/NativeGroupNicknameGate.kt']
sources += [r/'app/src/main/java/t/mma/hooks/items/chatname/ChatNameDecorationLayout.kt']
sources += [r/'app/src/main/java/t/mma/hooks/items/quickcontactedit/QuickContactEditFeature.kt',r/'app/src/main/java/t/mma/hooks/items/quickmoments/QuickMomentsFeature.kt']
sources += [r/'app/src/main/java/t/mma/hooks/items/grouplabel/QuickGroupChatLabelFeature.kt']
sources += [r/'app/src/main/java/t/mma/hooks/items/moments'/n for n in ['MomentsUploadTailFeature.kt','OriginalMomentsUploadFeature.kt','RemoveMomentsAdsFeature.kt']]
sources += list((r/'app/src/main/java/t/mma/hooks/items/homeextension').glob('*.kt'))
sources += list((r/'app/src/main/java/t/mma/hooks/items/floatingchat').glob('*.kt'))
sources += list((r/'app/src/main/java/t/mma/hooks/items/floatingshortcut').glob('*.kt'))
sources += [r/'app/src/main/java/t/mma/hooks/items/custombottombar'/n for n in ['CustomBottomBarFeature.kt','CustomBottomBarIconStore.kt','CustomBottomBarIconPicker.kt']]
sources += [r/'app/src/main/java/t/mma/ui/miuix/CustomBottomBarMiuixPage.kt']
sources += list((r/'app/src/main/java/t/mma/hooks/items/fakelocation').glob('*.kt'))
sources += list((r/'app/src/main/java/t/mma/hooks/items/gameemoji').glob('*.kt'))
sources += list((r/'app/src/main/java/t/mma/hooks/items/miniprogrambaselib').glob('*.kt'))
sources += list((r/'app/src/main/java/t/mma/hooks/items/miniprogramvideoad').glob('*.kt'))
sources += list((r/'app/src/main/java/t/mma/hooks/items/selectedmessages').glob('*.kt'))
sources += list((r/'app/src/main/java/t/mma/hooks/items/settings').glob('*.kt'))
java_sources += [r/'app/src/main/java/t/mma/hooks/api/ui/SettingsInjector.java', r/'app/src/main/java/t/mma/dexkit/SettingsDexFinder.java', r/'app/src/main/java/t/mma/dexkit/DexKitBridgeManager.java']
java_sources += [r/'app/src/main/java/t/mma/hooks/api'/name for name in ['core/WechatApiFeature.java','runtime/WeChatDatabaseListenerApi.java','message/WeChatMessageEventApi.java','message/WeChatMessageChangeApi.java','message/WeChatMessageObserveApi.java','conversation/WeChatConversationChangeApi.java','contact/WeChatContactChangeApi.java','contact/WeChatChatroomChangeApi.java','ui/WeChatChatPageApi.java','ui/WeChatLifecycleApi.java','ui/WeChatActivityStartApi.java','ui/WeChatCurrentActivityApi.java']]
sources += list((r/'app/src/main/java/t/mma/hooks/api/sns').glob('*.kt'))
sources += list((r/'app/src/main/java/t/mma/hooks/api/media').glob('*.kt'))
java_sources += list((r/'app/src/main/java/t/mma/hooks/api/media').glob('*.java'))
sources += [r/'app/src/main/java/t/mma/hooks/api/message/WeChatLocalMessageApi.kt']
java_sources += [r/'app/src/main/java/t/mma/hooks/api/net/WeChatNetworkDispatcher.java']
java_sources += [r/'app/src/main/java/t/mma/hooks/api/message/WeChatMessageApi.java']
sources += list((r/'app/src/main/java/t/mma/hooks/items/messageforward').glob('*.kt'))
java_sources += [r/'app/src/main/java/t/mma/event/EventBus.java']
sources += [r/'app/src/main/java/t/mma/hooks/core/ResourceMemoryCallbacks.kt']
java_sources += [r/'app/src/main/java/t/mma/ModuleEntry.java']
java_sources += [r/'app/src/main/java/t/mma/utils/StartupSyncRetryGuard.java',r/'app/src/main/java/t/mma/dexkit/DexFinder.java',r/'app/src/main/java/t/mma/dexkit/DexBridgeHolder.java']
sources+=java_sources
with tempfile.TemporaryDirectory() as td:
 # Generate compile-only ids from current declarations; cached R.jar may predate source.
 resource = Path(td)/'R.java'
 ids = re.findall(r'<item\s+name="([^"]+)"\s+type="id"', (r/'app/src/main/res/values/ids.xml').read_text())
 resource.write_text('package t.mma; public final class R { public static final class id {' + ''.join('public static final int '+name+'='+str(i+1)+';' for i,name in enumerate(ids)) + '} }')
 result=subprocess.run(['javac','-d',td,str(resource)],capture_output=True,text=True,timeout=30)
 if result.returncode:
  print(result.stderr);raise SystemExit(result.returncode)
 # Compile the exact production cache object without unrelated settings UI.
 ui=(r/'app/src/main/java/t/mma/ui/miuix/MiuixSettingsPage.kt').read_text()
 a=ui.index('internal object AvatarMemoryCache {');b=ui.index('{',a);end=b+1;depth=1
 while depth:
  depth+=(ui[end]=='{')-(ui[end]=='}');end+=1
 avatar=Path(td)/'AvatarMemoryCache.kt'
 avatar.write_text('package t.mma.ui.miuix'+chr(10)+'import androidx.compose.ui.graphics.ImageBitmap'+chr(10)+'import androidx.compose.ui.graphics.asAndroidBitmap'+chr(10)+ui[a:end])
 sources.append(avatar)
 cp.insert(0,Path(td))
 cmd=['java','-Xmx512m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-jvm-target','17','-Xfriend-paths='+str(r/'app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes'),'-nowarn','-no-stdlib','-no-reflect','-classpath',':'.join(map(str,cp)),'-d',td,*map(str,sources)]
 plugin=next(Path('/root/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-compose-compiler-plugin-embeddable/2.4.0').glob('*/*.jar'))
 cmd.insert(5,'-Xplugin='+str(plugin))
 result=subprocess.run(cmd,cwd=r,capture_output=True,text=True,timeout=180)
 if result.returncode:
  print(result.stdout);print(result.stderr);raise SystemExit(result.returncode)
 result=subprocess.run(['javac','-encoding','UTF-8','-cp',td+':'+':'.join(map(str,cp)),'-d',td,*map(str,java_sources)],cwd=r,capture_output=True,text=True,timeout=60)
 if result.returncode:
  print(result.stdout);print(result.stderr);raise SystemExit(result.returncode)
print('PASS changed monitor/voice/step feature files SDK + existing ABI direct compile (not Android/R8)')
