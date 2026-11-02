package com.tencent.mm.ui.chatting.viewitems.fake

import android.view.ContextMenu
import android.view.View

/**
 * Synthetic single-message menu creator: the descriptor the production locator accepts
 * (void, three parameters, second is a View, third is ContextMenu$ContextMenuInfo).
 */
class FakeChattingItem {
    fun onCreateContextMenu(menu: Any?, view: View, info: ContextMenu.ContextMenuInfo?) {
        created++
    }

    companion object {
        @JvmField var created: Int = 0
    }
}
