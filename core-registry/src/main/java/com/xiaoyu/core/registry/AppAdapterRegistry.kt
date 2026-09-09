package com.xiaoyu.core.registry

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

data class AppAdapter(
    val appId: String,
    val displayName: String,
    val packageName: String,
    val capabilities: Set<String>,
    val deepLinkScheme: String,
    var userEnabled: Boolean = true,
    val isBuiltin: Boolean = false,
    val signingCertSha256: String? = null,
)

class AppAdapterRegistry {
    private val adapters = mutableMapOf<String, AppAdapter>()
    private val _adaptersFlow = MutableStateFlow<List<AppAdapter>>(emptyList())
    val adaptersFlow: StateFlow<List<AppAdapter>> = _adaptersFlow.asStateFlow()

    init {
        registerBuiltin()
    }

    private fun registerBuiltin() {
        register(
            AppAdapter(
                appId = "xiaoyu",
                displayName = "小鱼内置音乐",
                packageName = "com.xiaoyu.app",
                capabilities = setOf(
                    "music.play_general",
                    "music.play_collection",
                    "music.play_track",
                    "radio.play",
                ),
                deepLinkScheme = "xiaoyu",
                userEnabled = true,
                isBuiltin = true,
            ),
        )
        register(
            AppAdapter(
                appId = "musicfree",
                displayName = "MusicFree",
                packageName = "fun.upup.musicfree",
                capabilities = setOf(
                    "music.play",
                    "music.pause",
                    "music.resume",
                    "music.next",
                    "music.previous",
                    "music.now_playing",
                ),
                deepLinkScheme = "xiaoyu-musicfree",
                userEnabled = true,
                signingCertSha256 = null,
            ),
        )
    }

    fun register(adapter: AppAdapter) {
        adapters[adapter.appId] = adapter
        publish()
    }

    fun scanInstalledApps(context: Context) {
        val pm = context.packageManager
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in installed) {
            val meta = app.metaData ?: continue
            val raw = meta.getString("xiaoyu_adapter_manifest") ?: continue
            parseManifestJson(raw)?.let { register(it) }
        }
        publish()
    }

    private fun parseManifestJson(raw: String): AppAdapter? {
        return try {
            val json = org.json.JSONObject(raw)
            val caps = mutableSetOf<String>()
            val capArray = json.optJSONArray("capabilities") ?: JSONArray()
            for (i in 0 until capArray.length()) {
                val item = capArray.optJSONObject(i)
                val id = item?.optString("id") ?: capArray.optString(i)
                if (id.isNotBlank()) caps.add(id)
            }
            AppAdapter(
                appId = json.getString("appId"),
                displayName = json.optString("displayName", json.getString("appId")),
                packageName = json.getString("packageName"),
                capabilities = caps,
                deepLinkScheme = json.optString("deepLinkScheme", json.getString("appId")),
                userEnabled = true,
                signingCertSha256 = json.optString("signingCertSha256").ifBlank { null },
            )
        } catch (_: Exception) {
            null
        }
    }

    fun getAdapter(appId: String): AppAdapter? = adapters[appId]

    fun findAdapterByPackageName(packageName: String): AppAdapter? =
        adapters.values.firstOrNull { it.packageName == packageName }

    /** COMMAND_RESULT 发送方：须在登记白名单内，且证书指纹一致（若已登记）。 */
    fun isTrustedCommandResultSender(context: Context, senderUid: Int): Boolean {
        val senderPackages = PackageSigning.packagesForUid(context, senderUid)
        if (senderPackages.isEmpty()) return false
        val adapter = senderPackages.firstNotNullOfOrNull { findAdapterByPackageName(it) }
            ?: return false
        if (adapter.isBuiltin) return false
        if (!adapter.userEnabled) return false
        val actualCert = PackageSigning.sha256Fingerprint(context, adapter.packageName)
        return PackageSigning.matches(adapter.signingCertSha256, actualCert)
    }

    /** APP_COMMAND 投递目标：已安装且证书指纹一致（若已登记）。 */
    fun isTrustedDispatchTarget(context: Context, targetPackage: String): Boolean {
        val adapter = findAdapterByPackageName(targetPackage) ?: return false
        if (adapter.isBuiltin) return false
        if (!adapter.userEnabled) return false
        val actualCert = PackageSigning.sha256Fingerprint(context, targetPackage)
        return PackageSigning.matches(adapter.signingCertSha256, actualCert)
    }

    fun setUserEnabled(appId: String, enabled: Boolean) {
        adapters[appId]?.userEnabled = enabled
        publish()
    }

    fun listAdapters(): List<AppAdapter> = adapters.values.toList()

    fun deepLinkScheme(appId: String): String? = adapters[appId]?.deepLinkScheme

    private fun publish() {
        _adaptersFlow.value = adapters.values.toList()
    }
}
