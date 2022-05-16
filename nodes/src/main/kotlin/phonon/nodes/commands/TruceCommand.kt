/**
 * Commands for viewing town's truces
 */

package phonon.nodes.commands

import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import phonon.nodes.Config
import phonon.nodes.Message
import phonon.nodes.Nodes
import phonon.nodes.objects.Town
import phonon.nodes.utils.string.filterTown
import phonon.nodes.war.Truce

/**
 * @command /truce
 * Print list of player's town's truces and list of times
 */
public class TruceCommand : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, cmd: Command, commandLabel: String, args: Array<String>): Boolean {

        // no args, use sender's town
        if ( args.size == 0 ) {
            if ( !(sender is Player) ) {
                return true
            }
    
            val player: Player = sender
            val resident = Nodes.getResident(player)
            if ( resident == null ) {
                return true
            }
            
            val town = resident.town
            if ( town == null ) {
                Message.error(player, "You have no town")
                return true
            }

            printTownTruces(player, town)
        }
        // parse town name
        else if ( args.size >= 1 ) {
            val townName = args[0]
            val town = Nodes.getTownFromName(townName)
            if ( town !== null ) {
                printTownTruces(sender, town)

                if ( sender is Player ) {
                    Message.print(sender, "Use \"/peace ${townName}\" to negotiate a treaty.")
                }
            }
            else {
                Message.error(sender, "Town \"${townName}\" does not exist")
            }
        }

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        if ( args.size == 1 ) {
            return filterTown(args[0])
        }
        
        return listOf()
    }

    // print truce list to sender
    private fun printTownTruces(sender: CommandSender, town: Town) {
        val time = System.currentTimeMillis()

        Message.print(sender, "Truces with ${town.name}:")

        // get truce list
        val truceList = Truce.get(town)
        for ( townPair in truceList ) {
            val startTime = Truce.truces.get(townPair)
            if ( startTime !== null ) {
                val remainingTime = Config.trucePeriod - (time - startTime)
                
                val remainingTimeString = if ( remainingTime > 0 ) {
                    val hour: Long = remainingTime/3600000L
                    val min: Long = 1L + (remainingTime - hour * 3600000L)/60000L
                    "${hour}hr ${min}min"
                }
                else {
                    "0hr 0min"
                }

                val otherTownName = if ( town === townPair.town1 ) {
                    townPair.town2.name
                } else {
                    townPair.town1.name
                }

                Message.print(sender, "- ${otherTownName}${ChatColor.WHITE}: ${remainingTimeString}")
            }
        }
    }
}