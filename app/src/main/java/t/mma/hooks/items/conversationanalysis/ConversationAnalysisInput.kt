package t.mma.hooks.items.conversationanalysis

/** Even time-ordered sampling without constructing a map/string for every database row. */
internal object ConversationAnalysisInput {
    fun positions(total: Int, limit: Int): IntArray {
        if (total <= 0 || limit <= 0) return IntArray(0)
        val size = minOf(total, limit)
        if (size == 1) return intArrayOf(total - 1)
        return IntArray(size) { index ->
            kotlin.math.round(index.toDouble() * (total - 1) / (size - 1)).toInt()
        }
    }

    fun autoLimit(capacity: String): Int = when (capacity) {
        "256k" -> 600
        "512k" -> 1200
        "1m" -> 2500
        "2m" -> 5000
        else -> 300
    }
}
