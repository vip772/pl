from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[3]
source=(root/'app/src/main/java/t/mma/hooks/items/homeextension/HomeWeather.kt').read_text()
normalize=source[source.index('    internal fun normalize('):source.index('    fun search(')]
lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
files={
'Json.kt':'''package org.json
class JSONObject { private val values=mutableMapOf<String,Any>();fun put(k:String,v:Any)=apply{values[k]=v};fun getDouble(k:String)=(values.getValue(k) as Number).toDouble();fun getString(k:String)=values.getValue(k) as String;fun getJSONObject(k:String)=values.getValue(k) as JSONObject;fun getJSONArray(k:String)=values.getValue(k) as JSONArray }
class JSONArray {private val values=mutableListOf<Any>();fun put(v:Any)=apply{values.add(v)};fun getJSONObject(i:Int)=values[i] as JSONObject;fun getDouble(i:Int)=(values[i] as Number).toDouble()}
''',
'Weather.kt':'import org.json.JSONObject\nobject Weather {\n'+normalize+'}\n',
'Test.kt':'''import org.json.*
fun measure(v:Double,u:String="°C")=JSONObject().put("value",v).put("unit",u)
fun now(h:Double=.69,u:String="m/s",speed:Double=4.74)=JSONObject().put("temperature",measure(31.71)).put("feelsLike",measure(33.64)).put("humidity",h).put("wind",JSONObject().put("speed",measure(speed,u))).put("condition",JSONObject().put("text","少云").put("code","102"))
fun forecast(high:Double=29.94,low:Double=20.93)=JSONObject().put("days",JSONArray().put(JSONObject().put("temperatureMax",measure(high)).put("temperatureMin",measure(low))))
fun main(){
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
 println("PASS: QWeather v1 fields, humidity, m/s and km/h, daily extrema, invalid/missing data rejected")
}
'''}
with tempfile.TemporaryDirectory() as d:
 p=Path(d)
 for name,text in files.items():(p/name).write_text(text)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),*map(str,p.glob('*.kt'))],check=True,timeout=40)
 subprocess.run(['java','-cp',f'{p}/test.jar:{std}','TestKt'],check=True,timeout=10)
assert 'open-meteo.com' not in source
assert 'X-QW-Api-Key' in source and 'GZIPInputStream' in source
print('PASS: old provider removed; header authentication and Gzip supported')