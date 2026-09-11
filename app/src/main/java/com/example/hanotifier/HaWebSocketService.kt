package com.example.hanotifier

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Mantém uma conexão permanente com a API WebSocket do Home Assistant, na rede local.
 * Escuta o evento customizado "mobile_notify" (você dispara esse evento nas suas automações
 * do HA, no lugar / além do telegram_bot.send_message) e grava cada notificação recebida
 * no banco local, além de mostrar uma notificação do Android.
 */
class HaWebSocketService : Service() {

    companion object {
        const val CHANNEL_ID = "ha_notifier_channel"
        const val FOREGROUND_ID = 1
        const val EVENT_TYPE = "mobile_notify"
        const val ACTION_STATUS = "com.example.hanotifier.STATUS"
        const val EXTRA_STATUS = "status"
    }

    private var client: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    private var messageIdCounter = 1
    private val handler = Handler(Looper.getMainLooper())
    private var reconnectScheduled = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(FOREGROUND_ID, buildForegroundNotification("Conectando ao Home Assistant..."))
        connect()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        webSocket?.close(1000, "service destroyed")
        client?.dispatcher?.executorService?.shutdown()
    }

    private fun connect() {
        if (!Prefs.isConfigured(this)) {
            broadcastStatus("Configure o host e o token nas Configurações")
            return
        }

        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // conexão longa, sem timeout de leitura
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(Prefs.wsUrl(this)).build()

        webSocket = client!!.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                broadcastStatus("conectado, autenticando...")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(webSocket, text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                broadcastStatus("desconectado")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                broadcastStatus("falha na conexão: ${t.message ?: "sem rede local"}")
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (reconnectScheduled) return
        reconnectScheduled = true
        handler.postDelayed({
            reconnectScheduled = false
            connect()
        }, 10_000)
    }

    private fun handleMessage(webSocket: WebSocket, text: String) {
        val json = JSONObject(text)
        when (json.optString("type")) {
            "auth_required" -> {
                val auth = JSONObject()
                auth.put("type", "auth")
                auth.put("access_token", Prefs.token(this))
                webSocket.send(auth.toString())
            }
            "auth_ok" -> {
                broadcastStatus("conectado ✔")
                subscribeToEvent(webSocket)
            }
            "auth_invalid" -> {
                broadcastStatus("token inválido — verifique nas Configurações")
            }
            "event" -> {
                val eventData = json.optJSONObject("event")?.optJSONObject("data") ?: return
                onNotifyEvent(eventData)
            }
        }
    }

    private fun subscribeToEvent(webSocket: WebSocket) {
        val msg = JSONObject()
        msg.put("id", messageIdCounter++)
        msg.put("type", "subscribe_events")
        msg.put("event_type", EVENT_TYPE)
        webSocket.send(msg.toString())
    }

    /**
     * Espera um payload assim (disparado pela automação do HA via `event: mobile_notify`):
     * {
     *   "title": "Câmera Rua",
     *   "message": "Movimento detectado",
     *   "image_url": "/api/camera_proxy/camera.rua?token=..."   (opcional, caminho relativo ou absoluto)
     * }
     */
    private fun onNotifyEvent(data: JSONObject) {
        val title = data.optString("title", "Home Assistant")
        val message = data.optString("message", "")
        var imageUrl = data.optString("image_url", null)
        if (!imageUrl.isNullOrBlank() && imageUrl.startsWith("/")) {
            imageUrl = Prefs.httpBase(this) + imageUrl
        }

        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.getInstance(applicationContext).notificationDao()
            dao.insert(
                NotificationEntity(
                    title = title,
                    message = message,
                    imageUrl = imageUrl,
                    timestamp = System.currentTimeMillis()
                )
            )
        }

        showAndroidNotification(title, message)
    }

    private fun showAndroidNotification(title: String, message: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun broadcastStatus(status: String) {
        val intent = Intent(ACTION_STATUS)
        intent.putExtra(EXTRA_STATUS, status)
        sendBroadcast(intent)
        updateForegroundNotification(status)
    }

    private fun updateForegroundNotification(status: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(FOREGROUND_ID, buildForegroundNotification(status))
    }

    private fun buildForegroundNotification(status: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("HA Notifier")
            .setContentText(status)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Notificações do Home Assistant",
            NotificationManager.IMPORTANCE_HIGH
        )
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }
}
