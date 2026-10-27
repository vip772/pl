package t.mma.hooks.items.homeextension

/** Keeps only the current lyric source; playback ticks perform a binary search. */
internal class HomeMusicLyricTimeline {
    private var source: String? = null
    private var lines = emptyList<Pair<Int, String>>()
    fun text(lyric: String, position: Int, title: String): String {
        if (source != lyric) {
            source = lyric
            lines = buildList {
                lyric.lineSequence().forEach { line ->
                    val text = tags.replace(line, "").trim()
                    timestamps.findAll(line).forEach { match ->
                        val minutes = match.groupValues[1].toLongOrNull()
                        val seconds = match.groupValues[2].toDoubleOrNull()
                        if (minutes != null && seconds != null) {
                            val time = minutes * 60000.0 + seconds * 1000.0
                            if (time in 0.0..Int.MAX_VALUE.toDouble()) add(time.toInt() to text)
                        }
                    }
                }
            }.sortedBy { it.first }
        }
        var low = 0
        var high = lines.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (lines[middle].first <= position) low = middle + 1 else high = middle
        }
        return if (low > 0) lines[low - 1].second
            else if (lines.isEmpty()) "$title · 暂无同步歌词" else title
    }
    private companion object {
        val timestamps = Regex("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\]")
        val tags = Regex("\\[[^]]*\\]")
    }
}
