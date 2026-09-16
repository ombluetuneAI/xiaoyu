package com.xiaoyu.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/** 短提示走 Toast，不使用系统 TTS */
object UserNotice {
    fun toast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}
