package com.example.hanotifier

import android.content.Context
import java.util.concurrent.TimeUnit

object HistoryCleaner {

    /**
     * Apaga do banco local as notificações mais antigas que o período configurado
     * pelo usuário nas Configurações. Se a retenção estiver como "Nunca" (0), não faz nada.
     */
    suspend fun cleanupIfNeeded(context: Context) {
        val days = Prefs.retentionDays(context)
        if (days <= 0) return // "Nunca apagar"

        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        AppDatabase.getInstance(context).notificationDao().deleteOlderThan(cutoff)
    }
}
