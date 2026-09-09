package com.xiaoyu.core.link

import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object LinkResultStore {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

    fun register(requestId: String): CompletableDeferred<JSONObject> {
        val deferred = CompletableDeferred<JSONObject>()
        pending[requestId] = deferred
        return deferred
    }

    fun complete(requestId: String, result: JSONObject): Boolean {
        return pending.remove(requestId)?.complete(result) != null
    }

    fun cancel(requestId: String) {
        pending.remove(requestId)?.cancel()
    }

    fun pendingCount(): Int = pending.size
}
