package at.cath

import at.cath.events.*
import at.cath.events.WynnEventRegistry.register
import at.cath.utility.DiscordWebhook
import at.cath.utility.WebhookConfigScreen
import com.google.gson.JsonParser
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.lwjgl.glfw.GLFW
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.readText


object RaidReporter : ModInitializer {

    private const val MOD_ID = "raid-reporter"
    val logger: Logger = LogManager.getLogger(MOD_ID)

    private var messageDisplayed = false
    private var cachedMessage: Text? = null
    lateinit var raidMatcher: GuildRaidMatcher

    private val RAID_NAMES_FALLBACK = listOf(
        "The Canyon Colossus",
        "The Nameless Anomaly",
        "Orphion's Nexus of Light",
        "Nest of the Grootslangs",
        "The Wartorn Palace"
    )
    private var raidNames = RAID_NAMES_FALLBACK


    private val menuKeyBind = KeyBindingHelper.registerKeyBinding(
        KeyBinding(
            "raidreporter.settings",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            KeyBinding.Category(Identifier.of("raidreporter", "title")),
        )
    )

    var webhook: DiscordWebhook? = null
    val CONFIG_PATH: Path = FabricLoader.getInstance().configDir.resolve("$MOD_ID/hook.txt")


    // fetch raid names from relay server so we don't have to push updates to the client mod on changes
    fun updateRaidNames(raidUrl: String? = null) {
        messageDisplayed = false
        cachedMessage = null

        // pass directly because otherwise we got a file system write race condition
        val relayUrl = raidUrl ?: if (CONFIG_PATH.exists()) CONFIG_PATH.readText() else ""
        if (relayUrl.isEmpty()) return

        val client: HttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()

        val request: HttpRequest = HttpRequest.newBuilder()
            .uri(URI.create(relayUrl))
            .GET()
            .build()

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept { response ->
                if (response.statusCode() == 200) {
                    try {
                        val jsonArray = JsonParser.parseString(response.body()).asJsonArray
                        raidNames = jsonArray.map { it.asString }

                        cachedMessage = Text.literal("Loaded supported raids: ${raidNames.joinToString(", ")}")
                            .formatted(Formatting.GREEN)
                        logger.info("Loaded raids: ${raidNames.joinToString(", ")}")

                        if (::raidMatcher.isInitialized) {
                            raidMatcher.updatePattern(raidNames)
                        }

                    } catch (_: Exception) {
                        cachedMessage = Text.literal(
                            "Failed to load supported raids. HTTP Status: ${response.statusCode()}. " +
                                    "Falling back to default selection."
                        ).formatted(Formatting.RED)
                    }
                } else {
                    cachedMessage =
                        Text.literal(
                            "Failed to load supported raids. HTTP Status: ${response.statusCode()}. " +
                                    "Falling back to default selection."
                        ).formatted(Formatting.RED)
                    logger.error("Failed to load supported raids. HTTP Status: ${response.statusCode()}")
                }

                printRaidUpdateResult()
            }
            .exceptionally { ex ->
                cachedMessage =
                    Text.literal("Error connecting to relay server: ${ex.message}. Did you set a URL in the mod config screen?")
                        .formatted(Formatting.RED)
                printRaidUpdateResult()
                null
            }
    }

    // this exists because we need to buffer the msg from mod init until player logs in
    fun printRaidUpdateResult() {
        if (!messageDisplayed && cachedMessage != null) {
            val mc = MinecraftClient.getInstance()
            if (mc.player != null) {
                mc.execute {
                    mc.player?.sendMessage(cachedMessage, false)
                    messageDisplayed = true
                }
            }
        }
    }

    override fun onInitialize() {
        ClientLifecycleEvents.CLIENT_STARTED.register(ClientLifecycleEvents.ClientStarted {
            if (CONFIG_PATH.exists()) {
                webhook = DiscordWebhook(CONFIG_PATH.readText())
            } else {
                Files.createDirectories(CONFIG_PATH.parent)
                CONFIG_PATH.createFile()
            }
        })

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (menuKeyBind.wasPressed()) {
                client.setScreen(WebhookConfigScreen(this, client.currentScreen))
            }
        }

        raidMatcher = GuildRaidMatcher(emptyList())
        register(GuildRaid::class.java, raidMatcher)

        updateRaidNames()

        ClientPlayConnectionEvents.JOIN.register(ClientPlayConnectionEvents.Join { _, _, _ ->
            printRaidUpdateResult()
        })

        // hook up event matchers to incoming chat
        GameMessageEvent.EVENT.register { message ->
            WynnEventDispatcher.EVENT.invoker().onWynnEvent(
                WynnEventRegistry.matchEvent(message) ?: return@register
            )
        }

        WynnEventDispatcher.EVENT.register { event ->
            when (event) {
                is GuildRaid -> {
                    webhook?.send(event) ?: run {
                        logger.warn("No webhook set, skipping raid completion")
                    }
                }
            }
        }
    }
}
