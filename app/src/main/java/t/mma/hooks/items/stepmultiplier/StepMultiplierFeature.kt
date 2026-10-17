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
    private var unhook:XC_MethodHook.Unhook?=null
    @Volatile private var destroyed=false
    override fun featureId()=ID
    override fun name()="步数倍增"
    override fun onFeatureInit(context:FeatureContext) {
        val prefs=HchatStorage.preferences(context.hostContext(),StepMultiplierSettings.PREFS)
        registerSettingsProvider(Provider()) { prefs.getBoolean(StepMultiplierSettings.ENABLE,false) }
    }
    override fun onFeatureInstall(context:FeatureContext) {
        val prefs=HchatStorage.preferences(context.hostContext(),StepMultiplierSettings.PREFS)
        DexInstallScheduler.schedule(ID,name(),DexInstallScheduler.Stage.WARMUP) {
            if(destroyed || unhook!=null) return@schedule true
            val version=WeChatApis.version()?.current()?.versionName ?: return@schedule false
            if(version!="8.0.78") {
                HLog.w("[Hchat:StepMultiplier] 未验证的微信版本 $version，未安装步数倍增")
                return@schedule true
            }
            try {
                val type=Class.forName("com.tencent.mm.plugin.sport.model.j",false,context.hostClassLoader())
                val intType=Int::class.javaPrimitiveType!!
                val constructor=KavaReflector.findConstructor(type,String::class.java,String::class.java,intType,intType,intType,String::class.java,intType)
                    ?: return@schedule false
                unhook=HookRegistry.get().hook(constructor,object:XC_MethodHook() {
                    override fun beforeHookedMethod(param:MethodHookParam) {
                        if(destroyed || !prefs.getBoolean(StepMultiplierSettings.ENABLE,false)) return
                        val original=param.args.getOrNull(4) as? Int ?: return
                        param.args[4]=StepMultiplierSettings.multiply(original,prefs.getInt(StepMultiplierSettings.FACTOR,1),true)
                    }
                })
                HLog.i("[Hchat:StepMultiplier] 已安装8.0.78上传步数构造入口，第5参数为原始步数")
                true
            } catch(error:Throwable) {
                HLog.e("[Hchat:StepMultiplier] 8.0.78 j(String,String,int,int,int,String,int) Hook失败",error)
                false
            }
        }
    }
    override fun onFeatureDestroy(context:FeatureContext) {
        destroyed=true
        HookRegistry.get().unhook(unhook);unhook=null
    }
    companion object { const val ID="step_multiplier" }
    private class Provider:SimpleFeatureSettingsProvider(ID,"步数倍增","按1至10倍调整微信运动后续上报步数",FeatureSettingsProvider.CATEGORY_ENHANCE,true)
}
