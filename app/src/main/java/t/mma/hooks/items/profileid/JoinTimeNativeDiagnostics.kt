package t.mma.hooks.items.profileid

import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.core.HookRegistry
import t.mma.utils.HLog
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap
import java.lang.reflect.Field

/** Read-only probes for the verified WeChat 8.0.78 protocol and storage boundaries. */
internal object JoinTimeNativeDiagnostics {
    private val handles = ArrayList<XC_MethodHook.Unhook>()
    private val sequence = AtomicLong()
    private val lifecycle = AtomicLong()
    private val fields = ConcurrentHashMap<Pair<Class<*>, String>, Field>()
    private val scopes = ThreadLocal<ArrayDeque<Long>>()
    @Volatile private var active = false
    private var verifyWrite: ((String, Long, String) -> Unit)? = null

    private fun log(message: String) = HLog.d("[Hchat:profile_group_info] JOIN_DIAG $message")
    private fun field(value: Any?, name: String): Any? {
        if (value == null) return null
        val key = value.javaClass to name
        fields[key]?.let { return it.get(value) }
        var type: Class<*>? = value.javaClass
        while (type != null) {
            val current = type
            val found = current.declaredFields.firstOrNull { it.name == name }
            if (found != null) {
                found.isAccessible = true
                fields[key] = found
                return found.get(value)
            }
            type = current.superclass
        }
        throw NoSuchFieldException("${value.javaClass.name}.$name")
    }
    private fun members(value: Any?): Collection<*> = when (value) {
        is Map<*, *> -> value.values
        is Collection<*> -> value
        null -> emptyList<Any>()
        else -> error("Unexpected member container: ${value.javaClass.name}")
    }
    private fun summary(value: Any?, timeField: String): String {
        val rows = members(value)
        var zero = 0; var valid = 0; var invalid = 0; var unnamed = 0
        val samples = ArrayList<String>(8)
        val now = System.currentTimeMillis() / 1000L + 86400L
        for (row in rows) {
            val id = field(row, "d") as? String
            val time = (field(row, timeField) as Number).toLong()
            if (id.isNullOrBlank()) unnamed++
            when {
                time == 0L -> zero++
                time in 946684800L..minOf(Int.MAX_VALUE.toLong(), now) -> valid++
                else -> invalid++
            }
            if (samples.size < 8 && time != 0L) samples += "${id?.hashCode()?.toUInt()?.toString(16)}:$time"
        }
        return "entries=${rows.size} zero=$zero valid=$valid invalid=$invalid unnamed=$unnamed nonzeroSamples=$samples"
    }
    private fun roomSummary(room: Any?): String {
        if (room == null) return "state=room-null"
        val map = field(room, "n2")
        val proto = field(room, "l2")
        val blob = field(room, "field_roomdata") as? ByteArray
        return "map={${summary(map, "i")}} proto={${summary(field(proto, "d"), "i")}} blobBytes=${blob?.size ?: 0} blobHash=${blobHash(blob)}"
    }
    private fun times(value: Any?, timeField: String): Map<String, Long> = buildMap {
        for (row in members(value)) {
            val id = field(row, "d") as? String ?: continue
            put(id, (field(row, timeField) as Number).toLong())
        }
    }
    private fun transitions(before: Map<String, Long>, after: Map<String, Long>, incoming: Map<String, Long>): String {
        var cleared = 0; var dropped = 0; var copied = 0; var changed = 0; var removed = 0
        for ((id, old) in before) {
            val next = after[id]
            if (next == null) removed++
            else {
                if (old > 0L && next == 0L) cleared++
                if (next != old) changed++
            }
        }
        for ((id, time) in incoming) {
            if (time > 0L) {
                if (after[id] == time) copied++ else dropped++
            }
        }
        return "cleared=$cleared changed=$changed removed=$removed incomingPositiveCopied=$copied incomingPositiveNotCopied=$dropped"
    }

    private fun blobHash(blob: ByteArray?): String = if (blob == null) "null" else
        java.security.MessageDigest.getInstance("SHA-256").digest(blob).joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun group(room: Any?): String = field(room, "field_chatroomname") as? String ?: ""
    private fun parent(): Long = scopes.get()?.lastOrNull() ?: 0L

