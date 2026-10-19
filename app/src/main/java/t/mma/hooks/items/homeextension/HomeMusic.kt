package t.mma.hooks.items.homeextension

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import t.mma.hooks.items.musicorder.QQMusicClient
import t.mma.hooks.items.musicorder.QQMusicSearchResult
import t.mma.hooks.items.musicorder.QQMusicTrack
import t.mma.preferences.HchatStorage
import java.net.URL
import java.util.concurrent.Executors

internal data class HomeMusicSong(
    val title: String,
    val artist: String,
    val playUrl: String,
    val coverUrl: String,
    val lyric: String
)

internal object HomeMusic {
    private const val PREFS = HomeExtensionSettings.PREFS
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "Hchat-HomeMusic").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())
    private val client = QQMusicClient()
    private var player: MediaPlayer? = null
    private var current: HomeMusicSong? = null
    private var playing = false
    private val listeners = mutableSetOf<() -> Unit>()

    private fun prefs(context: Context) = HchatStorage.preferences(context, PREFS)
    fun isEnabled(context: Context) = prefs(context).getBoolean(HomeExtensionSettings.MUSIC, false)
    fun style(context: Context) = prefs(context).getInt(HomeExtensionSettings.MUSIC_STYLE, 1).let { if (it == 4) 4 else 1 }
    fun setListener(listener: () -> Unit) { listeners += listener }
    fun removeListener(listener: () -> Unit) { listeners -= listener }
    private fun notifyChanged() = main.post { listeners.toList().forEach { it() } }

    fun search(context: Context, query: String, callback: (HomeMusicSong?) -> Unit) {
        worker.execute {
            val song = runCatching {
                when (val result = client.search(query)) {
                    is QQMusicSearchResult.Success -> result.track.toHomeSong()
                    else -> null
                }
            }.getOrNull()
            if (song != null) save(context, song)
            main.post { callback(song) }
        }
    }

    private fun QQMusicTrack.toHomeSong() = HomeMusicSong(title, singer, playUrl, coverUrl, lyric)
    private fun save(context: Context, song: HomeMusicSong) {
        prefs(context).edit().putString("title", song.title).putString("artist", song.artist)
            .putString("url", song.playUrl).putString("cover", song.coverUrl).putString("lyric", song.lyric).apply()
    }
    private fun load(context: Context): HomeMusicSong? = prefs(context).let {
        val url = it.getString("url", "").orEmpty()
        if (url.isBlank()) null else HomeMusicSong(it.getString("title", "音乐").orEmpty(), it.getString("artist", "").orEmpty(), url, it.getString("cover", "").orEmpty(), it.getString("lyric", "").orEmpty())
    }

    private fun play(context: Context, song: HomeMusicSong, button: TextView) {
        if (current?.playUrl == song.playUrl && player != null) {
            if (playing) { player?.pause(); playing = false } else { player?.start(); playing = true }
            button.text = if (playing) "❚❚" else "▶"; notifyChanged(); return
        }
        player?.release(); player = null; current = song; playing = false; button.text = "…"
        runCatching {
            MediaPlayer().also { mp ->
                player = mp
                mp.setDataSource(song.playUrl)
                mp.setOnPreparedListener { it.start(); playing = true; button.text = "❚❚"; notifyChanged() }
                mp.setOnCompletionListener { playing = false; button.text = "▶"; notifyChanged() }
                mp.setOnErrorListener { _, _, _ -> playing = false; button.text = "▶"; true }
                mp.prepareAsync()
            }
        }.onFailure { button.text = "▶" }
    }

    private fun rounded(context: Context, alpha: Int = 255, color: Int = Color.WHITE) = GradientDrawable().apply {
        cornerRadius = 22f * context.resources.displayMetrics.density
        setColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)))
    }
    private fun text(context: Context, value: String, size: Float, color: Int = 0xFF24272B.toInt()) = TextView(context).apply { text=value; textSize=size; setTextColor(color); maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END }
    private fun loadCover(view: ImageView, url: String) {
        if (url.isBlank()) return
        worker.execute { val bitmap = runCatching { URL(url).openStream().use { BitmapFactory.decodeStream(it) } }.getOrNull(); if (bitmap != null) main.post { view.setImageBitmap(bitmap) } }
    }
    private fun searchDialog(context: Context, card: LinearLayout) {
        val input = EditText(context).apply { hint = "输入歌曲名或歌手"; setSingleLine(true) }
        AlertDialog.Builder(context).setTitle("搜索音乐").setView(input).setNegativeButton("取消", null).setPositiveButton("搜索") { _, _ ->
            val query=input.text.toString().trim(); if(query.isNotBlank()) search(context, query) { card.removeAllViews(); card.addView(build(context)) }
        }.show()
    }
    fun card(context: Context, compact: Boolean = false): LinearLayout {
        val alpha = (prefs(context).getInt(HomeExtensionSettings.MUSIC_ALPHA, 100).coerceIn(0, 100) * 255 / 100)
        val root = LinearLayout(context).apply { orientation=LinearLayout.VERTICAL; setPadding(18,16,18,16); background=rounded(context, alpha) }
        root.layoutParams = LinearLayout.LayoutParams(-1,-2)
        root.addView(build(context, compact, root))
        return root
    }
    private fun build(context: Context, compact: Boolean = false, root: LinearLayout? = null): View {
        val song=load(context)
        val container=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
        val header=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(text(context, "♫ 音乐", 14f, 0xFF65727A.toInt()), LinearLayout.LayoutParams(0,-2,1f))
        header.addView(text(context, "⌕", 24f).apply { setOnClickListener { root?.let { searchDialog(context,it) } } })
        container.addView(header)
        when(style(context)) {
            1 -> buildStyleOne(context, container, song, compact)
            2,3,4 -> buildStyleFour(context, container, song, compact)
        }
        return container
    }
    private fun buildStyleOne(context: Context, parent: LinearLayout, song: HomeMusicSong?, compact: Boolean) {
        val row=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(0,12,0,4) }
        val disc=ImageView(context).apply { setImageResource(android.R.drawable.ic_media_play); scaleType=ImageView.ScaleType.CENTER_CROP; background=rounded(context,255,0xFFE5E8EB.toInt()) }
        val size=(if(compact) 62 else 82); row.addView(disc,LinearLayout.LayoutParams(size,size).apply { rightMargin=14 })
        val info=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
        info.addView(text(context,song?.title ?: "点击搜索歌曲",if(compact) 15f else 18f)); info.addView(text(context,song?.artist ?: "QQ音乐",12f,0xFF778087.toInt()))
        row.addView(info,LinearLayout.LayoutParams(0,-2,1f)); parent.addView(row)
        song?.let { loadCover(disc,it.coverUrl) }
        val controls=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        controls.addView(text(context,"唱片卡片 · 样式1",11f,0xFF89939A.toInt()),LinearLayout.LayoutParams(0,-2,1f))
        val play=text(context,if(playing && current?.playUrl==song?.playUrl) "❚❚" else "▶",22f).apply { gravity=Gravity.CENTER; setOnClickListener { if(song!=null) play(context,song,this) } }
        controls.addView(play,LinearLayout.LayoutParams(44,44)); parent.addView(controls)
    }
    private fun buildStyleFour(context: Context, parent: LinearLayout, song: HomeMusicSong?, compact: Boolean) {
        val box=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(12,12,8,8); background=rounded(context,255,0xFFF0F2F4.toInt()) }
        val cover=ImageView(context).apply { setImageResource(android.R.drawable.ic_media_play); scaleType=ImageView.ScaleType.CENTER_CROP; background=rounded(context,255,0xFFD9DEE2.toInt()) }
        box.addView(cover,LinearLayout.LayoutParams(if(compact) 54 else 66,if(compact) 54 else 66).apply { rightMargin=12 })
        val info=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
        info.addView(text(context,song?.title ?: "首页音乐",16f)); info.addView(text(context,song?.artist ?: "搜索并播放音乐",12f,0xFF707A82.toInt()))
        box.addView(info,LinearLayout.LayoutParams(0,-2,1f)); song?.let { loadCover(cover,it.coverUrl) }
        val play=text(context,if(playing && current?.playUrl==song?.playUrl) "❚❚" else "▶",22f).apply { setOnClickListener { if(song!=null) play(context,song,this) } }
        box.addView(play,LinearLayout.LayoutParams(42,42)); parent.addView(box)
    }
}
