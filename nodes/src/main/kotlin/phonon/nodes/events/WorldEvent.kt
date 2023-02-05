/**
 * Events for world loading/reloading, to signal to other plugins or
 * addons that world data has been loaded/reloaded.
 */

package phonon.nodes.event

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Event when entire world is reloaded
 * (territories, towns, nations, war, etc.)
 */
public class NodesWorldLoadedEvent(): Event() {
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

/**
 * Event when territories are loaded or reloaded.
 */
public class NodesTerritoriesLoadedEvent(): Event() {
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