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
    private const val CACHE_TTL = 6 * 60 * 60_000L
    data class City(val name: String, val latitude: Double, val longitude: Double)
    private val worker = t.mma.hooks.core.IdleSerialExecutor.create("Hchat-Weather")
    private val main = Handler(Looper.getMainLooper())
    private val inFlight = HashSet<String>()
    private val listeners = HashMap<String, MutableList<java.lang.ref.WeakReference<() -> Unit>>>()
    private val failures = HashMap<String, Long>()
    private fun request(url: String): JSONObject {
        val connection = URL(url).openConnection() as java.net.HttpURLConnection
        try {
            connection.connectTimeout = 5000; connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "Hchat-Weather/1.0")
            if (connection.url.host == HOST) connection.setRequestProperty("X-QW-Api-Key", API_KEY)
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
        val week = org.json.JSONArray()
        val days = forecast.getJSONArray("days")
        for (i in 0 until minOf(days.length(), 7)) {
            val item = days.getJSONObject(i)
            // Older one-day fixtures/cache do not contain daily condition metadata.
            if (!item.has("forecastStartTime")) continue
            val highDay = celsius(item.getJSONObject("temperatureMax"))
            val lowDay = celsius(item.getJSONObject("temperatureMin"))
            check(highDay >= lowDay)
            val sky = item.getJSONObject("daytime").getJSONObject("condition")
            week.put(JSONObject().put("date",item.getString("forecastStartTime").take(10))
                .put("high",highDay).put("low",lowDay)
                .put("code",sky.getString("code").toInt()).put("condition",sky.getString("text")))
        }
        return JSONObject().put("current",current).put("daily",daily).put("week",week).put("provider", "qweather")
    }
    internal fun normalizeBackup(data: JSONObject): JSONObject {
        val current = data.getJSONObject("current")
        val code = current.getInt("weather_code")
        val condition = when(code) {
            0 -> "晴"; 1,2 -> "多云"; 3 -> "阴"; 45,48 -> "雾"
            in 51..67 -> "雨"; in 71..77 -> "雪"; in 80..82 -> "阵雨"
            85,86 -> "阵雪"; in 95..99 -> "雷雨"; else -> "天气"
        }
        val mapped = when(code) {
            0 -> if(current.optInt("is_day",1)==1) 100 else 150
            1,2 -> 101; 3 -> 104; 45,48 -> 501
            in 51..67,in 80..82 -> 305; in 71..77,85,86 -> 400
            in 95..99 -> 302; else -> 999
        }
        current.put("condition", condition).put("weather_code", mapped)
        val week = org.json.JSONArray()
        val daily = data.optJSONObject("daily")
        val dates = daily?.optJSONArray("time")
        if (dates != null) for (i in 0 until minOf(dates.length(), 7)) {
            val rawCode = daily.getJSONArray("weather_code").getInt(i)
            val sky = normalizeBackup(JSONObject().put("current",JSONObject().put("weather_code",rawCode)))
                .getJSONObject("current")
            val high = daily.getJSONArray("temperature_2m_max").getDouble(i)
            val low = daily.getJSONArray("temperature_2m_min").getDouble(i)
            check(high.isFinite() && low.isFinite() && high >= low)
            week.put(JSONObject().put("date",dates.getString(i)).put("high",high).put("low",low)
                .put("code",sky.getInt("weather_code")).put("condition",sky.getString("condition")))
        }
        return data.put("week",week).put("provider", "open-meteo")
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
            }.recoverCatching {
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
        val storage = HchatStorage.preferences(context, "Hchat_home_qweather_v1_cache")
        val name = prefs.getString("weather_city", "").orEmpty()
        val lat = prefs.getString("weather_lat", "").orEmpty()
        val lon = prefs.getString("weather_lon", "").orEmpty()
        val showWeek = prefs.getBoolean("weather_seven_day", false)
        val key = "$lat,$lon" + if (showWeek) ":week7" else ""
        var weekPage = false
        var swipePage: (Boolean) -> Unit = {}
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + .5f).toInt()
        val panel: LinearLayout = object : LinearLayout(context) {
            private var downX = 0f
            private var downY = 0f
            private var horizontal = false
            private val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
            override fun onInterceptTouchEvent(event: android.view.MotionEvent): Boolean {
                if (!showWeek) return super.onInterceptTouchEvent(event)
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> { downX=event.x; downY=event.y; horizontal=false }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx=kotlin.math.abs(event.x-downX); val dy=kotlin.math.abs(event.y-downY)
                        if(dx>slop && dx>dy*1.5f) {
                            horizontal=true; parent?.requestDisallowInterceptTouchEvent(true); return true
                        }
                    }
                }
                return super.onInterceptTouchEvent(event)
            }
            override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
                if (!showWeek) return super.onTouchEvent(event)
                when(event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> { downX=event.x; downY=event.y; horizontal=false }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx=kotlin.math.abs(event.x-downX); val dy=kotlin.math.abs(event.y-downY)
                        if(dx>slop && dx>dy*1.5f) { horizontal=true; parent?.requestDisallowInterceptTouchEvent(true) }
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        if(horizontal && kotlin.math.abs(event.x-downX)>maxOf(slop*3,dp(40))) swipePage(event.x<downX)
                        horizontal=false; parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> { horizontal=false; parent?.requestDisallowInterceptTouchEvent(false) }
                }
                return true
            }
        }.apply {
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
        fun weatherIcon(code: Int) = when(code) { 100 -> "☀"; 150 -> "☾"; in 101..104,in 151..154 -> "☁"; in 300..399 -> if(code in 302..304) "ϟ" else "☂"; in 400..499 -> "❄"; else -> "≋" }
        fun render(data: JSONObject?, stale: Boolean = false) {
            swipePage = { next -> weekPage=next; render(data,stale) }
            panel.removeAllViews()
            val heading = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; if(compact) orientation=LinearLayout.VERTICAL }
            val cityLabel = text(name.substringBefore("·").trim().ifBlank { "请选择天气城市" }, 14f).apply {
                val pin = WeatherLocationDrawable(currentTextColor)
                pin.setBounds(0, 0, dp(16), dp(18))
                setCompoundDrawablesRelative(pin, null, null, null)
                compoundDrawablePadding = dp(5)
                gravity = Gravity.CENTER_VERTICAL
            }
            heading.addView(cityLabel, if(compact) LinearLayout.LayoutParams(-1,-2) else LinearLayout.LayoutParams(0,-2,1f))
            val stamp = if (data != null) java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(data.getLong("saved"))) else "--:--"
            heading.addView(text(if(stale) "更新失败 · $stamp" else "更新于 $stamp", 11f, true))
            panel.addView(heading)
            if (showWeek && weekPage) {
                panel.addView(text("七天预报",16f).apply { setPadding(0,dp(12),0,dp(8)) })
                val days = data?.optJSONArray("week")
                if (days == null || days.length() < 7) {
                    panel.addView(text("七天预报暂不可用，请稍后重试",13f,true))
                } else for (i in 0 until 7) {
                    val day=days.getJSONObject(i)
                    val date=java.time.LocalDate.parse(day.getString("date"))
                    val weekday=listOf("周一","周二","周三","周四","周五","周六","周日")[date.dayOfWeek.value-1]
                    val row=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(0,dp(7),0,dp(7)) }
                    row.addView(text("${date.monthValue}/${date.dayOfMonth} ${if(date==java.time.LocalDate.now()) "今天" else weekday}",if(compact) 11f else 13f),LinearLayout.LayoutParams(0,-2,1f))
                    row.addView(text("${weatherIcon(day.getInt("code"))} ${day.getString("condition")}",if(compact) 11f else 13f).apply { gravity=Gravity.CENTER },LinearLayout.LayoutParams(0,-2,1f))
                    row.addView(text("${day.getDouble("high").roundToInt()}° / ${day.getDouble("low").roundToInt()}°",if(compact) 11f else 13f).apply { gravity=Gravity.END },LinearLayout.LayoutParams(0,-2,1f))
                    panel.addView(row)
                }
            } else if (data == null) {
                panel.addView(text(if(name.isBlank()) "在首页扩展设置中选择城市" else "暂无天气缓存，稍后重试",16f))
            } else {
                val current = data.getJSONObject("current")
                val daily = data.getJSONObject("daily")
                fun number(key: String) = current.getDouble(key).roundToInt()
                val code = current.getInt("weather_code")
                val condition = current.getString("condition")
                val icon = weatherIcon(code)
                val body = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0,dp(12),0,dp(12)) }
                val temp = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                temp.addView(text("${number("temperature_2m")}°",if(compact) 40f else 56f));temp.addView(text("体感 ${number("apparent_temperature")}°",12f,true))
                body.addView(temp,LinearLayout.LayoutParams(0,-2,if(compact) 1f else 3f))
