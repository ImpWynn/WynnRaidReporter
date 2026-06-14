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


class GuildRaidMatcher(raidNames: List<String>) : EventMatcher<GuildRaid> {

    private var raidPattern: Regex? = null

    init {
        updatePattern(raidNames)
    }

    fun updatePattern(raidNames: List<String>) {
        if (raidNames.isEmpty()) return
        val raidNamePattern = raidNames.joinToString("|") { Regex.escape(it) }
        raidPattern = Regex(
            """^(.+) finished ($raidNamePattern) and claimed (?:[12]x Aspects\s*,\s*)?(\d+)x Emeralds\s*,\s*(?:and )?\+([\d.]+[mk]?) Guild Experience(?:\s*,\s*and \+(\d+) Seasonal Rating)?$"""
        )
    }

    companion object {
        const val DEBUG = true
    }

    override fun parse(message: String): GuildRaid? {
        if (message.contains(':')) return null

        val pattern = raidPattern ?: return null
        val match = pattern.find(message) ?: return null

        val (playersStr, raidName, emeraldsStr, gxp, seasonalRating) = match.destructured

        val raidParticipants = playersStr.replace(", and ", ", ").split(", ")
        val playerUuid =
            MinecraftClient.getInstance().player?.uuidAsString ?: throw IllegalStateException("Player UUID is null")

        if (DEBUG) logger.info("Raid '$raidName' found with participants: $raidParticipants ($emeraldsStr emeralds, +$seasonalRating SR, +$gxp GXP), original message: $message")

        return GuildRaid(
            players = raidParticipants,
            raidType = raidName,
            reporterUuid = playerUuid,
            gxpGained = gxp,
            srGained = seasonalRating.toIntOrNull() ?: 0 // SR can be empty in off-season
        )
    }

}