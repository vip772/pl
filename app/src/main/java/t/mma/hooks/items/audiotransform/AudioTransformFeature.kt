package t.mma.hooks.items.audiotransform

import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext

class AudioTransformFeature : BaseFeature() {
    override fun featureId(): String = ID

    override fun name(): String = "音频转换"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(AudioTransformSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
    }

    companion object {
        const val ID = "audio_transform"
    }
}
