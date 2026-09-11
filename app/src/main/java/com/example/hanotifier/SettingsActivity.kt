package com.example.hanotifier

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val etHost = findViewById<EditText>(R.id.etHost)
        val etPort = findViewById<EditText>(R.id.etPort)
        val etToken = findViewById<EditText>(R.id.etToken)
        val cbUseSsl = findViewById<CheckBox>(R.id.cbUseSsl)
        val btnSave = findViewById<Button>(R.id.btnSave)

        etHost.setText(Prefs.host(this))
        etPort.setText(Prefs.port(this))
        etToken.setText(Prefs.token(this))
        cbUseSsl.isChecked = Prefs.useSsl(this)

        btnSave.setOnClickListener {
            val host = etHost.text.toString().trim()
            val port = etPort.text.toString().trim().ifBlank { "8123" }
            val token = etToken.text.toString().trim()

            if (host.isBlank() || token.isBlank()) {
                Toast.makeText(this, "Preencha pelo menos o host e o token", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Prefs.save(this, host, port, token, cbUseSsl.isChecked)

            // reinicia o serviço com a nova configuração
            val serviceIntent = Intent(this, HaWebSocketService::class.java)
            stopService(serviceIntent)
            ContextCompat.startForegroundService(this, serviceIntent)

            Toast.makeText(this, "Salvo! Conectando...", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
