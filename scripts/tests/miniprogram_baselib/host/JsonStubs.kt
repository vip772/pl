package org.json

class JSONObject {
    private val values = LinkedHashMap<String, Any?>()
    fun optString(key: String): String = values[key] as? String ?: ""
    fun put(key: String, value: Any?): JSONObject {
        values[key] = value
        return this
    }
    fun raw(key: String): Any? = values[key]
}