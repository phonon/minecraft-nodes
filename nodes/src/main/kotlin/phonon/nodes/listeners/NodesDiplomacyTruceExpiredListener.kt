/**
 * List for truce expired events, print message
 * to residents in towns involved that truce expired
 */

package phonon.nodes.listeners;

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import phonon.nodes.Message
import phonon.nodes.event.TruceExpiredEvent

public class NodesDiplomacyTruceExpiredListener: Listener {

    @EventHandler
    public fun onTruceExpired(event: TruceExpiredEvent) {
        val town1 = event.town1
        val town2 = event.town2

        val msgTown1 = "Your truce with ${town2.name} has expired"
        for ( r in town1.residents ) {
            val player = r.player()
            if ( player !== null ) {
                Message.print(player, msgTown1)
            }
        }

        val msgTown2 = "Your truce with ${town1.name} has expired"
        for ( r in town2.residents ) {
            val player = r.player()
            if ( player !== null ) {
                Message.print(player, msgTown2)
            }
        }
    }
}