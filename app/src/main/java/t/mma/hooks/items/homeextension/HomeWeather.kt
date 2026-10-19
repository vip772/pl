package t.mma.hooks.items.homeextension

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import t.mma.preferences.HchatStorage
import kotlin.math.roundToInt

/** A separate worker/cache keeps weather independent of contacts and header reconstruction. */
internal object HomeWeather {
    data class City(val name: String, val latitude: Double, val longitude: Double)
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "Hchat-Weather").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())
    private val inFlight = HashSet<String>()
    private val listeners = HashMap<String, MutableList<java.lang.ref.WeakReference<() -> Unit>>>()
    private val failures = HashMap<String, Long>()
    private fun request(url: String): JSONObject {
        val connection = URL(url).openConnection() as java.net.HttpURLConnection
        try {
            connection.connectTimeout = 5000; connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "Hchat-FreeWeather/1.0")
            check(connection.responseCode == 200)
            val data = connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    check(out.size() + count <= 256 * 1024); out.write(buffer, 0, count)
                }
                out.toString("UTF-8")
            }
            return JSONObject(data)
        } finally { connection.disconnect() }
    }
    fun search(query: String, callback: (List<City>?) -> Unit) {
        worker.execute {
            val result = runCatching {
                val data = request("https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(query, "UTF-8")}&count=10&language=zh&format=json")
                val items = data.optJSONArray("results")
                (0 until (items?.length() ?: 0)).map { index ->
                    val item = items!!.getJSONObject(index)
                    City(listOf(item.optString("name"), item.optString("admin1"), item.optString("country")).filter { it.isNotBlank() }.distinct().joinToString(" · "), item.getDouble("latitude"), item.getDouble("longitude"))
                }
            }.getOrNull()
            main.post { callback(result) }
        }
    }
    fun card(context: Context, compact: Boolean = false, settings: android.content.SharedPreferences? = null): LinearLayout {
        val prefs = settings ?: HomeExtensionSettings.preferences(context)
        val storage = HchatStorage.preferences(context, "Hchat_home_weather_cache")
        val name = prefs.getString("weather_city", "").orEmpty()
        val lat = prefs.getString("weather_lat", "").orEmpty()
        val lon = prefs.getString("weather_lon", "").orEmpty()
        val key = "$lat,$lon"
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + .5f).toInt()
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val d = resources.displayMetrics.density
            setPadding(((if(compact) 12 else 20)*d).toInt(), (18*d).toInt(), ((if(compact) 12 else 20)*d).toInt(), (16*d).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 24*d
                setColor(Color.argb(prefs.getInt("weather_alpha", 100).coerceIn(0,100)*255/100, 255, 255, 255))
            }
            layoutParams = LinearLayout.LayoutParams(-1,-2).apply { setMargins((12*d).toInt(),(5*d).toInt(),(12*d).toInt(),(5*d).toInt()) }
        }
        fun text(value: String, size: Float, dim: Boolean = false) = TextView(context).apply {
            text = value; textSize = size; setTextColor(if (dim) 0xFF777777.toInt() else 0xFF23272D.toInt())
        }
        fun render(data: JSONObject?, stale: Boolean = false) {
            panel.removeAllViews()
            val heading = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; if(compact) orientation=LinearLayout.VERTICAL }
            heading.addView(text("⌖ ${name.ifBlank { "请选择天气城市" }}", 14f), if(compact) LinearLayout.LayoutParams(-1,-2) else LinearLayout.LayoutParams(0,-2,1f))
            val stamp = if (data != null) java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(data.getLong("saved"))) else "--:--"
            heading.addView(text(if(stale) "更新失败 · $stamp" else "更新于 $stamp", 11f, true))
            panel.addView(heading)
            if (data == null) {
                panel.addView(text(if(name.isBlank()) "在首页扩展设置中选择城市" else "暂无天气缓存，稍后重试",16f))
            } else {
                val current = data.getJSONObject("current")
                val daily = data.getJSONObject("daily")
                fun number(key: String) = current.getDouble(key).roundToInt()
                val code = current.getInt("weather_code")
                val condition = when(code) { 0 -> "晴"; 1,2 -> "多云"; 3 -> "阴"; 45,48 -> "雾"; in 51..67 -> "雨"; in 71..77 -> "雪"; in 80..82 -> "阵雨"; 85,86 -> "阵雪"; in 95..99 -> "雷雨"; else -> "天气" }
                val icon = when(code) { 0 -> if(current.optInt("is_day",1)==1) "☀" else "☾"; 1,2,3 -> "☁"; in 51..67,in 80..82 -> "☂"; in 71..77,85,86 -> "❄"; in 95..99 -> "ϟ"; else -> "≋" }
                val body = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0,dp(12),0,dp(12)) }
                val temp = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                temp.addView(text("${number("temperature_2m")}°",if(compact) 40f else 64f));temp.addView(text("体感 ${number("apparent_temperature")}°",14f,true))
                body.addView(temp,LinearLayout.LayoutParams(0,-2,1f))
                val sky = LinearLayout(context).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER }
                sky.addView(text(icon,if(compact) 32f else 56f));sky.addView(text(condition,17f))
                body.addView(sky,LinearLayout.LayoutParams(0,-2,1f));panel.addView(body)
                panel.addView(android.view.View(context).apply { setBackgroundColor(0xFFE8E8E8.toInt()) },LinearLayout.LayoutParams(-1,1))
                val stats=LinearLayout(context).apply { if(compact) orientation=LinearLayout.VERTICAL; setPadding(0,dp(18),0,dp(6)) }
                val values=listOf("${daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt()}° / ${daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt()}°" to "最高 / 最低", "${number("relative_humidity_2m")}%" to "湿度", "${String.format(java.util.Locale.ROOT,"%.1f",current.getDouble("wind_speed_10m"))} km/h" to "风速")
                values.forEachIndexed { index, (value,label) ->
                    if(index>0 && !compact) stats.addView(android.view.View(context).apply { setBackgroundColor(0xFFE8E8E8.toInt()) },LinearLayout.LayoutParams(dp(1),dp(36)))
                    stats.addView(LinearLayout(context).apply { orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;addView(text(value,if(compact) 14f else 16f));addView(text(label,12f,true)) },if(compact) LinearLayout.LayoutParams(-1,-2) else LinearLayout.LayoutParams(0,-2,1f))
                }
                panel.addView(stats)
            }
            panel.addView(text("天气数据 · Open-Meteo",9f,true))
        }
        fun validated(data: JSONObject): JSONObject {
            val current = data.getJSONObject("current")
            listOf("temperature_2m", "apparent_temperature", "relative_humidity_2m", "weather_code", "wind_speed_10m").forEach { check(current.getDouble(it).isFinite()) }
            val daily = data.getJSONObject("daily")
            listOf("temperature_2m_max", "temperature_2m_min").forEach { check(daily.getJSONArray(it).getDouble(0).isFinite()) }
            return data
        }
        val cached = runCatching { validated(JSONObject(storage.getString(key,null)!!)) }.getOrNull()
        render(cached)
        val refresh: () -> Unit = {
            if (panel.isAttachedToWindow) {
                val latest = runCatching { validated(JSONObject(storage.getString(key,null)!!)) }.getOrNull()
                render(latest ?: cached, failures.containsKey(key))
            }
        }
        panel.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: android.view.View) { refresh() }
            override fun onViewDetachedFromWindow(view: android.view.View) {}
        })
        listeners.getOrPut(key) { mutableListOf() }.apply {
            removeAll { it.get() == null }; add(java.lang.ref.WeakReference(refresh))
        }
        if(lat.toDoubleOrNull()==null || lon.toDoubleOrNull()==null) return panel
        if(cached != null && System.currentTimeMillis()-cached.optLong("saved") < 30*60_000L) return panel
        if(!inFlight.add(key)) return panel
        if(System.currentTimeMillis()-(failures[key] ?: 0L)<5*60_000L) { inFlight.remove(key);return panel }
        worker.execute {
            val result=runCatching {
                validated(request("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1")).apply { put("saved",System.currentTimeMillis()) }
            }.getOrNull()
            if(result!=null) storage.edit().putString(key,result.toString()).apply()
            main.post {
                inFlight.remove(key)
                if(result==null) failures[key]=System.currentTimeMillis() else failures.remove(key)
                listeners.remove(key)?.forEach { it.get()?.invoke() }
            }
        }
        return panel
    }
}