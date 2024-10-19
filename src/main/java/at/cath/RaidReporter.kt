package at.cath

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.shedaniel.clothconfig2.api.ConfigBuilder
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.text.Text
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.lwjgl.glfw.GLFW
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText


object RaidReporter : ModInitializer {

    private const val MOD_ID = "raid-reporter"
    private val logger: Logger = LogManager.getLogger(MOD_ID)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val menuKeyBind = KeyBindingHelper.registerKeyBinding(
        KeyBinding(
            "raidreporter.settings",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "raidreporter.title"
        )
    )

    private var webhook_url: String = ""
    private val client = OkHttpClient()
    private val JSON = "application/json".toMediaType()
    private val WEBHOOK_PATTERN = "https://[^/]*\\.discord\\.com/api/webhooks/\\d+/[\\w-]+".toRegex()
    private val CONFIG_PATH: Path = FabricLoader.getInstance().configDir.resolve("$MOD_ID/hook.txt")

    private val raids = mapOf(
        "The Canyon Colossus" to "https://static.wikia.nocookie.net/wynncraft_gamepedia_en/images/2/2d/TheCanyonColossusIcon.png",
        "The Nameless Anomaly" to "https://static.wikia.nocookie.net/wynncraft_gamepedia_en/images/9/92/TheNamelessAnomalyIcon.png",
        "Orphion's Nexus of Light" to "https://static.wikia.nocookie.net/wynncraft_gamepedia_en/images/6/63/Orphion%27sNexusofLightIcon.png",
        "Nest of the Grootslangs" to "https://static.wikia.nocookie.net/wynncraft_gamepedia_en/images/5/52/NestoftheGrootslangsIcon.png"
    )

    override fun onInitialize() {
        ClientLifecycleEvents.CLIENT_STARTED.register(ClientLifecycleEvents.ClientStarted {
            if (CONFIG_PATH.exists()) {
                webhook_url = CONFIG_PATH.readText()
            } else {
                Files.createDirectories(CONFIG_PATH.parent)
                CONFIG_PATH.createFile()
            }
        })

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (menuKeyBind.wasPressed()) {
                val builder = ConfigBuilder.create()
                    .setParentScreen(client.currentScreen)
                    .setTitle(Text.translatable("raidreporter.title"))

                val general = builder.getOrCreateCategory(Text.translatable("raidreporter.category"))
                val entryBuilder = builder.entryBuilder()
                general.addEntry(entryBuilder.startStrField(Text.translatable("raidreporter.webhook"), webhook_url)
                    .setDefaultValue("")
                    .setSaveConsumer { newValue ->
                        if (!WEBHOOK_PATTERN.matches(newValue)) {
                            client.player?.sendMessage(Text.of("Invalid webhook URL entered!"))
                            return@setSaveConsumer
                        }

                        webhook_url = newValue
                        if (!CONFIG_PATH.exists()) {
                            Files.createDirectories(CONFIG_PATH.parent)
                            CONFIG_PATH.createFile()
                        }
                        CONFIG_PATH.writeText(newValue)
                        client.player?.sendMessage(Text.of("Successfully set webhook URL!"))
                    }
                    .build())

                client.setScreen(builder.build())
            }
        }

        AnyClientMessageEvent.EVENT.register(AnyClientMessageEvent { message ->
            if (webhook_url.isEmpty()) {
                logger.warn("Raid completed but webhook URL not set")
                return@AnyClientMessageEvent
            }

            val raidParticipants = mutableListOf<String>()
            var raidInfo: Pair<String, String>? = null

            for (sibling in message.siblings) {
                val msgStr = sibling.string
                when (sibling.style.color?.hexCode) {
                    "#FFFF55" -> raidParticipants.add(msgStr)
                    "#00AAAA" -> {
                        raids.entries.find { it.key == msgStr }?.let {
                            raidInfo = it.key to it.value
                        }
                    }
                }
            }

            raidInfo?.let { (name, imgUrl) ->
                coroutineScope.launch {
                    runCatching {
                        val webhookMsg = webhookMsg(name, raidParticipants, imgUrl)
                        val request = Request.Builder()
                            .url(webhook_url)
                            .post(webhookMsg.toRequestBody(JSON))
                            .build()

                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                logger.error("Unexpected response: ${response.code}")
                            }
                        }
                    }.onFailure { e ->
                        logger.error("Failed to send webhook", e)
                    }.onSuccess {
                        logger.info("Registered raid completion of \"$name\" for players: $raidParticipants")
                    }
                }
            }
        })
    }

    private fun webhookMsg(raidName: String, players: List<String>, raidImgUrl: String): String {
        return """
        {
            "content": null,
            "embeds": [
                {
                    "title": "Completion: $raidName",
                    "color": null,
                    "fields": [
                        {
                            "name": "Player 1",
                            "value": "${players.getOrElse(0) { "N/A" }}",
                            "inline": true
                        },
                        {
                            "name": "Player 2",
                            "value": "${players.getOrElse(1) { "N/A" }}",
                            "inline": true
                        },
                        {
                            "name": "\t",
                            "value": "\t"
                        },
                        {
                            "name": "Player 3",
                            "value": "${players.getOrElse(2) { "N/A" }}",
                            "inline": true
                        },
                        {
                            "name": "Player 4",
                            "value": "${players.getOrElse(3) { "N/A" }}",
                            "inline": true
                        }
                    ],
                    "author": {
                        "name": "Guild Raid Notification",
                        "icon_url": "https://i.imgur.com/PTI0zxK.png"
                    },
                    "thumbnail": {
                        "url": "$raidImgUrl"
                    }
                }
            ],
            "attachments": []
        }
    """
    }
}
