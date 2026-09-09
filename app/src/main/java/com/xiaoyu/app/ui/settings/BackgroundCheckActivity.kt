package com.xiaoyu.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.xiaoyu.app.R
import com.xiaoyu.service.XiaoyuAssistantService

class BackgroundCheckActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_background_check)
        refreshChecks()
        findViewById<Button>(R.id.btnJumpSettings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
                startActivity(intent)
            }
        }
        findViewById<Button>(R.id.btnRomAutostart)?.setOnClickListener {
            if (!openRomAutostart()) {
                Toast.makeText(this, "请手动在系统设置中允许自启动", Toast.LENGTH_LONG).show()
            }
        }
        findViewById<Button>(R.id.btnDone).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        refreshChecks()
    }

    private fun refreshChecks() {
        val batteryOk = isBatteryUnrestricted()
        val micFgsOk = XiaoyuAssistantService::class.java.name.isNotBlank()
        findViewById<TextView>(R.id.checkBattery).text =
            if (batteryOk) "电池无限制 · 已通过" else "电池无限制 · 未通过（点击下方按钮申请）"
        findViewById<TextView>(R.id.checkAutostart).text =
            "自启动 · 请在系统设置中手动允许（各 ROM 入口不同）"
        findViewById<TextView>(R.id.checkMicFgs).text =
            if (micFgsOk) "麦克风前台服务 · 已通过" else "麦克风前台服务 · 未通过"
        findViewById<TextView>(R.id.checkNotification)?.text =
            "S-10 · 请勿划掉通知栏「小鱼同学」常驻通知，否则语音服务可能被系统回收"
    }

    private fun isBatteryUnrestricted(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            return pm.isIgnoringBatteryOptimizations(packageName)
        }
        return true
    }

    private fun openRomAutostart(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val intents = mutableListOf<Intent>()
        when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                intents += Intent().setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                )
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
                intents += Intent().setClassName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                )
                intents += Intent().setClassName(
                    "com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity",
                )
            }
            manufacturer.contains("vivo") -> {
                intents += Intent().setClassName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
                )
                intents += Intent().setClassName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                )
            }
        }
        intents += Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        for (intent in intents) {
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {
                continue
            }
        }
        return false
    }
}
