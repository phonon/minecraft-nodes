/**
 * Nodes chat listener
 */

package phonon.nodes.listeners

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import phonon.nodes.chat.Chat

class NodesChatListener: Listener {

    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        if (event.isCancelled) return

        Chat.process(event)
    }
}