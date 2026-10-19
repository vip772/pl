from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[3]
source=(root/'app/src/main/java/t/mma/hooks/items/homeextension/HomeWeather.kt').read_text()
normalize=source[source.index('    internal fun normalize('):source.index('    fun search(')]
lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
files={
'Json.kt':'''package org.json
class JSONObject { private val values=mutableMapOf<String,Any>();fun put(k:String,v:Any)=apply{values[k]=v};fun getDouble(k:String)=(values.getValue(k) as Number).toDouble();fun getString(k:String)=values.getValue(k) as String;fun getInt(k:String)=(values.getValue(k) as Number).toInt();fun optInt(k:String,d:Int)=(values[k] as? Number)?.toInt() ?: d;fun has(k:String)=values.containsKey(k);fun optJSONObject(k:String)=values[k] as? JSONObject;fun optJSONArray(k:String)=values[k] as? JSONArray;fun getJSONObject(k:String)=values.getValue(k) as JSONObject;fun getJSONArray(k:String)=values.getValue(k) as JSONArray }
class JSONArray {private val values=mutableListOf<Any>();fun put(v:Any)=apply{values.add(v)};fun length()=values.size;fun getInt(i:Int)=(values[i] as Number).toInt();fun getString(i:Int)=values[i] as String;fun getJSONObject(i:Int)=values[i] as JSONObject;fun getDouble(i:Int)=(values[i] as Number).toDouble()}
''',
'Weather.kt':'import org.json.JSONObject\nobject Weather {\n'+normalize+'}\n',
'Test.kt':'''import org.json.*
fun measure(v:Double,u:String="°C")=JSONObject().put("value",v).put("unit",u)
fun now(h:Double=.69,u:String="m/s",speed:Double=4.74)=JSONObject().put("temperature",measure(31.71)).put("feelsLike",measure(33.64)).put("humidity",h).put("wind",JSONObject().put("speed",measure(speed,u))).put("condition",JSONObject().put("text","少云").put("code","102"))
fun forecast(high:Double=29.94,low:Double=20.93)=JSONObject().put("days",JSONArray().put(JSONObject().put("temperatureMax",measure(high)).put("temperatureMin",measure(low))))
fun main(){
 for((code,expected) in listOf(0 to 100,3 to 104,45 to 501,63 to 305,75 to 400,95 to 302)) {
  val mapped=Weather.normalizeBackup(JSONObject().put("current",JSONObject().put("weather_code",code).put("is_day",1)))
  check(mapped.getJSONObject("current").getInt("weather_code")==expected);check(mapped.getString("provider")=="open-meteo")
 }
 check(Weather.normalizeBackup(JSONObject().put("current",JSONObject().put("weather_code",0).put("is_day",0))).getJSONObject("current").getInt("weather_code")==150)
 val result=Weather.normalize(now(),forecast());val c=result.getJSONObject("current")
 check(kotlin.math.abs(c.getDouble("relative_humidity_2m")-69)<.0001)
 check(kotlin.math.abs(c.getDouble("wind_speed_10m")-17.064)<.0001)
 check(c.getDouble("temperature_2m")==31.71);check(c.getString("condition")=="少云")
 check(result.getJSONObject("daily").getJSONArray("temperature_2m_min").getDouble(0)==20.93)
 check(Weather.normalize(now(u="km/h",speed=12.0),forecast()).getJSONObject("current").getDouble("wind_speed_10m")==12.0)
 for(h in listOf(-.1,1.1,Double.NaN)) check(runCatching{Weather.normalize(now(h),forecast())}.isFailure)
 check(runCatching{Weather.normalize(now(u="mph"),forecast())}.isFailure)
 check(runCatching{Weather.normalize(now(speed=-1.0),forecast())}.isFailure)
 check(runCatching{Weather.normalize(now(),forecast(high=0.0,low=10.0))}.isFailure)
 check(runCatching{Weather.normalize(now(),JSONObject().put("days",JSONArray()))}.isFailure)
 val days=JSONArray()
 val times=JSONArray();val highs=JSONArray();val lows=JSONArray();val codes=JSONArray()
 for(i in 0 until 7) {
  val date="2026-10-${20+i}"
  days.put(JSONObject().put("forecastStartTime",date+"T00:00+08:00").put("temperatureMax",measure(20.0+i)).put("temperatureMin",measure(10.0+i)).put("daytime",JSONObject().put("condition",JSONObject().put("text","多云").put("code","101"))))
  times.put(date);highs.put(20.0+i);lows.put(10.0+i);codes.put(3)
 }
 val week=Weather.normalize(now(),JSONObject().put("days",days)).getJSONArray("week")
 check(week.length()==7);check(week.getJSONObject(6).getString("date")=="2026-10-26")
 check(week.getJSONObject(6).getDouble("high")==26.0);check(week.getJSONObject(0).getString("condition")=="多云")
 val backup=Weather.normalizeBackup(JSONObject().put("current",JSONObject().put("weather_code",0)).put("daily",JSONObject().put("time",times).put("temperature_2m_max",highs).put("temperature_2m_min",lows).put("weather_code",codes))).getJSONArray("week")
 check(backup.length()==7);check(backup.getJSONObject(6).getInt("code")==104)
 check(backup.getJSONObject(6).getDouble("low")==16.0)
 println("PASS: seven-day primary and backup dates, extrema and condition conversion")
 println("PASS: QWeather v1 fields, humidity, m/s and km/h, daily extrema, invalid/missing data rejected")
}
'''}
with tempfile.TemporaryDirectory() as d:
 p=Path(d)
 for name,text in files.items():(p/name).write_text(text)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),*map(str,p.glob('*.kt'))],check=True,timeout=40)
 subprocess.run(['java','-cp',f'{p}/test.jar:{std}','TestKt'],check=True,timeout=10)
assert 'open-meteo.com' in source
assert 'private const val CACHE_TTL = 6 * 60 * 60_000L' in source
assert 'if (connection.url.host == HOST)' in source
assert 'validated(normalizeBackup(request(url)))' in source
assert 'X-QW-Api-Key' in source and 'GZIPInputStream' in source
print('PASS: six-hour cache and fallback configured; credential restricted to QWeather host; Gzip supported')