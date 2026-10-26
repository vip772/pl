package t.mma.hooks.items.backgroundbeauty

/** Only the preference keys verified in the supplied WeChat 8.0.78 MoreTabUI/b5.xml. */
internal object MeEntryVisibility {
    enum class Entry(val key: String, val title: String, val nativeKeys: Set<String>) {
        FAVORITE("me_hide_favorite", "收藏", setOf("settings_mm_favorite")),
        MOMENTS("me_hide_moments", "朋友圈", setOf("settings_my_album")),
        WORKS("me_hide_works", "作品", setOf("settings_my_finder_album", "settings_my_finder_and_biz")),
        CARDS("me_hide_cards", "小店与卡包", setOf("settings_mm_cardpackage", "settings_mm_cardpackage_new")),
        EMOJI("me_hide_emoji", "表情", setOf("settings_emoji_store"))
    }

    fun hiddenKeys(enabled: (String) -> Boolean): Set<String> =
        Entry.values().filter { enabled(it.key) }.flatMap { it.nativeKeys }.toSet()

    // The host's own hidden set is restored after rebuilding its visible list.
    // Disabling our filter therefore never resurrects a host-hidden/gray entry.
    fun overlay(host: MutableSet<String>, requested: Set<String>): Set<String> {
        val added = requested.filterNot { it in host }.toSet()
        host.addAll(added)
        return added
    }
}
