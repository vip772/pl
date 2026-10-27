package t.mma.hooks.items.groupbar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import t.mma.preferences.HchatStorage
import java.util.UUID

data class BarGroup(val id: String, val name: String, val enabled: Boolean = true, val members: Set<String> = emptySet()) {
    val fixed: Boolean get() = id in GroupBarStore.fixedNames
}
object GroupBarStore {
    const val ID = "conversation_group_bar"
    const val PREFS = "Hchat_conversation_group_bar_config"
    const val ENABLE = "enable"
    const val STYLE = "style"
    const val ALPHA = "transparency"
    const val GROUPS = "groups"
    const val NEXT = "next_number"
    const val MAX = 10
    val fixedNames = linkedMapOf("all" to "全部", "friends" to "好友", "rooms" to "群聊", "official" to "公众号", "unread" to "未读")
    fun prefs(context: Context) = HchatStorage.preferences(context, PREFS)
    fun normalize(input: List<BarGroup>): List<BarGroup> {
        val seen = HashSet<String>()
        val result = ArrayList<BarGroup>()
        for (group in input) {
            if (group.id.isBlank() || !seen.add(group.id)) continue
            if (group.id !in fixedNames && result.count { !it.fixed } >= MAX - fixedNames.size) continue
            result += group.copy(name = fixedNames[group.id] ?: group.name.trim().take(20).ifBlank { "分组" },
                members = if (group.fixed) emptySet() else group.members.filterTo(linkedSetOf()) { it.isNotBlank() })
        }
        fixedNames.forEach { (id, name) -> if (seen.add(id)) result += BarGroup(id, name) }
        return result
    }
    fun load(context: Context): List<BarGroup> = runCatching {
        val array = JSONArray(prefs(context).getString(GROUPS, "[]"))
        normalize((0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            val members = item.optJSONArray("members") ?: JSONArray()
            BarGroup(item.getString("id"), item.optString("name"), item.optBoolean("enabled", true),
                (0 until members.length()).map { members.getString(it) }.toSet())
        })
    }.getOrElse { normalize(emptyList()) }
    fun save(context: Context, groups: List<BarGroup>) {
        val array = JSONArray()
        normalize(groups).forEach { group -> array.put(JSONObject().put("id", group.id).put("name", group.name)
            .put("enabled", group.enabled).put("members", JSONArray(group.members.toList()))) }
        prefs(context).edit().putString(GROUPS, array.toString()).apply()
    }
    fun add(context: Context): Boolean {
        val groups = load(context)
        if (groups.size >= MAX) return false
        val preferences = prefs(context)
        val number = preferences.getInt(NEXT, 1).coerceAtLeast(1)
        save(context, groups + BarGroup("custom_" + UUID.randomUUID(), "分组$number"))
        preferences.edit().putInt(NEXT, number + 1).apply()
        return true
    }
    fun move(context: Context, id: String, delta: Int) {
        val groups = load(context).toMutableList()
        val index = groups.indexOfFirst { it.id == id }
        val target = index + delta
        if (index >= 0 && target in groups.indices) {
            java.util.Collections.swap(groups, index, target)
            save(context, groups)
        }
    }
}

/** Immutable projections built on data/config changes; selecting a group is a map lookup. */
internal class GroupBarIndex<T>(
    rows: List<T>, groups: List<BarGroup>, identity: (T) -> String, unread: (T) -> Int,
    friends: Set<String>, officials: Set<String>
) {
    private val projections = HashMap<String, List<T>>()
    init {
        val buckets = groups.associate { it.id to ArrayList<T>() }
        for (row in rows) {
            val id = identity(row)
            val room = id.endsWith("@chatroom") || id.endsWith("@im.chatroom")
            val official = id in officials || id.startsWith("gh_") || id == "officialaccounts"
            val count = unread(row)
            for (group in groups) {
                val include = when (group.id) {
                    "all" -> true
                    "friends" -> !room && !official && id in friends
                    "rooms" -> room
                    "official" -> official
                    "unread" -> count > 0
                    else -> id in group.members
                }
                if (include) buckets.getValue(group.id).add(row)
            }
        }
        projections.putAll(buckets)
    }
    fun rows(id: String): List<T> = projections[id].orEmpty()
}