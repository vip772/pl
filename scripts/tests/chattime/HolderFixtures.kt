package com.tencent.mm.ui.chatting.view

import android.view.View
import android.widget.TextView

/** Synthetic chat item holder: itemView for the root, timeTV for the time label. */
class FakeHolder {
    @JvmField
    var itemView: View? = View()

    @JvmField
    var timeTV: TextView = TextView()

    fun onBindView(holder: FakeHolder, message: Any?, position: Int) {
        holder.timeTV.text = "ignored"
    }
}

/** Holder without a time label: exercises the unsupported-holder cache. */
class FakeHolderNoTime {
    @JvmField
    var itemView: View? = View()
}
