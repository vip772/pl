package t.mma.hooks.items.homeextension

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal object HomeMusicSources {
    fun searchNetease(keyword: String): HomeMusicSong? = runCatching {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val root = get("https://music.163.com/api/search/get/web?s=$encoded&type=1&offset=0&limit=1")?.let(::JSONObject) ?: return null
        val item = root.optJSONObject("result")?.optJSONArray("songs")?.optJSONObject(0) ?: return null
        val id = item.optLong("id", 0L).takeIf { it > 0 } ?: return null
        val title = item.optString("name").ifBlank { keyword }
        val artist = item.optJSONArray("artists")?.optJSONObject(0)?.optString("name").orEmpty()
        val cover = item.optJSONObject("album")?.optString("picUrl").orEmpty()
        val url = get("https://interface3.music.163.com/api/song/url/v1?id=$id&level=standard&encodeType=mp3")?.let(::JSONObject)
            ?.optJSONArray("data")?.optJSONObject(0)?.optString("url").orEmpty()
        if (url.isBlank()) return null
        HomeMusicSong(title, artist, url, cover, "", "网易云", id.toString())
    }.getOrNull()

    fun searchKuwo(keyword: String): HomeMusicSong? = runCatching {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val root = get("https://search.kuwo.cn/r.s?all=$encoded&ft=music&itemset=web_2013&client=kt&pn=0&rn=1&encoding=utf8")?.let(::JSONObject) ?: return null
        val item = root.optJSONArray("abslist")?.optJSONObject(0) ?: return null
        val mid = item.optString("MUSICRID").removePrefix("MUSIC_").ifBlank { item.optString("DC_TARGETID") }
        if (mid.isBlank()) return null
        val title = item.optString("SONGNAME").ifBlank { keyword }
        val artist = item.optString("ARTIST").orEmpty()
        val cover = item.optString("web_albumpic_short").orEmpty().let { if (it.startsWith("//")) "https:$it" else it }
        val url = get("https://api.kuwo.cn/api/v1/www/music/playUrl?mid=$mid&type=convert_url3&br=320kmp3")?.let(::JSONObject)?.optString("data").orEmpty()
        if (url.isBlank()) return null
        HomeMusicSong(title, artist, url, cover, "", "酷我", mid)
    }.getOrNull()

    private fun get(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8000; connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.setRequestProperty("Referer", "https://music.163.com/")
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }
}