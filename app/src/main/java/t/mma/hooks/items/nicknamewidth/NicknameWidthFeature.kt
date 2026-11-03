package t.mma.hooks.items.nicknamewidth

import android.os.Handler
import android.os.Looper
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.OwnedHooks
import t.mma.hooks.core.PreferenceRuntimeOwner
import t.mma.preferences.HchatStorage
import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider
import t.mma.utils.HLog
import java.util.WeakHashMap

object NicknameWidthSettings {
    const val ID = "group_nickname_width"
    const val PREFS = "Hchat_group_nickname_width_config"
    const val ENABLE = "enable"
    const val TITLE = "解除群成员昵称长度限制"
    const val SUMMARY = "移除群成员昵称的固定最大宽度，使用消息区域可用空间"
}

class NicknameWidthSettingsProvider : SimpleFeatureSettingsProvider(
    NicknameWidthSettings.ID, NicknameWidthSettings.TITLE, NicknameWidthSettings.SUMMARY,
    FeatureSettingsProvider.CATEGORY_ENHANCE, true
)

/**
 * Main-thread maxWidth ownership. Never touches text, ellipsis, line count or parent geometry.
 *
 * The record table is process-wide and weakly keyed: a recycled TextView or a newer runtime must
 * inherit the real original width instead of losing it, and a destroyed runtime must never restore
 * over a width that a newer runtime already took over. close() sets the closed flag under the same
 * lock as bind(), so a hook callback that already entered cannot write after the restore, and a
 * rejected main-thread posting falls back to an in-place restore instead of leaking maxWidth.
 */
internal class NicknameWidthState(private val onError: (Throwable) -> Unit) {
    private class Record(var original: Int, var token: Any)

    private val token = Any()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false

    fun isClosed(): Boolean = closed

    /** Main thread only. false = feature off or not a group room: release our claim on the view. */
    fun bind(view: TextView, group: Boolean) {
        if (closed) return
        if (!group) {
            release(view)
            return
        }
        synchronized(lock) {
            if (closed) return
            val existing = records[view]
            if (existing != null) {
                if (existing.token === token) return
                // Take over the record: dropping it would lose the real original width.
                val previous = existing.token
                val current = view.maxWidth
                if (current in 0 until Int.MAX_VALUE) existing.original = current
                existing.token = token
                if (current == Int.MAX_VALUE) return
                try {
                    view.maxWidth = Int.MAX_VALUE
                } catch (error: Throwable) {
                    existing.token = previous
                    throw error
                }
                return
            }
            val width = view.maxWidth
            if (width < 0 || width == Int.MAX_VALUE) return
            val record = Record(width, token)
            records[view] = record
            try {
                view.maxWidth = Int.MAX_VALUE
            } catch (error: Throwable) {
                if (records[view] === record) records.remove(view)
                throw error
            }
        }
    }

    /** Any thread. Stop new binds first, then restore every view this runtime still owns. */
    fun close() {
        val pending = synchronized(lock) {
            if (closed) return
            closed = true
            val mine = ArrayList<Pair<TextView, Record>>()
            val iterator = records.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value.token === token) {
                    mine.add(entry.key to entry.value)
                    iterator.remove()
                }
            }
            mine
        }
        if (pending.isEmpty()) return
        val task = Runnable { pending.forEach { (view, record) -> restore(view, record) } }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run()
            return
        }
        val posted = try {
            main.post(task)
        } catch (error: Throwable) {
            report(error)
            false
        }
        if (!posted) {
            report(IllegalStateException("主线程投递被拒绝，改为就地恢复昵称宽度"))
            task.run()
        }
    }

    private fun release(view: TextView) {
        synchronized(lock) {
            val record = records[view] ?: return
            if (record.token !== token) return
            records.remove(view)
            restore(view, record)
        }
    }

    private fun restore(view: TextView, record: Record) {
        if (view.maxWidth != Int.MAX_VALUE) return
        try {
            view.maxWidth = record.original
        } catch (error: Throwable) {
            report(error)
        }
    }

    private fun report(error: Throwable) {
        try {
            onError(error)
        } catch (_: Throwable) {
            // Cleanup must continue.
        }
    }

    private companion object {
        private val lock = Any()
        private val records = WeakHashMap<TextView, Record>()
    }
}

/**
 * One enabled interval. Owns its width state, hook handles and install ticket; close() is
 * idempotent and releases in the order that keeps widths consistent.
 */
