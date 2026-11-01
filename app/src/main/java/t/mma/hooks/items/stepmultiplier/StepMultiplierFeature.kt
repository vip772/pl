package t.mma.hooks.items.stepmultiplier

import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.*
import t.mma.preferences.HchatStorage
import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider
import t.mma.utils.KavaReflector
import t.mma.utils.HLog

object StepMultiplierSettings {
    const val PREFS = "Hchat_step_multiplier"
    const val ENABLE = "enable"
    const val FACTOR = "factor"
    fun multiply(original:Int, factor:Int, enabled:Boolean):Int {
        if(!enabled || original <= 0) return original
        return (original.toLong()*factor.coerceIn(1,10)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}

/** Verified 8.0.78 request constructor; never touch cumulative sensor readings or request retry dispatch. */
class StepMultiplierFeature:BaseFeature() {
    override fun featureId()=ID
    override fun name()="步数倍增"
    override fun onFeatureInit(context:FeatureContext) {
        val prefs=HchatStorage.preferences(context.hostContext(),StepMultiplierSettings.PREFS)
        registerSettingsProvider(Provider()) { prefs.getBoolean(StepMultiplierSettings.ENABLE,false) }
    }
    override fun onFeatureInstall(context:FeatureContext) {
        val prefs=HchatStorage.preferences(context.hostContext(),StepMultiplierSettings.PREFS)
        val owner = PreferenceRuntimeOwner(prefs, setOf(StepMultiplierSettings.ENABLE),
            enabled = { prefs.getBoolean(StepMultiplierSettings.ENABLE, false) },
            create = { resources ->
                val ticket = DexInstallScheduler.installation(ID, name(), DexInstallScheduler.Stage.WARMUP)
                resources.register { ticket.close() }
                ticket.schedule {
                    if (resources.isClosed()) return@schedule true
                    val version = WeChatApis.version()?.current()?.versionName ?: return@schedule false
                    if (version != "8.0.78") {
                        HLog.w("[Hchat:StepMultiplier] 未验证的微信版本 $version，未安装步数倍增")
                        return@schedule true
                    }
                    try {
                        val type = Class.forName("com.tencent.mm.plugin.sport.model.j", false, context.hostClassLoader())
                        val intType = Int::class.javaPrimitiveType!!
                        val constructor = KavaReflector.findConstructor(type, String::class.java, String::class.java,
                            intType, intType, intType, String::class.java, intType) ?: return@schedule false
                        val handle = HookRegistry.get().hook(constructor, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (resources.isClosed() || !prefs.getBoolean(StepMultiplierSettings.ENABLE, false)) return
                                val original = param.args.getOrNull(4) as? Int ?: return
                                param.args[4] = StepMultiplierSettings.multiply(original, prefs.getInt(StepMultiplierSettings.FACTOR, 1), true)
                            }
                        })
                        // A concurrent close releases this late handle immediately, never into a new interval.
                        resources.register { HookRegistry.get().unhook(handle) }
                        HLog.i("[Hchat:StepMultiplier] 已安装8.0.78上传步数构造入口，第5参数为原始步数")
                        true
                    } catch (error: Throwable) {
                        HLog.e("[Hchat:StepMultiplier] 8.0.78上传步数入口Hook失败", error)
                        false
                    }
                }
            }, onError = { logError("步数倍增启停失败", it) })
        trackResource { owner.close() }
        owner.start()
    }
    companion object { const val ID="step_multiplier" }
    private class Provider:SimpleFeatureSettingsProvider(ID,"步数倍增","按1至10倍调整微信运动后续上报步数",FeatureSettingsProvider.CATEGORY_ENHANCE,true)
}
