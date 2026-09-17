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
import android.util.Log
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

class HaWebSocketService : Service() {

    companion object {
        const val CHANNEL_ID = "ha_notifier_channel"
        const val FOREGROUND_CHANNEL_ID = "ha_notifier_service_channel"
        const val FOREGROUND_ID = 1
        const val EVENT_TYPE = "mobile_notify"
        const val ACTION_STATUS = "com.example.hanotifier.STATUS"
        const val EXTRA_STATUS = "status"
        private const val TAG = "HaNotifierAuth"
    }

    private var client: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    private var messageIdCounter = 1
    private val handler = Handler(Looper.getMainLooper())
    private var reconnectScheduled = false
    private var lastNotifiedConnectionState: Boolean? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(FOREGROUND_ID, buildForegroundNotification())
        connect()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

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

        val token = Prefs.token(this)
        Log.d(TAG, "connect(): token configurado=${token.isNotBlank()}, tamanho=${token.length}, resumo=${tokenSummary(token)}")
        Log.d(TAG, "connect(): wsUrl=${Prefs.wsUrl(this)}")

        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(Prefs.wsUrl(this)).build()

        webSocket = client!!.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket aberto. Aguardando auth_required...")
                broadcastStatus("conectado, autenticando...")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(webSocket, text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                broadcastStatus("desconectado")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
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
                val token = Prefs.token(this)
                Log.d(TAG, "Recebido auth_required. Enviando auth: token configurado=${token.isNotBlank()}, tamanho=${token.length}, resumo=${tokenSummary(token)}")
                val auth = JSONObject()
                auth.put("type", "auth")
                auth.put("access_token", token)
                val sent = webSocket.send(auth.toString())
                Log.d(TAG, "Mensagem auth enviada pelo WebSocket: $sent")
            }
            "auth_ok" -> {
                Log.d(TAG, "AUTH OK recebido do Home Assistant")
                broadcastStatus("conectado ✔")
                subscribeToEvent(webSocket)
            }
            "auth_invalid" -> {
                Log.e(TAG, "AUTH INVALID recebido do Home Assistant")
                broadcastStatus("token inválido — verifique nas Configurações")
            }
            "event" -> {
                val eventData = json.optJSONObject("event")?.optJSONObject("data") ?: return
                onNotifyEvent(eventData)
            }
        }
    }

    private fun tokenSummary(token: String): String {
        if (token.length < 8) return "curto-demais"
        return "${token.take(4)}...${token.takeLast(4)}"
    }

    private fun subscribeToEvent(webSocket: WebSocket) {
        val msg = JSONObject()
        msg.put("id", messageIdCounter++)
        msg.put("type", "subscribe_events")
        msg.put("event_type", EVENT_TYPE)
        webSocket.send(msg.toString())
    }

    private fun onNotifyEvent(data: JSONObject) {
        val title = data.optString("title", "Home Assistant")
        val message = data.optString("message", "")
        var cameraUrl = data.optString("camera_url", null)
        var cameraUrl2 = data.optString("camera_url_2", null)
        val cameraName = data.optString("camera_name", "Ver câmera")
        val cameraName2 = data.optString("camera_name_2", "Ver câmera 2")

        if (!cameraUrl2.isNullOrBlank() && cameraUrl2.startsWith("/")) {
            cameraUrl2 = Prefs.httpBase(this) + cameraUrl2
        }
        if (!cameraUrl.isNullOrBlank() && cameraUrl.startsWith("/")) {
            cameraUrl = Prefs.httpBase(this) + cameraUrl
        }

        var imageUrl = data.optString("image_url", null)
        if (!imageUrl.isNullOrBlank() && imageUrl.startsWith("/")) {
            imageUrl = Prefs.httpBase(this) + imageUrl
        }

        val httpClient = client

        CoroutineScope(Dispatchers.IO).launch {
            // O image_proxy usa URL/token temporário. Baixamos a imagem imediatamente,
            // enquanto o token recebido neste evento ainda está válido.
            val storedImageUrl = if (!imageUrl.isNullOrBlank() && httpClient != null) {
                val localPath = NotificationImageStore.download(
                    applicationContext,
                    httpClient,
                    imageUrl!!
                )

                if (localPath != null) {
                    Log.d(TAG, "Imagem armazenada localmente: $localPath")
                    localPath
                } else {
                    Log.e(TAG, "Falha no download imediato; usando image_url como fallback")
                    imageUrl
                }
            } else {
                imageUrl
            }

            val dao = AppDatabase.getInstance(applicationContext).notificationDao()
            dao.insert(
                NotificationEntity(
                    title = title,
                    message = message,
                    imageUrl = storedImageUrl,
                    cameraUrl = cameraUrl,
                    cameraUrl2 = cameraUrl2,
                    cameraName = cameraName,
                    cameraName2 = cameraName2,
                    timestamp = System.currentTimeMillis()
                )
            )
            HistoryCleaner.cleanupIfNeeded(applicationContext)
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

    private fun showDisconnectNotification(status: String) {
        showAndroidNotification("Conexão perdida", status)
    }

    private fun broadcastStatus(status: String) {
        Prefs.saveStatus(this, status)

        val intent = Intent(ACTION_STATUS)
        intent.putExtra(EXTRA_STATUS, status)
        sendBroadcast(intent)

        val connectionState = when {
            status == "conectado ✔" -> true
            status == "desconectado" || status.startsWith("falha na conexão") -> false
            else -> null
        }

        if (connectionState == null || connectionState != lastNotifiedConnectionState) {
            if (connectionState != null) {
                lastNotifiedConnectionState = connectionState
                if (!connectionState) {
                    showDisconnectNotification(status)
                }
            }
            updateForegroundNotification()
        }
    }

    private fun updateForegroundNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(FOREGROUND_ID, buildForegroundNotification())
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("HA Notifier")
            .setContentText("Serviço ativo")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        val eventChannel = NotificationChannel(
            CHANNEL_ID,
            "Notificações do Home Assistant",
            NotificationManager.IMPORTANCE_HIGH
        )
        nm.createNotificationChannel(eventChannel)

        val foregroundChannel = NotificationChannel(
            FOREGROUND_CHANNEL_ID,
            "Serviço em segundo plano",
            NotificationManager.IMPORTANCE_MIN
        )
        foregroundChannel.setShowBadge(false)
        nm.createNotificationChannel(foregroundChannel)
    }
}
