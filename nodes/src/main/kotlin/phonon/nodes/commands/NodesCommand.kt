/**
 * General commands for info
 * /nodes [command]
 */

package phonon.nodes.commands

import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import phonon.nodes.Message
import phonon.nodes.Nodes
import phonon.nodes.objects.TerritoryId
import phonon.nodes.utils.string.filterByStart
import phonon.nodes.utils.string.filterNation
import phonon.nodes.utils.string.filterResident
import phonon.nodes.utils.string.filterTown

// list of all subcommands, used for onTabComplete
private val subcommands: List<String> = listOf(
    "help",
    "resource",
    "territory",
    "town",
    "nation",
    "player",
    "war"
)

public class NodesCommand : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, cmd: Command, commandLabel: String, args: Array<String>): Boolean {
            
        // no args, print plugin info
        if ( args.size == 0 ) {
            Message.print(sender, "${ChatColor.BOLD}Nodes ${Nodes.version}")

            // print number of resource nodes and territories loaded
            Message.print(sender, "World info:")
            Message.print(sender, "- Resource Nodes${ChatColor.WHITE}: ${Nodes.getResourceNodeCount()}")
            Message.print(sender, "- Territories${ChatColor.WHITE}: ${Nodes.getTerritoryCount()}")
            Message.print(sender, "- Residents${ChatColor.WHITE}: ${Nodes.getResidentCount()}")
            Message.print(sender, "- Towns${ChatColor.WHITE}: ${Nodes.getTownCount()}")
            Message.print(sender, "- Nations${ChatColor.WHITE}: ${Nodes.getNationCount()}")

            Message.print(sender, "Use \"/nodes help\" to see subcommands")

            return true
        }

        // parse subcommand
        when ( args[0].lowercase() ) {
            "help" -> printHelp(sender)
            "resource" -> printResourceNodeInfo(sender, args)
            "territory" -> printTerritoryInfo(sender, args)
            "town" -> printTownInfo(sender, args)
            "towns" -> printTownInfo(sender, args)
            "nation" -> printNationInfo(sender, args)
            "nations" -> printNationInfo(sender, args)
            "player" -> printPlayerInfo(sender, args)
            "players" -> printPlayerInfo(sender, args)
            "war" -> printWarInfo(sender, args)
            else -> { Message.error(sender, "Invalid command, use \"/nodes help\"") }
        }

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        // match subcommand
        if ( args.size == 1 ) {
            return filterByStart(subcommands, args[0])
        }
        // match each subcommand format
        else if ( args.size > 1 ) {
            // handle specific subcommands
            when ( args[0].lowercase() ) {

                // /nodes town name
                "town",
                "towns" -> {
                    if ( args.size == 2 ) {
                        return filterTown(args[1])
                    }
                }

                // /nodes nation name
                "nation",
                "nations" -> {
                    if ( args.size == 2 ) {
                        return filterNation(args[1])
                    }
                }

                // /nodes player resident
                "player",
                "players" -> {
                    if ( args.size == 2 ) {
                        return filterResident(args[1])
                    }
                }
            }
        }

