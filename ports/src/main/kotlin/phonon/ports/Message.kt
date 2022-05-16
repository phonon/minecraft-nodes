/*
 * Send message to player
 */

package phonon.ports

import org.bukkit.entity.Player
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent

public object Message {

    public val PREFIX = "[Ports]"
    public val COL_MSG = ChatColor.DARK_GREEN
    public val COL_ERROR = ChatColor.RED

    // print generic message to chat
    public fun print(sender: Any?, s: String) {
        if ( sender === null ) {
            System.out.println("${PREFIX} Message called with null sender: ${s}")
            return
        }

        if ( sender is Player ) {
            sender.sendMessage(s)
        }
        else {
            (sender as CommandSender).sendMessage(s)
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
            sender.sendMessage(msg)
        }
        else {
            (sender as CommandSender).sendMessage(msg)
        }
    }

    // print text to the player action bar
    public fun announcement(player: Player, s: String) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent(s));
    }
}