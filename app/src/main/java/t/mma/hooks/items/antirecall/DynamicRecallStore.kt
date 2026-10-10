package t.mma.hooks.items.antirecall

import android.content.Context
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.api.model.WeChatMessage
import t.mma.preferences.HchatStorage

/** Only successful anti-recall interceptions enter this account-scoped index. */
internal object DynamicRecallStore {
    const val KEY_RECORDS = "dynamic_recall_records_v1"
    private const val MAX_RECORDS = 8192

    private fun prefix(talker: String): String? {
        val account = WeChatApis.account()?.selfWxId().orEmpty()
        if (account.isBlank() || talker.isBlank()) return null
        return "$account|$talker|"
    }

    @Synchronized
    fun mark(context: Context, message: WeChatMessage?) {
        message ?: return
        if (message.isRecalled()) return
        val prefix = prefix(message.talker) ?: return
        val prefs = HchatStorage.preferences(context, AntiRecallSettings.PREFS_NAME)
        val records = LinkedHashSet(prefs.getStringSet(KEY_RECORDS, emptySet()).orEmpty())
        if (message.msgId > 0L) records.add("${prefix}l${message.msgId}")
        if (message.msgSvrId > 0L) records.add("${prefix}s${message.msgSvrId}")
        while (records.size > MAX_RECORDS) records.remove(records.first())
        prefs.edit().putStringSet(KEY_RECORDS, records).apply()
    }

    fun contains(context: Context, talker: String, msgId: Long, serverId: Long): Boolean {
        val prefix = prefix(talker) ?: return false
        val records = HchatStorage.preferences(context, AntiRecallSettings.PREFS_NAME)
            .getStringSet(KEY_RECORDS, emptySet()).orEmpty()
        return (msgId > 0L && "${prefix}l$msgId" in records) ||
            (serverId > 0L && "${prefix}s$serverId" in records)
    }
}