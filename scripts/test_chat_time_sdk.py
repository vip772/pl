"""Compile the production chat-time feature against the real Android SDK.

Catches Android API misuse (Handler/Looper/post/TextView/visibility/SharedPreferences)
that the synthetic JVM host cannot see. No Gradle and no device.
"""
from pathlib import Path
import subprocess, sys, tempfile

r = Path(__file__).resolve().parents[1]
sdk = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('/usr/lib/android-sdk/platforms/android-36/android.jar')
assert sdk.is_file(), f'missing android.jar: {sdk}'
items = r / 'app/src/main/java/t/mma/hooks/items/chattime'
core = r / 'app/src/main/java/t/mma/hooks/core'
libs = Path('/tmp/pl-kotlin-libs')
std = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))

stubs = {
    'CoreStubs.kt': '''package t.mma.hooks.core
import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import java.lang.reflect.Member
import java.lang.reflect.Method

abstract class BaseFeature {
    private val releases = mutableListOf<() -> Unit>()
    open fun featureId(): String = "stub"
    open fun name(): String = featureId()
    open fun onFeatureInit(context: FeatureContext) {}
    open fun onFeatureInstall(context: FeatureContext) {}
    protected fun trackResource(release: () -> Unit) { releases.add(release) }
    protected fun registerSettingsProvider(provider: Any) {}
    protected fun logError(message: String, throwable: Throwable?) {}
    protected fun logInfo(message: String) {}
}

class FeatureContext(private val host: Context, private val loader: ClassLoader) {
    fun hostContext(): Context = host
    fun hostClassLoader(): ClassLoader = loader
    fun eventBus(): t.mma.event.EventBus = t.mma.event.EventBus()
    fun dexKitManager(): t.mma.dexkit.DexKitBridgeManager = t.mma.dexkit.DexKitBridgeManager()
}

object DexInstallScheduler {
    enum class Stage { EARLY, BRIDGE, WARMUP }
    class Installation {
        fun schedule(installer: () -> Boolean) {}
        fun close() {}
    }
    fun installation(key: String, label: String = key, stage: Stage = Stage.BRIDGE, priority: Int = 0) =
        Installation()
}

class HookRegistry {
    fun hook(member: Member, callback: XC_MethodHook): XC_MethodHook.Unhook = XC_MethodHook.Unhook()
    fun unhook(unhook: XC_MethodHook.Unhook?) {}
    companion object {
        private val INSTANCE = HookRegistry()
        @JvmStatic
        fun get(): HookRegistry = INSTANCE
    }
}
''',
    'XposedStubs.kt': '''package de.robv.android.xposed
open class XC_MethodHook {
    open fun afterHookedMethod(param: MethodHookParam) {}
    class MethodHookParam(val args: Array<Any?>, var thisObject: Any?)
    class Unhook {
        fun unhook() {}
    }
}
''',
    'DexKitStubs.kt': '''package t.mma.dexkit
import android.content.Context
import android.content.SharedPreferences
import java.lang.reflect.Method
import java.util.function.Function

class DexKitBridge {
    fun findMethod(request: org.luckypray.dexkit.query.FindMethod): List<DexMethodData> = emptyList()
}

class DexMethodData {
    fun getMethodInstance(loader: ClassLoader): Method = error("stub")
}

class DexKitBridgeManager {
    fun <T> withCurrentBridge(action: Function<DexKitBridge, T>): T = action.apply(DexKitBridge())
}

object DexMethodCache {
    fun prefs(context: Context, name: String): SharedPreferences = HchatPrefs
    fun runtimeKey(context: Context, classLoader: ClassLoader): String = "runtime"
    fun load(prefs: SharedPreferences, runtimeKey: String, classLoader: ClassLoader, name: String): Method? = null
    fun save(prefs: SharedPreferences, runtimeKey: String, name: String, method: Method?) {}
    fun clear(prefs: SharedPreferences, runtimeKey: String, name: String) {}
}

object HchatPrefs : SharedPreferences {
    override fun getAll(): MutableMap<String, *> = mutableMapOf<String, Any>()
    override fun getString(key: String, defValue: String?): String? = defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String, defValue: Int): Int = defValue
    override fun getLong(key: String, defValue: Long): Long = defValue
    override fun getFloat(key: String, defValue: Float): Float = defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = defValue
    override fun contains(key: String): Boolean = false
    override fun edit(): SharedPreferences.Editor = error("stub")
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
}
''',
    'HostStubs.kt': '''package t.mma.preferences
import android.content.Context
import android.content.SharedPreferences

object HchatStorage {
    @JvmStatic
    fun preferences(context: Context, name: String): SharedPreferences = t.mma.dexkit.HchatPrefs
}
''',
    'EventStubs.kt': '''package t.mma.event
fun interface EventHandler<E> { fun handleEvent(event: E) }
object Events { class DexReady }
class EventBus {
    class Subscription
    fun <E> subscribe(type: Class<E>, handler: EventHandler<E>): Subscription = Subscription()
    fun unsubscribe(subscription: Subscription) {}
}
''',
    'DexQueryStubs.kt': '''package org.luckypray.dexkit.query
class FindMethod {
    fun matcher(matcher: org.luckypray.dexkit.query.matchers.MethodMatcher): FindMethod = this
}
''',
    'DexMatcherStubs.kt': '''package org.luckypray.dexkit.query.matchers
class MethodMatcher {
    fun usingStrings(vararg values: String): MethodMatcher = this
}
''',
    'UiStubs.kt': '''package t.mma.ui
interface FeatureSettingsProvider {
    fun featureId(): String
    companion object { const val CATEGORY_PRACTICAL = "practical" }
}
open class SimpleFeatureSettingsProvider protected constructor(
    private val featureId: String,
    private val title: String,
    private val subtitle: String,
    private val category: String
) : FeatureSettingsProvider {
    final override fun featureId(): String = featureId
}
''',
    'ReflectStubs.kt': '''package t.mma.utils
import java.lang.reflect.Field
import java.lang.reflect.Method

object HLog {
    fun e(message: String) {}
    fun e(message: String, throwable: Throwable?) {}
    fun i(message: String) {}
    fun w(message: String) {}
}

object KavaReflector {
    @JvmStatic fun declaredFields(clazz: Class<*>?): List<Field> = clazz?.declaredFields?.toList() ?: emptyList()
    @JvmStatic fun isStatic(field: Field?): Boolean = field != null && java.lang.reflect.Modifier.isStatic(field.modifiers)
    @JvmStatic fun findMethod(clazz: Class<*>?, name: String?): Method? = null
    @JvmStatic fun findDeclaredField(clazz: Class<*>?, name: String?): Field? = null
    @JvmStatic fun readField(field: Field?, receiver: Any?): Any? = null
    @JvmStatic fun readField(receiver: Any?, name: String?): Any? = null
    @JvmStatic fun invoke(method: Method?, receiver: Any?, vararg args: Any?): Any? = null
}
''',
}

with tempfile.TemporaryDirectory() as td:
    p = Path(td)
    for name, body in stubs.items():
        (p / name).write_text(body)
    sources = [str(p / name) for name in stubs]
    sources += [str(items / n) for n in (
        'ChatTimeStyleFeature.kt', 'ChatTimeStyleSettings.kt',
        'ChatTimeStyleSettingsProvider.kt', 'ChatTimeFormatter.kt')]
    sources += [str(core / 'PreferenceRuntimeOwner.kt'), str(core / 'FeatureResourceScope.kt')]
    cmd = [
        'java', '-Xmx512m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
        '-no-stdlib', '-no-reflect', '-nowarn',
        '-classpath', f'{std}:{sdk}', '-d', str(p / 'check.jar'), *sources,
    ]
    done = subprocess.run(cmd, capture_output=True, text=True, timeout=900)
    if done.returncode:
        print(done.stdout)
        print(done.stderr)
        raise SystemExit(done.returncode)
print('PASS: chat-time feature compiles against', sdk.name)
