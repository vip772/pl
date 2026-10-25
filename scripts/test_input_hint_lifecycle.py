"""Exercise the production input hint runtime with event-driven JVM host stubs, not an Android UI test."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];lib=Path('/tmp/pl-kotlin-libs');std=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
s=(r/'app/src/main/java/t/mma/hooks/items/inputhint/InputHintFeature.kt').read_text()
imports=s[:s.index('class InputHintFeature')].replace('import t.mma.event.Events\n','').replace('import t.mma.hooks.core.BaseFeature\n','').replace('import t.mma.hooks.core.FeatureContext\n','')
files={
'Runtime.kt':imports+s[s.index('private class InputHintRuntime'):]+'''
fun main(){
 val r=InputHintRuntime(android.content.Context(),InputHintRuntime::class.java.classLoader){s,t->error(s+":"+t)}
 check(r.installInputHook());check(r.installInputHook());check(t.mma.hooks.core.HookRegistry.count()==2)
 val footer=com.tencent.mm.pluginsdk.ui.chat.ChatFooter()
 footer.onAttachedToWindow();android.os.Handler.drain()
 // Not ready at attach or the single posted retry. A later real layout discovers it.
 val input=android.widget.TextView();footer.input=input;footer.layoutEvent();android.os.Handler.drain()
 check(input.hint.toString()=="custom");val writes=input.writes;val renders=InputHintSettings.renders
 repeat(1000){footer.layoutEvent();android.os.Handler.drain()}
 check(input.writes==writes);check(InputHintSettings.renders==renders)
 // Copied Spanned with equivalent color must not rebuild or rewrite.
 InputHintSettings.color=2;InputHintSettings.notifyChange("input_hint_color_test");android.os.Handler.drain()
 check((input.hint as android.text.Styled).color==2 && input.writes==writes+1)
 val coloredWrites=input.writes;val coloredRenders=InputHintSettings.renders
 repeat(1000){footer.layoutEvent()};android.os.Handler.drain()
 check(input.writes==coloredWrites && InputHintSettings.renders==coloredRenders)
 // Same text without spans is a real style overwrite, repaired from cache.
 input.hint="custom";val strippedWrites=input.writes
 footer.layoutEvent();android.os.Handler.drain()
 check(input.writes==strippedWrites+1 && (input.hint as android.text.Styled).color==2)
 check(InputHintSettings.renders==coloredRenders)
 // No-op refreshes render once per burst but don't write unchanged output.
 val beforeBurst=input.writes;val beforeRender=InputHintSettings.renders
 repeat(20){InputHintSettings.notifyChange()};android.os.Handler.drain()
 check(input.writes==beforeBurst && InputHintSettings.renders==beforeRender+1)
 InputHintSettings.templateValue="changed";InputHintSettings.notifyChange(InputHintSettings.KEY_TEMPLATE);android.os.Handler.drain()
 check(input.hint.toString()=="changed")
 InputHintSettings.templateValue="custom";InputHintSettings.notifyChange(InputHintSettings.KEY_TEMPLATE);android.os.Handler.drain()
 footer.setHint("");check(input.hint.toString()=="custom");android.os.Handler.drain()
 footer.setHint("native");check(input.hint.toString()=="custom");android.os.Handler.drain()
 val replacement=android.widget.TextView();footer.input=replacement;footer.layoutEvent();android.os.Handler.drain()
 check(replacement.hint.toString()=="custom")
 footer.setHint("latest native");android.os.Handler.drain()
 InputHintSettings.enabled=false;InputHintSettings.notifyChange();android.os.Handler.drain()
 check(replacement.hint.toString()=="latest native")
 footer.setHint("disabled native");android.os.Handler.drain();check(replacement.hint.toString()=="disabled native")
 InputHintSettings.enabled=true;InputHintSettings.notifyChange();android.os.Handler.drain();check(replacement.hint.toString()=="custom")
 // Statistics updates invalidate the render cache, stable layouts never reread.
 check(r.installStatistics()!=null)
 InputHintSettings.statisticsEnabled=true
 InputHintSettings.notifyChange(InputHintSettings.KEY_STATISTICS_ENABLE);android.os.Handler.drain()
 check(replacement.hint.toString()=="custom0")
 OutgoingMessageStatsRepository.count=9;OutgoingMessageStatsRepository.listener!!();android.os.Handler.drain()
 check(replacement.hint.toString()=="custom9")
 val statsReads=OutgoingMessageStatsRepository.reads
 repeat(1000){footer.layoutEvent()};android.os.Handler.drain()
 check(OutgoingMessageStatsRepository.reads==statsReads)
 // Expiration must invalidate even when no statistics callback is delivered.
 val expires=r.javaClass.getDeclaredField("renderedHintExpiresAt");expires.isAccessible=true;expires.setLong(r,0)
 footer.layoutEvent();android.os.Handler.drain()
 check(OutgoingMessageStatsRepository.reads==statsReads+1)
 InputHintSettings.statisticsEnabled=false
 InputHintSettings.notifyChange(InputHintSettings.KEY_STATISTICS_ENABLE);android.os.Handler.drain()
 check(replacement.hint.toString()=="custom")
 // Recursion through the public footer setHint is guarded.
 val proxy=com.tencent.mm.pluginsdk.ui.chat.ProxyInput(footer);footer.input=proxy
 footer.layoutEvent();android.os.Handler.drain();check(proxy.getHint().toString()=="custom")
 footer.setHint("proxy native");android.os.Handler.drain();check(proxy.getHint().toString()=="custom")
 InputHintSettings.notifyChange();val beforeDestroy=InputHintSettings.renders
 r.destroy();android.os.Handler.drain();check(InputHintSettings.renders==beforeDestroy);check(t.mma.hooks.core.HookRegistry.count()==0 && footer.listeners.isEmpty())
 check(proxy.getHint().toString()=="proxy native")
 footer.layoutEvent();android.os.Handler.drain()
 println("PASS: late readiness, replacement, host blank/native overwrite, copied Spanned, 3000 stable layouts no render/write, style-only and template changes, stripped styles, burst coalescing, statistics and expiry, disable/restore, recursion guard, destroy cleanup")
}
''',
'Context.kt':'''package android.content
open class Context {val applicationContext get()=this}
interface SharedPreferences {
fun getBoolean(k:String,d:Boolean):Boolean
fun registerOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener)
fun unregisterOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener)
fun interface OnSharedPreferenceChangeListener {fun onSharedPreferenceChanged(p:SharedPreferences?,k:String?)}
}
''',
'Handler.kt':'''package android.os
class Looper {companion object{fun getMainLooper()=Looper()}}
class Handler(l:Looper) {fun post(f:()->Unit){queue.add(f)};companion object{val queue=java.util.ArrayDeque<()->Unit>();fun drain(){var n=0;while(queue.isNotEmpty()){check(n++<10000);queue.removeFirst()()}}}}
''',
'View.kt':'''package android.view
open class View {
var isAttachedToWindow=true
val listeners=mutableListOf<OnLayoutChangeListener>()
fun interface OnLayoutChangeListener {fun onLayoutChange(v:View,l:Int,t:Int,r:Int,b:Int,ol:Int,ot:Int,or:Int,ob:Int)}
fun addOnLayoutChangeListener(l:OnLayoutChangeListener){listeners+=l}
fun removeOnLayoutChangeListener(l:OnLayoutChangeListener){listeners-=l}
fun layoutEvent(){listeners.toList().forEach{it.onLayoutChange(this,0,0,1,1,0,0,0,0)}}
fun post(f:()->Unit){android.os.Handler.queue.add(f)}
}
''',
'Text.kt':'''package android.text
interface Spanned:CharSequence {fun <T> getSpans(start:Int,end:Int,c:Class<T>):Array<T>;fun getSpanStart(o:Any):Int;fun getSpanEnd(o:Any):Int;fun getSpanFlags(o:Any):Int}
class Styled(val value:String,val color:Int):Spanned {
 val span=android.text.style.ForegroundColorSpan(color)
 override val length get()=value.length
 override fun get(i:Int)=value[i]
 override fun subSequence(a:Int,b:Int)=value.subSequence(a,b)
 override fun toString()=value
 @Suppress("UNCHECKED_CAST") override fun <T> getSpans(start:Int,end:Int,c:Class<T>):Array<T> = arrayOf<Any>(span) as Array<T>
 override fun getSpanStart(o:Any)=0
 override fun getSpanEnd(o:Any)=length
 override fun getSpanFlags(o:Any)=33
}
object TextUtils {fun equals(a:CharSequence?,b:CharSequence?)=a?.toString()==b?.toString()}
''',
'Span.kt':'''package android.text.style
class ForegroundColorSpan(val foregroundColor:Int)
''',
'Widget.kt':'''package android.widget
open class TextView:android.view.View(){var writes=0;var hint:CharSequence?=null;set(v){writes++;field=when(v){is android.text.Styled->android.text.Styled(v.value,v.color);null->null;else->StringBuilder(v)}}}
''',
'Hook.kt':'''package de.robv.android.xposed
open class XC_MethodHook {
class Unhook(val remove:()->Unit){fun unhook()=remove()}
class MethodHookParam(val thisObject:Any)
open fun afterHookedMethod(param:MethodHookParam){}
}
''',
'Registry.kt':'''package t.mma.hooks.core
import de.robv.android.xposed.XC_MethodHook
object HookRegistry {
val callbacks=mutableMapOf<java.lang.reflect.Method,XC_MethodHook>()
fun get()=this
fun hook(m:java.lang.reflect.Method,h:XC_MethodHook):XC_MethodHook.Unhook{callbacks[m]=h;return XC_MethodHook.Unhook{callbacks.remove(m);Unit}}
fun unhook(h:XC_MethodHook.Unhook){h.unhook()}
fun count()=callbacks.size
fun after(o:Any,n:String){callbacks.entries.toList().filter{it.key.name==n}.forEach{it.value.afterHookedMethod(XC_MethodHook.MethodHookParam(o))}}
}
''',
'Reflect.kt':'''package t.mma.utils
object KavaReflector {
fun loadClass(n:String,c:ClassLoader)=Class.forName(n,true,c)
fun declaredMethods(c:Class<*>)=c.declaredMethods
fun findMethodRecursive(c:Class<*>,n:String,vararg types:Class<*>):java.lang.reflect.Method?=runCatching{c.getMethod(n,*types)}.getOrNull()
fun invoke(m:java.lang.reflect.Method?,o:Any):Any?=m?.invoke(o)
fun invokeSuccessfully(m:java.lang.reflect.Method?,o:Any,v:Any?):Boolean {m?.invoke(o,v);return m!=null}
}
''',
'Script.kt':'''package t.mma.hooks.items.script
object ScriptSendButtonHook {fun findInputView(f:Any)=(f as com.tencent.mm.pluginsdk.ui.chat.ChatFooter).input;fun trackInputView(i:Any){}}
''',
'Footer.kt':'''package com.tencent.mm.pluginsdk.ui.chat
class ChatFooter:android.view.View(){
var input:Any?=null
fun onAttachedToWindow(){t.mma.hooks.core.HookRegistry.after(this,"onAttachedToWindow")}
fun setHint(h:CharSequence?){when(val i=input){is android.widget.TextView->i.hint=h;is ProxyInput->i.value=h};t.mma.hooks.core.HookRegistry.after(this,"setHint")}
}
class ProxyInput(val footer:ChatFooter){var value:CharSequence?="proxy original";fun getHint()=value;fun setHint(h:CharSequence?){footer.setHint(h)}}
''',
'Settings.kt':'''package t.mma.hooks.items.inputhint
data class InputHintStats(val count:Int=0)
object InputHintSettings {
const val KEY_ENABLE="enabled";const val KEY_TEMPLATE="template";const val KEY_STATISTICS_ENABLE="stats";const val DEFAULT_ENABLE=false;const val DEFAULT_STATISTICS_ENABLE=false
var statisticsEnabled=false;var enabled=true;var color=1;var renders=0;var templateValue="custom"
val listeners=mutableListOf<android.content.SharedPreferences.OnSharedPreferenceChangeListener>()
val prefs=object:android.content.SharedPreferences {
override fun getBoolean(k:String,d:Boolean)=if(k==KEY_ENABLE)enabled else statisticsEnabled
override fun registerOnSharedPreferenceChangeListener(l:android.content.SharedPreferences.OnSharedPreferenceChangeListener){listeners+=l}
override fun unregisterOnSharedPreferenceChangeListener(l:android.content.SharedPreferences.OnSharedPreferenceChangeListener){listeners-=l}
}
fun preferences(c:android.content.Context)=prefs
fun notifyChange(key:String=KEY_ENABLE){listeners.toList().forEach{it.onSharedPreferenceChanged(prefs,key)}}
fun template(p:android.content.SharedPreferences)=templateValue
fun variableColors(p:android.content.SharedPreferences)=color
fun renderTemplateSpanned(t:String,s:InputHintStats,c:Int):CharSequence{renders++;return android.text.Styled(if(statisticsEnabled) t+s.count else t,c)}
}
object OutgoingMessageStatsRepository {class Subscription;var count=0;var reads=0;var listener:(()->Unit)?=null;fun install(l:(String,Throwable?)->Unit)=true;fun subscribe(f:()->Unit):Subscription{listener=f;return Subscription()};fun current():InputHintStats{reads++;return InputHintStats(count)}}
'''
}
with tempfile.TemporaryDirectory() as td:
 p=Path(td)
 for n,v in files.items():(p/n).write_text(v)
 jar=p/'test.jar'
 cmd=['java','-Xmx384m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(jar),*[str(p/n) for n in files]]
 result=subprocess.run(cmd,capture_output=True,text=True,timeout=60)
 if result.returncode:print(result.stderr)
 result.check_returncode()
 subprocess.run(['java','-cp',f'{jar}:{std}','t.mma.hooks.items.inputhint.RuntimeKt'],check=True,timeout=20)
