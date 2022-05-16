/**
 * Ingame player message printing manager
 * 
 */

package phonon.nodes

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent

public object Message {

    public val PREFIX = "[Nodes]"
    public val COL_MSG = ChatColor.AQUA
    public val COL_ERROR = ChatColor.RED

    // print generic message to chat
    public fun print(sender: Any?, s: String) {
		if ( sender === null ) {
            System.out.println("${PREFIX} Message called with null sender: ${s}")
            return
		}

        val msg = "${COL_MSG}${s}"
        if ( sender is Player ) {
            (sender as Player).sendMessage(msg)
        }
        else {
            (sender as CommandSender).sendMessage(msg)
        }
    }

    // print error message to chat
    public fun error(sender: Any?, s: String) {
		if ( sender === null ) {
            System.out.println("${PREFIX} Message called with null sender: ${s}")
            return
		}

        val msg = "${COL_ERROR}${s}"
        if ( sender is Player ) {
            (sender as Player).sendMessage(msg)
        }
        else {
            (sender as CommandSender).sendMessage(msg)
        }
    }

    // wrapper around Bukkit.broadcast to send
    // messages to all players
    public fun broadcast(s: String) {
        val msg = "${COL_MSG}${s}"
        Bukkit.broadcastMessage(msg)
    }

    // print text to the player action bar
    public fun announcement(player: Player, s: String) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent(s));
    }
}