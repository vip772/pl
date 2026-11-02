package android.view

open class View {
    var tag: Any? = null
}

open class MenuItem {
    var itemId: Int = 0
}

/** Mirrors the nested interface name the production locator matches exactly. */
class ContextMenu {
    interface ContextMenuInfo
}
