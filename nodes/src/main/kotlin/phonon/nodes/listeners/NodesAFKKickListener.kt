/**
 * Listen to players kicked for AFK, reduce 
 * claims progress by amount in Config
 */

package phonon.nodes.listeners

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerKickEvent
import phonon.nodes.Config
import phonon.nodes.Nodes

val AFK_MESSAGES = listOf(
    "You have been idle for too long!"
)

public class NodesPlayerAFKKickListener: Listener {

    @EventHandler(priority = EventPriority.MONITOR)
	public fun onPlayerKick(event: PlayerKickEvent) {

        val reason: String = event.getReason()

        for ( msg in AFK_MESSAGES ) {
            if ( msg == reason ) {

                // get resident
                val player: Player = event.getPlayer()        
                val resident = Nodes.getResident(player)
                if ( resident === null ) {
                    return
                }

                val newClaimsTime = Math.max(0, resident.claimsTime - Config.afkKickTime)
                Nodes.setResidentClaimTimer(resident, newClaimsTime)

                return
            }
        }

    }
    
}