val sky = LinearLayout(context).apply {
                     orientation = LinearLayout.VERTICAL
                     gravity = Gravity.CENTER
                     setGravity(Gravity.CENTER)
                 }
                 sky.addView(text(icon,if(compact) 32f else 40f).apply {
                     gravity = Gravity.CENTER
                     textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                 }, LinearLayout.LayoutParams(-1,-2))
                 sky.addView(text(condition,15f).apply {
                     gravity = Gravity.CENTER
                     textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                 }, LinearLayout.LayoutParams(-1,-2))
                 body.addView(sky,LinearLayout.LayoutParams(0,-1,if(compact) 1f else 2f).apply {
                     gravity = Gravity.CENTER_VERTICAL
                 })
                val feels = number("apparent_temperature")
                val wind = current.getDouble("wind_speed_10m")
                val clothing = when {
                    feels < 5 -> "建议穿厚外套，注意保暖"
                    feels < 15 -> "建议穿外套或毛衣"
                    feels < 23 -> "建议穿长袖，备薄外套"
                    feels < 28 -> "建议穿轻薄、透气衣物"
                    else -> "建议穿短袖，注意防暑"
                }
                val life = when {
                    code in 302..304 -> "有雷雨，减少户外活动"
                    code in 300..399 -> "出门带伞，注意路面湿滑"
                    code in 400..499 -> "注意防滑，外出保暖"
                    code in 500..515 -> "能见度可能较低，出行留意"
                    wind >= 39 -> "风力较大，外出注意防风"
                    feels >= 30 -> "及时补水，避开午后高温"
                    feels < 5 -> "户外活动注意防寒"
                    else -> "可安排日常户外活动"
                }
                val advice = LinearLayout(context).apply {
                    orientation=LinearLayout.VERTICAL
                    setPadding(dp(if(compact) 0 else 14),dp(6),0,dp(6))
                    addView(text("穿衣提醒",12f,true))
                    addView(text(clothing,12f))
                    addView(text("生活建议",12f,true).apply { setPadding(0,dp(8),0,0) })
                    addView(text(life,12f))
                }
                // With weights 3:2:7, the sky center is at one third of the card content.
                if (!compact) body.addView(advice,LinearLayout.LayoutParams(0,-2,7f))
                panel.addView(body)
                if (compact) panel.addView(advice)
                panel.addView(android.view.View(context).apply { setBackgroundColor(0xFFE8E8E8.toInt()) },LinearLayout.LayoutParams(-1,1))
                val stats=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(0,dp(18),0,dp(6)) }
                val values=listOf(
                    Triple("🌡", "${daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt()}° / ${daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt()}°", "最高 / 最低"),
                    Triple("💧", "${number("relative_humidity_2m")}%", "湿度"),
                    Triple("≋", "${String.format(java.util.Locale.ROOT,"%.1f",wind)} km/h", "风速")
                )
                values.forEachIndexed { index, (symbol,value,label) ->
                    if(index>0) stats.addView(android.view.View(context).apply { setBackgroundColor(0xFFE8E8E8.toInt()) },LinearLayout.LayoutParams(dp(1),dp(36)))
                    stats.addView(LinearLayout(context).apply {
                        orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER
                        addView(text("$symbol $value",if(compact) 11f else 14f).apply {
                            gravity=Gravity.CENTER
                            setSingleLine(true)
                            setAutoSizeTextTypeUniformWithConfiguration(8,if(compact) 11 else 14,1,android.util.TypedValue.COMPLEX_UNIT_SP)
                            contentDescription="$label $value"
                        },LinearLayout.LayoutParams(-1,dp(25)))
                        addView(text(label,if(compact) 10f else 12f,true).apply { gravity=Gravity.CENTER })
                    },LinearLayout.LayoutParams(0,-2,1f))
                }
                panel.addView(stats)
            }
            if(showWeek) panel.addView(text(if(weekPage) "○  ●" else "●  ○",12f,true).apply { gravity=Gravity.CENTER; setPadding(0,dp(8),0,0); contentDescription=if(weekPage) "七天预报，右滑返回" else "当前天气，左滑查看七天预报" })
        }
        fun validated(data: JSONObject): JSONObject {
            val current = data.getJSONObject("current")
            listOf("temperature_2m", "apparent_temperature", "relative_humidity_2m", "weather_code", "wind_speed_10m").forEach { check(current.getDouble(it).isFinite()) }
            check(current.getDouble("relative_humidity_2m") in 0.0..100.0)
            check(current.getDouble("wind_speed_10m") >= 0.0)
            check(current.getString("condition").isNotBlank())
            val daily = data.getJSONObject("daily")
            listOf("temperature_2m_max", "temperature_2m_min").forEach { check(daily.getJSONArray(it).getDouble(0).isFinite()) }
            if(showWeek) {
                val days=data.getJSONArray("week"); check(days.length()==7)
                var previous: java.time.LocalDate? = null
                for(i in 0 until 7) {
                    val day=days.getJSONObject(i)
                    val date=java.time.LocalDate.parse(day.getString("date"))
                    check(previous==null || date==previous!!.plusDays(1)); previous=date
                    val high=day.getDouble("high"); val low=day.getDouble("low")
                    check(high.isFinite() && low.isFinite() && high>=low)
                    check(day.getString("condition").isNotBlank()); day.getInt("code")
                }
            }
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
        if(cached != null && System.currentTimeMillis()-cached.optLong("saved") < CACHE_TTL) return panel
        if(!inFlight.add(key)) return panel
        if(System.currentTimeMillis()-(failures[key] ?: 0L)<5*60_000L) { inFlight.remove(key);return panel }
        worker.execute {
            val result=runCatching {
                val latitude = lat.toDouble().also { check(it.isFinite() && it in -90.0..90.0) }
                val longitude = lon.toDouble().also { check(it.isFinite() && it in -180.0..180.0) }
                val location = String.format(java.util.Locale.ROOT, "%.2f/%.2f", latitude, longitude)
                val now = request("https://$HOST/weather/v1/current/$location?lang=zh")
                val forecast = request("https://$HOST/weather/v1/daily/$location?days=${if(showWeek) 7 else 1}&lang=zh&localTime=true")
                validated(normalize(now, forecast)).apply { put("saved",System.currentTimeMillis()) }
            }.recoverCatching {
                val latitude = lat.toDouble().also { check(it.isFinite() && it in -90.0..90.0) }
                val longitude = lon.toDouble().also { check(it.isFinite() && it in -180.0..180.0) }
                val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,weather_code&timezone=auto&forecast_days=${if(showWeek) 7 else 1}"
                validated(normalizeBackup(request(url))).apply { put("saved",System.currentTimeMillis()) }
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
/** Static vector pin; tint follows the city label rather than font-specific glyphs. */
internal class WeatherLocationDrawable(color: Int) : android.graphics.drawable.Drawable() {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1.65f
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    private val outline = android.graphics.Path().apply {
        moveTo(8f, 16.3f)
        cubicTo(6.2f, 14.2f, 2.8f, 10.5f, 2.8f, 7f)
        cubicTo(2.8f, 4.1f, 5.1f, 1.8f, 8f, 1.8f)
        cubicTo(10.9f, 1.8f, 13.2f, 4.1f, 13.2f, 7f)
        cubicTo(13.2f, 10.5f, 9.8f, 14.2f, 8f, 16.3f)
        close()
    }
    override fun draw(canvas: android.graphics.Canvas) {
        if (bounds.isEmpty) return
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 16f, bounds.height() / 18f)
        canvas.drawPath(outline, paint)
        canvas.drawCircle(8f, 7f, 1.8f, paint)
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
    @Deprecated("Drawable opacity is deprecated")
    override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}
