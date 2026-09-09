package com.xiaoyu.core.link

import android.content.Context
import org.json.JSONObject

/**
 * 进程内 pendingQueue：冷启动 Deep Link 后暂存完整 payload，进程恢复后重发定向广播。
 */
class PendingLinkQueue(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun store(targetPackage: String, message: XiaoyuLinkMessage) {
        val id = message.requestId
        val ids = pendingIds().toMutableSet()
        ids.add(id)
        prefs.edit()
            .putString(keyPkg(id), targetPackage)
            .putString(keyPayload(id), message.toJson().toString())
            .putLong(keyTs(id), System.currentTimeMillis())
            .putStringSet(KEY_IDS, ids)
            .apply()
    }

    fun remove(requestId: String) {
        val ids = pendingIds().toMutableSet()
        ids.remove(requestId)
        prefs.edit()
            .remove(keyPkg(requestId))
            .remove(keyPayload(requestId))
            .remove(keyTs(requestId))
            .putStringSet(KEY_IDS, ids)
            .apply()
    }

    fun allPending(): List<PendingEntry> {
        return pendingIds().mapNotNull { id ->
            val pkg = prefs.getString(keyPkg(id), null) ?: return@mapNotNull null
            val raw = prefs.getString(keyPayload(id), null) ?: return@mapNotNull null
            PendingEntry(
                requestId = id,
                targetPackage = pkg,
                message = XiaoyuLinkMessage.fromJson(JSONObject(raw)),
                storedAtMs = prefs.getLong(keyTs(id), 0L),
            )
        }.sortedBy { it.storedAtMs }
    }

    private fun pendingIds(): Set<String> = prefs.getStringSet(KEY_IDS, emptySet()) ?: emptySet()

    private fun keyPkg(id: String) = "pkg_$id"
    private fun keyPayload(id: String) = "payload_$id"
    private fun keyTs(id: String) = "ts_$id"

    data class PendingEntry(
        val requestId: String,
        val targetPackage: String,
        val message: XiaoyuLinkMessage,
        val storedAtMs: Long,
    )

    companion object {
        private const val PREFS_NAME = "xiaoyu_pending_link"
        private const val KEY_IDS = "pending_ids"
    }
}
