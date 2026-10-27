package t.mma.hooks.items.floatingchat

internal object FloatingChatOpacity {
    // -1 retains the pre-existing surface, including its legacy alpha.
    fun color(color: Int, transparency: Int): Int {
        if (transparency < 0) return color
        val alpha = ((100 - transparency.coerceIn(0, 100)) * 255 + 50) / 100
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }
}
