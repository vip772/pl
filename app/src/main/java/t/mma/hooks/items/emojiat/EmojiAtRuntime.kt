package t.mma.hooks.items.emojiat

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.content.SharedPreferences
import android.widget.Toast
import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.api.message.OutgoingAtMessageHook
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.ui.miuix.VoiceForwardMiuixDialog
import t.mma.utils.HLog
import t.mma.utils.KavaReflector as R
import java.util.Collections
import java.util.WeakHashMap
import java.util.IdentityHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal class EmojiAtRuntime(private val context: FeatureContext) {
    private val prefs = EmojiAtSettings.preferences(context.hostContext())
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "Hchat-EmojiAt-Members").apply { isDaemon = true } }
    private val handles = mutableListOf<XC_MethodHook.Unhook>()
    private var storageHandle: java.io.Closeable? = null
    private val pending = EmojiAtPending { SystemClock.elapsedRealtime() }
    private data class Request(val source: String, val created: Long)
    private val requests = Collections.synchronizedMap(WeakHashMap<Any, Request>())
    private data class Send(val talker: String, val users: List<String>, val records: IdentityHashMap<Any, String> = IdentityHashMap())
    private val scope = ThreadLocal<Send>()
    @Volatile private var enabled = prefs.getBoolean(EmojiAtSettings.ENABLE, false)
    @Volatile private var alive = true
    @Volatile private var generation = 0
    private var targets: EmojiAtLocator.Targets? = null
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == EmojiAtSettings.ENABLE) {
            enabled = prefs.getBoolean(key, false)
            if (enabled) schedule() else stop()
        }
    }
    private val cleanup = object : Runnable {
        override fun run() { if (alive) { pending.prune(); synchronized(requests) { requests.entries.removeAll { SystemClock.elapsedRealtime() - it.value.created >= 120_000 } }; main.postDelayed(this, 30_000) } }
    }
    fun install() {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        main.postDelayed(cleanup, 30_000)
        if (enabled) schedule()
    }
    private fun schedule() {
        val epoch = generation
        DexInstallScheduler.schedule("emoji_at_$epoch", "表情艾特") {
            synchronized(this) {
                if (!alive || !enabled || epoch != generation) true
                else if (handles.isNotEmpty()) true
                else try {
                    val t = EmojiAtLocator(context).locate()
                    installTargets(t); targets = t; HLog.d("[Hchat:表情艾特] 安装成功 send=${t.send} storage=${t.storage} scene=${t.scene} serialize=${t.serialize}"); true
                } catch (e: Throwable) {
                    handles.forEach { HookRegistry.get().unhook(it) }; handles.clear(); storageHandle?.close(); storageHandle = null
                    targets = null; pending.clear(); requests.clear()
                    report("适配检查或安装失败，保留原发送流程", e); false
                }
            }
        }
    }
    private fun report(stage: String, e: Throwable) {
        val version = runCatching { context.hostContext().packageManager.getPackageInfo(context.hostContext().packageName, 0).versionName }.getOrNull()
        HLog.e("[Hchat:表情艾特] 微信=$version 阶段=$stage 入口=${targets?.send}", e)
    }
    private fun notice(text: String) { main.post { Toast.makeText(context.hostContext(), text, Toast.LENGTH_SHORT).show() } }
    private fun hook(member: java.lang.reflect.Member, callback: XC_MethodHook) { handles += HookRegistry.get().hook(member, callback) }
    private fun installTargets(t: EmojiAtLocator.Targets) {
        hook(t.serialize, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!enabled) return
                val request = requests[param.thisObject] ?: return
                if (SystemClock.elapsedRealtime() - request.created >= 120_000) { requests.remove(param.thisObject); return }
                val source = request.source
                if (!R.writeField(param.thisObject, "p", source) || R.readField(param.thisObject, "p") != source)
                    report("序列化前写入失败", IllegalStateException(t.serialize.toString()))
            }
        })
        hook(t.scene, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!enabled || param.hasThrowable()) return
                try {
                    val talker = param.args.getOrNull(1) as? String ?: return
                    val id = (param.args.getOrNull(3) as? Number)?.toLong() ?: return
                    val source = pending.take(talker, id) ?: return
                    var body: Any? = param.thisObject
                    for (field in listOf("d", "a", "a", "e")) body = R.readField(body, field)
                    val item = (body as? List<*>)?.singleOrNull() ?: error("请求项为空或存在多个请求项")
                    require(t.serialize.declaringClass.isInstance(item))
                    check(R.writeField(item, "p", source) && R.readField(item, "p") == source)
                    synchronized(requests) {
                        if (requests.size >= 128) requests.remove(requests.keys.first())
                        requests[item] = Request(source, SystemClock.elapsedRealtime())
                        HLog.d("[Hchat:表情艾特] 已绑定上行请求 msgId=$id")
                    }
                } catch (e: Throwable) { report("绑定上行名单失败", e); notice("表情已按原流程发送，艾特名单绑定失败") }
            }
        })
        storageHandle = OutgoingAtMessageHook.register(t.storage, "emoji_at", object : OutgoingAtMessageHook.Observer {
            override fun before(param: XC_MethodHook.MethodHookParam) {
                val send = scope.get() ?: return
                if (!enabled || send.users.isEmpty()) return
                val message = param.args.firstOrNull() ?: return
                try {
                    if (R.invokeMethod(message, "N0") != send.talker || (R.invokeMethod(message, "getType") as? Number)?.toInt() != 47 ||
                        (R.invokeMethod(message, "z0") as? Number)?.toInt() != 1) return
                    val source = EmojiAtText.merge(R.readField(message, "G") as? String, send.users)
                    val setter = R.findMethodRecursive(message.javaClass, "r3", String::class.java) ?: error("消息 setter 不存在")
                    R.invokeOrThrow(setter, message, source)
                    check(R.readField(message, "G") == source)
                    send.records[message] = source
                } catch (e: Throwable) { report("入库名单写入失败", e) }
            }
            override fun after(param: XC_MethodHook.MethodHookParam) {
                val send = scope.get() ?: return
                val message = param.args.firstOrNull() ?: return
                val source = send.records.remove(message) ?: return
                if (!enabled || param.hasThrowable()) return
                val id = (param.result as? Number)?.toLong() ?: return
                pending.put(send.talker, id, source)
                HLog.d("[Hchat:表情艾特] 已关联表情入库 msgId=$id count=${send.users.size}")
            }
        })
        // Install interception last, after every downstream hook exists.
        hook(t.send, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!enabled || scope.get() != null) return
                val talker = (param.args.firstOrNull() as? String)?.takeIf { it.endsWith("@chatroom") } ?: return
                val activity = WeChatApis.currentActivity()?.currentActivity()?.takeUnless { it.isFinishing || it.isDestroyed } ?: return
                if (Looper.myLooper() != Looper.getMainLooper()) return
                val receiver = param.thisObject
                val args = param.args.clone()
                val epoch = generation
                val used = AtomicBoolean(false)
                try {
                    VoiceForwardMiuixDialog.showChoices(activity, "发送表情", "艾特发送为表情附带隐藏成员名单，不额外发送文字。", listOf(
                        "普通发送" to "保持微信原有发送方式", "艾特发送" to "最多附带499位其他成员的艾特名单"
                    ), onSelected = { index ->
                        if (used.compareAndSet(false, true) && alive && enabled && epoch == generation && !activity.isFinishing && !activity.isDestroyed) {
                            if (index == 0) resume(t, receiver, args, talker, emptyList(), epoch)
                            else worker.execute {
                                try {
                                    val account = WeChatApis.contact().account()?.selfWxId().orEmpty()
                                    val ids = WeChatApis.contact().chatrooms()?.getMemberIds(talker).orEmpty().ifEmpty {
                                        WeChatApis.contact().contacts()?.getGroupMemberIds(talker).orEmpty()
                                    }
                                    val users = EmojiAtText.users(ids, account)
                                    main.post {
                                        if (activity.isFinishing || activity.isDestroyed) return@post
                                        if (users.isEmpty()) notice("未取得当前账号或群成员，未发送，请重试或选择普通发送")
                                        else resume(t, receiver, args, talker, users, epoch)
                                    }
                                } catch (e: Throwable) { report("读取成员失败，未发送", e); notice("读取群成员失败，未发送") }
                            }
                        }
                    }, onDismiss = {})
                    param.result = null
                } catch (e: Throwable) { report("显示选择失败，保留原发送", e) }
            }
        })
    }
    private fun resume(t: EmojiAtLocator.Targets, receiver: Any, args: Array<Any?>, talker: String, users: List<String>, epoch: Int) {
        if (!alive || !enabled || epoch != generation) return
        val previous = scope.get()
        scope.set(Send(talker, users))
        try {
            HLog.d("[Hchat:表情艾特] 恢复发送 withAt=${users.isNotEmpty()} count=${users.size}")
            R.invokeOrThrow(t.send, receiver, *args)
        }
        catch (e: Throwable) { report("恢复原发送失败", e); notice("表情发送失败，请重试") }
        finally { if (previous == null) scope.remove() else scope.set(previous) }
    }
    @Synchronized private fun stop() {
        generation++
        handles.forEach { HookRegistry.get().unhook(it) }; handles.clear(); storageHandle?.close(); storageHandle = null
        targets = null; pending.clear(); requests.clear()
    }
    fun destroy() {
        alive = false; enabled = false
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        stop(); main.removeCallbacks(cleanup); worker.shutdownNow(); scope.remove()
    }
}
