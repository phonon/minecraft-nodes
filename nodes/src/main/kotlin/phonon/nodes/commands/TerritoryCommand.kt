/**
 * Commands for viewing territory, alias of /nodes territory
 */

package phonon.nodes.commands

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import phonon.nodes.Message
import phonon.nodes.Nodes
import phonon.nodes.objects.TerritoryId

/**
 * @command /territory
 * View territory info
 * 
 * @subcommand /territory [id]
 * View info about territory from id
 */
public class TerritoryCommand : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, cmd: Command, commandLabel: String, args: Array<String>): Boolean {
        
        // if command sender was player, print territory info of current location
        val territory = if ( args.size < 1 ) {
            val player = if ( sender is Player ) sender else null
            if ( player != null ) {
                val loc = player.getLocation()
                val getTerritory = Nodes.getTerritoryFromBlock(loc.x.toInt(), loc.z.toInt())

                Message.print(sender, "Territory at current location:")
                Message.print(sender, "(Other usage: \"/territory [id]\")")

                if ( getTerritory == null ) {
                    Message.error(sender, "No territory at current location")
                    return true
                }

                getTerritory
            }
            // console user, just print territory count
            else {
                Message.print(sender, "Territories: ${Nodes.getTerritoryCount()}")
                Message.print(sender, "Usage: \"/territory [id]\"")
                Message.print(sender, "(This is intended to only be used ingame)")
                return true
            }
        }
        else {
            // parse input as id
            val getTerritory = args[0].toIntOrNull()?.let { id -> Nodes.territories[TerritoryId(id)] }
            if ( getTerritory == null ) {
                Message.error(sender, "Invalid territory id \"${args[0]}\"")
                return true
            }

            getTerritory
        }

        // print territory info
        territory.printInfo(sender)
        territory.printResources(sender)

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {      
        return listOf()
    }
}