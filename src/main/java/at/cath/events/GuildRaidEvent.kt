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

    private val emeraldsRegex = Regex("""(\d+)x Emeralds""")
    private val aspectsRegex = Regex("""([12])x Aspects""")
    private val gxpRegex = Regex("""\+([\d.]+[mk]?) Guild Experience""")
    private val srRegex = Regex("""\+(\d+) Seasonal Rating""")

    init {
        updatePattern(raidNames)
    }

    fun updatePattern(raidNames: List<String>) {
        if (raidNames.isEmpty()) return
        val raidNamePattern = raidNames.joinToString("|") { Regex.escape(it) }
        raidPattern = Regex("""^(.+) finished ($raidNamePattern) and claimed (.*)$""")
    }

    companion object {
        const val DEBUG = true
    }

    override fun parse(message: String): GuildRaid? {
        if (message.contains(':')) return null

        val pattern = raidPattern ?: return null
        val match = pattern.find(message) ?: return null

        val (playersStr, raidName, rewardsStr) = match.destructured

        val emeraldsStr = emeraldsRegex.find(rewardsStr)?.groupValues?.get(1)
        val aspectsStr = aspectsRegex.find(rewardsStr)?.groupValues?.get(1)
        val gxp = gxpRegex.find(rewardsStr)?.groupValues?.get(1) ?: "0" // Fallback if missing
        val seasonalRating = srRegex.find(rewardsStr)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        val raidParticipants = playersStr.replace(", and ", ", ").split(", ")

        val playerUuid =
            MinecraftClient.getInstance().player?.uuidAsString ?: throw IllegalStateException("Player UUID is null")

        if (DEBUG) {
            logger.info("Raid '$raidName' found with participants: $raidParticipants ($emeraldsStr emeralds, $aspectsStr aspects, +$seasonalRating SR, +$gxp GXP)")
        }

        return GuildRaid(
            players = raidParticipants,
            raidType = raidName,
            reporterUuid = playerUuid,
            gxpGained = gxp,
            srGained = seasonalRating
        )
    }
}