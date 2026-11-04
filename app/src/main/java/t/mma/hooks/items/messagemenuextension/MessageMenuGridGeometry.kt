package t.mma.hooks.items.messagemenuextension

/** Pixel geometry only: read density/window bounds afresh for every native menu. */
internal object MessageMenuGridGeometry {
    data class Width(val item: Int, val row: Int)

    fun width(available: Int, padding: Int, rowPadding: Int, columns: Int, nativeItem: Int): Width {
        require(columns in 5..8 && padding >= rowPadding && rowPadding >= 0)
        require(available >= padding + columns) { "Window too narrow for menu columns" }
        val item = minOf(nativeItem.coerceAtLeast(1), (available - padding) / columns)
        return Width(item, item * columns + rowPadding)
    }

    fun viewport(content: Int, row: Int, rows: Int, padding: Int, separators: Int, available: Int): Int {
        require(content > 0 && row > 0 && rows in 3..5 && available > 0)
        return minOf(content, row * rows + padding + separators, available)
    }
}