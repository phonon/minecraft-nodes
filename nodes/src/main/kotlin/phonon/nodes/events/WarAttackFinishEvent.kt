/**
 * Event that occurs when a flag attack finishes
 */

package phonon.nodes.event

import java.util.UUID
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.event.Cancellable
import org.bukkit.block.Block
import phonon.nodes.objects.Town
import phonon.nodes.objects.Territory

class WarAttackFinishEvent(
    val attacker: UUID,
    val attackingTown: Town,
    val territory: Territory,
    val block: Block
): Event(), Cancellable {

    private var isCancelled: Boolean = false

    override fun isCancelled(): Boolean {
        return isCancelled
    }

    override fun setCancelled(cancel: Boolean) {
        this.isCancelled = cancel
    }

    override fun getHandlers(): HandlerList {
        return WarAttackFinishEvent.handlers
    }

    companion object {
        private val handlers: HandlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return handlers
        }
    }
}