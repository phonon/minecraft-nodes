/**
 * Handle player scoreboard manager
 * 
 * Manages per player scoreboards
 * 
 * Player scoreboards handle:
 * - Player nametags:
 *     Each scoreboard team adjusts viewed player nametag prefixes
 * - Nodes minimap:
 *     Player nodes territory minimap rendered on scoreboard objective
 * 
 */

package phonon.nodes

import java.util.UUID
import java.util.LinkedHashMap
import org.bukkit.Bukkit
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard

object PlayerScoreboardManager {

    // bukkit scoreboard manager
    val scoreboardManager = Bukkit.getScoreboardManager()

    // map player -> scoreboard
    val playerScoreboards: LinkedHashMap<UUID, Scoreboard> = LinkedHashMap()

    /**
     * Get scoreboard from player, creates new one if does not exist
     */
    public fun getScoreboard(uuid: UUID): Scoreboard {
        val scoreboard = PlayerScoreboardManager.playerScoreboards.get(uuid)
        if ( scoreboard === null ) {
            val newScoreboard = scoreboardManager.getNewScoreboard()
            PlayerScoreboardManager.playerScoreboards.put(uuid, newScoreboard)
            return newScoreboard
        }
        else {
            return scoreboard
        }
    }
}