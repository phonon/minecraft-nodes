/**
 * Event that triggers when a town pair's truce expires
 */

package phonon.nodes.event

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import phonon.nodes.objects.Town

public class TruceExpiredEvent(
    public val town1: Town,
    public val town2: Town
): Event() {

    override fun getHandlers(): HandlerList {
        return HANDLERS
    }

    companion object {
        private val HANDLERS: HandlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return HANDLERS
        }
    }
}