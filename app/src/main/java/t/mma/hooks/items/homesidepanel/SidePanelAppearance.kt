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
    fun isPhoto(id: String) = id == "photo" || id.matches(Regex("photo_[a-f0-9]+"))
    fun photoRevision(id: String) = if(id == "photo") PHOTO else "${id}_revision"
    fun photoHeight(context: Context,id: String) = HomeSidePanelSettings.preferences(context).getInt("${id}_height",170).coerceIn(80,500)
    fun extraPhotos(context: Context): List<String> = HomeSidePanelSettings.preferences(context).getString("extra_photos","").orEmpty()
        .split(',').filter { it.startsWith("photo_") && isPhoto(it) }.distinct()
    fun addPhoto(context: Context): String {
        val id="photo_"+java.util.UUID.randomUUID().toString().replace("-","")
        HomeSidePanelSettings.preferences(context).edit().putString("extra_photos",(extraPhotos(context)+id).joinToString(",")).apply()
        return id
    }
    fun removePhoto(context: Context,id:String) {
        if(id !in extraPhotos(context)) return
        val prefs=HomeSidePanelSettings.preferences(context)
        prefs.edit().putString("extra_photos",extraPhotos(context).filter { it!=id }.joinToString(","))
            .putString(ORDER,order(context).filter { it!=id }.joinToString(","))
            .remove(cardOpacity(id)).remove("${id}_height").apply()
        SidePanelImages.clear(context,id)
    }
    val cards = listOf("clock", "photo", "shortcuts", "hchat")
    fun order(context: Context): List<String> {
        val available = cards + extraPhotos(context)
        val saved = HomeSidePanelSettings.preferences(context).getString(ORDER, "").orEmpty()
            .split(',').filter { it in available }.distinct()
        return saved + available.filter { it !in saved }
    }
    fun colors(context: Context): IntArray = DynamicRecallGradient.parts(
        HomeSidePanelSettings.preferences(context).getString(COLORS, DynamicRecallGradient.DEFAULT).orEmpty()
    ).map(android.graphics.Color::parseColor).toIntArray()
}
