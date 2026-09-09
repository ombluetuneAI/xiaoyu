package com.xiaoyu.core.registry

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

object PackageSigning {
    fun sha256Fingerprint(context: Context, packageName: String): String? {
        return try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            } ?: return null
            if (signatures.isEmpty()) return null
            val digest = MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray())
            digest.joinToString("") { byte -> "%02X".format(byte) }
        } catch (_: Exception) {
            null
        }
    }

    fun normalizeSha256(raw: String): String =
        raw.uppercase().replace(":", "").replace(" ", "")

    fun matches(expected: String?, actual: String?): Boolean {
        if (expected.isNullOrBlank()) return true
        if (actual.isNullOrBlank()) return false
        return normalizeSha256(expected) == normalizeSha256(actual)
    }

    fun packagesForUid(context: Context, uid: Int): List<String> {
        return context.packageManager.getPackagesForUid(uid)?.toList() ?: emptyList()
    }
}
