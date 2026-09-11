package com.example.hanotifier

import android.content.Context

object Prefs {
    private const val FILE = "ha_notifier_prefs"

    fun save(context: Context, host: String, port: String, token: String, useSsl: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("host", host)
            .putString("port", port)
            .putString("token", token)
            .putBoolean("ssl", useSsl)
            .apply()
    }

    fun host(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("host", "") ?: ""

    fun port(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("port", "8123") ?: "8123"

    fun token(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("token", "") ?: ""

    fun useSsl(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("ssl", false)

    fun isConfigured(context: Context): Boolean =
        host(context).isNotBlank() && token(context).isNotBlank()

    /** Monta a URL base http(s)://host:port */
    fun httpBase(context: Context): String {
        val scheme = if (useSsl(context)) "https" else "http"
        return "$scheme://${host(context)}:${port(context)}"
    }

    /** Monta a URL do websocket ws(s)://host:port/api/websocket */
    fun wsUrl(context: Context): String {
        val scheme = if (useSsl(context)) "wss" else "ws"
        return "$scheme://${host(context)}:${port(context)}/api/websocket"
    }
}
