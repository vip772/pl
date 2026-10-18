package t.mma.hooks.items.chatopendiagnostics

import android.content.Context
import android.database.Cursor
import android.util.Base64
import org.json.JSONObject
import t.mma.hooks.api.core.WeChatApis
import t.mma.utils.HLog
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Local, one-shot diagnostic command. No command file means no mutation. */
internal object SingleMessageRemoval {
    fun run(context: Context) {
        val command = File(context.dataDir, "Hchat/remove-388308.request")
        if (!command.isFile) return
        val claimed = File(command.parentFile, "remove-388308.processing")
        if (claimed.exists() || !command.renameTo(claimed)) return
        val resultFile = File(command.parentFile, "remove-388308.result.json")
        val result = JSONObject()
        try {
            check(claimed.readText().trim() == "DELETE 388308 5063355820916335885") { "Invalid command" }
            val db = checkNotNull(WeChatApis.database())
            var ready = db.isReady
            repeat(60) {
                if (!ready) {
                    ready = db.prepare()
                    if (!ready) Thread.sleep(500)
                }
            }
            check(ready) { "Database not ready" }
            val matches = db.messageTables().mapNotNull { table ->
                val cursor = db.rawQuery("SELECT * FROM ${db.quoteTable(table)} WHERE msgId=? AND msgSvrId=?", arrayOf("388308", "5063355820916335885"))
                    ?: error("Query failed: $table")
                cursor.use {
                    if (!it.moveToFirst()) null else {
                        val content = it.getString(it.getColumnIndexOrThrow("content"))
                        val hash = MessageDigest.getInstance("SHA-256").digest(content.toByteArray())
                            .joinToString("") { byte -> "%02x".format(byte) }
                        check(hash.startsWith("cb9e279d1e2978d4")) { "Content fingerprint mismatch" }
                        check(it.getInt(it.getColumnIndexOrThrow("type")) == 1) { "Type mismatch" }
                        check(it.getInt(it.getColumnIndexOrThrow("isSend")) == 0) { "Direction mismatch" }
                        val row = JSONObject()
                        for (i in 0 until it.columnCount) {
                            val cell = JSONObject().put("sqliteType", it.getType(i))
                            val value: Any = when (it.getType(i)) {
                                Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                                Cursor.FIELD_TYPE_INTEGER -> it.getLong(i).toString()
                                Cursor.FIELD_TYPE_FLOAT -> it.getDouble(i).toString()
                                Cursor.FIELD_TYPE_BLOB -> Base64.encodeToString(it.getBlob(i), Base64.NO_WRAP)
                                else -> it.getString(i)
                            }
                            row.put(it.getColumnName(i), cell.put("value", value))
                        }
                        check(!it.moveToNext()) { "Duplicate message" }
                        Triple(table, content, row)
                    }
                }
            }
            check(matches.size == 1) { "Expected one matching row, found ${matches.size}" }
            val (table, content, row) = matches.single()
            val backup = File(command.parentFile, "message-388308-backup.json")
            check(!backup.exists()) { "Backup already exists; refusing repeat removal" }
            val bytes = JSONObject().put("table", table).put("row", row).toString(2).toByteArray()
            FileOutputStream(backup).use { it.write(bytes); it.fd.sync() }
            check(backup.readBytes().contentEquals(bytes)) { "Backup verification failed" }
            val deleted = db.delete(table, "msgId=? AND msgSvrId=? AND type=? AND isSend=? AND content=?", arrayOf("388308", "5063355820916335885", "1", "0", content))
            result.put("deleted", deleted).put("backup", backup.absolutePath)
            check(deleted == 1) { "Delete returned $deleted: ${db.lastMutationError()}" }
            val verify = checkNotNull(db.rawQuery("SELECT msgId FROM ${db.quoteTable(table)} WHERE msgId=? AND msgSvrId=?", arrayOf("388308", "5063355820916335885")))
            verify.use { check(!it.moveToFirst()) { "Message still present" } }
            result.put("verifiedAbsent", true)
        } catch (error: Throwable) {
            result.put("error", error.toString())
        }
        resultFile.writeText(result.toString(2))
        HLog.i("[Hchat:SingleMessageRemoval] $result")
    }
}
