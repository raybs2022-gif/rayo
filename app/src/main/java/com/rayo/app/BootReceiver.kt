package com.rayo.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val activo = context.getSharedPreferences("rayo", Context.MODE_PRIVATE)
            .getBoolean("autostart", true)
        if (activo && Settings.canDrawOverlays(context)) {
            try {
                context.startForegroundService(Intent(context, OverlayService::class.java))
            } catch (e: Exception) {
            }
        }
    }
}
