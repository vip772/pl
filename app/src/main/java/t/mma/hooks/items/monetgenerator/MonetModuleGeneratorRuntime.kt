package t.mma.hooks.items.monetgenerator
import t.mma.utils.HLog

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import t.mma.dexkit.DexKitBridgeManager
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.items.monetgenerator.engine.MonetDexCandidate
import t.mma.hooks.items.monetgenerator.engine.MonetDexEvidenceCollector
import t.mma.hooks.items.monetgenerator.engine.MonetGenerationEvent
import t.mma.hooks.items.monetgenerator.engine.MonetGenerationListener
import t.mma.hooks.items.monetgenerator.engine.MonetGenerationOptions
import t.mma.hooks.items.monetgenerator.engine.MonetGenerationRequest
import t.mma.hooks.items.monetgenerator.engine.MonetLogLevel
import t.mma.hooks.items.monetgenerator.engine.MonetModuleGenerator
import t.mma.hooks.items.monetgenerator.engine.MonetResourceDexEvidence
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

object MonetModuleGeneratorRuntime {
    private const val TAG = "[Hchat:MonetGenerator]"
    private const val WECHAT_PACKAGE = "com.tencent.mm"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)

    @Volatile
    private var binding: Binding? = null

    @Synchronized
    fun attach(context: FeatureContext) {
        binding = Binding(
            context.hostContext().applicationContext ?: context.hostContext(),
            context.dexKitManager(),
            WeakReference(context)
        )
    }

    @Synchronized
    fun detach(context: FeatureContext) {
        val current = binding ?: return
        if (current.owner.get() === context) binding = null
    }

    fun isRunning(): Boolean = running.get()

    fun suggestedFileName(): String {
        val version = runCatching {
            binding?.hostContext?.packageManager
                ?.getPackageInfo(WECHAT_PACKAGE, 0)
                ?.versionName
                .orEmpty()
        }.getOrDefault("")
        val suffix = version.replace(Regex("[^0-9A-Za-z._-]"), "_").trim('_')
        return if (suffix.isBlank()) "Hchat_Monet_WeChat.zip" else "Hchat_Monet_WeChat_$suffix.zip"
    }

    internal fun generate(
        output: MonetModuleDocumentBridge.ExportTarget,
        options: MonetGenerationOptions,
        onEvent: (MonetGenerationEvent) -> Unit,
        onComplete: (Result<ExportResult>) -> Unit
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            onComplete(Result.failure(IllegalStateException("需要 Android 12 或更高版本")))
            return false
        }
        val current = binding
        if (current == null) {
            onComplete(Result.failure(IllegalStateException("Hchat 运行环境尚未就绪，请重新打开微信后再试")))
            return false
        }
        if (!running.compareAndSet(false, true)) return false

        Thread({
            val sessionDir = File(
                current.hostContext.cacheDir,
                "Hchat/monet_generator/session_${SystemClock.elapsedRealtime()}"
            )
            try {
                require(sessionDir.mkdirs() || sessionDir.isDirectory) { "无法创建生成缓存目录" }
                val packageInfo = current.hostContext.packageManager.getPackageInfo(WECHAT_PACKAGE, 0)
                val applicationInfo = current.hostContext.applicationInfo
                require(applicationInfo.packageName == WECHAT_PACKAGE) {
                    "当前宿主不是微信：${applicationInfo.packageName}"
                }
                val apkPaths = (listOf(applicationInfo.sourceDir) + applicationInfo.splitSourceDirs.orEmpty())
                    .filter { it.isNotBlank() && File(it).isFile }
                    .distinct()
                require(apkPaths.isNotEmpty()) { "没有找到微信 APK" }

                val temporaryZip = File(sessionDir, "Hchat_Monet_WeChat.zip")
                val request = MonetGenerationRequest(
                    resources = current.hostContext.resources,
                    packageName = WECHAT_PACKAGE,
                    sourceApkPath = applicationInfo.sourceDir,
                    sourceApkPaths = apkPaths,
                    versionCode = packageInfo.longVersionCode,
                    versionName = packageInfo.versionName.orEmpty().ifBlank { "unknown" },
                    sdkInt = Build.VERSION.SDK_INT,
                    dexEvidenceProvider = { candidates ->
                        collectDexEvidence(current.manager, candidates)
                    },
                    options = options,
                    workDir = File(sessionDir, "work").apply { mkdirs() },
                    outputZip = temporaryZip
                )
                val result = MonetModuleGenerator.generate(request, MonetGenerationListener { event ->
                    logEvent(event)
                    mainHandler.post { onEvent(event) }
                })
                require(result.outputZip.isFile && result.outputZip.length() > 0L) {
                    "生成结果为空"
                }
                current.hostContext.contentResolver.openOutputStream(output.uri, "w")?.use { target ->
                    result.outputZip.inputStream().buffered().use { source -> source.copyTo(target) }
                } ?: error("无法写入所选输出文件")
                val exported = ExportResult(
                    displayName = output.displayName,
                    resourceCount = result.resourceCount,
                    overlayCount = result.overlayCount
                )
                mainHandler.post { onComplete(Result.success(exported)) }
            } catch (error: Throwable) {
                HLog.e("$TAG 生成失败: ${error.message}", error)
                mainHandler.post { onComplete(Result.failure(error)) }
            } finally {
                running.set(false)
                runCatching { sessionDir.deleteRecursively() }
            }
        }, "Hchat-MonetGenerator").apply {
            isDaemon = true
            start()
        }
        return true
    }

    private fun collectDexEvidence(
        manager: DexKitBridgeManager,
        candidates: List<MonetDexCandidate>
    ): List<MonetResourceDexEvidence> {
        return manager.withCurrentBridge { bridge ->
            MonetDexEvidenceCollector.collect(bridge, candidates)
        }
    }

    private fun logEvent(event: MonetGenerationEvent) {
        if (event !is MonetGenerationEvent.Log) return
        when (event.level) {
            MonetLogLevel.ERROR -> HLog.e("$TAG ${event.message}", event.error)
            MonetLogLevel.WARN -> HLog.e("$TAG ${event.message}", event.error)
            MonetLogLevel.DEBUG, MonetLogLevel.INFO -> Unit
        }
    }

    data class ExportResult(
        val displayName: String,
        val resourceCount: Int,
        val overlayCount: Int
    )

    private data class Binding(
        val hostContext: Context,
        val manager: DexKitBridgeManager,
        val owner: WeakReference<FeatureContext>
    )
}
