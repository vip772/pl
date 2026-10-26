#!/usr/bin/env python3
from pathlib import Path
import tempfile,subprocess,glob
repo=Path(__file__).resolve().parents[3]
lib=Path('/tmp/pl-kotlin-libs');jars=list(lib.glob('*.jar'));stdlib=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
sources={
'Prefs.kt':'''package android.content
class SharedPreferences {var value=false;fun getBoolean(k:String,d:Boolean)=value;fun registerOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener){};fun edit()=Editor(this);fun interface OnSharedPreferenceChangeListener {fun onSharedPreferenceChanged(p:SharedPreferences,k:String?)};class Editor(val p:SharedPreferences){fun putBoolean(k:String,v:Boolean)=apply{p.value=v};fun apply(){}}}
''',
'Context.kt':'package android.content\nopen class Context { val applicationContext: Context get()=this }',
'Build.kt':'package android.os\nobject Build { object VERSION { const val RELEASE="test";const val SDK_INT=35 };const val MANUFACTURER="test";const val MODEL="test" }\nobject SystemClock { var now=0L; fun elapsedRealtime()=now;fun elapsedRealtimeNanos()=now*1000000 }',
'Log.kt':'package android.util\nobject Log {fun getStackTraceString(t:Throwable)=t.toString()}',
'Config.kt':'package t.mma\nobject BuildConfig {const val VERSION_NAME="test";const val VERSION_CODE=1}',
'Api.kt':'package t.mma.hooks.api.core\nobject WeChatApis {fun version(): Version?=null;class Version {fun current()=Info()};class Info {val versionName="test";val versionCode=1}}',
'Store.kt':'package t.mma.preferences\nobject HchatStorage { lateinit var dir:java.io.File;fun storageDir(context:android.content.Context)=dir;fun preferences(c:android.content.Context,n:String)=android.content.SharedPreferences() }',
'HLog.kt':'package t.mma.utils\nobject HLog {val lines=mutableListOf<String>();fun i(s:String){lines.add(s)}}',
'Graphics.kt':'''package android.graphics
class Matrix {fun setRotate(p:Float,x:Float,y:Float){}}
class SweepGradient(x:Float,y:Float,c:IntArray,p:FloatArray) {init {created++};fun setLocalMatrix(m:Matrix){};companion object {var created=0}}
class Paint {enum class Style {STROKE};var style=Style.STROKE;var shader:SweepGradient?=null}
class RectF {var x=5f;fun centerX()=x;fun centerY()=6f}
class Canvas {fun drawRoundRect(r:RectF,a:Float,b:Float,p:Paint){}}
''',
'Test.kt':'''import t.mma.utils.*
import android.graphics.*
fun main(){
 val dir=java.nio.file.Files.createTempDirectory("runtime-test").toFile();t.mma.preferences.HchatStorage.dir=dir
 val context=android.content.Context();RuntimeLogStore.initialize(context);RuntimeLogStore.setEnabled(context,true)
 repeat(100){RuntimeLogStore.append("INFO","entry-$it",null)}
 val text=RuntimeLogStore.readRuntimeLog(context);check(text.indexOf("entry-0")<text.indexOf("entry-99"));check(text.contains("entry-99"))
 RuntimeLogStore.clearRuntimeLog(context);check(RuntimeLogStore.readRuntimeLog(context).isEmpty())
 RuntimeLogStore.append("ERROR","failure",IllegalStateException("problem"));check(RuntimeLogStore.buildDiagnosticReport(context).contains("problem"))
 val paint=Paint();val rect=RectF();val canvas=Canvas();repeat(120){t.mma.hooks.items.dynamicbadge.RotatingGradientStroke.draw(canvas,paint,rect,2f,1,2,it.toFloat())};check(SweepGradient.created==1);check(paint.shader==null)
 t.mma.hooks.items.dynamicbadge.RotatingGradientStroke.draw(canvas,paint,rect,2f,1,3,0f);check(SweepGradient.created==2)
 rect.x=9f;t.mma.hooks.items.dynamicbadge.RotatingGradientStroke.draw(canvas,paint,rect,2f,1,3,0f);check(SweepGradient.created==3)
 println("PASS asynchronous log order/export/clear/error text, shader reuse and size/color invalidation")
}
'''
}
with tempfile.TemporaryDirectory() as d:
 p=Path(d);files=[]
 for n,s in sources.items():
  f=p/n;f.write_text(s);files.append(str(f))
 production=['utils/RuntimeLogStore.kt','hooks/items/dynamicbadge/RotatingGradientStroke.kt']
 cmd=['java','-Xmx256m','-cp',':'.join(map(str,jars)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(stdlib),'-d',str(p/'test.jar'),*files,*[str(repo/'app/src/main/java/t/mma'/x) for x in production]]
 subprocess.run(cmd,check=True)
 subprocess.run(['java','-cp',str(p/'test.jar')+':'+str(stdlib),'TestKt'],check=True,timeout=20)
