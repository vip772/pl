package t.mma.ui.miuix

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import t.mma.preferences.HchatStorage
import t.mma.utils.HLog
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Version 2 archives discover settings automatically; v1 JSON remains readable. */
internal object HchatConfigBackup {
    private const val VERSION = 2
    private const val TYPE_BOOLEAN = "boolean"
    private const val TYPE_INT = "int"
    private const val TYPE_LONG = "long"
    private const val TYPE_FLOAT = "float"
    private const val TYPE_STRING = "string"
    private const val TYPE_STRING_SET = "string_set"
    private const val KEY_TYPE = "type"
    private const val KEY_VALUE = "value"
    private const val MAX_JSON = 32L * 1024 * 1024
    private const val MAX_TOTAL = 512L * 1024 * 1024
    // Existing resource stores, plus the automatic root for all future features.
    private val resourceRoots = setOf("resources", "background_beauty", "side_panel",
        "home_profile", "custom_friend_avatars", "custom_bottom_bar_icons",
        "message_bubbles", "FloatingShortcut/icons")
    private val transactionLock = Any()
    data class Result(val stores: Int, val items: Int, val resources: Int) {
        fun description() = "$stores 个配置库、$items 项配置、$resources 个资源"
    }

    fun exportToUri(context: Context, uri: Uri): Result = synchronized(transactionLock) {
        val rootDir = HchatStorage.storageDir(context)
        val prefs = JSONObject()
        var itemCount = 0
        for (name in HchatStorage.backupPreferenceNames(context)) {
            val entries = JSONObject()
            HchatStorage.preferences(context, name).all.forEach { (key, value) ->
                val encoded = encodePreferenceValue(value)
                    ?: error("不支持的配置类型: $name/$key")
                entries.put(key, encoded)
                itemCount++
            }
            prefs.put(name, entries)
        }
        val resources = linkedMapOf<String, File>()
        for (relative in resourceRoots) {
            val directory = File(rootDir, relative)
            if (!directory.exists()) continue
            for (file in directory.walkTopDown().onEnter { folder ->
                val path = folder.relativeTo(rootDir).invariantSeparatorsPath
                check(folder.canonicalPath == rootDir.canonicalPath + "/" + path) { "资源目录包含链接" }
                true
            }) {
                if (!file.isFile || file.extension in setOf("tmp", "import")) continue
                val path = file.relativeTo(rootDir).invariantSeparatorsPath
                checkedResource(rootDir, path)
                check(resources.put(path, file) == null) { "重复资源: $path" }
            }
        }
        check(resources.values.sumOf { it.length() } <= MAX_TOTAL) { "配置资源超过 512MB" }
        val manifest = JSONObject().apply {
            put("format", "HchatConfigBackup")
            put("version", VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("storageRoot", rootDir.absolutePath)
            put("prefs", prefs)
            put("resources", JSONArray(resources.keys.toList()))
        }.toString(2).toByteArray(Charsets.UTF_8)
        check(resources.size < 10000) { "资源数量过多" }
        check(manifest.size <= MAX_JSON) { "配置清单过大" }
        // Build locally first: failed serialization cannot produce a success Toast.
        val stage = File.createTempFile("hchat-export-", ".zip", context.cacheDir)
        try {
            ZipOutputStream(stage.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("config.json")); zip.write(manifest); zip.closeEntry()
                var total = manifest.size.toLong()
                resources.forEach { (path, file) ->
                    zip.putNextEntry(ZipEntry("resources/$path"))
                    file.inputStream().use { input -> total += copyLimited(input, zip, MAX_TOTAL - total) }
                    zip.closeEntry()
                }
            }
            val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("无法写入备份文件")
            output.use { stream -> stage.inputStream().use { it.copyTo(stream) } }
            Result(prefs.length(), itemCount, resources.size)
        } finally { stage.delete() }
    }

