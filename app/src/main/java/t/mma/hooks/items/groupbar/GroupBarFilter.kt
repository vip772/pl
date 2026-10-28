package t.mma.hooks.items.groupbar

/** Immutable state shared by native loader threads and incremental updates. */
internal class GroupBarFilter(
    val group: BarGroup?, val friends: Set<String> = emptySet(), val officials: Set<String> = emptySet()
) {
    fun accepts(id: String, unread: Int): Boolean {
        val current = group ?: return true
        if (id.startsWith("wxid_hchat_group_") || id.startsWith("hchat_conv_group:")) return false
        val room = id.endsWith("@chatroom") || id.endsWith("@im.chatroom")
        val official = id in officials || id.startsWith("gh_") || id == "officialaccounts"
        return when (current.id) {
            "all" -> true
            "friends" -> !room && !official && id in friends
            "rooms" -> room
            "official" -> official
            "unread" -> unread > 0
            else -> id in current.members
        }
    }
    private val cachedPredicate: String? by lazy(LazyThreadSafetyMode.PUBLICATION) { buildPredicate() }
    fun predicate(): String? = if (group == null) null else {
        val real = "rconversation.username NOT LIKE 'wxid!_hchat!_group!_%' ESCAPE '!' AND rconversation.username NOT LIKE 'hchat!_conv!_group:%' ESCAPE '!'"
        cachedPredicate?.let { "($real) AND ($it)" } ?: real
    }
    fun acceptsNativeParent(native: Boolean, parent: String?): Boolean = native ||
        (group != null && parent != null && (parent.startsWith("wxid_hchat_group_") || parent.startsWith("hchat_conv_group:")))
    private fun buildPredicate(): String? {
        val current = group ?: return null
        fun quote(value: String) = "'" + value.replace("'", "''") + "'"
        fun ids(values: Set<String>): String = if (values.isEmpty()) "0" else
            "rconversation.username IN (" + values.sorted().joinToString(",", transform = ::quote) + ")"
        val room = "(rconversation.username LIKE '%@chatroom' OR rconversation.username LIKE '%@im.chatroom')"
        val official = "(rconversation.username LIKE 'gh!_%' ESCAPE '!' OR rconversation.username='officialaccounts' OR ${ids(officials)})"
        return when (current.id) {
            "all" -> null
            "rooms" -> room
            "official" -> official
            "friends" -> "(NOT $room AND NOT $official AND ${ids(friends)})"
            "unread" -> "rconversation.unReadCount > 0"
            else -> ids(current.members)
        }
    }
}

/** Only used inside jo5.e.g's scoped native query; never rewrites arbitrary module queries. */
internal object GroupBarSql {
    fun rewrite(sql: String, predicate: String?): String {
        if (predicate == null || "hchat_group_bar" in sql) return sql
        // Tokens exclude strings, quoted identifiers and comments; track nesting for ORDER/LIMIT.
        val tokens = ArrayList<Pair<String, Int>>()
        val allTokens = ArrayList<Pair<String, Int>>()
        var depth = 0; var i = 0
        while (i < sql.length) {
            val c = sql[i]
            if (c == '\'' || c == '"' || c == '`' || c == '[') {
                val start = i
                val end = if (c == '[') ']' else c
                i++
                while (i < sql.length) {
                    if (sql[i++] == end) {
                        if (i < sql.length && sql[i] == end) i++ else break
                    }
                }
                if (depth == 0 && c != '\'' && sql.substring(start + 1, (i - 1).coerceAtLeast(start + 1)).equals("rconversation", true)) {
                    tokens += "rconversation" to start
                }
            } else if (c == '-' && sql.getOrNull(i + 1) == '-') {
                while (i < sql.length && sql[i] != '\n') i++
            } else if (c == '/' && sql.getOrNull(i + 1) == '*') {
                val end = sql.indexOf("*/", i + 2); if (end < 0) return sql; i = end + 2
            } else if (c == '(') { depth++; i++ }
            else if (c == ')') { depth--; if (depth < 0) return sql; i++ }
            else if (c.isLetter() || c == '_') {
                val start = i++
                while (i < sql.length && (sql[i].isLetterOrDigit() || sql[i] == '_')) i++
                val token = sql.substring(start, i).lowercase(java.util.Locale.ROOT) to start
                allTokens += token
                if (depth == 0) tokens += token
            } else i++
        }
        if (depth != 0 || tokens.firstOrNull()?.first != "select") return sql
        if (tokens.none { it.first == "rconversation" } || tokens.any { it.first in setOf("union", "intersect", "except") }) return sql
        val where = tokens.firstOrNull { it.first == "where" } ?: return sql
        val suffix = tokens.firstOrNull { it.second > where.second && it.first in setOf("order", "group", "limit", "having") }?.second
            ?: sql.trimEnd().removeSuffix(";").length
        // Parenthesize the existing WHERE to preserve OR precedence and placeholder ordering.
        val conditionStart = where.second + 5
        // Expand only the native parent-null atom, retaining all other native filters.
        var condition = sql.substring(conditionStart, suffix)
        allTokens.windowed(3).asReversed().forEach { atom ->
            if (atom.map { it.first } == listOf("parentref", "is", "null") &&
                atom[0].second >= conditionStart && atom[2].second < suffix) {
                val start = atom[0].second
                val end = atom[2].second + 4
                val text = sql.substring(start, end)
                if (Regex("parentRef\\s+is\\s+null", RegexOption.IGNORE_CASE).matches(text) && sql.getOrNull(start - 1) != '.') {
                    condition = condition.replaceRange(start - conditionStart, end - conditionStart,
                        "($text OR parentRef LIKE 'wxid!_hchat!_group!_%' ESCAPE '!' OR parentRef LIKE 'hchat!_conv!_group:%' ESCAPE '!')")
                }
            }
        }
        return sql.substring(0, conditionStart) + " (" + condition.trim() +
            ") AND (" + predicate + ") /*hchat_group_bar*/ " + sql.substring(suffix)
    }
}
