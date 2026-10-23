package com.xiaoyu.app.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.xiaoyu.app.R
import com.xiaoyu.service.XiaoyuAppGraph

class TxbSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_txb_settings)
        val graph = XiaoyuAppGraph.get(this)
        val input = findViewById<EditText>(R.id.txbInput)
        val errorView = findViewById<TextView>(R.id.txbError)
        input.setText(graph.preferences.txbApiBase)
        findViewById<TextView>(R.id.txbHint).text =
            "手机须与 TXB 同一局域网。Android 9+ HTTP 明文需在 networkSecurityConfig 开启 cleartext。"
        findViewById<Button>(R.id.txbSave).setOnClickListener {
            val value = input.text.toString().trim()
            if (!isValidTxbUrl(value)) {
                errorView.visibility = View.VISIBLE
                errorView.text = getString(R.string.txb_save_error)
                return@setOnClickListener
            }
            errorView.visibility = View.GONE
            graph.preferences.txbApiBase = value
            finish()
        }
    }

    private fun isValidTxbUrl(url: String): Boolean {
        return url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)
    }
}
