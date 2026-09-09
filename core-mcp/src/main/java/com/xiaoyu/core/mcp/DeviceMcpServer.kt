package com.xiaoyu.core.mcp

import android.util.Log
import com.xiaoyu.core.router.MediaCommandHandler
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class DeviceMcpServer(
    private val mediaCommandHandler: MediaCommandHandler,
) {
    fun handleMcpPayload(payload: JSONObject): JSONObject {
        return when (payload.optString("method")) {
            "tools/list" -> buildToolsListResult()
            "tools/call" -> handleToolCall(payload)
            else -> JSONObject().put("error", JSONObject().put("message", "unsupported"))
        }
    }

    private fun handleToolCall(payload: JSONObject): JSONObject = runBlocking {
        val params = payload.optJSONObject("params") ?: JSONObject()
        val name = params.optString("name")
        val args = params.optJSONObject("arguments") ?: JSONObject()
        Log.i(TAG, "tools/call name=$name args=$args")
        mediaCommandHandler.handleTool(name, args)
    }

    private fun buildToolsListResult(): JSONObject =
        JSONObject().put("tools", McpToolCatalog.buildToolsJsonArray())

    companion object {
        private const val TAG = "DeviceMcpServer"
    }
}
