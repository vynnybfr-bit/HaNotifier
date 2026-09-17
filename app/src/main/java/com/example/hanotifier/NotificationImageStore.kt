package com.example.hanotifier

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Baixa a imagem da notificação no momento em que ela chega e a mantém no armazenamento
 * interno do app. Assim, o histórico não depende da URL temporária do Home Assistant.
 */
object NotificationImageStore {

    private const val DIRECTORY_NAME = "notification_images"
    private const val MAX_ATTEMPTS = 3

    suspend fun download(context: Context, client: OkHttpClient, imageUrl: String): String? {
        val directory = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }
        val finalFile = File(directory, "${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
        val tempFile = File(directory, "${finalFile.name}.tmp")

        var lastError: IOException? = null

        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val request = Request.Builder()
                    .url(imageUrl)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}")
                    }

                    val body = response.body ?: throw IOException("Resposta sem conteúdo")
                    body.byteStream().use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                if (tempFile.length() <= 0L) {
                    throw IOException("Arquivo recebido vazio")
                }

                if (!tempFile.renameTo(finalFile)) {
                    throw IOException("Não foi possível salvar a imagem")
                }

                return finalFile.absolutePath
            } catch (e: IOException) {
                lastError = e
                tempFile.delete()
                if (attempt < MAX_ATTEMPTS - 1) {
                    Thread.sleep(250L)
                }
            }
        }

        finalFile.delete()
        return null
    }
}
