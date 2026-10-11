package t.mma.hooks.items.hchatextra

import android.view.View
import android.view.ViewGroup

fun main() {
    val row = ViewGroup()
    val shared = ViewGroup()
    val avatar = View()
    val card = ViewGroup()
    row.addView(shared)
    shared.addView(avatar)
    shared.addView(card)
    check(MessageDetailsCardHierarchy.contentBranch(row, card) === shared)
    check(MessageDetailsCardHierarchy.contentBranch(row, avatar) === shared)
    println("PASS content and avatar sharing a row branch retain the complete card")

    var leaf = card
    repeat(20) { val next = ViewGroup(); leaf.addView(next); leaf = next }
    check(MessageDetailsCardHierarchy.contentBranch(row, leaf) === shared)
    println("PASS deeply nested native content resolves to the original row")

    check(MessageDetailsCardHierarchy.contentBranch(ViewGroup(), leaf) == null)
    check(MessageDetailsCardHierarchy.contentBranch(row, row) == null)
    val cycle = ViewGroup(); cycle.parent = cycle
    check(MessageDetailsCardHierarchy.contentBranch(row, cycle) == null)
    println("PASS foreign content, row itself and invalid parent cycles are rejected")

    for (subtype in listOf(3, 4, 5, 33, 36, 76, 92)) {
        check(MessageDetailsCardHierarchy.isCardMessage(49, subtype))
    }
    check(MessageDetailsCardHierarchy.isCardMessage(48, null))
    // Native contact cards must use the whole-card bottom anchor, not the inner time label.
    check(MessageDetailsCardHierarchy.isCardMessage(42, null))
    check(MessageDetailsCardHierarchy.isCardMessage(66, null))
    check(MessageDetailsCardHierarchy.isCardMessage(1040187441, 76))
    println("PASS links, music, mini programs and locations use card layout")

    for (subtype in listOf(1, 2, 8, 19, 24, 57, 87)) {
        check(!MessageDetailsCardHierarchy.isCardMessage(49, subtype))
    }
    for (type in listOf(1, 3, 34, 43, 47, 10000, 10002)) {
        check(!MessageDetailsCardHierarchy.isCardMessage(type, null))
    }
    println("PASS text, media, notices and reference rows retain existing layout")
}
