/**
 * List for truce expired events, print message
 * to residents in towns involved that truce expired
 */

package phonon.nodes.listeners;

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import phonon.nodes.Message
import phonon.nodes.event.AllianceCreatedEvent

public class NodesDiplomacyAllianceListener: Listener {

    @EventHandler
    public fun onAllianceCreated(event: AllianceCreatedEvent) {
        val town1 = event.town1
        val town2 = event.town2

        val msgTown1 = "Your town is now allied with ${town2.name}"
        for ( r in town1.residents ) {
            val player = r.player()
            if ( player !== null ) {
                Message.print(player, msgTown1)
            }
        }

        val msgTown2 = "Your town is now allied with ${town1.name}"
        for ( r in town2.residents ) {
            val player = r.player()
            if ( player !== null ) {
                Message.print(player, msgTown2)
            }
        }
    }
}