    @Synchronized fun install(loader: ClassLoader, onWrite: (String, Long, String) -> Unit) {
        if (active) return
        active = true
        val installation = lifecycle.incrementAndGet()
        verifyWrite = onWrite
        fun probe(className: String, name: String, types: List<String>, before: (XC_MethodHook.MethodHookParam, Long) -> Unit,
                  after: (XC_MethodHook.MethodHookParam, Long) -> Unit = { _, _ -> }) {
            try {
                val type = Class.forName(className, false, loader)
                val method = type.declaredMethods.single { it.name == name && it.parameterTypes.map(Class<*>::getName) == types }
                method.isAccessible = true
                val key = "Hchat.JoinTime.$className.$name"
                handles += HookRegistry.get().hook(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!active || lifecycle.get() != installation) return
                        if (className == "lb2.e" || className == "ou5.n1") {
                            val keyArg = param.args.firstOrNull()
                            if (keyArg?.javaClass?.name != "com.tencent.mm.repairer.config.profile.RepairerConfigChatroomJoinInfo" &&
                                keyArg != "clicfg_chatroom_join_info") return
                        }
                        val id = sequence.incrementAndGet()
                        val stack = scopes.get() ?: ArrayDeque<Long>().also { scopes.set(it) }
                        val parent = parent()
                        stack.addLast(id)
                        param.setObjectExtra(key, id)
                        observe(name, id) {
                            log("nativeEvent id=$id parent=$parent stage=$name phase=before")
                            before(param, id)
                        }
                    }
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val id = param.getObjectExtra(key) as? Long ?: return
                        try {
                            if (active && lifecycle.get() == installation) observe(name, id) {
                                log("nativeEvent id=$id stage=$name phase=after error=${param.throwable?.javaClass?.name ?: "none"}")
                                if (!param.hasThrowable()) after(param, id)
                            }
                        } finally {
                            scopes.get()?.let { stack ->
                                if (stack.lastOrNull() == id) stack.removeLast() else stack.remove(id)
                                if (stack.isEmpty()) scopes.remove()
                            }
                        }
                    }
                })
                log("probe stage=$name class=$className state=installed")
            } catch (error: Throwable) {
                log("probe stage=$name class=$className state=unavailable error=${error.javaClass.name}")
            }
        }
        probe("qn.w", "doScene", listOf("com.tencent.mm.network.s", "com.tencent.mm.modelbase.u0"), { p, id ->
            log("request id=$id group=${field(p.thisObject, "f")} scene=${System.identityHashCode(p.thisObject)} version=${field(p.thisObject, "g")}")
        }, { p, id -> log("requestResult id=$id result=${p.result}") })
        probe("qn.w", "onGYNetEnd", listOf("int", "int", "int", "java.lang.String", "com.tencent.mm.network.y0", "[B"), { p, id ->
            val room = field(p.thisObject, "f")
            log("response id=$id group=$room scene=${System.identityHashCode(p.thisObject)} errType=${p.args[1]} errCode=${p.args[2]}")
            if (p.args[1] == 0 && p.args[2] == 0) {
                val response = field(field(field(p.thisObject, "d"), "b"), "a")
                val list = field(response, "f")
                log("responseMembers id=$id group=$room responseVersion=${field(response, "e")} declared=${field(list, "d")} ${summary(field(list, "e"), "v")}")
            }
        }, { p, id -> log("responseEnd id=$id group=${field(p.thisObject, "f")} gateUsed=${field(p.thisObject, "m")}") })
        probe("lb2.e", "c", listOf("od5.e"), { _, _ -> }, { p, id ->
            if (p.args[0]?.javaClass?.name == "com.tencent.mm.repairer.config.profile.RepairerConfigChatroomJoinInfo")
                log("gateUsed id=$id value=${p.result}")
        })
        probe("com.tencent.mm.storage.z2", "P0", listOf("java.lang.String", "java.util.List", "java.util.List", "java.util.List", "boolean"), { p, id ->
            p.setObjectExtra("join.before", times(field(p.thisObject, "n2"), "i"))
            p.setObjectExtra("join.incoming", times(p.args[1], "v"))
            log("detailApply id=$id group=${group(p.thisObject)} gate=${p.args[4]} incoming={${summary(p.args[1], "v")}} before={${roomSummary(p.thisObject)}}")
        }, { p, id ->
            @Suppress("UNCHECKED_CAST")
            val before = p.getObjectExtra("join.before") as? Map<String, Long>
            @Suppress("UNCHECKED_CAST")
            val incoming = p.getObjectExtra("join.incoming") as? Map<String, Long>
            if (before == null || incoming == null) {
                log("detailApplied id=$id state=before-observation-unavailable")
                return@probe
            }
            log("detailApplied id=$id group=${group(p.thisObject)} ${transitions(before, times(field(p.thisObject, "n2"), "i"), incoming)} after={${roomSummary(p.thisObject)}}")
        })
        probe("b41.u1", "N", listOf("java.lang.String", "java.lang.String", "pc5.zz", "int", "int", "int", "java.lang.String", "so.a", "boolean", "boolean", "int"), { p, id ->
            log("modContact id=$id group=${p.args[0]} declared=${field(p.args[2], "d")} incoming={${summary(field(p.args[2], "e"), "v")}} previousProto={${summary(field(p.args[7], "d"), "i")}}")
        }, { p, id -> log("modContactResult id=$id result=${p.result}") })
        probe("b41.u1", "M", listOf("com.tencent.mm.storage.z2"), { p, id ->
            log("persistDispatch id=$id group=${group(p.args[0])} ${roomSummary(p.args[0])}")
        }, { p, id -> log("persistDispatchResult id=$id result=${p.result}") })
        probe("com.tencent.mm.storage.a3", "Q1", listOf("com.tencent.mm.storage.z2", "boolean"), { p, id ->
            log("persist id=$id group=${group(p.args[0])} notify=${p.args[1]} before={${roomSummary(p.args[0])}}")
        }, { p, id ->
            val room = group(p.args[0])
            log("persistResult id=$id group=$room result=${p.result} after={${roomSummary(p.args[0])}}")
            if (p.result == true && room.isNotBlank()) verifyWrite?.invoke(room, id, blobHash(field(p.args[0], "field_roomdata") as? ByteArray))
        })
        probe("com.tencent.mm.storage.z2", "X0", listOf("java.lang.String", "so.a", "boolean"), { p, id ->
            log("serialize id=$id group=${group(p.thisObject)} incomingProto={${summary(field(p.args[1], "d"), "i")}}")
        }, { p, id -> log("serialized id=$id group=${group(p.thisObject)} ${roomSummary(p.thisObject)}") })
        probe("ou5.n1", "m", listOf("od5.e"), { _, _ -> }, { p, id ->
            log("gatePolicy id=$id localPolicy=${p.result}")
        })
        probe("ou5.n1", "g", listOf("od5.e"), { _, _ -> }, { p, id ->
            log("gateLocal id=$id value=${p.result}")
        })
        probe("com.tencent.mm.repairer.config.profile.RepairerConfigChatroomJoinInfo", "c", emptyList(), { _, _ -> }, { p, id ->
            log("gateDefault id=$id value=${p.result}")
        })
        probe("lb2.e", "i", listOf("java.lang.String", "int", "boolean", "boolean"), { p, id ->
            log("gateExperiment id=$id fallback=${p.args[1]} flags=${p.args[2]},${p.args[3]}")
        }, { p, id -> log("gateExperimentResult id=$id value=${p.result}") })
        probe("lb2.e", "a", listOf("java.lang.String", "java.lang.String", "boolean", "boolean"), { _, _ -> }, { p, id ->
            // Only this numeric experiment key is admitted by the callback filter.
            val raw = p.result as? String
            log("gateExperimentRaw id=$id state=${if (raw.isNullOrEmpty()) "empty" else "present"} numeric=${raw?.toIntOrNull()}")
        })
        log("probeSummary installed=${handles.size} expected=13 state=${if (handles.size == 13) "complete" else "incomplete"}")
    }

    private inline fun observe(stage: String, id: Long, block: () -> Unit) {
        try { block() } catch (error: Throwable) {
            log("observerError id=$id stage=$stage error=${error.javaClass.name}")
        }
    }

    @Synchronized fun close() {
        active = false
        lifecycle.incrementAndGet()
        verifyWrite = null
        handles.forEach { handle ->
            try { HookRegistry.get().unhook(handle) }
            catch (error: Throwable) { log("probeClose error=${error.javaClass.name}") }
        }
        handles.clear()
        fields.clear()
        scopes.remove()
    }
}
