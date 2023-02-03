/*
 * Send message to player
 */

package phonon.refinery

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component

internal object Message {

    val PREFIX = "[Refinery]"
    val COL_MSG = ChatColor.DARK_GREEN
    val COL_ERROR = ChatColor.RED

    /**
     * Print generic plugin message to command sender's chat (either console
     * or player).
     */
    public fun print(sender: CommandSender?, s: String) {
		if ( sender === null ) {
            System.out.println("${PREFIX} Message called with null sender: ${s}")
            return
		}

        val msg = Component.text("${COL_MSG}${s}")
        sender.sendMessage(msg)
    }

    /**
     * Print error message to a command sender's chat (either console or player).
     */
    public fun error(sender: CommandSender?, s: String) {
		if ( sender === null ) {
            System.out.println("${PREFIX} Message called with null sender: ${s}")
            return
		}

        val msg = Component.text("${COL_ERROR}${s}")
        sender.sendMessage(msg)
    }

    /**
     * Wrapper around Bukkit.broadcast to send plugin formatted messages
     * to all players.
     */
    public fun broadcast(s: String) {
        val msg = Component.text("${COL_MSG}${s}")
        Bukkit.broadcast(msg)
    }

    /**
     * Wrapper around paper sendActionBar to send plugin formatted messages.
     */
    public fun announcement(player: Player, s: String) {
        player.sendActionBar(Component.text(s))
    }
}