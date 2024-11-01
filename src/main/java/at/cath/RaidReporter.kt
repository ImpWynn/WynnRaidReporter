package at.cath

import at.cath.events.*
import at.cath.events.WynnEventRegistry.register
import at.cath.utility.DiscordWebhook
import me.shedaniel.clothconfig2.api.ConfigBuilder
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.text.Text
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

    private val menuKeyBind = KeyBindingHelper.registerKeyBinding(
        KeyBinding(
            "raidreporter.settings",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "raidreporter.title"
        )
    )

    private var webhook: DiscordWebhook? = null
    private val CONFIG_PATH: Path = FabricLoader.getInstance().configDir.resolve("$MOD_ID/hook.txt")

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
                openSettings(client)
            }
        }

        register(GuildRaid::class.java, GuildRaidMatcher())

        // hook up event matchers to incoming chat
        AnyClientMessageEvent.EVENT.register { message ->
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

    private fun openSettings(client: MinecraftClient) {
        val builder = ConfigBuilder.create()
            .setParentScreen(client.currentScreen)
            .setTitle(Text.translatable("raidreporter.title"))

        val general = builder.getOrCreateCategory(Text.translatable("raidreporter.category"))
        val entryBuilder = builder.entryBuilder()
        general.addEntry(entryBuilder.startStrField(Text.translatable("raidreporter.url"), webhook?.url)
            .setDefaultValue("")
            .setSaveConsumer { newValue ->
                webhook = DiscordWebhook(newValue)
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
