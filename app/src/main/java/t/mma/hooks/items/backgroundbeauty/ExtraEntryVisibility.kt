package t.mma.hooks.items.backgroundbeauty

/** Separate from Me-page preference rows; user options default to false. */
internal object ExtraEntryVisibility {
    const val COMPUTER = "hide_home_computer_login"
    const val VOICE = "hide_input_voice_icon"
    // t8.xml: w2n, the speech-input control inside the right edge of the text field.
    const val RIGHT_VOICE_ID = 0x7f0a64c1
    val keys = setOf(COMPUTER, VOICE)
    // Verified display_regular / display_lock_regular. Do not hide tablets or watches.
    fun isComputerIcon(tag: Any?): Boolean = tag == 0x7f11028b || tag == 0x7f110289
}
