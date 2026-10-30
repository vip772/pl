"""Compile production QQ music feature and verify toggle/runtime generation ownership."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1];libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
    p=Path(td)
    files={
    
    'Prefs.kt':'''package android.content
    interface SharedPreferences{fun getBoolean(k:String,d:Boolean):Boolean;fun registerOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener);fun unregisterOnSharedPreferenceChangeListener(l:OnSharedPreferenceChangeListener);fun interface OnSharedPreferenceChangeListener{fun onSharedPreferenceChanged(p:SharedPreferences,k:String?)}}
    ''',
    'Core.kt':'''package t.mma.hooks.core
    import android.content.SharedPreferences
    class FeatureContext{fun hostContext():Any=Any();fun eventBus()=Bus();fun uiRegistry()=Registry()}
    class Bus {fun post(e:Any){}}
class Registry{fun registerProvider(p:Any){};fun unregisterProvider(id:String){}}
    open class BaseFeature{
    private var scope=FeatureResourceScope{}
    val callbacks=mutableListOf<()->Unit>()
    open fun featureId()="";open fun name()="";open fun onFeatureInit(c:FeatureContext){};open fun onFeatureInstall(c:FeatureContext){}
    fun registerSettingsProvider(p:Any){};fun logError(s:String,t:Throwable?){}
    fun trackResource(r:()->Unit){scope.register(r)}
    fun subscribe(c:Class<*>,f:()->Unit){callbacks.add(f)}
    fun close(){scope.close()};fun reopen(){scope=FeatureResourceScope{}}
    }
    object DexInstallScheduler{enum class Stage{WARMUP};fun schedule(k:String,l:String,stage:Stage,installer:()->Boolean){installer()};fun installation(k:String,l:String)=Any()}
    ''',
    'Event.kt':'''package t.mma.event
    object Events{class DexReady}
    ''',
    'Storage.kt':'''package t.mma.preferences
    import android.content.SharedPreferences
    object HchatStorage{
    var enabled=false
    val listeners=mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()
    val prefs=java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader,arrayOf(SharedPreferences::class.java)){_,m,a->when(m.name){
    "getBoolean"->enabled
    "registerOnSharedPreferenceChangeListener"->{listeners.add(a!![0] as SharedPreferences.OnSharedPreferenceChangeListener);null}
    "unregisterOnSharedPreferenceChangeListener"->{listeners.remove(a!![0]);null}
    else->null
    }} as SharedPreferences
    fun preferences(c:Any,n:String)=prefs
    fun toggle(v:Boolean){enabled=v;listeners.toList().forEach{it.onSharedPreferenceChanged(prefs,"qq_music_order_enable")}}
    }
    ''',
    'Utils.kt':'''package t.mma.utils
    object HLog{fun e(s:String,t:Throwable?=null){}}
    object KavaReflector{fun findMethod(c:Class<*>,n:String)=c.getMethod(n);fun invoke(m:java.lang.reflect.Method,o:Any)=m.invoke(o)}
    ''',
    'Send.kt':'''package t.mma.hooks.items.script
    object ScriptSendButtonHook{var installs=0;fun install(c:t.mma.hooks.core.FeatureContext):Boolean{installs++;return true}}
    ''',
    'MusicSettings.kt':'''package t.mma.hooks.items.musicorder
    object QQMusicOrderSettings{const val PREFS_NAME="fixture";const val KEY_ENABLE="qq_music_order_enable";const val DEFAULT_ENABLE=false}
    class QQMusicOrderSettingsProvider
    ''',
    'Runtime.kt':'''package t.mma.hooks.items.musicorder
    object RuntimeState{var creates=0;var destroys=0;var fail=false;val subscriptions=mutableListOf<Sub>();val tracks=mutableListOf<(Any)->Unit>()}
    class Sub{var active=true;fun unsubscribe(){active=false}}
    class QQMusicOrderRuntime(c:Any,log:(String,Throwable?)->Unit){init{RuntimeState.creates++}
    fun install(track:(Any)->Unit){RuntimeState.tracks.add(track);val sub=Sub();RuntimeState.subscriptions.add(sub);track(sub);if(RuntimeState.fail)error("injected")}
    fun destroy(){RuntimeState.destroys++}
    }
    ''',
    'Test.kt':'''package t.mma.hooks.items.musicorder
    import t.mma.hooks.core.*
    import t.mma.preferences.HchatStorage
    import t.mma.hooks.items.script.ScriptSendButtonHook
    fun main(){
    val f=QQMusicOrderFeature();val c=FeatureContext();val sp=HchatStorage
    f.onFeatureInstall(c);check(RuntimeState.creates==0&&sp.listeners.size==1)
    sp.toggle(true);check(RuntimeState.creates==1)
    val oldTrack=RuntimeState.tracks.last();val oldSub=RuntimeState.subscriptions.last()
    sp.toggle(true);check(RuntimeState.creates==1)
    sp.toggle(false);check(RuntimeState.destroys==1&&!oldSub.active)
    val stale=Sub();oldTrack(stale);check(!stale.active)
    sp.toggle(true);check(RuntimeState.creates==2)
    val oldCallback=f.callbacks.last();val hookCount=ScriptSendButtonHook.installs
    f.callbacks.first()();check(ScriptSendButtonHook.installs==hookCount+1)
    val late=sp.listeners.single();f.close();check(sp.listeners.isEmpty()&&RuntimeState.destroys==2)
    late.onSharedPreferenceChanged(sp.prefs,QQMusicOrderSettings.KEY_ENABLE);oldCallback();check(RuntimeState.creates==2&&ScriptSendButtonHook.installs==hookCount+1)
    f.reopen();sp.enabled=false;f.onFeatureInstall(c);sp.toggle(true);check(RuntimeState.creates==3)
    f.close();check(RuntimeState.destroys==3)
    f.reopen();sp.enabled=false;f.onFeatureInstall(c)
    val sharedCount=ScriptSendButtonHook.installs
    repeat(20){sp.toggle(true);sp.toggle(true);sp.toggle(false);sp.toggle(false)}
    check(RuntimeState.creates==23&&RuntimeState.destroys==23)
    check(RuntimeState.subscriptions.all{!it.active}&&ScriptSendButtonHook.installs==sharedCount)
    RuntimeState.fail=true;sp.toggle(true);check(RuntimeState.creates==24&&RuntimeState.destroys==24)
    RuntimeState.fail=false;sp.toggle(false);sp.toggle(true);check(RuntimeState.creates==25)
    f.close();check(RuntimeState.destroys==25&&sp.listeners.isEmpty()&&RuntimeState.subscriptions.all{!it.active})
    println("PASS QQ music lifecycle: 20-toggle generation, duplicate enable, stale listener/registrar, subscription cleanup and shared Hook retained")
    }
    '''
    }
    for n,s in files.items():(p/n).write_text(s)
    source=r/'app/src/main/java/t/mma/hooks/items/musicorder/QQMusicOrderFeature.kt';s=source.read_text()
    assert 'ScriptSendButtonHook.install(context)' in s
    assert 'resources.register { installed.destroy()' in s and 'installation.schedule' not in s
    assert 'KavaReflector.findMethod(subscription.javaClass, "unsubscribe")' in s
    subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(std),'-d',str(p/'test.jar'),*map(str,p.glob('*.kt')),str(r/'app/src/main/java/t/mma/hooks/core/FeatureResourceScope.kt'),str(source)],check=True,timeout=45)
    subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','t.mma.hooks.items.musicorder.TestKt'],check=True,timeout=15)
