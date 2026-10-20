package t.mma.hooks.items.homeextension

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.media.AudioManager
import android.view.View
import android.view.Window
import android.widget.*
import t.mma.hooks.items.musicorder.QQMusicClient
import t.mma.hooks.items.musicorder.QQMusicSearchResult
import t.mma.hooks.items.musicorder.QQMusicTrack
import t.mma.preferences.HchatStorage
import java.net.URL
import java.util.concurrent.Executors

internal data class HomeMusicSong(val title: String, val artist: String, val playUrl: String, val coverUrl: String, val lyric: String, val source: String = "QQ音乐", val sourceId: String = "")

internal object HomeMusic {
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "Hchat-HomeMusic").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())
    private val client = QQMusicClient()
    private var player: MediaPlayer? = null
    private var current: HomeMusicSong? = null
    private var playing = false
    private var liked = false
    private var mode = "单曲"
    private val listeners = mutableSetOf<() -> Unit>()

    private fun prefs(c: Context) = HchatStorage.preferences(c, HomeExtensionSettings.PREFS)
    fun style(c: Context) = prefs(c).getInt(HomeExtensionSettings.MUSIC_STYLE, 1).let { if (it == 4) 4 else 1 }
    fun setListener(listener: () -> Unit) { listeners += listener }
    fun removeListener(listener: () -> Unit) { listeners -= listener }
    private fun changed() = main.post { listeners.toList().forEach { it() } }
    private fun dp(c: Context, value: Int) = (value * c.resources.displayMetrics.density).toInt()
    private fun rounded(c: Context, color: Int, radius: Int = 18) = GradientDrawable().apply { setColor(color); cornerRadius = dp(c, radius).toFloat() }
    private fun label(c: Context, value: String, size: Float, color: Int = Color.WHITE) = TextView(c).apply { text = value; textSize = size; setTextColor(color); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
    private fun prefsSong(c: Context, s: HomeMusicSong) = prefs(c).edit().putString("title", s.title).putString("artist", s.artist).putString("url", s.playUrl).putString("cover", s.coverUrl).putString("lyric", s.lyric).putString("source", s.source).putString("source_id", s.sourceId).apply()
    private fun load(c: Context): HomeMusicSong? = prefs(c).let { p ->
        p.getString("url", "").orEmpty().takeIf { it.isNotBlank() }?.let { url ->
            HomeMusicSong(p.getString("title", "音乐").orEmpty(), p.getString("artist", "").orEmpty(), url, p.getString("cover", "").orEmpty(), p.getString("lyric", "").orEmpty(), p.getString("source", "QQ音乐").orEmpty(), p.getString("source_id", "").orEmpty())
        }
    }
    fun search(c: Context, query: String, callback: (HomeMusicSong?) -> Unit) = worker.execute {
        val song = runCatching { when (val result = client.search(query)) { is QQMusicSearchResult.Success -> result.track.toHomeSong(); else -> null } }.getOrNull()
            ?: HomeMusicSources.searchNetease(query)
            ?: HomeMusicSources.searchKuwo(query)
        if (song != null) prefsSong(c, song)
        main.post { callback(song) }
    }
    private fun QQMusicTrack.toHomeSong() = HomeMusicSong(title, singer, playUrl, coverUrl, lyric, "QQ音乐", mid)
    private fun loadCover(view: ImageView, url: String) { if (url.isBlank()) return; worker.execute { runCatching { URL(url).openStream().use { BitmapFactory.decodeStream(it) } }.getOrNull()?.let { main.post { view.setImageBitmap(it) } } } }
    private fun play(c: Context, song: HomeMusicSong) {
        if (song.playUrl.isBlank()) { Toast.makeText(c, "歌曲播放地址为空", Toast.LENGTH_SHORT).show(); return }
        if (current?.playUrl == song.playUrl && player != null) {
            runCatching { if (playing) player?.pause() else player?.start(); playing = !playing; changed() }
                .onFailure { Toast.makeText(c, "播放器尚未准备完成", Toast.LENGTH_SHORT).show() }
            return
        }
        player?.runCatching { stop() }?.getOrNull(); player?.release(); player = null
        current = song; playing = false
        val mp = MediaPlayer()
        player = mp
        runCatching {
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC)
            mp.setDataSource(song.playUrl)
            mp.setOnPreparedListener { prepared -> prepared.start(); playing = true; changed() }
            mp.setOnCompletionListener { playing = false; changed() }
            mp.setOnErrorListener { _, _, _ -> playing = false; player?.release(); player = null; changed(); Toast.makeText(c, "音乐播放失败，请重新搜索歌曲", Toast.LENGTH_SHORT).show(); true }
            mp.prepareAsync()
        }.onFailure {
            player = null; mp.release(); Toast.makeText(c, "音乐地址无效或网络不可用", Toast.LENGTH_SHORT).show()
        }
    }
