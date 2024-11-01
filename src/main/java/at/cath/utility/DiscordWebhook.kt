package at.cath.utility

import at.cath.events.WynnEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.logging.log4j.LogManager
import java.io.IOException

class DiscordWebhook(val url: String) {
    companion object {
        private val JSON = "application/json".toMediaType()
        private val client = OkHttpClient()
        private val coroutineScope = CoroutineScope(Dispatchers.IO)
        private val logger = LogManager.getLogger()
    }

    fun send(payload: WynnEvent) {
        val body = Json.encodeToString(payload).toRequestBody(JSON)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        coroutineScope.launch {
            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Webhook request failed with code ${response.code}")
                    }
                }
            }.onFailure {
                logger.error("Failed to send webhook payload to $url: $it.message")
            }.onSuccess {
                logger.info("Successfully sent webhook payload to $url")
            }
        }
    }
}