package at.cath.utility

import at.cath.events.WynnEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import org.apache.logging.log4j.LogManager
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class DiscordWebhook(private val url: String) {
    companion object {
        // remove polymorphic serialization type because it messes up w ignoreUnknownKeys = false
        @OptIn(ExperimentalSerializationApi::class)
        private val json = Json { classDiscriminatorMode = ClassDiscriminatorMode.NONE }

        // HttpClient is thread-safe and should be reused
        private val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()

        private val coroutineScope = CoroutineScope(Dispatchers.IO)
        private val logger = LogManager.getLogger()
    }

    fun send(payload: WynnEvent) {
        val uri = runCatching { URI.create(url) }.getOrNull()
        if (uri == null || (uri.scheme != "http" && uri.scheme != "https")) {
            logger.error("Invalid URL provided, skipping request: $url")
            return
        }

        val bodyString = json.encodeToString(payload)
        val request = HttpRequest.newBuilder()
            .uri(uri)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(bodyString))
            .build()

        coroutineScope.launch {
            runCatching {
                val response = client.send(request, HttpResponse.BodyHandlers.discarding())

                if (response.statusCode() !in 200..299) {
                    throw IOException("Webhook request failed with code ${response.statusCode()}")
                }
            }.onFailure {
                logger.error("Failed to send webhook payload to $uri: ${it.message}")
            }.onSuccess {
                logger.info("Successfully sent webhook payload to $uri")
            }
        }
    }
}