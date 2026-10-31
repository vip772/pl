"""Compile complete production finders against SDK/DexKit; assert every public query owns its gate."""
from pathlib import Path
import subprocess,tempfile,re
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
base=r/'app/src/main/java/t/mma';dex=Path('/tmp/pl-dexkit.jar');sdk=Path('/usr/lib/android-sdk/platforms/android-36/android.jar')
kava=next(Path('/root/.gradle/caches/modules-2/files-2.1/com.highcapable.kavaref/kavaref-core').rglob('*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);classes=p/'classes';classes.mkdir()
 (p/'HLog.java').write_text('package t.mma.utils; public class HLog{public static void e(String s,Throwable t){} public static void e(String s){} public static void d(String s){} public static void i(String s){} public static void w(String s){}}')
 (p/'HchatStorage.java').write_text('package t.mma.preferences; public class HchatStorage{public static android.content.SharedPreferences preferences(android.content.Context c,String n){return null;}public static android.content.SharedPreferences runtimePreferences(android.content.Context c,String n){return null;}}')
 (p/'XposedBridge.java').write_text('package de.robv.android.xposed; public class XposedBridge{public static void log(String s){}}')
 subprocess.run(['javac','-cp',str(sdk),'-d',str(classes),*map(str,p.glob('*.java'))],check=True,timeout=20)
 cp=':'.join(map(str,[classes,std,dex,sdk,kava,libs/"kotlinx-coroutines-core-jvm-1.8.0.jar"]))
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-jvm-target','17','-nowarn','-no-stdlib','-no-reflect','-classpath',cp,'-d',str(classes),str(base/'utils/KavaReflector.kt'),str(base/'hooks/core/DexInstallScheduler.kt'),str(base/'hooks/api/model/WeChatVersionInfo.kt')],check=True,timeout=45)
 subprocess.run(['javac','-cp',cp,'-d',str(classes),str(base/'hooks/api/runtime/WeChatVersionApi.java'),str(base/'dexkit/DexFinder.java'),str(base/'dexkit/CacheSnapshotEditor.java'),str(base/'dexkit/SettingsDexFinder.java')],check=True,timeout=30)
 (p/'Hook.kt').write_text('package de.robv.android.xposed\nopen class XC_MethodHook{class MethodHookParam{var result:Any?=null;var args=arrayOf<Any>();var thisObject:Any?=null};open fun afterHookedMethod(p:MethodHookParam){}}')
 (p/'Registry.kt').write_text('package t.mma.hooks.core\nobject HookRegistry{fun get()=this;fun hook(m:java.lang.reflect.Member,c:de.robv.android.xposed.XC_MethodHook){}}')
 (p/'Identity.kt').write_text('package t.mma.hooks.api.sns\nobject SnsLocalPostIdentity{fun isLocalOnly(c:android.content.Context,id:Any?)=false}')
 (p/'Apis.kt').write_text('package t.mma.hooks.api.core\nclass Tasks{fun runOnMain(r:Runnable){}}\nobject WeChatApis{fun tasks():Tasks?=null}')
 subprocess.run(['javac','-cp',cp,'-d',str(classes),str(base/'dexkit/DexKitBridgeManager.java')],check=True,timeout=30)
 (p/'Context.kt').write_text('package t.mma.hooks.core\nclass FeatureContext{fun hostContext():android.content.Context=error("fixture");fun hostClassLoader():ClassLoader=error("fixture");fun dexKitBridge():org.luckypray.dexkit.DexKitBridge=error("fixture");fun dexKitManager():t.mma.dexkit.DexKitBridgeManager=error("fixture")}')
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-jvm-target','17','-nowarn','-no-stdlib','-no-reflect','-classpath',cp,'-d',str(p/'sns.jar'),str(base/'dexkit/DexMethodCache.kt'),str(base/'hooks/api/sns/SnsInteractionLocator.kt'),str(base/'hooks/api/sns/SnsLivePhotoUploadLocator.kt'),str(p/'Context.kt'),str(base/'hooks/api/message/SingleMessageMenuLocator.kt'),str(base/'hooks/api/message/MultiSelectMessageMenuLocator.kt'),str(base/'hooks/api/media/FavoriteMenuLocator.kt'),str(p/'Apis.kt'),str(p/'Hook.kt'),str(p/'Registry.kt'),str(p/'Identity.kt'),str(base/'hooks/api/contact/WeContactHeaderApi.kt'),str(base/'hooks/items/quickcontactedit/ConversationMenuLocator.kt'),str(base/'hooks/items/quickcontactedit/SnsAvatarMenuLocator.kt'),str(base/'hooks/api/sns/WeChatSnsPostObserver.kt'),str(base/'hooks/api/media/FavoriteVoiceResolver.kt'),*[str(base/'hooks/api/sns'/n) for n in ['SnsForwardNativeMediaLocator.kt','SnsForwardLocalMediaLocator.kt','SnsContextMenuLocator.kt']]],check=True,timeout=45)
for name in ['DexFinder.java','SettingsDexFinder.java']:
 s=(base/'dexkit'/name).read_text()
 for m in re.finditer(r'public (?:synchronized )?void (resolve\w+)\(([^)]*)\) \{([^}]+)\}',s):
  if name=='SettingsDexFinder.java' and not m[2]:assert 'resolveAll(true)' in m[3]
  else:assert 'DexInstallScheduler.runDexKitTask(' in m[3],m[1]
 assert not re.search(r'public synchronized void resolve',s)
print('PASS complete production DexFinder/SettingsDexFinder SDK and DexKit ABI compile; all public locator entry gates; shared-gate-before-finder-monitor order')
interaction=(base/'hooks/api/sns/SnsInteractionLocator.kt').read_text()
for name in ['nativeLikeMethod','nativeCommentMethod','nativeCommentGuardMethods','timelineRefreshConstructor']:
 assert f'DexInstallScheduler.queryDexKit {{ {name}Locked() }}' in interaction
live=(base/'hooks/api/sns/SnsLivePhotoUploadLocator.kt').read_text()
assert live.index('DexInstallScheduler.queryDexKit {') < live.index('private fun locateLocked') < live.index('methodData.usingFields')
print('PASS production SNS locators and real DexMethodCache compile; query/usingFields consumption scoped before detached result return')

for file in ['SingleMessageMenuLocator.kt','MultiSelectMessageMenuLocator.kt']:
 source=(base/'hooks/api/message'/file).read_text()
 assert 'DexInstallScheduler.queryDexKit' in source
print('PASS production single/multi-select message menu locator SDK/DexKit compile')

voice=(base/'hooks/api/media/FavoriteVoiceResolver.kt').read_text()
assert 'DexInstallScheduler.queryDexKit { favoriteDownloadMethodLocked(' in voice
assert 'DexInstallScheduler.queryDexKit { locateFavoriteDataPathMethodsLocked(' in voice
print('PASS complete favorite menu/voice resolver compile; lazy invokes consumed within query gate')

for relative in ['sns/WeChatSnsPostObserver.kt','contact/WeContactHeaderApi.kt']:
 source=(base/'hooks/api'/relative).read_text()
 assert 'DexInstallScheduler.queryDexKit { installLocked(' in source
print('PASS shared post observer and contact header complete production SDK compile; gate precedes install monitor')
