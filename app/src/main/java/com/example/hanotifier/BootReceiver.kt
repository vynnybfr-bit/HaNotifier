package com.example.hanotifier

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs.isConfigured(context)) {
            val serviceIntent = Intent(context, HaWebSocketService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
