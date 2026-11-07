package t.mma.hooks.items.profileid

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import t.mma.preferences.HchatStorage

/** Accessed only by the profile worker. Account and group are separate persistent namespaces. */
internal class JoinTimeBindingStore(context: Context) {
    private val prefs = HchatStorage.runtimePreferences(context.applicationContext ?: context, "Hchat_member_join_bindings_v1")

    data class Binding(val time: Long, val source: String)
    class Group {
        val members = linkedSetOf<String>()
        val bindings = linkedMapOf<String, Binding>()
        // A departure tombstone prevents old history/native data from reviving an old membership.
        val floors = linkedMapOf<String, Long>()
        var initialized = false
        // Only populated by a successful, observed create-chatroom request.
        var creator = ""
        val initialMembers = linkedSetOf<String>()

        fun recordCreation(account: String, selected: Set<String>) {
            if (creator.isNotEmpty() || initialized || bindings.isNotEmpty()) return
            creator = account
            initialMembers.addAll(selected)
            members.addAll(selected)
            members.add(account)
            initialized = true
        }

        fun bindInitialMembers(creatorTime: Long) {
            if (creator.isBlank() || creatorTime <= 0L || creator !in members || creator in floors) return
            val times = initialMembers.filter { it in members && it !in floors }.associateWith { creatorTime }
            for ((id, time) in times) {
                val old = bindings[id]
                if (old == null || old.source == "history" || old.source == "creation")
                    bindings[id] = Binding(time, "creation")
            }
        }

        fun reconcile(current: Set<String>, now: Long) {
            if (current.isEmpty()) return // Empty/unavailable reads never prove departure.
            if (initialized) {
                for (id in members - current) {
                    bindings.remove(id)
                    initialMembers.remove(id)
                    if (id == creator) initialMembers.clear()
                    floors[id] = maxOf(floors[id] ?: 0L, now)
                }
            }
            members.clear()
            members.addAll(current)
            initialized = true
            bindings.keys.retainAll(current)
        }

        fun merge(times: Map<String, Long>, source: (String) -> String) {
            for ((id, time) in times) {
                if (id !in members || time <= (floors[id] ?: 0L) || time <= 0L) continue
                val incomingSource = source(id)
                val existing = bindings[id]
                if (existing == null || incomingSource == "native" || incomingSource == "server") {
                    bindings[id] = Binding(time, incomingSource)
                }
            }
        }
    }

    private fun key(account: String, group: String) = "${account.length}:$account$group"

    fun contains(account: String, groupId: String) = prefs.contains(key(account, groupId))

    fun read(account: String, groupId: String): Group {
        val group = Group()
        val raw = prefs.getString(key(account, groupId), null) ?: return group
        val json = JSONObject(raw)
        val members = json.getJSONArray("members")
        for (i in 0 until members.length()) group.members.add(members.getString(i))
        group.initialized = json.optBoolean("initialized", false)
        group.creator = json.optString("creator", "")
        val initial = json.optJSONArray("initialMembers")
        if (initial != null) for (i in 0 until initial.length()) group.initialMembers.add(initial.getString(i))
        val bindings = json.getJSONObject("bindings")
        for (id in bindings.keys()) {
            val entry = bindings.getJSONObject(id)
            group.bindings[id] = Binding(entry.getLong("time"), entry.getString("source"))
        }
        val floors = json.getJSONObject("floors")
        for (id in floors.keys()) group.floors[id] = floors.getLong(id)
        return group
    }

    fun write(account: String, groupId: String, group: Group) {
        val bindings = JSONObject()
        for ((id, entry) in group.bindings) {
            bindings.put(id, JSONObject().put("time", entry.time).put("source", entry.source))
        }
        val floors = JSONObject()
        for ((id, floor) in group.floors) floors.put(id, floor)
        val json = JSONObject().put("members", JSONArray(group.members.toList()))
            .put("initialized", group.initialized).put("bindings", bindings).put("floors", floors)
            .put("creator", group.creator).put("initialMembers", JSONArray(group.initialMembers.toList()))
        check(prefs.edit().putString(key(account, groupId), json.toString()).commit()) {
            "Cannot persist group join bindings"
        }
    }
}
