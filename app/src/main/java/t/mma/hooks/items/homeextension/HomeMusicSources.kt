package t.mma.hooks.items.homeextension
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
internal object HomeMusicSources {
    fun searchNetease(keyword:String)=searchMany(keyword,"网易云").firstNotNullOfOrNull{resolve(it)}
    fun searchKuwo(keyword:String)=searchMany(keyword,"酷我").firstNotNullOfOrNull{resolve(it)}
    fun searchMany(keyword:String,source:String):List<HomeMusicSong> {
        val direct = if(source == "网易云" || source == "酷我") searchDirect(keyword, source) else emptyList()
        return direct.ifEmpty { backupSearch(keyword, source) }
    }
    private fun searchDirect(keyword:String,source:String):List<HomeMusicSong> = runCatching {
        val q=URLEncoder.encode(keyword,"UTF-8")
        if(source=="网易云") {
            val songs=JSONObject(get("https://music.163.com/api/search/get/web?s=$q&type=1&offset=0&limit=20")?:return emptyList()).optJSONObject("result")?.optJSONArray("songs")?:return emptyList()
            (0 until songs.length()).mapNotNull{i->val o=songs.getJSONObject(i);val id=o.optLong("id");if(id<=0)null else HomeMusicSong(o.optString("name"),o.optJSONArray("artists")?.optJSONObject(0)?.optString("name").orEmpty(),"",o.optJSONObject("album")?.optString("picUrl").orEmpty(),"",source,id.toString())}
        } else {
            val songs=JSONObject(get("https://search.kuwo.cn/r.s?all=$q&ft=music&pn=0&rn=20&encoding=utf8&rformat=json")?:return emptyList()).optJSONArray("abslist")?:return emptyList()
            (0 until songs.length()).mapNotNull{i->val o=songs.getJSONObject(i);val id=o.optString("MUSICRID").removePrefix("MUSIC_").ifBlank{o.optString("DC_TARGETID")};if(id.isBlank())null else HomeMusicSong(o.optString("SONGNAME"),o.optString("ARTIST"),"",o.optString("web_albumpic_short").let{if(it.startsWith("//"))"https:$it" else if(it.startsWith("https://"))it else ""},"",source,id)}
        }
    }.getOrDefault(emptyList())
    fun resolve(s:HomeMusicSong,quality:Int=0):HomeMusicSong? {
        val q = quality.coerceIn(0,2)
        if(HomeMusicAggregate.owns(s.source)) return runCatching{HomeMusicAggregate.resolve(s,q)}.getOrNull()
        val direct = if(s.source == "网易云" || s.source == "酷我") resolveDirect(s,q) else null
        return direct ?: if(s.source in listOf("网易云","酷我","咪咕")) backupResolve(s,q) else s.takeIf{it.playUrl.isNotBlank()}
    }
    private fun resolveDirect(s:HomeMusicSong,quality:Int=0):HomeMusicSong?=runCatching {
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
    private const val BACKUP = "https://oiapi.net/api/"
    private fun endpoint(source:String) = when(source){"网易云"->"Music_163";"酷我"->"Kuwo";"咪咕"->"MiGu_Music";else->null}
    private fun encode(s:String)=URLEncoder.encode(s,"UTF-8")
    private fun json(url:String)=runCatching{get(url)?.let(::JSONObject)}.getOrNull()
    private fun validUrl(s:String)=runCatching{val u=URL(s);u.protocol in listOf("http","https") && u.host.isNotBlank()}.getOrDefault(false)
    private fun dataItem(root:JSONObject):JSONObject?=root.optJSONObject("data") ?: root.optJSONArray("data")?.optJSONObject(0)
    private fun trackId(o:JSONObject,source:String)=when(source){
        "网易云"->o.optString("id")
        "酷我"->o.optString("rid").ifBlank{o.optString("id")}.removePrefix("MUSIC_")
        else->o.optString("contentId")
    }
    private fun backupList(keyword:String,source:String):List<JSONObject> {
        val api=endpoint(source)?:return emptyList()
        val param=if(source=="网易云")"name" else "msg"
        val root=json("$BACKUP$api?$param=${encode(keyword)}&limit=20&type=json")?:return emptyList()
        if(root.optInt("code",-1)!=(if(source=="网易云")0 else 1))return emptyList()
        val arr=root.optJSONArray("data")?:return emptyList()
        return (0 until arr.length()).mapNotNull{arr.optJSONObject(it)}
    }
    private fun backupSearch(keyword:String,source:String):List<HomeMusicSong> = backupList(keyword,source).mapNotNull { o ->
        val id=trackId(o,source);val title=o.optString(if(source=="网易云")"name" else "song")
        if(id.isBlank()||title.isBlank())null else {
            val artist=o.optString("singer").ifBlank{
                val singers=o.optJSONArray("singers")
                singers?.optJSONObject(0)?.optString("name") ?: singers?.optString(0).orEmpty()
            }
            val cover=o.optString(if(source=="网易云")"picurl" else "picture")
            HomeMusicSong(title,artist,"",cover.takeIf{validUrl(it)}.orEmpty(),"",source,id)
        }
    }.distinctBy{it.sourceId}
    private fun backupResolve(s:HomeMusicSong,quality:Int):HomeMusicSong?=runCatching {
        val api=endpoint(s.source)?:return null
        if(s.sourceId.isBlank())return null
        val url=if(s.source=="网易云") "$BACKUP$api?id=${encode(s.sourceId)}" else {
            // Search by stable identity each time; never persist a provider result index.
            var keyword=s.title
            var list=backupList(keyword,s.source)
            var index=list.indexOfFirst{trackId(it,s.source)==s.sourceId.removePrefix("MUSIC_")}
            if(index<0){
                keyword=s.title+" "+s.artist
                list=backupList(keyword,s.source)
                index=list.indexOfFirst{trackId(it,s.source)==s.sourceId.removePrefix("MUSIC_")}
            }
            if(index<0)return null
            val br=if(s.source=="咪咕")arrayOf("PQ","HQ","SQ")[quality] else arrayOf("128kmp3","320kmp3","2000kflac")[quality]
            "$BACKUP$api?msg=${encode(keyword)}&n=${index+1}&br=$br&type=json"
        }
        val root=json(url)?:return null
        if(root.optInt("code",-1)!=(if(s.source=="网易云")0 else 1))return null
        val item=dataItem(root)?:return null
        if(trackId(item,s.source)!=s.sourceId.removePrefix("MUSIC_"))return null
        val play=item.optString("url");if(!validUrl(play))return null
        val format=item.optJSONObject("formatInfo")
        val bitrate=(format?.optString("bitrate")?:item.optString("bitrate")).filter{it.isDigit()}.toIntOrNull()?:0
        val codec=(format?.optString("format")?:item.optString("format")).lowercase()
        val level=item.optString("quality").ifBlank{item.optString("level")}
        if(quality==1 && bitrate !in listOf(320,320000) && level !in listOf("HQ","exhigh"))return null
        if(quality==2 && codec!="flac" && level !in listOf("SQ","lossless","hires"))return null
        var lyric=s.lyric
        if(lyric.isBlank() && s.source=="网易云")lyric=json("https://music.163.com/api/song/lyric?id=${encode(s.sourceId)}&lv=1&kv=-1&tv=-1")?.optJSONObject("lrc")?.optString("lyric").orEmpty()
        val lrc=item.optString("lrcUrl")
        if(lyric.isBlank() && validUrl(lrc))lyric=runCatching{get(lrc)}.getOrNull().orEmpty().takeIf{it.contains(Regex("\\[\\d+:\\d+"))}.orEmpty()
        s.copy(playUrl=java.net.URI(play).toASCIIString(),lyric=lyric,coverUrl=s.coverUrl.ifBlank{item.optString("picture").ifBlank{item.optString("picurl")}.takeIf{validUrl(it)}.orEmpty()})
    }.getOrNull()
    private fun get(url:String):String? {
        val c=URL(url).openConnection() as HttpURLConnection
        return try{c.connectTimeout=8000;c.readTimeout=8000;c.setRequestProperty("User-Agent","Mozilla/5.0");c.setRequestProperty("Referer",if(url.contains("kuwo.cn"))"https://www.kuwo.cn/" else "https://music.163.com/");if(c.responseCode !in 200..299)null else c.inputStream.bufferedReader().use{it.readText()}}finally{c.disconnect()}
    }
}
