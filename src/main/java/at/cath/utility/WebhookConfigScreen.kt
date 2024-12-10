package at.cath.utility

import at.cath.RaidReporter.CONFIG_PATH
import at.cath.RaidReporter.webhook
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Style
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW
import java.nio.file.Files
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText


class WebhookConfigScreen(private val parent: Screen?) : Screen(Text.translatable("raidreporter.title")) {
    private lateinit var promptField: TextFieldWidget
    private lateinit var doneButton: ButtonWidget

    companion object {
        private val rainbowColours: IntArray = intArrayOf(
            0xFF0000,  // Red
            0xFF7F00,  // Orange
            0xFFFF00,  // Yellow
            0x00FF00,  // Green
            0x0000FF,  // Blue
            0x4B0082,  // Indigo
            0x9400D3 // Violet
        )

        fun Text.rainbow(): Text {
            val content = this.string
            val rainbowText = Text.literal("").setStyle(this.style)
            for (i in content.indices) {
                val colour = rainbowColours[i % rainbowColours.size]
                rainbowText.append(Text.literal(content[i].toString()).setStyle(Style.EMPTY.withColor(colour)))
            }
            return rainbowText
        }
    }

    override fun init() {
        promptField = TextFieldWidget(
            textRenderer,
            width / 2 - 100,
            height / 2 - 20,
            200,
            20,
            Text.translatable("raidreporter.promptUrl")
        )
        promptField.text = CONFIG_PATH.takeIf { it.exists() }?.readText() ?: ""
        addDrawableChild(promptField)

        doneButton = ButtonWidget.builder(Text.translatable("raidreporter.confirmUrl"), {
            val newValue = promptField.text.trim()
            webhook = DiscordWebhook(newValue)
            if (!CONFIG_PATH.exists()) {
                Files.createDirectories(CONFIG_PATH.parent)
                CONFIG_PATH.createFile()
            }

            CONFIG_PATH.writeText(newValue)
            client?.player?.sendMessage(Text.of("Successfully set relay server URL!"), false)
            close()
        }).dimensions(width / 2 - 50, height / 2 + 20, 100, 20).build();

        addDrawableChild(doneButton)
    }

    override fun close() {
        client?.setScreen(parent)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val titleText = Text.translatable("raidreporter.promptUrl").setStyle(Style.EMPTY.withBold(true)).rainbow()

        val titleWidth = textRenderer.getWidth(titleText)
        val titleX = (width - titleWidth) / 2
        val titleY = (height / 2 - 40)

        super.render(context, mouseX, mouseY, delta)
        context.drawText(textRenderer, titleText, titleX, titleY, 0xff0049, true)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            doneButton.onPress()
            return true
        }
        return promptField.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers)
    }
}