    fun importFromUri(context: Context, uri: Uri): Result = synchronized(transactionLock) {
        val stage = File.createTempFile("hchat-import-", ".stage", context.cacheDir)
        check(stage.delete() && stage.mkdirs()) { "无法创建恢复暂存目录" }
        try {
            val input = context.contentResolver.openInputStream(uri)?.buffered() ?: error("无法读取配置文件")
            val files = linkedMapOf<String, File>()
            val manifest: JSONObject
            input.use { stream ->
                stream.mark(4)
                val zip = stream.read() == 0x50 && stream.read() == 0x4b
                stream.reset()
                if (zip) {
                    var total = 0L
                    var json: ByteArray? = null
                    val names = hashSetOf<String>()
                    ZipInputStream(stream).use { archive ->
                        while (true) {
                            val entry = archive.nextEntry ?: break
                            check(!entry.isDirectory && names.add(entry.name) && names.size <= 10000) { "备份条目无效或重复" }
                            if (entry.name == "config.json") {
                                val bytes = java.io.ByteArrayOutputStream()
                                total += copyLimited(archive, bytes, minOf(MAX_JSON, MAX_TOTAL - total))
                                json = bytes.toByteArray()
                            } else {
                                check(entry.name.startsWith("resources/")) { "未知备份条目" }
                                val relative = entry.name.removePrefix("resources/")
                                checkedResource(HchatStorage.storageDir(context), relative)
                                val target = File(stage, "incoming/$relative")
                                check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
                                target.outputStream().use { total += copyLimited(archive, it, MAX_TOTAL - total) }
                                files[relative] = target
                            }
                            archive.closeEntry()
                        }
                    }
                    manifest = JSONObject((json ?: error("缺少配置清单")).toString(Charsets.UTF_8))
                    check(manifest.optInt("version") == VERSION) { "不支持的归档版本" }
                } else {
                    val bytes = java.io.ByteArrayOutputStream()
                    copyLimited(stream, bytes, MAX_JSON)
                    manifest = JSONObject(bytes.toString("UTF-8"))
                    check(manifest.optInt("version", 1) == 1) { "不支持的 JSON 配置版本" }
                }
            }
            check(manifest.optString("format") == "HchatConfigBackup") { "不是 Hchat 配置备份" }
            val declared = manifest.optJSONArray("resources")
            val declaredNames = (0 until (declared?.length() ?: 0)).map { declared!!.getString(it) }
            check(declaredNames.size == declaredNames.toSet().size && declaredNames.toSet() == files.keys) { "资源清单不完整" }
            val prefs = manifest.getJSONObject("prefs")
            val decoded = linkedMapOf<String, Map<String, Any>>()
            val oldRoot = manifest.optString("storageRoot")
            val newRoot = HchatStorage.storageDir(context).absolutePath
            for (name in prefs.keys().asSequence()) {
                check(HchatStorage.isBackupPreference(context, name)) { "禁止恢复非用户配置: $name" }
                val entries = prefs.getJSONObject(name)
                val values = linkedMapOf<String, Any>()
                for (key in entries.keys().asSequence()) {
                    val obj = entries.getJSONObject(key)
                    val value = decodeStrict(obj)
                    values[key] = when (value) {
                        is String -> relocate(value, oldRoot, newRoot)
                        is Set<*> -> value.map { relocate(it as String, oldRoot, newRoot) }.toSet()
                        else -> value
                    }
                }
                decoded[name] = values
            }
            val before = decoded.keys.associateWith { HchatStorage.preferences(context, it).all.toMap() }
            val originals = linkedMapOf<File, File?>()
            // Snapshot every affected resource before making any mutation.
            files.keys.forEachIndexed { index, relative ->
                val target = checkedResource(HchatStorage.storageDir(context), relative)
                check(!target.exists() || target.isFile) { "资源目标不是文件" }
                val snapshot = if (target.exists()) File(stage, "original/$index").also {
                    it.parentFile!!.mkdirs(); target.copyTo(it)
                } else null
                originals[target] = snapshot
            }
            val touchedFiles = arrayListOf<File>()
            val touchedPrefs = arrayListOf<String>()
            try {
                files.forEach { (relative, source) ->
                    val target = checkedResource(HchatStorage.storageDir(context), relative)
                    touchedFiles.add(target)
                    check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
                    source.copyTo(target, overwrite = true)
                    target.setLastModified(System.currentTimeMillis())
                }
                decoded.forEach { (name, values) ->
                    touchedPrefs.add(name)
                    writePreferences(HchatStorage.preferences(context, name), values)
                }
            } catch (failure: Throwable) {
                var rollbackFailed = false
                touchedPrefs.asReversed().forEach { name ->
                    runCatching { writePreferences(HchatStorage.preferences(context, name), before.getValue(name)) }
                        .onFailure { rollbackFailed = true; HLog.e("[Hchat:ConfigBackup] 配置回滚失败: $name", it) }
                }
                touchedFiles.asReversed().forEach { target ->
                    runCatching {
                        val original = originals[target]
                        if (original != null) original.copyTo(target, overwrite = true)
                        else check(!target.exists() || target.delete())
                    }.onFailure { rollbackFailed = true; HLog.e("[Hchat:ConfigBackup] 资源回滚失败", it) }
                }
                throw IllegalStateException(if (rollbackFailed) "导入失败，部分回滚失败，请重新恢复备份" else "导入失败，已恢复导入前数据", failure)
            }
            Result(decoded.size, decoded.values.sumOf { it.size }, files.size)
        } finally { stage.deleteRecursively() }
    }

