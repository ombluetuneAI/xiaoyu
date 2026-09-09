package com.xiaoyu.core.link

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * XiaoyuLink 定向广播 + Deep Link 冷启动，5s 等待 COMMAND_RESULT。
 * 超时或冷启动时写入 pendingQueue，进程恢复后重发。
 */
class BroadcastLinkDispatcher(
    private val context: Context,
    private val deepLinkResolver: (String) -> String? = { null },
    private val targetPackageVerifier: (String) -> Boolean = { true },
    private val pendingQueue: PendingLinkQueue = PendingLinkQueue(context),
    private val resendScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    fun flushPendingQueue() {
        val pending = pendingQueue.allPending()
        if (pending.isEmpty()) return
        Log.i(TAG, "flush pendingQueue size=${pending.size}")
        pending.forEach { entry ->
            resendScope.launch {
                resendWithRetries(entry.targetPackage, entry.message, removeOnSuccess = true)
            }
        }
    }

    suspend fun dispatch(targetPackage: String, message: XiaoyuLinkMessage): JSONObject {
        if (!targetPackageVerifier(targetPackage)) {
            return JSONObject()
                .put("ok", false)
                .put("code", "UNTRUSTED_TARGET")
                .put("message", "目标应用未通过签名校验")
                .put("requestId", message.requestId)
        }
        val deferred = LinkResultStore.register(message.requestId)
        sendCommandBroadcast(targetPackage, message)

        val scheme = deepLinkResolver(message.appId)
        if (scheme != null) {
            pendingQueue.store(targetPackage, message)
            launchDeepLink(scheme, targetPackage, message)
            scheduleResend(targetPackage, message)
        }

        return try {
            val result = withTimeout(XiaoyuLinkActions.DISPATCH_TIMEOUT_MS) { deferred.await() }
            pendingQueue.remove(message.requestId)
            result
        } catch (_: Exception) {
            LinkResultStore.cancel(message.requestId)
            pendingQueue.store(targetPackage, message)
            scheduleResend(targetPackage, message)
            JSONObject()
                .put("ok", false)
                .put("code", "TIMEOUT")
                .put("message", "应用未在 5 秒内响应，已加入待重发队列")
                .put("requestId", message.requestId)
        }
    }

    private fun sendCommandBroadcast(targetPackage: String, message: XiaoyuLinkMessage) {
        val payload = message.toJson().toString()
        val commandIntent = Intent(XiaoyuLinkActions.APP_COMMAND).apply {
            setPackage(targetPackage)
            putExtra(XiaoyuLinkActions.EXTRA_PAYLOAD, payload)
            putExtra(XiaoyuLinkActions.EXTRA_SENDER_PACKAGE, context.packageName)
            putExtra(XiaoyuLinkActions.EXTRA_REQUEST_ID, message.requestId)
        }
        context.sendBroadcast(commandIntent)
    }

    private fun launchDeepLink(scheme: String, targetPackage: String, message: XiaoyuLinkMessage) {
        val deepLink = Uri.parse("$scheme://invoke?requestId=${message.requestId}")
        val launch = Intent(Intent.ACTION_VIEW, deepLink).apply {
            setPackage(targetPackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(XiaoyuLinkActions.EXTRA_PAYLOAD, message.toJson().toString())
        }
        try {
            context.startActivity(launch)
        } catch (_: Exception) {
            // 冷启动失败时仍等待广播回执或 pending 重发
        }
    }

    private fun scheduleResend(targetPackage: String, message: XiaoyuLinkMessage) {
        resendScope.launch {
            delay(RESEND_DELAY_MS)
            resendWithRetries(targetPackage, message, removeOnSuccess = false)
        }
    }

    private suspend fun resendWithRetries(
        targetPackage: String,
        message: XiaoyuLinkMessage,
        removeOnSuccess: Boolean,
    ) {
        repeat(MAX_RESEND) { attempt ->
            val deferred = LinkResultStore.register(message.requestId)
            sendCommandBroadcast(targetPackage, message)
            try {
                val result = withTimeout(XiaoyuLinkActions.DISPATCH_TIMEOUT_MS) { deferred.await() }
                if (removeOnSuccess || result.optBoolean("ok")) {
                    pendingQueue.remove(message.requestId)
                }
                return
            } catch (_: Exception) {
                LinkResultStore.cancel(message.requestId)
                if (attempt < MAX_RESEND - 1) delay(RESEND_DELAY_MS * (attempt + 1))
            }
        }
        Log.w(TAG, "pending resend exhausted requestId=${message.requestId}")
    }

    companion object {
        private const val TAG = "BroadcastLinkDispatcher"
        private const val RESEND_DELAY_MS = 2000L
        private const val MAX_RESEND = 3
    }
}
