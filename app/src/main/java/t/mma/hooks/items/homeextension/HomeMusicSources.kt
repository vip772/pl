package t.mma.hooks.items.homeextension
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
internal object HomeMusicSources {
    fun searchNetease(keyword:String)=searchMany(keyword,"网易云").firstNotNullOfOrNull{resolve(it)}
    fun searchKuwo(keyword:String)=searchMany(keyword,"酷我").firstNotNullOfOrNull{resolve(it)}
    fun searchMany(keyword:String,source:String):List<HomeMusicSong> = runCatching {
        val q=URLEncoder.encode(keyword,"UTF-8")
        if(source=="网易云") {
            val songs=JSONObject(get("https://music.163.com/api/search/get/web?s=$q&type=1&offset=0&limit=20")?:return emptyList()).optJSONObject("result")?.optJSONArray("songs")?:return emptyList()
            (0 until songs.length()).mapNotNull{i->val o=songs.getJSONObject(i);val id=o.optLong("id");if(id<=0)null else HomeMusicSong(o.optString("name"),o.optJSONArray("artists")?.optJSONObject(0)?.optString("name").orEmpty(),"",o.optJSONObject("album")?.optString("picUrl").orEmpty(),"",source,id.toString())}
        } else {
            val songs=JSONObject(get("https://search.kuwo.cn/r.s?all=$q&ft=music&pn=0&rn=20&encoding=utf8&rformat=json")?:return emptyList()).optJSONArray("abslist")?:return emptyList()
            (0 until songs.length()).mapNotNull{i->val o=songs.getJSONObject(i);val id=o.optString("MUSICRID").removePrefix("MUSIC_").ifBlank{o.optString("DC_TARGETID")};if(id.isBlank())null else HomeMusicSong(o.optString("SONGNAME"),o.optString("ARTIST"),"",o.optString("web_albumpic_short").let{if(it.startsWith("//"))"https:$it" else if(it.startsWith("https://"))it else ""},"",source,id)}
        }
    }.getOrDefault(emptyList())
    fun resolve(s:HomeMusicSong,quality:Int=0):HomeMusicSong?=runCatching {
        if(s.sourceId.isBlank())return s.takeIf{it.playUrl.isNotBlank()}
        val id=URLEncoder.encode(s.sourceId,"UTF-8")
        if(s.source=="网易云") {
            val level=arrayOf("standard","exhigh","lossless")[quality.coerceIn(0,2)]
            val primary=runCatching{JSONObject(get("https://interface3.music.163.com/api/song/url/v1?id=$id&level=$level&encodeType=flac")?:"{}").optJSONArray("data")?.optJSONObject(0)}.getOrNull()
            val data=if(primary?.optString("url").orEmpty().startsWith("http"))primary!! else JSONObject(get("https://music.163.com/api/song/enhance/player/url?ids=%5B$id%5D&br=${intArrayOf(128000,320000,999000)[quality.coerceIn(0,2)]}")?:return null).optJSONArray("data")?.optJSONObject(0)?:return null
            val url=data.optString("url");if(!url.startsWith("http"))return null
            val actual=data.optString("level");if(quality>0&&actual!=level)return null
            val lyric=if(s.lyric.isBlank())runCatching{JSONObject(get("https://music.163.com/api/song/lyric?id=$id&lv=1&kv=-1&tv=-1")?:"{}").optJSONObject("lrc")?.optString("lyric").orEmpty()}.getOrDefault("")else s.lyric
            s.copy(playUrl=url,lyric=lyric)
        }else if(s.source=="酷我") {
            val br=arrayOf("128kmp3","320kmp3","2000kflac")[quality.coerceIn(0,2)]
            val root=JSONObject(get("https://api.kuwo.cn/api/v1/www/music/playUrl?mid=$id&type=convert_url3&br=$br")?:return null)
            val data=root.optJSONObject("data");val url=data?.optString("url")?:root.optString("data")
            if(!url.startsWith("http"))return null
            // Do not claim a quality upgrade when the provider does not identify the returned format.
            if(quality==2&&!url.contains(".flac",true))return null
            if(quality==1&&data?.optInt("bitrate",0)!=320000&&data?.optString("bitrate")!="320")return null
            s.copy(playUrl=url)
        }else s.takeIf{it.playUrl.isNotBlank()}
    }.getOrNull()
    private fun get(url:String):String? {
        val c=URL(url).openConnection() as HttpURLConnection
        return try{c.connectTimeout=8000;c.readTimeout=8000;c.setRequestProperty("User-Agent","Mozilla/5.0");c.setRequestProperty("Referer",if(url.contains("kuwo.cn"))"https://www.kuwo.cn/" else "https://music.163.com/");if(c.responseCode !in 200..299)null else c.inputStream.bufferedReader().use{it.readText()}}finally{c.disconnect()}
    }
}
