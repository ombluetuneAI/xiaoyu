package com.xiaoyu.app.ui.settings

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.xiaoyu.app.R
import com.xiaoyu.service.XiaoyuAppGraph
import kotlinx.coroutines.launch

class AdapterAppsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_adapter_apps)
        val container = findViewById<LinearLayout>(R.id.adapterContainer)
        val graph = XiaoyuAppGraph.get(this)
        lifecycleScope.launch {
            graph.registry.adaptersFlow.collect { adapters ->
                container.removeAllViews()
                adapters.forEach { adapter ->
                    val row = layoutInflater.inflate(R.layout.item_adapter, container, false)
                    row.findViewById<TextView>(R.id.adapterName).text = adapter.displayName
                    val sw = row.findViewById<Switch>(R.id.adapterSwitch)
                    sw.isChecked = adapter.userEnabled
                    sw.isEnabled = !adapter.isBuiltin
                    sw.setOnCheckedChangeListener { _, checked ->
                        graph.registry.setUserEnabled(adapter.appId, checked)
                    }
                    container.addView(row)
                }
            }
        }
    }
}
