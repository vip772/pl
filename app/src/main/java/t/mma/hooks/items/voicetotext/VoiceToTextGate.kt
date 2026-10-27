package t.mma.hooks.items.voicetotext

internal class VoiceToTextGate {
    private val pending = HashSet<Long>()
    private val attempted = object : LinkedHashMap<Long, Boolean>(256, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Boolean>?) = size > 512
    }
    fun offer(id: Long): Boolean = id > 0 && pending.size < 16 && !attempted.containsKey(id) && pending.add(id)
    fun release(id: Long) { pending.remove(id) }
    fun finish(id: Long) { if (pending.remove(id)) attempted[id] = true }
    fun clearPending() { pending.clear() }
    fun clear() { pending.clear(); attempted.clear() }
}