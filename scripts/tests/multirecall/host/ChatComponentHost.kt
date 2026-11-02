package com.tencent.mm.ui.chatting.component.fake

import android.view.MenuItem
import com.tencent.mm.storage.fake.FakeMessage
import java.util.ArrayList

/** Synthetic chat component: menu creation, menu click and the native exit entry. */
open class FakeChattingComponent {
    val selected = ArrayList<FakeMessage>()
    var exitCalls = 0
    var menuCreates = 0

    fun onCreateMMMenu(menu: Any?) {
        menuCreates++
    }

    fun onMMMenuItemSelected(item: MenuItem?, index: Int) {
        // Deliberately no side effect: the native handler must not run for our own item.
    }

    fun exitMultiSelect() {
        exitCalls++
    }
}

/** Synthetic popup menu: only the two entry points the runtime calls. */
class FakeMenu {
    val items = LinkedHashMap<Int, String>()
    var findItemCalls = 0

    fun findItem(itemId: Int): Any? {
        findItemCalls++
        return items[itemId]?.let { this }
    }

    fun add(groupId: Int, itemId: Int, order: Int, title: CharSequence): Any? {
        items[itemId] = title.toString()
        return this
    }

    fun reset() {
        items.clear()
        findItemCalls = 0
    }
}
