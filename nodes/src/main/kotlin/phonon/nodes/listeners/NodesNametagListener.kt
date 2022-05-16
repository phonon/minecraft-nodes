/**
 * Handles all events for creating/deleting
 * player town name tags
 */

package phonon.nodes.listeners

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.*
import phonon.nodes.Nodes
import phonon.nodes.objects.Nametag

public class NodesNametagListener: Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        if ( Nodes.getResident(player)?.town !== null ) {
            Nametag.updateTextForPlayer(player)
        }
    }
    
    // @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    // public fun onPlayerQuit(event: PlayerQuitEvent) {
    //     Nametag.destroy(event.player)
    // }

    // @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    // public fun onPlayerSpawn(event: PlayerRespawnEvent) {
    //     val player = event.player
    //     if ( Nodes.getResident(player)?.town !== null ) {
    //         Nametag.create(player)
    //     }
    // }

    // @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    // public fun onPlayerDeath(event: PlayerDeathEvent) {
    //     Nametag.destroy(event.entity)
    // }

    // @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    // public fun onPlayerMove(event: PlayerMoveEvent) {
    //     val from = event.getFrom()
    //     val to = event.getTo()

    //     // ignore if player only moved head
    //     if ( from.x == to.x && from.y == to.y && from.z == to.z ) {
    //         return
    //     }

    //     Nametag.update(event.player, to)
    // }

    // @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    // public fun onPlayerTeleport(event: PlayerTeleportEvent) {
    //     val from = event.getFrom()
    //     val to = event.getTo()

    //     // ignore if player only moved head
    //     if ( from.x == to.x && from.y == to.y && from.z == to.z ) {
    //         return
    //     }

    //     Nametag.update(event.player, to)
    // }

    // @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    // public fun onPlayerSneak(event: PlayerToggleSneakEvent) {
    //     val player: Player = event.player
    //     val sneaking: Boolean = event.isSneaking()
    //     if ( sneaking ) {
    //         Nametag.visibility(player, false)
    //     }
    //     else {
    //         Nametag.visibility(player, true)
    //     }
    // }

}