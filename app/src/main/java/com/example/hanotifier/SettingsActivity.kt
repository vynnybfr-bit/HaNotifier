package com.example.hanotifier

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val etHost = findViewById<EditText>(R.id.etHost)
        val etPort = findViewById<EditText>(R.id.etPort)
        val etToken = findViewById<EditText>(R.id.etToken)
        val cbUseSsl = findViewById<CheckBox>(R.id.cbUseSsl)
        val rgTheme = findViewById<RadioGroup>(R.id.rgTheme)
        val rbThemeLight = findViewById<android.widget.RadioButton>(R.id.rbThemeLight)
        val rbThemeDark = findViewById<android.widget.RadioButton>(R.id.rbThemeDark)
        val spRetention = findViewById<Spinner>(R.id.spRetention)
        val btnSave = findViewById<Button>(R.id.btnSave)

        etHost.setText(Prefs.host(this))
        etPort.setText(Prefs.port(this))
        etToken.setText(Prefs.token(this))
        cbUseSsl.isChecked = Prefs.useSsl(this)

        if (Prefs.isDarkTheme(this)) rbThemeDark.isChecked = true else rbThemeLight.isChecked = true

        val retentionValues = resources.getStringArray(R.array.retention_values).map { it.toInt() }
        val currentRetention = Prefs.retentionDays(this)
        val retentionIndex = retentionValues.indexOf(currentRetention).let { if (it == -1) 3 else it } // padrão: 7 dias
        spRetention.setSelection(retentionIndex)

        btnSave.setOnClickListener {
            val host = etHost.text.toString().trim()
            val port = etPort.text.toString().trim().ifBlank { "8123" }
            val token = etToken.text.toString().trim()

            if (host.isBlank() || token.isBlank()) {
                Toast.makeText(this, "Preencha pelo menos o host e o token", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Prefs.save(this, host, port, token, cbUseSsl.isChecked)
            Prefs.saveRetentionDays(this, retentionValues[spRetention.selectedItemPosition])

            val wantsDark = rgTheme.checkedRadioButtonId == R.id.rbThemeDark
            val themeChanged = wantsDark != Prefs.isDarkTheme(this)
            Prefs.saveDarkTheme(this, wantsDark)

            // reinicia o serviço com a nova configuração
            val serviceIntent = Intent(this, HaWebSocketService::class.java)
            stopService(serviceIntent)
            ContextCompat.startForegroundService(this, serviceIntent)

            Toast.makeText(this, "Salvo! Conectando...", Toast.LENGTH_SHORT).show()

            if (themeChanged) {
                // Aplica o novo tema — isso recria as telas automaticamente
                AppCompatDelegate.setDefaultNightMode(Prefs.nightMode(this))
            }

            finish()
        }
    }
}
