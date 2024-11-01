package at.cath.events

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.text.Text

@Serializable
data class GuildRaid(
    @Transient
    override val rawMsg: Text = Text.of(""),
    val raidType: String,
    val players: List<String>
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

        return raidName?.let { GuildRaid(message, it, raidParticipants) }
    }
}