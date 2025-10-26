package at.cath

import at.cath.events.*
import at.cath.events.WynnEventRegistry.register
import at.cath.utility.DiscordWebhook
import at.cath.utility.WebhookConfigScreen
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.lwjgl.glfw.GLFW
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.readText


object RaidReporter : ModInitializer {

    private const val MOD_ID = "raid-reporter"
    val logger: Logger = LogManager.getLogger(MOD_ID)

    private val menuKeyBind = KeyBindingHelper.registerKeyBinding(
        KeyBinding(
            "raidreporter.settings",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "raidreporter.title"
        )
    )

    var webhook: DiscordWebhook? = null
    val CONFIG_PATH: Path = FabricLoader.getInstance().configDir.resolve("$MOD_ID/hook.txt")

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
                client.setScreen(WebhookConfigScreen(client.currentScreen))
            }
        }

        register(GuildRaid::class.java, GuildRaidMatcher())

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
