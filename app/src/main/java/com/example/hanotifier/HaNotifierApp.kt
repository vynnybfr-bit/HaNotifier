package com.example.hanotifier

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class HaNotifierApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Aplica o tema salvo pelo usuário (claro ou escuro) — não segue o modo do sistema
        AppCompatDelegate.setDefaultNightMode(Prefs.nightMode(this))
    }
}
