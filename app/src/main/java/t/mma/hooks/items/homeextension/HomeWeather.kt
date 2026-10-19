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
    private const val HOST = "n276xa5gxm.re.qweatherapi.com"
    private const val API_KEY = "92858ad8f04a427480e004ae8fefac7e"
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
            connection.setRequestProperty("User-Agent", "Hchat-Weather/1.0")
            connection.setRequestProperty("X-QW-Api-Key", API_KEY)
            connection.setRequestProperty("Accept-Encoding", "gzip")
            check(connection.responseCode == 200)
            val data = connection.inputStream.use { raw ->
                val buffered = java.io.BufferedInputStream(raw)
                buffered.mark(2)
                val gzip = buffered.read() == 31 && buffered.read() == 139
                buffered.reset()
                val input = if (gzip) java.util.zip.GZIPInputStream(buffered) else buffered
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
    internal fun normalize(now: JSONObject, forecast: JSONObject): JSONObject {
        val humidity = now.getDouble("humidity").also { check(it.isFinite() && it in 0.0..1.0) }
        val wind = now.getJSONObject("wind").getJSONObject("speed")
        val speed = wind.getDouble("value").also { check(it.isFinite() && it >= 0.0) }
        val kmh = when(wind.getString("unit")) { "m/s" -> speed * 3.6; "km/h" -> speed; else -> error("Unsupported wind unit") }
        fun celsius(value: JSONObject): Double {
            check(value.getString("unit") == "°C")
            return value.getDouble("value").also { check(it.isFinite()) }
        }
        val day = forecast.getJSONArray("days").getJSONObject(0)
        val condition = now.getJSONObject("condition")
        val current = JSONObject().put("temperature_2m",celsius(now.getJSONObject("temperature")))
            .put("apparent_temperature",celsius(now.getJSONObject("feelsLike")))
            .put("relative_humidity_2m",humidity * 100).put("wind_speed_10m",kmh)
            .put("weather_code",condition.getString("code").toInt()).put("condition",condition.getString("text"))
        val high = celsius(day.getJSONObject("temperatureMax"))
        val low = celsius(day.getJSONObject("temperatureMin"))
        check(high >= low)
        val daily = JSONObject().put("temperature_2m_max",org.json.JSONArray().put(high))
            .put("temperature_2m_min",org.json.JSONArray().put(low))
        return JSONObject().put("current",current).put("daily",daily)
    }
    fun search(query: String, callback: (List<City>?) -> Unit) {
        worker.execute {
            val result = runCatching {
                val data = request("https://$HOST/geo/v2/city/lookup?location=${URLEncoder.encode(query, "UTF-8")}&number=10&lang=zh")
                check(data.optString("code") == "200")
                val items = data.optJSONArray("location")
                (0 until (items?.length() ?: 0)).map { index ->
                    val item = items!!.getJSONObject(index)
                    City(listOf(item.optString("name"), item.optString("adm2"), item.optString("adm1"), item.optString("country")).filter { it.isNotBlank() }.distinct().joinToString(" · "), item.getDouble("lat"), item.getDouble("lon"))
                }
            }.getOrNull()
            main.post { callback(result) }
        }
    }
    fun card(context: Context, compact: Boolean = false, settings: android.content.SharedPreferences? = null): LinearLayout {
        val prefs = settings ?: HomeExtensionSettings.preferences(context)
        val storage = HchatStorage.preferences(context, "Hchat_home_qweather_v1_cache")
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
                val condition = current.getString("condition")
                val icon = when(code) { 100 -> "☀"; 150 -> "☾"; in 101..104,in 151..154 -> "☁"; in 300..399 -> if(code in 302..304) "ϟ" else "☂"; in 400..499 -> "❄"; else -> "≋" }
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
            panel.addView(text("天气数据 · 和风天气 QWeather",9f,true).apply {
                setOnClickListener {
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://developer.qweather.com/attribution.html")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            })
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
                val latitude = lat.toDouble().also { check(it.isFinite() && it in -90.0..90.0) }
                val longitude = lon.toDouble().also { check(it.isFinite() && it in -180.0..180.0) }
                val location = String.format(java.util.Locale.ROOT, "%.2f/%.2f", latitude, longitude)
                val now = request("https://$HOST/weather/v1/current/$location?lang=zh")
                val forecast = request("https://$HOST/weather/v1/daily/$location?days=1&lang=zh&localTime=true")
                validated(normalize(now, forecast)).apply { put("saved",System.currentTimeMillis()) }
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