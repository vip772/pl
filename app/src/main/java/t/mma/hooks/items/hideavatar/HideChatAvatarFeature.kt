package t.mma.hooks.items.hideavatar

import android.view.View
import t.mma.event.Events
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.preferences.HchatStorage
import t.mma.utils.KavaReflector
import java.util.Collections
import java.util.WeakHashMap

class HideChatAvatarFeature : BaseFeature() {
    private var runtime: HideChatAvatarRuntime? = null

    override fun featureId(): String = ID

    override fun name(): String = "隐藏头像"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(HideChatAvatarSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        runtime = HideChatAvatarRuntime(context)
        scheduleInstall()
        subscribe(Events.DexReady::class.java) { scheduleInstall() }
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        runtime?.close()
        runtime = null
    }

    private fun scheduleInstall() {
        DexInstallScheduler.schedule(ID, name()) {
            runtime?.install() == true
        }
    }

    companion object {
        const val ID = "hide_chat_avatar"
    }
}

private class HideChatAvatarRuntime(
    private val context: FeatureContext
) {
    private val prefs = HchatStorage.preferences(context.hostContext(), HideChatAvatarSettings.PREFS_NAME)
    private val originalMaskWidths = Collections.synchronizedMap(WeakHashMap<View, Int>())

    @Volatile private var installed = false
    private var closed = false

    @Synchronized
    fun install(): Boolean {
        if (closed || installed) return true
        installed = ChatAvatarBinding.subscribe(context, this) { binding ->
            val outgoing = resolveOutgoing(binding.username, binding.message) ?: return@subscribe
            val hide = if (outgoing) prefs.getBoolean(HideChatAvatarSettings.KEY_HIDE_SELF, HideChatAvatarSettings.DEFAULT_HIDE_SELF)
                else prefs.getBoolean(HideChatAvatarSettings.KEY_HIDE_OTHER, HideChatAvatarSettings.DEFAULT_HIDE_OTHER)
            applyAvatarLayout(binding.avatar, binding.mask, hide)
        }
        return installed
    }
    @Synchronized
    fun close() {
        closed = true
        ChatAvatarBinding.unsubscribe(this)
        installed = false
        originalMaskWidths.clear()
    }
    private fun applyAvatarLayout(avatar: View, mask: View?, hide: Boolean) {
        val wrapper = sequenceOf(mask, avatar.parent as? View)
            .filterNotNull()
            .firstOrNull { it.javaClass.name == MASK_LAYOUT_CLASS }
        if (wrapper == null) {
            if (hide) avatar.visibility = View.GONE
            return
        }

        val params = wrapper.layoutParams ?: return
        if (hide) {
            synchronized(originalMaskWidths) {
                if (!originalMaskWidths.containsKey(wrapper)) {
                    originalMaskWidths[wrapper] = params.width
                }
            }
            if (params.width != 0) {
                params.width = 0
                wrapper.layoutParams = params
            }
            avatar.visibility = View.GONE
            return
        }

        val originalWidth = synchronized(originalMaskWidths) { originalMaskWidths.remove(wrapper) }
        if (originalWidth != null && params.width != originalWidth) {
            params.width = originalWidth
            wrapper.layoutParams = params
        }
    }

    private fun resolveOutgoing(avatarUsername: String?, message: Any): Boolean? {
        val selfWxId = WeChatApis.account()?.selfWxId().orEmpty()
        if (selfWxId.isNotBlank() && !avatarUsername.isNullOrBlank()) {
            return selfWxId == avatarUsername
        }
        for (getter in arrayOf("getIsSend", "isSend")) {
            parseBoolean(KavaReflector.invoke(KavaReflector.findMethod(message.javaClass, getter), message))
                ?.let { return it }
        }
        for (field in arrayOf("field_isSend", "isSend")) {
            parseBoolean(KavaReflector.readField(message, field))?.let { return it }
        }
        return null
    }

    private fun parseBoolean(value: Any?): Boolean? {
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() == 1
            is String -> value.trim().toIntOrNull()?.let { it == 1 }
            else -> null
        }
    }

    private companion object {
        const val MASK_LAYOUT_CLASS = "com.tencent.mm.ui.base.MaskLayout"
    }
}
