package org.json
/**
 * Synthetic org.json surface: flat object parse/serialize only, enough for the
 * production video-progress payload rewrite. Not the Android implementation.
 */
class JSONObject {
    private val values = LinkedHashMap<String, Any?>()

    constructor()

    constructor(source: String) {
        require(source.trimStart().startsWith("{")) { "synthetic JSONObject requires an object literal" }
        values.putAll(parse(source))
    }

    fun optString(key: String): String = values[key] as? String ?: ""
    fun optInt(key: String, fallback: Int): Int = values[key] as? Int ?: fallback
    fun put(key: String, value: Any?): JSONObject {
        values[key] = value
        return this
    }
    fun raw(key: String): Any? = values[key]
    fun keys(): Set<String> = values.keys.toSet()

    override fun toString(): String {
        val body = values.entries.joinToString(",") { (key, value) ->
            val rendered = when (value) {
                null -> "null"
                is Int, is Long, is Boolean -> value.toString()
                else -> "\"" + value.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\""
            }
            "\"" + key + "\":" + rendered
        }
        return "{" + body + "}"
    }

    private companion object {
        fun parse(source: String): LinkedHashMap<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            var index = source.indexOf('{')
            if (index < 0) return out
            index++
            while (index < source.length) {
                while (index < source.length && source[index] != '"') {
                    if (source[index] == '}') return out
                    index++
                }
                if (index >= source.length) return out
                val keyEnd = source.indexOf('"', index + 1)
                if (keyEnd < 0) return out
                val key = source.substring(index + 1, keyEnd)
                index = source.indexOf(':', keyEnd)
                if (index < 0) return out
                index++
                while (index < source.length && source[index] == ' ') index++
                if (index >= source.length) return out
                if (source[index] == '"') {
                    val valueEnd = source.indexOf('"', index + 1)
                    if (valueEnd < 0) return out
                    out[key] = source.substring(index + 1, valueEnd)
                    index = valueEnd + 1
                } else {
                    var valueEnd = index
                    while (valueEnd < source.length && source[valueEnd] != ',' && source[valueEnd] != '}') {
                        valueEnd++
                    }
                    val text = source.substring(index, valueEnd).trim()
                    out[key] = when (text) {
                        "true" -> true
                        "false" -> false
                        "null" -> null
                        else -> text.toIntOrNull() ?: text
                    }
                    index = valueEnd
                }
            }
            return out
        }
    }
}