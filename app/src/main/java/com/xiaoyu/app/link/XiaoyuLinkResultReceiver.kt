package com.xiaoyu.app.link

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.util.Log
import com.xiaoyu.core.link.LinkResultStore
import com.xiaoyu.core.link.XiaoyuLinkActions
import com.xiaoyu.service.XiaoyuAppGraph
import org.json.JSONObject

class XiaoyuLinkResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != XiaoyuLinkActions.COMMAND_RESULT) return
        val graph = XiaoyuAppGraph.get(context)
        val senderUid = Binder.getCallingUid()
        if (!graph.registry.isTrustedCommandResultSender(context, senderUid)) {
            Log.w(TAG, "Rejected COMMAND_RESULT from untrusted uid=$senderUid")
            return
        }
        val requestId = intent.getStringExtra(XiaoyuLinkActions.EXTRA_REQUEST_ID)
            ?: intent.getStringExtra("requestId")
            ?: return
        val success = intent.getBooleanExtra(XiaoyuLinkActions.EXTRA_SUCCESS, false)
        val message = intent.getStringExtra(XiaoyuLinkActions.EXTRA_MESSAGE) ?: ""
        val code = intent.getStringExtra(XiaoyuLinkActions.EXTRA_CODE)
            ?: if (success) "OK" else "ERROR"
        LinkResultStore.complete(
            requestId,
            JSONObject()
                .put("ok", success)
                .put("code", code)
                .put("message", message)
                .put("requestId", requestId),
        )
    }

    companion object {
        private const val TAG = "XiaoyuLinkResult"
    }
}
