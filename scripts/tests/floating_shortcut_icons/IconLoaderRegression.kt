package t.mma.hooks.items.floatingshortcut
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import java.util.concurrent.TimeUnit
private fun ImageView.source() = (drawable as? BitmapDrawable)?.bitmap?.source
private val loader = FloatingShortcutIconLoader()
fun main() {
    Looper.getMainLooper()
    var passed = 0
    fun verify(name: String, body: () -> Unit) {
        loader.clear()
        Handler.drain()
        body()
        passed++
        println("PASS " + name)
    }
    verify("cold binding returns fallback while background decode is blocked") {
        val gate = BitmapFactory.Gate(); BitmapFactory.gates["cold"] = gate
        val fallback = Drawable(); val view = ImageView()
        loader.bind(view, "cold", fallback)
        check(view.drawable === fallback)
        check(gate.started.await(3, TimeUnit.SECONDS))
        gate.release.countDown(); Handler.runNext()
        check(view.source() == "cold")
    }
    verify("shared path decodes once and cache reopening performs no decode") {
        val first = ImageView(); val second = ImageView()
        loader.bind(first, "shared", Drawable())
        loader.bind(second, "shared", Drawable())
        Handler.runNext()
        val third = ImageView(); loader.bind(third, "shared", Drawable())
        check(listOf(first, second, third).all { it.source() == "shared" })
        check(BitmapFactory.calls["shared"]!!.get() == 1)
    }
    verify("prefetch is reused by the first visible binding") {
        loader.prefetch(listOf("warm", "warm", ""))
        Handler.runNext()
        val view = ImageView(); loader.bind(view, "warm", Drawable())
        check(view.source() == "warm" && BitmapFactory.calls["warm"]!!.get() == 1)
    }
    verify("late light icon never overwrites a rebound dark icon") {
        val view = ImageView()
        loader.bind(view, "light", Drawable())
        loader.bind(view, "dark", Drawable())
        Handler.runNext(); Handler.runNext()
        check(view.source() == "dark")
    }
    verify("detached Activity view is not updated") {
        val view = ImageView(); val fallback = Drawable()
        loader.bind(view, "detached", fallback)
        view.isAttachedToWindow = false
        Handler.runNext(); check(view.drawable === fallback)
    }
    verify("replacement invalidates in-flight old file data") {
        val gate = BitmapFactory.Gate(); BitmapFactory.gates["replace"] = gate
        BitmapFactory.versions["replace"] = "old"
        val view = ImageView(); loader.bind(view, "replace", Drawable())
        check(gate.started.await(3, TimeUnit.SECONDS))
        loader.invalidate("replace")
        BitmapFactory.versions["replace"] = "new"
        loader.bind(view, "replace", Drawable())
        gate.release.countDown(); Handler.runNext()
        check(view.source() == "new")
        val reopened = ImageView(); loader.bind(reopened, "replace", Drawable())
        check(reopened.source() == "new")
    }
    verify("clear discards late callbacks and permits a fresh decode") {
        val gate = BitmapFactory.Gate(); BitmapFactory.gates["clear"] = gate
        val oldView = ImageView(); val fallback = Drawable()
        loader.bind(oldView, "clear", fallback)
        check(gate.started.await(3, TimeUnit.SECONDS))
        loader.clear()
        gate.release.countDown(); check(oldView.drawable === fallback)
        val newView = ImageView(); loader.bind(newView, "clear", Drawable())
        Handler.runNext(); check(newView.source() == "clear")
    }
    verify("large source is downsampled and capped at 256 pixels") {
        val view = ImageView(); loader.bind(view, "large", Drawable())
        Handler.runNext()
        val bitmap = (view.drawable as BitmapDrawable).bitmap
        check(bitmap.width == 256 && bitmap.height == 128)
    }
    verify("missing image is negatively cached and keeps fallback") {
        val fallback = Drawable(); val first = ImageView()
        loader.bind(first, "missing", fallback); Handler.runNext()
        val second = ImageView(); loader.bind(second, "missing", fallback)
        check(first.drawable === fallback && second.drawable === fallback)
        check(BitmapFactory.calls["missing"]!!.get() == 1)
    }
    verify("closed loader releases queues without interrupting entered decode; fresh owner survives") {
        val old = FloatingShortcutIconLoader(); val fresh = FloatingShortcutIconLoader()
        val gate = BitmapFactory.Gate(); BitmapFactory.gates["closing-owner"] = gate
        val oldView = ImageView(); val fallback = Drawable()
        old.bind(oldView, "closing-owner", fallback)
        check(gate.started.await(3, TimeUnit.SECONDS))
        repeat(100) { old.prefetch(listOf("must-not-decode-$it")) }
        val field = old.javaClass.getDeclaredField("worker").apply { isAccessible = true }
        val executor = field.get(old) as java.util.concurrent.ThreadPoolExecutor
        val pendingField = old.javaClass.getDeclaredField("pending").apply { isAccessible = true }
        check((pendingField.get(old) as Map<*, *>).size <= 64)
        old.close(); old.close()
        check(executor.isShutdown && executor.queue.isEmpty())
        check((pendingField.get(old) as Map<*, *>).isEmpty())
        val freshView = ImageView(); fresh.bind(freshView, "fresh-owner", Drawable())
        gate.release.countDown()
        check(executor.awaitTermination(3, TimeUnit.SECONDS))
        Handler.runNext(); check(freshView.source() == "fresh-owner")
        check(oldView.drawable === fallback && BitmapFactory.calls.keys.none { it.startsWith("must-not-decode-") })
        old.bind(oldView, "late-closed", Drawable()); check(BitmapFactory.calls["late-closed"] == null)
        fresh.close()
    }
    verify("post rejection clears request references and permits retry") {
        val instance = FloatingShortcutIconLoader(); Handler.reject = true
        instance.bind(ImageView(), "rejected-post", Drawable())
        val workerField = instance.javaClass.getDeclaredField("worker").apply { isAccessible = true }
        val worker = workerField.get(instance) as java.util.concurrent.ThreadPoolExecutor
        val done = java.util.concurrent.CountDownLatch(1); worker.execute { done.countDown() }
        check(done.await(3, TimeUnit.SECONDS)); Handler.reject = false
        val pendingField = instance.javaClass.getDeclaredField("pending").apply { isAccessible = true }
        check((pendingField.get(instance) as Map<*, *>).isEmpty())
        val view = ImageView(); instance.bind(view, "rejected-post", Drawable())
        Handler.runNext(); check(view.source() == "rejected-post"); instance.close()
    }
    verify("unpublished decoded bitmap is recycled on close or invalidate") {
        for (closing in listOf(false, true)) {
            val instance = FloatingShortcutIconLoader()
            val view = ImageView(); val fallback = Drawable()
            val path = "unpublished-$closing"
            instance.bind(view, path, fallback)
            val workerField = instance.javaClass.getDeclaredField("worker").apply { isAccessible = true }
            val worker = workerField.get(instance) as java.util.concurrent.ThreadPoolExecutor
            val done = java.util.concurrent.CountDownLatch(1); worker.execute { done.countDown() }
            check(done.await(3, TimeUnit.SECONDS))
            val pendingField = instance.javaClass.getDeclaredField("pending").apply { isAccessible = true }
            val request = (pendingField.get(instance) as Map<*, *>)[path]!!
            val bitmapField = request.javaClass.getDeclaredField("unpublished").apply { isAccessible = true }
            val bitmap = bitmapField.get(request) as android.graphics.Bitmap
            check(!bitmap.recycled)
            if (closing) instance.close() else instance.invalidate(path)
            check(bitmap.recycled && bitmapField.get(request) == null)
            Handler.drain(); check(view.drawable === fallback)
            instance.close()
        }
    }
    loader.close()
    println("Passed " + passed + " floating icon regression cases")
}