private fun control(c: Context, text: String, action: () -> Unit) = label(c, text, 14f).apply { gravity = Gravity.CENTER; setPadding(dp(c, 8), dp(c, 7), dp(c, 8), dp(c, 7)); background = rounded(c, 0x334A8298); setOnClickListener { action() } }
    private fun spaced(c: Context, view: View) = LinearLayout.LayoutParams(0, dp(c, 70), 1f).apply { setMargins(dp(c, 3), 0, dp(c, 3), 0) }
    private fun actionSpacing(c: Context) = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(c, 3), 0, dp(c, 3), 0) }

    fun card(c: Context, compact: Boolean = false): LinearLayout {
        val alpha = prefs(c).getInt(HomeExtensionSettings.MUSIC_ALPHA, 100).coerceIn(0, 100) * 255 / 100
        val root = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14)); background = rounded(c, Color.argb(alpha, 29, 29, 31), 22) }
        root.addView(if (style(c) == 4) buildStyleFour(c, compact, root) else buildCard(c, compact, root)); return root
    }
    private fun buildStyleFour(c: Context, compact: Boolean, root: LinearLayout): View {
        val song = load(c)
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(c, 16), dp(c, 14), dp(c, 10), dp(c, 14)); background = rounded(c, Color.argb(prefs(c).getInt(HomeExtensionSettings.MUSIC_ALPHA, 100).coerceIn(0, 100) * 255 / 100, 11, 66, 96), 20); setOnClickListener { if (song != null) showPlayer(c) } }
        val header = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label(c, "⌄", 28f), LinearLayout.LayoutParams(dp(c, 42), -2))
        val title = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        title.addView(label(c, song?.title ?: "首页音乐", 20f)); title.addView(label(c, "${song?.artist ?: "搜索歌曲"}  ·  音乐", 13f, 0xFFB8C8D0.toInt()))
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f)); header.addView(label(c, "⌕", 28f).apply { setOnClickListener { searchDialog(c, root) } }); box.addView(header)
        val image = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = rounded(c, 0xFF1A5874.toInt(), 160) }
        song?.let { loadCover(image, it.coverUrl) }; box.addView(image, LinearLayout.LayoutParams(-1, dp(c, if (compact) 190 else 270)).apply { setMargins(dp(c, 20), dp(c, 24), dp(c, 20), dp(c, 22)) })
        val actions = LinearLayout(c).apply { gravity = Gravity.CENTER }; actions.addView(control(c, "◀  单曲") { mode = "单曲" }); actions.addView(control(c, "▣  音质") { quality(c) }); actions.addView(control(c, "◷  定时") { timer(c) }); actions.addView(control(c, "▤  悬浮词") { Toast.makeText(c, "悬浮歌词已切换", Toast.LENGTH_SHORT).show() }); box.addView(actions)
        val play = label(c, if (playing && current?.playUrl == song?.playUrl) "❚❚" else "▶", 30f).apply { gravity = Gravity.CENTER; setOnClickListener { song?.let { play(c, it) } } }; box.addView(play, LinearLayout.LayoutParams(-1, dp(c, 58)))
        return box
    }
    private fun buildCard(c: Context, compact: Boolean, root: LinearLayout): View {
        val song = load(c)
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setOnClickListener { if (song != null) showPlayer(c) } }
        val header = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label(c, song?.title ?: "点击搜索音乐", if (compact) 19f else 24f), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label(c, "${song?.artist ?: "音乐"}  ·  ${song?.source ?: "QQ音乐"}", 13f, 0xFFAAAAAE.toInt())); box.addView(header)
        val row = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(c, 12), 0, dp(c, 8)) }
        val imageSize = if (compact) 64 else 96
        val image = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = rounded(c, 0xFF343438.toInt(), 14) }
        row.addView(image, LinearLayout.LayoutParams(dp(c, imageSize), dp(c, imageSize)).apply { rightMargin = dp(c, 14) }); song?.let { loadCover(image, it.coverUrl) }
        val info = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        info.addView(label(c, song?.title ?: "搜索并播放音乐", if (compact) 17f else 22f)); info.addView(label(c, "${song?.artist ?: "搜索并播放音乐"}  ·  ${song?.source ?: "音乐"}", 14f, 0xFFAAAAAE.toInt()))
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(label(c, if (playing && current?.playUrl == song?.playUrl) "❚❚" else "▶", 28f).apply { gravity = Gravity.CENTER; setOnClickListener { song?.let { play(c, it) } } }, LinearLayout.LayoutParams(dp(c, 52), dp(c, 52))); box.addView(row)
        val progress = SeekBar(c).apply { max = 100; setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(v: SeekBar?, value: Int, fromUser: Boolean) { if (fromUser && player?.duration ?: 0 > 0) player?.seekTo((player?.duration ?: 0) * value / 100) }; override fun onStartTrackingTouch(v: SeekBar?) {}; override fun onStopTrackingTouch(v: SeekBar?) {} }) }; box.addView(progress)
        val footer = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL }; footer.addView(label(c, if (playing) "播放中" else "已暂停", 12f, 0xFF9B9BA0.toInt()), LinearLayout.LayoutParams(0, -2, 1f)); footer.addView(label(c, "☰", 24f).apply { setOnClickListener { playlist(c) } }); box.addView(footer)
        return box
    }
    private fun showPlayer(c: Context) {
        // The secondary page uses the same search action as both card styles.
        val song = current ?: load(c) ?: return
        val dialog = Dialog(c); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val page = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(c, 22), dp(c, 20), dp(c, 22), dp(c, 18)); background = rounded(c, 0xFF063A53.toInt(), 0) }
        val top = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(label(c, "⌄", 38f).apply { gravity = Gravity.CENTER; setOnClickListener { dialog.dismiss() } }, LinearLayout.LayoutParams(dp(c, 50), dp(c, 50)))
        val title = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }; title.addView(label(c, song.title, 21f)); title.addView(label(c, "${song.artist}  ·  QQ音乐", 14f, 0xFFB6C3C8.toInt())); top.addView(title, LinearLayout.LayoutParams(0, -2, 1f)); top.addView(label(c, "⌕", 34f).apply { setOnClickListener { searchDialog(c, page) } }); page.addView(top)
        val artwork = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = rounded(c, 0xFF1A5874.toInt(), 180) }; loadCover(artwork, song.coverUrl); page.addView(artwork, LinearLayout.LayoutParams(-1, 0, 1f).apply { setMargins(dp(c, 35), dp(c, 32), dp(c, 35), dp(c, 22)) })
        val actions = LinearLayout(c).apply { gravity = Gravity.CENTER }
        actions.addView(control(c, "◀  $mode") { mode = if (mode == "单曲") "列表" else "单曲" }, actionSpacing(c))
        actions.addView(control(c, "▣  音质") { quality(c) }, actionSpacing(c))
        actions.addView(control(c, "◷  定时") { timer(c) }, actionSpacing(c))
        actions.addView(control(c, "▤  悬浮词") { Toast.makeText(c, "悬浮歌词已切换", Toast.LENGTH_SHORT).show() }, actionSpacing(c)); page.addView(actions)
        page.addView(SeekBar(c).apply { max = 100 })
        val controls = LinearLayout(c).apply { gravity = Gravity.CENTER }
        controls.addView(control(c, if (liked) "♥" else "♡") { liked = !liked }, spaced(c, controls))
        controls.addView(control(c, "|◀") { Toast.makeText(c, "上一首", Toast.LENGTH_SHORT).show() }, spaced(c, controls))
        controls.addView(control(c, if (playing) "❚❚" else "▶") { play(c, song) }, spaced(c, controls))
        controls.addView(control(c, "▶|") { Toast.makeText(c, "下一首", Toast.LENGTH_SHORT).show() }, spaced(c, controls))
        controls.addView(control(c, "☰") { playlist(c) }, spaced(c, controls)); page.addView(controls)
        dialog.setContentView(page); dialog.show(); dialog.window?.setLayout(-1, -1)
    }
    private fun quality(c: Context) = AlertDialog.Builder(c).setTitle("音质").setItems(arrayOf("标准", "高品质", "无损"), null).show()
    private fun timer(c: Context) = AlertDialog.Builder(c).setTitle("定时关闭").setItems(arrayOf("不开启", "15分钟", "30分钟", "60分钟")) { _, which -> if (which > 0) main.postDelayed({ player?.pause(); playing = false; changed() }, which * 15 * 60 * 1000L) }.show()
    private fun playlist(c: Context) = AlertDialog.Builder(c).setTitle("播放列表").setMessage(current?.let { "${it.title}\n${it.artist}" } ?: "暂无播放歌曲").setPositiveButton("关闭", null).show()
    private fun searchDialog(c: Context, root: LinearLayout) {
        val input = EditText(c).apply { hint = "输入歌曲名或歌手"; setSingleLine(true) }
        AlertDialog.Builder(c).setTitle("搜索音乐").setView(input).setNegativeButton("取消", null).setPositiveButton("搜索") { _, _ ->
            input.text.toString().trim().takeIf { it.isNotBlank() }?.let { query ->
                search(c, query) { root.removeAllViews(); root.addView(if (style(c) == 4) buildStyleFour(c, false, root) else buildCard(c, false, root)) }
            }
        }.show()
    }
}
