package at.cath.events

import at.cath.RaidReporter.logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.client.MinecraftClient
import net.minecraft.text.HoverEvent
import net.minecraft.text.Text

@Serializable
data class GuildRaid(
    @Transient
    override val rawMsg: Text = Text.of(""),
    val raidType: String,
    val players: List<String>,
    val reporterUuid: String
) : WynnEvent

private val raidNames = listOf(
    "The Canyon Colossus",
    "The Nameless Anomaly",
    "Orphion's Nexus of Light",
    "Nest of the Grootslangs"
)
private val raidKeywords = raidNames.map { it.substringAfterLast(" ", "") }.toList()


class GuildRaidMatcher : EventMatcher<GuildRaid> {

    companion object {
        const val DEBUG = false

        private fun extractHoverName(msg: Text): String? {
            val hoverText = msg.style.hoverEvent?.getValue(HoverEvent.Action.SHOW_TEXT) ?: return null
            val hoverName = hoverText.string.substringAfterLast(" ", msg.string)
            if (DEBUG) logger.info("Found hover text: $hoverText, extracted name: $hoverName")
            return hoverName
        }
    }

    override fun parse(message: Text): GuildRaid? {
        val raidParticipants = mutableListOf<String>()
        var raidName: String? = null

        for (sibling in message.siblings) {
            val msgStr = sibling.string
            when (sibling.style.color?.hexCode) {
                // add player name
                "#FFFF55" -> {
                    // check for renamed players
                    extractHoverName(sibling)?.let { raidParticipants.add(it) } ?: raidParticipants.add(msgStr)
                }
                // check for raid keyword match
                "#00AAAA" -> {
                    raidName = raidKeywords.withIndex().find { msgStr.contains(it.value) }?.let {
                        raidNames.getOrNull(it.index)
                    }
                    // no need to scan the rest of the message if we found the raid name
                    if (raidName != null) break
                }
            }
        }

        if (raidName == null) return null
        if (DEBUG) logger.info("Raid '$raidName' found with participants: $raidParticipants, original message: $message")
        if (raidParticipants.size != 4) return null

        val playerUuid =
            MinecraftClient.getInstance().player?.uuidAsString ?: throw IllegalStateException("Player UUID is null")
        return GuildRaid(message, raidName, raidParticipants, playerUuid)

    }
}