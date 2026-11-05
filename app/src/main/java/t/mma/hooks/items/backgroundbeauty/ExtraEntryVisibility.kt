package t.mma.hooks.items.backgroundbeauty

/** Separate from Me-page preference rows; user options default to false. */
internal object ExtraEntryVisibility {
    const val COMPUTER = "hide_home_computer_login"
    const val VOICE = "hide_input_voice_icon"
    const val VOICE_DRAWABLE = 0x7f08052e
    val keys = setOf(COMPUTER, VOICE)
    // Verified display_regular / display_lock_regular. Do not hide tablets or watches.
    fun isComputerIcon(tag: Any?): Boolean = tag == 0x7f11028b || tag == 0x7f110289
}
