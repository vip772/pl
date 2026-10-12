package t.mma.hooks.items.hchatextra

import android.view.View
import android.view.ViewGroup
import t.mma.hooks.api.model.WeChatMessageTypes
import java.util.Collections
import java.util.IdentityHashMap

/** Resolves native content without reparenting host views or excluding shared avatar branches. */
internal object MessageDetailsCardHierarchy {
    /** Match the verified declaring class, including service-notify subclasses (as extends cs).
     * The caller reads cs's own field: a subclass's unrelated field named b must not shadow it. */
    fun templateHolderClass(type: Class<*>): Class<*>? {
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            if (current.name == "com.tencent.mm.ui.chatting.viewitems.cs") return current
            current = current.superclass
        }
        return null
    }
    fun contentBranch(row: ViewGroup, content: View): View? {
        if (content === row) return null
        var current = content
        val visited = Collections.newSetFromMap(IdentityHashMap<View, Boolean>())
        while (visited.add(current)) {
            val parent = current.parent as? View ?: return null
            if (parent === row) return current
            current = parent
        }
        return null
    }

    fun isCardMessage(type: Int, appSubtype: Int?): Boolean {
        val normalized = WeChatMessageTypes.normalize(type)
        // Personal/recommended contact cards are native types, not app-message subtypes.
        if (normalized == 42 || normalized == 66) return true
        if (normalized == WeChatMessageTypes.LOCATION) return true
        if (normalized != WeChatMessageTypes.APP && type != 44 && type != 82 && appSubtype == null) {
            return false
        }
        // Text/media/reference rows retain their native time/content anchor.
        return appSubtype !in setOf(1, 2, 8, 24, 57, 87)
    }
}