    private fun relocate(value: String, oldRoot: String, newRoot: String): String =
        if (oldRoot.isNotBlank() && value.startsWith("$oldRoot/")) newRoot + value.removePrefix(oldRoot) else value

    private fun checkedResource(root: File, relative: String): File {
        check(relative.isNotBlank() && !relative.contains('\\') && !relative.startsWith('/') &&
            relative.split('/').none { it.isBlank() || it == "." || it == ".." } &&
            resourceRoots.any { relative.startsWith("$it/") }) { "非法资源路径" }
        val file = File(root, relative)
        check(file.canonicalPath == root.canonicalPath + "/" + relative) { "资源路径越界或包含链接" }
        return file
    }
    private fun copyLimited(input: InputStream, output: java.io.OutputStream, limit: Long): Long {
        check(limit >= 0) { "备份过大" }
        var size = 0L
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return size
            size += read
            check(size <= limit) { "备份超过大小限制" }
            output.write(buffer, 0, read)
        }
    }
    private fun decodeStrict(obj: JSONObject): Any {
        val value = obj.get(KEY_VALUE)
        return when (obj.getString(KEY_TYPE)) {
            TYPE_BOOLEAN -> value.also { check(it is Boolean) }
            TYPE_INT -> value.also { check(it is Number && it.toLong() in Int.MIN_VALUE..Int.MAX_VALUE && it.toDouble() == it.toInt().toDouble()) }.let { (it as Number).toInt() }
            TYPE_LONG -> value.also { check(it is Int || it is Long) }.let { (it as Number).toLong() }
            TYPE_FLOAT -> (value as? Number)?.toFloat()?.also { check(it.isFinite()) } ?: error("无效浮点数")
            TYPE_STRING -> value.also { check(it is String) }
            TYPE_STRING_SET -> (value as? JSONArray)?.let { array ->
                (0 until array.length()).map { array.get(it).also { v -> check(v is String) } as String }.toSet()
            } ?: error("无效字符串集合")
            else -> error("未知配置类型")
        }
    }
    private fun writePreferences(prefs: SharedPreferences, values: Map<String, *>) {
        val edit = prefs.edit().clear()
        values.forEach { (key, value) ->
            val encoded = encodePreferenceValue(value) ?: error("不支持的配置类型")
            check(decodePreferenceValue(edit, key, encoded)) { "配置解码失败" }
        }
        check(edit.commit()) { "配置写入失败" }
    }
    private fun encodePreferenceValue(value: Any?): JSONObject? {
        val obj = JSONObject()
        when (value) {
            is Boolean -> {
                obj.put(KEY_TYPE, TYPE_BOOLEAN)
                obj.put(KEY_VALUE, value)
            }
            is Int -> {
                obj.put(KEY_TYPE, TYPE_INT)
                obj.put(KEY_VALUE, value)
            }
            is Long -> {
                obj.put(KEY_TYPE, TYPE_LONG)
                obj.put(KEY_VALUE, value)
            }
            is Float -> {
                obj.put(KEY_TYPE, TYPE_FLOAT)
                obj.put(KEY_VALUE, value.toDouble())
            }
            is String -> {
                obj.put(KEY_TYPE, TYPE_STRING)
                obj.put(KEY_VALUE, value)
            }
            is Set<*> -> {
                obj.put(KEY_TYPE, TYPE_STRING_SET)
                obj.put(KEY_VALUE, JSONArray().apply {
                    value.filterIsInstance<String>().forEach { put(it) }
                })
            }
            else -> return null
        }
        return obj
    }

    private fun decodePreferenceValue(
        editor: SharedPreferences.Editor,
        key: String,
        encoded: JSONObject
    ): Boolean {
        return try {
            when (encoded.optString(KEY_TYPE)) {
                TYPE_BOOLEAN -> editor.putBoolean(key, encoded.optBoolean(KEY_VALUE))
                TYPE_INT -> editor.putInt(key, encoded.optInt(KEY_VALUE))
                TYPE_LONG -> editor.putLong(key, encoded.optLong(KEY_VALUE))
                TYPE_FLOAT -> editor.putFloat(key, encoded.optDouble(KEY_VALUE).toFloat())
                TYPE_STRING -> editor.putString(key, encoded.optString(KEY_VALUE, ""))
                TYPE_STRING_SET -> editor.putStringSet(key, decodeStringSet(encoded.optJSONArray(KEY_VALUE)))
                else -> return false
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun decodeStringSet(array: JSONArray?): Set<String> {
        if (array == null) return emptySet()
        val result = LinkedHashSet<String>()
        for (i in 0 until array.length()) {
            result.add(array.optString(i, ""))
        }
        return result
    }
}