internal class NicknameWidthRuntime(
    private val context: FeatureContext,
    private val report: (String, Throwable?) -> Unit
) {
    private val state = NicknameWidthState { report("昵称宽度恢复失败", it) }
    @Volatile private var hooks = OwnedHooks { report("昵称宽度 Hook 释放失败", it) }
    private var installation: DexInstallScheduler.Installation? = null
    private var closeRequested = false
    private var hookFailures = 0

    fun start() {
        val version = readVersion() ?: return
        if (version != VERIFIED_VERSION) {
            HLog.w("$TAG 未验证微信版本 $version，未安装昵称宽度 Hook")
            return
        }
        val ticket = try {
            // EARLY keeps the original immediate timing (no DexKit lookup); the ticket still gives
            // us bounded retries and cancellable ownership of any in-flight install attempt.
            DexInstallScheduler.installation(
                NicknameWidthSettings.ID, NicknameWidthSettings.TITLE, DexInstallScheduler.Stage.EARLY
            )
        } catch (error: Throwable) {
            report("创建昵称宽度安装票据失败", error)
            return
        }
        installation = ticket
        try {
            ticket.schedule { install() }
        } catch (error: Throwable) {
            report("调度昵称宽度 Hook 安装失败", error)
            close()
        }
    }

    fun close() {
        if (closeRequested) return
        closeRequested = true
        // Order matters: stop new binds and restore widths, then drop hooks, then cancel retries.
        state.close()
        releaseHooks()
        val ticket = installation
        installation = null
        try {
            ticket?.close()
        } catch (error: Throwable) {
            report("取消昵称宽度安装重试失败", error)
        }
    }

    private fun readVersion(): String? = try {
        val host = context.hostContext()
        host.packageManager.getPackageInfo(host.packageName, 0).versionName
    } catch (error: Throwable) {
        report("读取微信版本失败，未安装昵称宽度 Hook", error)
        null
    }

    /** Runs on the DexInstallScheduler thread; returning false lets the scheduler retry. */
    private fun install(): Boolean {
        if (closeRequested || state.isClosed()) return false
        val owner = hooks
        if (owner.isClosed()) return false
        if (owner.size() > 0) return true
        return try {
            val loader = context.hostClassLoader()
            val holder = Class.forName(HOLDER_CLASS, false, loader)
            val chat = Class.forName(CHAT_CLASS, false, loader)
            val message = Class.forName(MESSAGE_CLASS, false, loader)
            val binder = Class.forName(BINDER_CLASS, false, loader)
            val field = holder.getDeclaredField("userTV").apply { isAccessible = true }
            check(field.type == TextView::class.java) { "昵称视图字段类型变化" }
            val talker = chat.getDeclaredMethod("x").apply { isAccessible = true }
            check(talker.returnType == String::class.java) { "会话标识方法返回类型变化" }
            val method = binder.getDeclaredMethod("p", holder, chat, message, String::class.java)
            check(method.returnType == Void.TYPE) { "昵称绑定方法签名变化" }
            val handle = owner.hook(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (closeRequested || state.isClosed()) return
                    if (Looper.myLooper() != Looper.getMainLooper()) return
                    try {
                        val view = field.get(param.args[0]) as? TextView ?: return
                        val chatObject = param.args.getOrNull(1) ?: return
                        val room = talker.invoke(chatObject) as? String ?: ""
                        state.bind(view, isGroupRoom(room))
                    } catch (error: Throwable) {
                        onHookFailure(error)
                    }
                }
            })
            if (handle == null) {
                releaseHooks()
                false
            } else true
        } catch (error: Throwable) {
            releaseHooks()
            report("安装昵称宽度 Hook 失败", error)
            false
        }
    }

    /** Drop this attempt's handles so a bounded scheduler retry starts from a clean ownership set. */
    private fun releaseHooks() {
        val stale = hooks
        hooks = OwnedHooks { report("昵称宽度 Hook 释放失败", it) }
        try {
            stale.close()
        } catch (error: Throwable) {
            report("昵称宽度 Hook 释放失败", error)
        }
    }

    private fun onHookFailure(error: Throwable) {
        hookFailures += 1
        if (hookFailures <= MAX_LOGGED_FAILURES || hookFailures % LOG_INTERVAL == 0) {
            report("昵称宽度调整失败（第 $hookFailures 次）", error)
        }
        if (hookFailures < MAX_TOLERATED_FAILURES) return
        report("昵称宽度连续失败 $hookFailures 次，停止本次运行", error)
        close()
    }

    private fun isGroupRoom(room: String): Boolean =
        room.endsWith("@chatroom") || room.endsWith("@im.chatroom")

    private companion object {
        private const val TAG = "[Hchat:NicknameWidth]"
        private const val VERIFIED_VERSION = "8.0.78"
        private const val HOLDER_CLASS = "com.tencent.mm.ui.chatting.viewitems.h0"
        private const val CHAT_CLASS = "gk5.d"
        private const val MESSAGE_CLASS = "com.tencent.mm.storage.e9"
        private const val BINDER_CLASS = "com.tencent.mm.ui.chatting.viewitems.b0"
        private const val MAX_LOGGED_FAILURES = 5
        private const val LOG_INTERVAL = 10
        private const val MAX_TOLERATED_FAILURES = 50
    }
}

/** Verified 8.0.78 username binder; no global TextView hook or per-frame work. */
class NicknameWidthFeature : BaseFeature() {
    override fun featureId() = NicknameWidthSettings.ID
    override fun name() = NicknameWidthSettings.TITLE

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(NicknameWidthSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val prefs = HchatStorage.preferences(context.hostContext(), NicknameWidthSettings.PREFS)
        var runtime: NicknameWidthRuntime? = null
        val owner = PreferenceRuntimeOwner(
            prefs,
            setOf(NicknameWidthSettings.ENABLE),
            enabled = { prefs.getBoolean(NicknameWidthSettings.ENABLE, false) },
            create = { resources ->
                val created = NicknameWidthRuntime(context) { message, error -> logError(message, error) }
                runtime = created
                resources.register {
                    // Isolate cleanup: the runtime reference must drop even if the runtime throws.
                    try {
                        created.close()
                    } finally {
                        if (runtime === created) runtime = null
                    }
                }
                try {
                    created.start()
                } catch (error: Throwable) {
                    logError("昵称宽度启动失败", error)
                    resources.close()
                }
            },
            onError = { logError("昵称宽度热启停失败", it) }
        )
        trackResource { owner.close() }
        owner.start()
    }
}