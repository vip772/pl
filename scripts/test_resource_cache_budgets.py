"""Exercise production cache eviction and memory callbacks with JVM fixtures."""
from pathlib import Path
import subprocess, tempfile
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/t/mma/ui/miuix/MiuixSettingsPage.kt').read_text()
a=s.index('internal object AvatarMemoryCache');b=s.index('internal object WeChatAvatarPathCache',a)
avatar=s[a:b]
s=(root/'app/src/main/java/t/mma/hooks/items/backgroundbeauty/BackgroundBeautyStore.kt').read_text()
a=s.index('    private const val MAX_CACHE_ENTRIES');b=s.index('    fun hasImage(',a)
background=s[a:b]
callback=(root/'app/src/main/java/t/mma/hooks/core/ResourceMemoryCallbacks.kt').read_text()
code='''import java.util.LinkedHashMap
class Bitmap(val allocationByteCount:Int)
class ImageBitmap(val width:Int,val height:Int,val bytes:Int=width*height*4)
fun ImageBitmap.asAndroidBitmap()=Bitmap(bytes)
object BackgroundBeautySettings {enum class Slot {A,B,C}}
'''+avatar+'''
object BackgroundCache {
'''+background+'''
 fun put(slot:BackgroundBeautySettings.Slot,bytes:Int){cache[slot]=CacheEntry(0,0,Bitmap(bytes));trimCache(MAX_CACHE_BYTES)}
 fun has(slot:BackgroundBeautySettings.Slot)=cache.containsKey(slot)
 fun bytes()=cache.values.sumOf{it.bitmap.allocationByteCount.toLong()}
}
fun main(){
 val c=AvatarMemoryCache
 c.trimMemory();c.put("null",null);check(c.cached("null")==Pair(true,null))
 repeat(63){c.put("k$it",ImageBitmap(1,1))}
 check(c.cached("null").first);c.put("extra",ImageBitmap(1,1))
 check(!c.cached("k0").first && c.cached("null").first)
 c.trimMemory();val large=ImageBitmap(1,1,5*1024*1024)
 c.put("a",large);c.put("b",large);check(!c.cached("a").first && c.cached("b").first)
 c.put("b",ImageBitmap(1,1));c.put("a",large);check(c.cached("b").first)
 c.put("oversize",ImageBitmap(1,1,9*1024*1024));check(!c.cached("oversize").first)
 c.put("normal",ImageBitmap(1,1));check(c.cached("normal").first)
 c.trimMemory();check(!c.cached("normal").first)
 val bg=BackgroundCache;val a=BackgroundBeautySettings.Slot.A;val b=BackgroundBeautySettings.Slot.B;val d=BackgroundBeautySettings.Slot.C
 bg.put(a,16*1024*1024);bg.put(b,16*1024*1024);check(!bg.has(a) && bg.has(b))
 bg.put(d,4);check(bg.has(b) && bg.has(d));bg.put(a,4);check(!bg.has(b))
 bg.put(a,25*1024*1024);check(bg.bytes()==0L)
 bg.put(a,1024);bg.trimMemory();check(bg.bytes()==0L)
 println("PASS production cache budgets: access LRU, entries, allocated bytes, replacement, null, oversized entry, cache clear")
}
'''
libs=Path('/tmp/pl-kotlin-libs');std=next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'Test.kt').write_text(code)
 compiler=['java','-Xmx256m','-cp',':'.join(map(str,libs.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect']
 subprocess.run(compiler+['-classpath',str(std),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=45)
 subprocess.run(['java','-cp',f'{p/"test.jar"}:{std}','TestKt'],check=True,timeout=10)
 # Compile the actual memory callback against the Android public ABI.
 (p/'Callback.kt').write_text(callback)
 (p/'Background.kt').write_text('package t.mma.hooks.items.backgroundbeauty\nobject BackgroundBeautyStore {fun trimMemory(){}}')
 (p/'Log.kt').write_text('package t.mma.utils\nobject HLog {fun e(s:String,t:Throwable){}}')
 (p/'Avatar.kt').write_text('package t.mma.ui.miuix\nobject AvatarMemoryCache {fun trimMemory(){}}')
 subprocess.run(compiler+['-classpath',str(std)+':/usr/lib/android-sdk/platforms/android-36/android.jar','-d',str(p/'callback.jar'),str(p/'Callback.kt'),str(p/'Background.kt'),str(p/'Avatar.kt'),str(p/'Log.kt')],check=True,timeout=45)
 print('PASS production memory callbacks: Android SDK signature compile')
