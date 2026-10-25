package t.mma.preferences
import t.mma.utils.HLog

import android.content.Context
import android.content.SharedPreferences
import io.fastkv.FastKV
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object HchatStorage {
    private const val TAG = "[Hchat:Storage]"
    private const val DIR_NAME = "Hchat"
    private val cache = ConcurrentHashMap<String, SharedPreferences>()

    @JvmStatic
    fun preferences(context: Context, name: String): SharedPreferences {
        val appContext = context.applicationContext ?: context
        val cacheKey = cacheKey(appContext, name)
        cache[cacheKey]?.let { return it }

        val created = create(appContext, name)
        val existing = cache.putIfAbsent(cacheKey, created)
        return existing ?: created
    }

    @JvmStatic
    fun reopenIfFilesMissing(context: Context, name: String): SharedPreferences {
        val appContext = context.applicationContext ?: context
        val dir = storageDir(appContext)
        if (hasBackingFile(dir, name)) return preferences(appContext, name)

        val cacheKey = cacheKey(appContext, name)
        synchronized(cache) {
            if (hasBackingFile(dir, name)) return preferences(appContext, name)
            val stale = cache.remove(cacheKey)
            if (stale is FastKV) {
                runCatching { stale.close() }
                    .onFailure { HLog.e("$TAG 关闭失效配置失败: $name ${it.message}", it) }
            }
            return create(appContext, name).also { cache[cacheKey] = it }
        }
    }

    private fun create(context: Context, name: String): SharedPreferences {
        val dir = storageDir(context)
        return FastKV.Builder(dir.absolutePath, name).build().also { target ->
            if (name == "Hchat_conversation_analysis_config" &&
                !File(dir, ".conversation_ai_migrated").exists()) {
                val legacy = context.getSharedPreferences(name, Context.MODE_PRIVATE)
                val editor = target.edit()
                legacy.all.filterKeys { !target.contains(it) }.forEach { (key, value) ->
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                }
                check(editor.commit()) { "会话分析配置迁移失败" }
                val marker = File(dir, ".conversation_ai_migrated")
                check(marker.exists() || marker.createNewFile())
            }
        }
    }

    private fun cacheKey(context: Context, name: String): String = "${context.packageName}:$name"

    private fun hasBackingFile(dir: File, name: String): Boolean {
        return FAST_KV_SUFFIXES.any { suffix -> File(dir, name + suffix).isFile }
    }

    @JvmStatic
    fun storageDir(context: Context): File {
        val base = try {
            context.dataDir
        } catch (_: Throwable) {
            context.filesDir
        }
        val dir = File(base, DIR_NAME)
        if (!dir.isDirectory && !dir.mkdirs()) {
            HLog.e("$TAG 创建目录失败: ${dir.absolutePath}")
        }
        return dir
    }

    /** New settings are backed up by default; runtime stores must opt out. */
    private val runtimeNames = ConcurrentHashMap.newKeySet<String>()
    @JvmStatic
    fun runtimePreferences(context: Context, name: String): SharedPreferences {
        require(validBackupName(name)) { "Invalid runtime store name" }
        if (!runtimeNames.add(name)) return preferences(context, name)
        // Persist classification so unopened stores remain excluded after restart.
        File(storageDir(context), ".runtime_stores").apply { mkdirs() }
            .resolve(name).apply { if (!exists()) createNewFile() }
        return preferences(context, name)
    }
    @JvmStatic
    fun validBackupName(name: String): Boolean =
        name.length in 1..200 && name.matches(Regex("[A-Za-z0-9_-]+")) && name.startsWith("Hchat")
    @JvmStatic
    fun isBackupPreference(context: Context, name: String): Boolean {
        val lower = name.lowercase(java.util.Locale.ROOT)
        return validBackupName(name) && name !in runtimeNames &&
            !File(File(storageDir(context), ".runtime_stores"), name).exists() &&
            !lower.endsWith("_cache") && !lower.endsWith("_seen") &&
            !lower.contains("_method_cache") && !lower.contains("_runtime_log")
    }
    @JvmStatic
    fun backupPreferenceNames(context: Context): Set<String> {
        val names = sortedSetOf<String>()
        if (context.getSharedPreferences("Hchat_conversation_analysis_config", Context.MODE_PRIVATE).all.isNotEmpty()) {
            preferences(context, "Hchat_conversation_analysis_config")
        }
        val prefix = context.packageName + ":"
        cache.keys.filter { it.startsWith(prefix) }.forEach { names.add(it.removePrefix(prefix)) }
        storageDir(context).listFiles()?.filter { it.isFile }?.forEach { file ->
            FAST_KV_SUFFIXES.firstOrNull { file.name.endsWith(it) }?.let {
                names.add(file.name.removeSuffix(it))
            }
        }
        return names.filterTo(sortedSetOf()) { isBackupPreference(context, it) }
    }
    /** Feature resources must live under this root to be automatically archived. */
    @JvmStatic
    fun resourceDirectory(context: Context, feature: String): File {
        require(feature.matches(Regex("[A-Za-z0-9_-]+")))
        return File(File(storageDir(context), "resources"), feature).apply { mkdirs() }
    }

    private val FAST_KV_SUFFIXES = arrayOf(".kva", ".kvb", ".kvc")
}
