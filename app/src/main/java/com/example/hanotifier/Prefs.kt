package com.example.hanotifier

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object Prefs {
    private const val FILE = "ha_notifier_prefs"

    // ---- Tema (escolhido manualmente pelo usuário, não segue o sistema) ----

    fun saveDarkTheme(context: Context, dark: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("dark_theme", dark)
            .apply()
    }

    fun isDarkTheme(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("dark_theme", false)

    fun nightMode(context: Context): Int =
        if (isDarkTheme(context)) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO

    // ---- Último status de conexão conhecido (pra mostrar na tela ao reabrir o app) ----

    fun saveStatus(context: Context, status: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("last_status", status)
            .apply()
    }

    fun lastStatus(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("last_status", "") ?: ""

    // ---- Retenção do histórico: apaga notificações mais antigas que N dias ----
    // 0 = nunca apagar

    fun saveRetentionDays(context: Context, days: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt("retention_days", days)
            .apply()
    }

    fun retentionDays(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt("retention_days", 7)

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
