package t.mma.hooks.items.homeextension

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** APK 音源协议的定点迁移：只启用已验证的 QQ 搜索/取流及网易云聚合接口。 */
internal object HomeMusicAggregate {
    const val CHANNEL = "聚合音乐"
    const val QQ = "聚合QQ"
    const val NETEASE = "聚合网易云"
    private const val LIMIT = 80
    private const val GD = "https://music-api.gdstudio.xyz/api.php"
    private val searches = Executors.newFixedThreadPool(2) { task ->
        Thread(task, "Hchat-AggregateMusic").apply { isDaemon = true }
    }

    fun owns(source: String) = source == QQ || source == NETEASE

    fun search(keyword: String): List<HomeMusicSong> {
        if (keyword.isBlank()) return emptyList()
        val tasks = searches.invokeAll(listOf(
            Callable { searchQQ(keyword) },
            Callable { searchNetease(keyword) }
        ), 28, TimeUnit.SECONDS)
        val lists = tasks.map { runCatching { it.get() }.getOrDefault(emptyList()) }
        return merge(lists[0], lists[1])
    }

    internal fun merge(qq: List<HomeMusicSong>, netease: List<HomeMusicSong>): List<HomeMusicSong> {
        val ids = HashSet<String>()
        val tracks = HashSet<String>()
        val result = ArrayList<HomeMusicSong>()
        // 交替合并，避免一种平台的结果占满 80 首。
        for (i in 0 until maxOf(qq.size, netease.size)) {
            for (list in listOf(qq, netease)) {
                val song = list.getOrNull(i) ?: continue
                val id = song.source + ":" + song.sourceId
                val track = normalize(song.title) + "\u0000" + normalize(song.artist)
                if (id in ids || track in tracks) continue
                ids += id
                tracks += track
                result += song
                if (result.size == LIMIT) return result
            }
        }
        return result
    }

    private fun normalize(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), "")
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun validUrl(value: String) = runCatching {
        val url = URL(value)
        url.protocol in listOf("http", "https") && url.host.isNotBlank()
    }.getOrDefault(false)

    private fun json(url: String, referer: String = "https://music.gdstudio.xyz/"): JSONObject? =
        get(url, referer)?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun get(url: String, referer: String): String? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.setRequestProperty("Referer", referer)
            if (connection.responseCode !in 200..299) null
            else connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun searchQQ(keyword: String): List<HomeMusicSong> {
        val result = ArrayList<HomeMusicSong>()
        for (page in 1..3) {
            if (Thread.currentThread().isInterrupted) break
            val root = json("https://c.y.qq.com/soso/fcgi-bin/search_for_qq_cp?p=$page&n=30&w=${encode(keyword)}&format=json", "https://y.qq.com/") ?: break
            val data = root.optJSONObject("data")?.optJSONObject("song") ?: break
            val songs = data.optJSONArray("list") ?: break
            val before = result.size
            for (i in 0 until songs.length()) {
                val song = songs.optJSONObject(i) ?: continue
                val id = song.optString("songmid").ifBlank { song.optString("mid") }
                val title = song.optString("songname").ifBlank { song.optString("name") }
                if (id.isBlank() || title.isBlank() || result.any { it.sourceId == id }) continue
                val singers = song.optJSONArray("singer")
                val artist = (0 until (singers?.length() ?: 0)).mapNotNull {
                    singers?.optJSONObject(it)?.optString("name")?.takeIf { name -> name.isNotBlank() }
                }.joinToString(" / ")
                val album = song.optString("albummid").ifBlank { song.optJSONObject("album")?.optString("mid").orEmpty() }
                val cover = if (album.isBlank()) "" else "https://y.gtimg.cn/music/photo_new/T002R300x300M000$album.jpg"
                result += HomeMusicSong(title, artist, "", cover, "", QQ, id)
                if (result.size == LIMIT) return result
            }
            if (result.size == before || songs.length() < 30 || page * 30 >= data.optInt("totalnum", Int.MAX_VALUE)) break
        }
        return result
    }

    private fun searchNetease(keyword: String): List<HomeMusicSong> {
        val body = get("$GD?types=search&source=netease&name=${encode(keyword)}&count=$LIMIT&pages=1", "https://music.gdstudio.xyz/") ?: return emptyList()
        val songs = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()
        return (0 until minOf(songs.length(), LIMIT)).mapNotNull { i ->
            val song = songs.optJSONObject(i) ?: return@mapNotNull null
            val id = song.optString("id")
            val title = song.optString("name")
            if (id.isBlank() || title.isBlank()) return@mapNotNull null
            val artists = song.optJSONArray("artist")
            val artist = (0 until (artists?.length() ?: 0)).mapNotNull { artists?.optString(it)?.takeIf { name -> name.isNotBlank() } }.joinToString(" / ")
            HomeMusicSong(title, artist, "", "", "", NETEASE, id)
        }.distinctBy { it.sourceId }
    }

    fun resolve(song: HomeMusicSong, quality: Int): HomeMusicSong? {
        if (song.sourceId.isBlank()) return null
        return when (song.source) {
            QQ -> {
                if (quality != 0) return null
                val root = json("https://a.aa.cab/qq.music?msg=${encode(song.title + " " + song.artist.replace(" / ", " "))}&n=1&type=1") ?: return null
                val data = root.optJSONObject("data") ?: return null
                // 搜索式取流必须确认 songmid，不能把翻唱或同名版本当作选中的歌曲。
                if (data.optString("mid") != song.sourceId) return null
                val url = data.optString("music")
                if (!validUrl(url)) return null
                song.copy(playUrl = java.net.URI(url).toASCIIString(), coverUrl = song.coverUrl.ifBlank { data.optString("cover").takeIf(::validUrl).orEmpty() })
            }
            NETEASE -> {
                val br = intArrayOf(128, 320, 740)[quality.coerceIn(0, 2)]
                val data = json("$GD?types=url&source=netease&id=${encode(song.sourceId)}&br=$br") ?: return null
                val url = data.optString("url")
                if (!validUrl(url) || (quality > 0 && data.optInt("br") < br)) return null
                val lyrics = if (song.lyric.isBlank()) json("https://music.163.com/api/song/lyric?id=${encode(song.sourceId)}&lv=1&kv=-1&tv=-1", "https://music.163.com/")?.optJSONObject("lrc")?.optString("lyric").orEmpty() else song.lyric
                song.copy(playUrl = java.net.URI(url).toASCIIString(), lyric = lyrics)
            }
            else -> null
        }
    }
}
