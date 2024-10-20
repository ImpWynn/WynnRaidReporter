package at.cath

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

    private var relay_url: String = ""
    private val client = OkHttpClient()
    private val JSON = "application/json".toMediaType()
    private val CONFIG_PATH: Path = FabricLoader.getInstance().configDir.resolve("$MOD_ID/hook.txt")

    @Serializable
    data class RaidMessage(val type: String, val players: List<String>)

    private val raidNames = listOf(
        "The Canyon Colossus",
        "The Nameless Anomaly",
        "Orphion's Nexus of Light",
        "Nest of the Grootslangs"
    )
    private val raidKeywords = raidNames.map { it.substringAfterLast(" ", "") }.toList()

    override fun onInitialize() {
        ClientLifecycleEvents.CLIENT_STARTED.register(ClientLifecycleEvents.ClientStarted {
            if (CONFIG_PATH.exists()) {
                relay_url = CONFIG_PATH.readText()
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
                general.addEntry(entryBuilder.startStrField(Text.translatable("raidreporter.url"), relay_url)
                    .setDefaultValue("")
                    .setSaveConsumer { newValue ->
                        relay_url = newValue
                        if (!CONFIG_PATH.exists()) {
                            Files.createDirectories(CONFIG_PATH.parent)
                            CONFIG_PATH.createFile()
                        }
                        CONFIG_PATH.writeText(newValue)
                        client.player?.sendMessage(Text.of("Successfully set relay server URL!"))
                    }
                    .build())

                client.setScreen(builder.build())
            }
        }

        AnyClientMessageEvent.EVENT.register(AnyClientMessageEvent { message ->
            if (relay_url.isEmpty())
                return@AnyClientMessageEvent

            val raidParticipants = mutableListOf<String>()
            var raidName: String? = null

            for (sibling in message.siblings) {
                val msgStr = sibling.string
                when (sibling.style.color?.hexCode) {
                    // add player name
                    "#FFFF55" -> raidParticipants.add(msgStr)
                    // check for raid keyword match
                    "#00AAAA" -> {
                        if (raidKeywords.size != 4) break
                        raidName = raidKeywords.withIndex().find { msgStr.contains(it.value) }?.let {
                            raidNames.getOrNull(it.index)
                        }
                        if (raidName != null) break
                    }
                }
            }

            raidName?.let {
                coroutineScope.launch {
                    runCatching {

                        val payload = RaidMessage(it, raidParticipants)
                        val request = Request.Builder()
                            .url(relay_url)
                            .post(Json.encodeToString(payload).toRequestBody(JSON))
                            .build()

                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                logger.error("Unexpected response: ${response.code}: ${response.message}")
                                return@launch
                            }
                        }
                    }.onFailure { e ->
                        logger.error("Failed to send to relay URL", e)
                    }.onSuccess {
                        logger.info("Registered raid completion of \"$raidName\" for players: $raidParticipants")
                    }
                }
            }
        })
    }
}
