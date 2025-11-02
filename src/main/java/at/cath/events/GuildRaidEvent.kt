package at.cath.events

import at.cath.RaidReporter.logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text

@Serializable
data class GuildRaid(
    @Transient
    override val rawMsg: Text = Text.of(""),
    val raidType: String,
    val players: List<String>,
    val reporterUuid: String,
    val gxpGained: String,
    val srGained: Int
) : WynnEvent

private val raidNames = listOf(
    "The Canyon Colossus",
    "The Nameless Anomaly",
    "Orphion's Nexus of Light",
    "Nest of the Grootslangs"
)

private val raidNamePattern = raidNames.joinToString("|") { Regex.escape(it) }
private val raidPattern = Regex(
    """^(.+) finished ($raidNamePattern) and claimed 2x Aspects\s*,\s*2048x Emeralds\s*,\s*(?:and )?\+([\d.]+[mk]?) Guild Experience(?:\s*,\s*and \+(\d+) Seasonal Rating)?$"""
)

class GuildRaidMatcher : EventMatcher<GuildRaid> {

    companion object {
        const val DEBUG = true
    }

    override fun parse(message: String): GuildRaid? {
        if (message.contains(':')) return null

        val match = raidPattern.find(message) ?: return null
        val (playersStr, raidName, gxp, seasonalRating) = match.destructured

        val raidParticipants = playersStr.replace(", and ", ", ").split(", ")

        val playerUuid =
            MinecraftClient.getInstance().player?.uuidAsString ?: throw IllegalStateException("Player UUID is null")

        if (DEBUG) logger.info("Raid '$raidName' found with participants: $raidParticipants (+$seasonalRating SR, +$gxp GXP), original message: $message")

        return GuildRaid(
            players = raidParticipants,
            raidType = raidName,
            reporterUuid = playerUuid,
            gxpGained = gxp,
            srGained = seasonalRating.toIntOrNull() ?: 0 // SR can be empty in off-season
        )
    }

}