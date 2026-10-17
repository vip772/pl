package t.mma.hooks.items.homesidepanel

import android.content.Context
import t.mma.hooks.items.antirecall.DynamicRecallGradient

internal object SidePanelAppearance {
    const val OPACITY = "panel_opacity"
    const val BACKGROUND = "panel_background_revision"
    const val PHOTO = "panel_photo_revision"
    const val EFFECT = "panel_effect"
    const val EFFECT_KIND = "panel_effect_kind"
    const val LIGHTNING = "panel_lightning"
    const val WIDTH = "panel_lightning_width"
    const val COLORS = "panel_lightning_colors"
    const val ORDER = "panel_card_order"
    fun cardOpacity(id: String) = "panel_card_opacity_$id"
    val cards = listOf("clock", "photo", "shortcuts", "hchat")
    fun order(context: Context): List<String> {
        val saved = HomeSidePanelSettings.preferences(context).getString(ORDER, "").orEmpty()
            .split(',').filter { it in cards }.distinct()
        return saved + cards.filter { it !in saved }
    }
    fun colors(context: Context): IntArray = DynamicRecallGradient.parts(
        HomeSidePanelSettings.preferences(context).getString(COLORS, DynamicRecallGradient.DEFAULT).orEmpty()
    ).map(android.graphics.Color::parseColor).toIntArray()
}
