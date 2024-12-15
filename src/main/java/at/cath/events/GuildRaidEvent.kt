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
    override fun parse(message: Text): GuildRaid? {
        val raidParticipants = mutableListOf<String>()
        var raidName: String? = null

        // todo: maybe switch to just matching by full msg string
        for (sibling in message.siblings) {
            val msgStr = sibling.string
            when (sibling.style.color?.hexCode) {
                // add player name
                "#FFFF55" -> {
                    // check for renamed players
                    val hoverText = sibling.style.hoverEvent?.getValue(HoverEvent.Action.SHOW_TEXT)
                    val hoverName = hoverText?.siblings?.last()?.string
                        ?.substringAfterLast(" ", hoverText.string) ?: hoverText?.string
                    if (hoverName == null) {
                        logger.error("Found nickname but could not determine hover name")
                        break
                    }
                    hoverText?.let { raidParticipants.add(hoverName) } ?: raidParticipants.add(msgStr)
                }
                // check for raid keyword match
                "#00AAAA" -> {
                    raidName = raidKeywords.withIndex().find { msgStr.contains(it.value) }?.let {
                        raidNames.getOrNull(it.index)
                    }

                    if (raidName != null) break
                }
            }
        }

        if (raidParticipants.size != 4)
            return null

        val raidReporter =
            MinecraftClient.getInstance().player?.uuidAsString ?: throw IllegalStateException("Player UUID is null")
        return raidName?.let { GuildRaid(message, it, raidParticipants, raidReporter) }
    }
}