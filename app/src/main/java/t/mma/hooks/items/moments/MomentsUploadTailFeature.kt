package t.mma.hooks.items.moments

import de.robv.android.xposed.XC_MethodHook
import t.mma.event.Events
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.preferences.HchatStorage
import t.mma.utils.KavaReflector
import java.lang.reflect.Modifier

class MomentsUploadTailFeature : BaseFeature() {
    override fun featureId(): String = ID

    override fun name(): String = "朋友圈上传尾巴"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(MomentsUploadTailSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val installed = MomentsUploadTailHooker(context, ::logError)
        val ticket = DexInstallScheduler.installation(ID, name(), DexInstallScheduler.Stage.WARMUP)
        trackResource { installed.destroy() }
        trackResource { ticket.close() }
        ticket.schedule { installed.install() }
        subscribe(Events.DexReady::class.java) { ticket.schedule { installed.install() } }
    }

    companion object {
        const val ID = "moments_upload_tail"
    }
}

private class MomentsUploadTailHooker(
    private val context: FeatureContext,
    private val logger: (String, Throwable?) -> Unit
) {
    private val prefs = HchatStorage.preferences(
        context.hostContext(),
        MomentsUploadTailSettings.PREFS_NAME
    )
    private val resources = t.mma.hooks.core.FeatureResourceScope { logger("朋友圈上传尾巴资源释放失败", it) }
    @Synchronized fun destroy() { resources.close() }
    @Volatile
    private var installed = false

    @Synchronized
    fun install(): Boolean {
        if (resources.isClosed()) return false
        if (installed) return true
        val finder = context.dexFinder()
        runCatching { finder.resolveSnsUploadApi() }.onFailure {
            logger("朋友圈上传尾巴定位朋友圈发布方法失败", it)
        }
        val commit = finder.snsCommitMethod ?: return false
        val sdkId = finder.snsSetSdkIdMethod ?: return false
        val sdkAppName = finder.snsSetSdkAppNameMethod ?: return false
        if (Modifier.isAbstract(commit.modifiers) || commit.declaringClass.isInterface) return false
        return runCatching {
            val handle = HookRegistry.get().hook(commit, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (resources.isClosed()) return
                    if (!prefs.getBoolean(
                            MomentsUploadTailSettings.KEY_ENABLE,
                            MomentsUploadTailSettings.DEFAULT_ENABLE
                        )
                    ) return
                    val id = prefs.getString(
                        MomentsUploadTailSettings.KEY_SDK_ID,
                        MomentsUploadTailSettings.DEFAULT_SDK_ID
                    ).orEmpty().trim()
                    val name = prefs.getString(
                        MomentsUploadTailSettings.KEY_SDK_APP_NAME,
                        MomentsUploadTailSettings.DEFAULT_SDK_APP_NAME
                    ).orEmpty().trim()
                    if (id.isBlank() || name.isBlank()) return
                    KavaReflector.invoke(sdkId, param.thisObject, id)
                    KavaReflector.invoke(sdkAppName, param.thisObject, name)
                }
            })
            resources.register { HookRegistry.get().unhook(handle) }
            installed = true
            true
        }.onFailure {
            logger("朋友圈上传尾巴Hook安装失败: ${commit.toGenericString()}", it)
        }.getOrDefault(false)
    }
}