        return listOf()
    }

    /**
     * @command /nodes help
     * Prints list of commands
     */
    private fun printHelp(sender: CommandSender) {
        Message.print(sender, "${ChatColor.BOLD}[Nodes] World info commands:")
        Message.print(sender, "/nodes resource${ChatColor.WHITE}: Get a resource node's properties")
        Message.print(sender, "/nodes territory${ChatColor.WHITE}: Get territory info")
        Message.print(sender, "/nodes town${ChatColor.WHITE}: Get town info")
        Message.print(sender, "/nodes nation${ChatColor.WHITE}: Get nation info")
        Message.print(sender, "/nodes player${ChatColor.WHITE}: Get player info")
        Message.print(sender, "/nodes war${ChatColor.WHITE}: Current war status")
        return
    }

    /**
     * @command /nodes resource
     * Prints list of all resource nodes
     * 
     * @subcommand /nodes resource [name]
     * Print detailed stats of a resource node type (income, crops, animals, ore)
     */
    private fun printResourceNodeInfo(sender: CommandSender, args: Array<String>) {
        if ( args.size < 2 ) {
            Message.print(sender, "${ChatColor.BOLD}Resource nodes:")
            for ( v in Nodes.resourceNodes.values ) {
                Message.print(sender, "- ${v.name}")
            }
            Message.print(sender, "Use \"/nodes resource [name]\" to get more info")
        }
        else {
            // parse resource node name
            val name = args[1]
            val resource = Nodes.resourceNodes.get(name)
            if ( resource != null ) {
                resource.printInfo(sender)
            }
            else {
                Message.error(sender, "Invalid resource node \"${name}\"")
            }
        }
       
    }

    /**
     * @command /nodes territory
     * In console, just prints total territory count.
     * Ingame, prints info about territory player is standing in.
     * 
     * @subcommand /nodes territory [id]
     * Prints info about territory from id
     */
    private fun printTerritoryInfo(sender: CommandSender, args: Array<String>) {
        val territory = if ( args.size < 2 ) {
            // if command sender was player, print territory info of current location
            val player = if ( sender is Player ) sender else null
            if ( player != null ) {
                val loc = player.getLocation()
                val getTerritory = Nodes.getTerritoryFromBlock(loc.x.toInt(), loc.z.toInt())

                Message.print(sender, "Territory at current location:")
                Message.print(sender, "(Other usage: \"/nodes territory [id]\")")

                if ( getTerritory == null ) {
                    Message.error(sender, "No territory at current location")
                    return
                }

                getTerritory
            }
            // console user, just print territory count
            else {
                Message.print(sender, "Territories: ${Nodes.getTerritoryCount()}")
                Message.print(sender, "Usage: \"/nodes territory [id]\"")
                return
            }
        }
        else {
            // try parse input as id, then try to get territory with that id
            val getTerritory = args[1].toIntOrNull()?.let { id -> Nodes.territories[TerritoryId(id)] }
            if ( getTerritory == null ) {
                Message.error(sender, "Invalid territory id \"${args[1]}\"")
                return
            }

            getTerritory
        }

        // print territory info
        territory.printInfo(sender)
        territory.printResources(sender)
    }

    /**
     * @command /nodes town
     * Prints list of all towns, their player count and territory count
     * 
     * @subcommand /nodes town [name]
     * Prints detailed info about town from [name] (territories, players, etc...)
     */
    private fun printTownInfo(sender: CommandSender, args: Array<String>) {
        // print list of all towns and their player count
        if ( args.size < 2 ) {
            Message.print(sender, "${ChatColor.BOLD}Towns (${Nodes.getTownCount()}): Showing [Players] [Territories]")
            val towns = Nodes.towns.values.toMutableList()
            towns.sortByDescending { it.residents.size }

            for ( t in towns ) {
                Message.print(sender, "- ${t.name}${ChatColor.WHITE}: ${t.residents.size}P ${t.territories.size}T")
            }

            Message.print(sender, "Use \"/nodes town [name]\" to get a town's info")
        }
        // print specific town info
        else {
            val town = Nodes.towns.get(args[1])
            if ( town != null ) {
                town.printInfo(sender)
            }
            else {
                Message.error(sender, "Invalid town name \"${args[1]}\"")
            }
        }
        
    }

    /**
     * @command /nodes nation
     * Prints list of all nations
     * 
     * @subcommand /nodes nation [name]
     * Prints detailed info about nation from [name] (towns, allies, enemies, etc...)
     */
    private fun printNationInfo(sender: CommandSender, args: Array<String>) {
        // print list of all nations and their town + player count
        if ( args.size < 2 ) {
            Message.print(sender, "${ChatColor.BOLD}Nations (${Nodes.getNationCount()}):")
            val nations = Nodes.nations.values.toMutableList()
            nations.sortBy { it.name }

            for ( n in nations ) {
                Message.print(sender, "- ${n.name}${ChatColor.WHITE}")
            }

            Message.print(sender, "Use \"/nodes nation [name]\" to get a nation's info")
        }
        // print specific town info
        else {
            val nation = Nodes.nations.get(args[1])
            if ( nation != null ) {
                nation.printInfo(sender)
            }
            else {
                Message.error(sender, "Invalid nation name \"${args[1]}\"")
            }
        }
    }

    /**
     * @command /nodes player [name]
     * Prints player info (their town and nation)
     */
    private fun printPlayerInfo(sender: CommandSender, args: Array<String>) {
        if ( args.size < 2 ) {
            Message.error(sender, "Usage: \"/nodes player [name]\"")
        }
        else {
            val resident = Nodes.getResidentFromName(args[1])
            if ( resident != null ) {
                resident.printInfo(sender)
            }
            else {
                Message.error(sender, "Invalid player name \"${args[1]}\"")
            }
        }
    }

    /**
     * @command /nodes war
     * Print if war enabled/disabled
     */
    private fun printWarInfo(sender: CommandSender, args: Array<String>) {
        // print general war state
        if ( args.size < 2 ) {
            Nodes.war.printInfo(sender, true)
        }
        // attempt modify war state
        else {
            Message.error(sender, "Toggle using admin command: \"/nodesadmin war [enable|disable]\"")
        }
    }
}