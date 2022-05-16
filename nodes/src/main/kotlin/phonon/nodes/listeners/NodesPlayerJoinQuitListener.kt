/**
 * Handle when player join or quit server
 * join: create resident (if does not exist) and mark player online
 * quit: mark player offline
 */

package phonon.nodes.listeners

import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import phonon.nodes.Nodes
import phonon.nodes.chat.Chat
import phonon.nodes.objects.Resident

public class NodesPlayerJoinQuitListener: Listener {

    @EventHandler(priority = EventPriority.HIGH)
	public fun onPlayerJoin(event: PlayerJoinEvent) {
        // create resident wrapper for player
        // createResident checks if resident already exists
        val player: Player = event.getPlayer()
        Nodes.createResident(player)
        
        val resident: Resident = Nodes.getResident(player)!!
        Nodes.setResidentOnline(resident, player)

        // if war enabled, send active chunk attack progress bars
        if ( Nodes.war.enabled == true ) {
            Nodes.war.sendWarProgressBarToPlayer(player)
        }
    }
    
    @EventHandler
    public fun onPlayerQuit(event: PlayerQuitEvent) {
        val player: Player = event.getPlayer()
        val resident = Nodes.getResident(player)
        if ( resident != null ) {
            resident.destroyMinimap()
            Nodes.setResidentOffline(resident, player)
        }

        // remove player from muting global chat
        Chat.enableGlobalChat(player)

        // if playing attacking a chunk, stop it
        if ( Nodes.war.enabled ) {
            val attacks = Nodes.war.attackers.get(player.getUniqueId())
            if ( attacks !== null ) {
                for ( a in attacks ) {
                    a.cancel()
                }
            }
        }
    }
    
}