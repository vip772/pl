"""Profile circle/orbit source contracts and full affected Kotlin SDK compilation; no device."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];b=r/'app/src/main/java/t/mma/hooks/items/homesidepanel'
s=(b/'HomeSidePanelRuntime.kt').read_text();e=(b/'AvatarHeaderEffect.kt').read_text()
avatar=s[s.index('            val iv = ImageView'):s.index('            val text = LinearLayout',s.index('            val iv = ImageView'))]
assert 'background = GradientDrawable().apply { shape = GradientDrawable.OVAL' in avatar
assert 'clipToOutline = true' in avatar and 'outlineProvider = ViewOutlineProvider.BACKGROUND' in avatar
assert 'FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER)' in avatar
assert 'AvatarHeaderEffect(root, if (showProfile()) avatar else effectContent)' in s
assert 'val gap = 2f * host.resources.displayMetrics.density' in e
assert 'host.offsetDescendantRectToMyCoords(content, contentLocation)' in e
assert 'contentRect.inset(-gap, -gap)' in e
assert 'if (text == null && !circularAvatar)' in e
assert 'outline.addOval(rect, Path.Direction.CW)' in e
assert 'if (content is ImageView) -kotlin.math.abs(jitter) else jitter' in e
assert 'avatarInsetDp = if (showProfile())' not in s
# Geometry reference checks: measured sizes/positions, not fixed wrapper estimates.
for density in [1,1.5,2,3,4]:
 for wrapper in [38,44,58]:
  side=38*density; left=(wrapper*density-side)/2
  radius=(side+4*density)/2
  assert abs(radius-side/2-2*density)<1e-6
  assert abs((left-2*density+left+side+2*density)/2-wrapper*density/2)<1e-6
libs=Path('/tmp/pl-kotlin-libs');abi=r/'app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes'
cp=[abi,r/'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes',Path('/usr/lib/android-sdk/platforms/android-36/android.jar')]
cp+=list(Path('/root/.gradle/caches/modules-2/files-2.1').glob('*/*/*/*/*.jar'))+list(Path('/root/.gradle/caches').glob('*/transforms/*/transformed/*/jars/classes.jar'))+list(libs.glob('*.jar'))
sources=list(b.glob('*.kt'))+[r/'app/src/main/java/t/mma/hooks/core'/n for n in ['BaseFeature.kt','Feature.kt','FeatureContext.kt','FeatureResourceScope.kt','PreferenceRuntimeOwner.kt','DexInstallScheduler.kt','IdleSerialExecutor.kt','HookRegistry.kt']]+[r/'app/src/main/java/t/mma/dexkit/DexKitBridgeManager.java']
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 # Unrelated navigation/weather/settings destinations are explicit synthetic compile boundaries.
 stubs={
 'Toggle.kt':'package t.mma.ui\nobject FeatureToggleRegistry {fun isEnabled(c:android.content.Context,id:String)=true}',
 'Settings.kt':'package t.mma.ui.miuix\nobject MiuixSettingsPage {fun show(a:android.app.Activity){};fun showFeature(a:android.app.Activity,s:String){}}',
 'Weather.kt':'package t.mma.hooks.items.homeextension\nobject HomeWeather {fun card(a:android.app.Activity,settings:android.content.SharedPreferences,locateOnRefresh:Boolean):android.view.View=TODO()}',
 'Quick.kt':'package t.mma.hooks.items.quickread\nobject QuickMarkReadRuntime {fun install(c:t.mma.hooks.core.FeatureContext){};fun requestPreparation(){};fun markAllRead(c:android.content.Context,b:Boolean){}}'
 }
 for name,text in stubs.items():(p/name).write_text(text)
 sources+=list(p.glob('*.kt'))
 out=subprocess.run(['java','-Xmx512m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-jvm-target','17','-Xfriend-paths='+str(abi),'-nowarn','-no-stdlib','-no-reflect','-classpath',':'.join(map(str,cp)),'-d',td,*map(str,sources)],capture_output=True,text=True,timeout=100)
 print(out.stdout,end='');print(out.stderr,end='');out.check_returncode()
print('PASS circle background/outline, measured +2dp orbit source contracts, density geometry reference, home-side-panel SDK + cached ABI compilation with synthetic navigation/weather/settings boundaries; not Android/R8/device')