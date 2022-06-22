/**
 * /nation (/n) command
 */

package phonon.nodes.commands

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.ChatColor
import org.bukkit.inventory.ItemStack
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import phonon.nodes.Nodes
import phonon.nodes.Config
import phonon.nodes.Message
import phonon.nodes.objects.Nation
import phonon.nodes.constants.*
import phonon.nodes.utils.sanitizeString
import phonon.nodes.utils.stringInputIsValid
import phonon.nodes.utils.string.*

// list of all subcommands, used for onTabComplete
private val subcommands: List<String> = listOf(
    "help",
    "create",
    "new",
    "delete",
    "disband",
    "leave",
    "capital",
    "invite",
    "accept",
    "deny",
    "reject",
    "list",
    "color",
    "rename",
    "online",
    "info",
    "spawn"
)

public class NationCommand : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, cmd: Command, commandLabel: String, args: Array<String>): Boolean {
        
        val player = if ( sender is Player ) sender else null
    
        // no args, print current nation info
        if ( args.size == 0 ) {
            if ( player != null ) {
                // print player's nation info
                val resident = Nodes.getResident(player)
                if ( resident != null && resident.nation != null ) {
                    resident.nation!!.printInfo(player)
                }
                Message.print(player, "Use \"/nation help\" to view commands")
            }
            return true
        }

        // parse subcommand
        when ( args[0].lowercase() ) {
            "help" -> printHelp(sender)
            "create" -> createNation(player, args)
            "new" -> createNation(player, args)
            "delete" -> deleteNation(player)
            "disband" -> deleteNation(player)
            "leave" -> leaveNation(player)
            "capital" -> setCapital(player, args)
            "invite" -> inviteToNation(player, args)
            "accept" -> accept(player)
            "deny" -> deny(player)
            "reject" -> deny(player)
            "list" -> listNations(player)
            "color" -> setColor(player, args)
            "rename" -> renameNation(player, args)
            "online" -> getOnline(player, args)
            "info" -> getInfo(player, args)
            "spawn" -> goToNationTownSpawn(player, args)
            else -> { Message.error(player, "Invalid command, use /nation help") }
        }

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        val player: Player = if ( sender is Player ) {
            sender
        } else {
            return listOf()
        }

        // match subcommand
        if ( args.size == 1 ) {
            return filterByStart(subcommands, args[0])
        }
        // match each subcommand format
        else if ( args.size > 1 ) {
            // handle specific subcommands
            when ( args[0].lowercase() ) {

                // /nation invite town
                "invite" -> {
                    if ( args.size == 2 ) {
                        return filterTown(args[1])
                    }
                }

                // /nation [subcommand] [nation]
                "list",
                "online",
                "info" -> {
                    if ( args.size == 2 ) {
                        return filterNation(args[1])
                    }
                }

                // /nation [subcommand] [nation town]
                "capital",
                "spawn" -> {
                    if ( args.size == 2 ) {
                        val nation = Nodes.getResident(player)?.nation
                        if ( nation !== null ) {
                            return filterNationTown(nation, args[1])
                        }
                    }
                }
            }
        }

        return listOf()
    }

    private fun printHelp(sender: CommandSender) {
        Message.print(sender, "${ChatColor.BOLD}[Nodes] Nation commands:")
        Message.print(sender, "/nation create${ChatColor.WHITE}: Create nation with name at location")
        Message.print(sender, "/nation delete${ChatColor.WHITE}: Delete your nation")
        Message.print(sender, "/nation leave${ChatColor.WHITE}: Leave your nation")
        Message.print(sender, "/nation invite${ChatColor.WHITE}: Invite a nation to your nation")
        Message.print(sender, "/nation list${ChatColor.WHITE}: List all nations")
        Message.print(sender, "/nation color${ChatColor.WHITE}: Set nation color on map")
        return
    }

    /**
     * @command /nation create [name]
     * Create a new nation with your town as capital.
     */
    private fun createNation(player: Player?, args: Array<String>) {
        if ( player == null ) {
            return
        }

        if ( args.size < 2 ) {
            Message.print(player, "Usage: ${ChatColor.WHITE}/nation create [name]")
            return
        }

        // do not allow during war
        if ( Nodes.war.enabled == true ) {
            Message.error(player, "Cannot create nations during war")
            return
        }
        
        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }

        val town = resident.town
        if ( town == null ) {
            Message.error(player, "You need a town to form a nation")
            return
        }

        // only allow leaders to create nation
        if ( resident !== town.leader ) {
            Message.error(player, "Only the town leader can form a nation")
            return
        }

        val name = args[1]
        if ( !stringInputIsValid(name) ) {
            Message.error(player, "Invalid nation name")
            return
        }

        val result = Nodes.createNation(sanitizeString(name), town, resident)
        if ( result.isSuccess ) {
            Message.broadcast("${ChatColor.BOLD}Nation ${name} has been formed by ${town.name}")
        }
        else {
            when ( result.exceptionOrNull() ) {
                ErrorNationExists -> Message.error(player, "Nation \"${name}\" already exists")
                ErrorTownHasNation -> Message.error(player, "You already belong to a nation")
                ErrorPlayerHasNation -> Message.error(player, "You already belong to a nation")
            }
        }
    }

    /**
     * @command /nation delete
     * Delete your nation. Leader of capital town only.
     */
    private fun deleteNation(player: Player?) {
        if ( player == null ) {
            return
        }

        // check if player is nation leader
        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }

        val nation = resident.nation
        if ( nation == null ) {
            Message.error(player,"You do not belong to a nation")
            return
        }

        val leader = nation.capital.leader
        if ( resident !== leader ) {
            Message.error(player,"You are not the nation leader")
            return
        }

        // do not allow during war
        if ( Nodes.war.enabled == true ) {
            Message.error(player, "Cannot delete your nation during war")
            return
        }
        
        Nodes.destroyNation(nation)
        Message.broadcast("${ChatColor.DARK_RED}${ChatColor.BOLD}Nation ${nation.name} has been destroyed")
    }

    /**
     * @command /nation leave
     * Leave your nation. Used by town leaders only.
     */
    private fun leaveNation(player: Player?) {
        if ( player == null ) {
            return
        }

        // check if player is nation leader
        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }

        val town = resident.town
        if ( town == null ) {
            Message.error(player,"You do not belong to a town")
            return
        }

        val nation = town.nation
        if ( nation == null ) {
            Message.error(player,"You do not belong to a nation")
            return
        }

        if ( town === nation.capital ) {
            Message.error(player, "The nation's capital cannot leave (use /n delete)")
            return
        }

        val leader = town.leader
        if ( resident !== leader ) {
            Message.error(player,"You are not the town leader")
            return
        }

        // do not allow during war
        if ( Nodes.war.enabled == true && Config.canLeaveNationDuringWar == false ) {
            Message.error(player, "Cannot leave your nation during war")
            return
        }
        
        // remove town
        Nodes.removeTownFromNation(nation, town)

        for ( r in town.residents ) {
            val p = r.player()
            if ( p != null ) {
                Message.print(p, "${ChatColor.BOLD}${ChatColor.DARK_RED}Your town has left nation ${ChatColor.WHITE}${nation.name}")
            }
        }
    }

    /**
     * @command /nation capital [town]
     * Set another town in your nation as its capital
     */
    private fun setCapital(player: Player?, args: Array<String>) {
        if ( player == null ) {
            return
        }

        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }

        val nation = resident.nation
        if ( nation == null ) {
            Message.error(player,"You do not belong to a nation")
            return
        }

        val leader = nation.capital.leader
        if ( resident !== leader ) {
            Message.error(player, "Only nation leaders can change the capital town")
            return
        }

        if ( args.size < 2 ) {
            Message.print(player, "Usage: ${ChatColor.WHITE}/nation capital [town]")
            return
        }

        val newCapital = Nodes.getTownFromName(args[1])
        if ( newCapital === null) {
            Message.error(player, "That town does not exist")
            return
        }
        if ( newCapital.nation !== nation ) {
            Message.error(player, "That town does not belong to this nation")
            return
        }
        if ( newCapital === nation.capital ) {
            Message.error(player, "This town is already the nation capital")
            return
        }

        Nodes.setNationCapital(nation, newCapital)
        
        // broadcast message
        Message.broadcast("${ChatColor.BOLD}${newCapital.name} is now the capital of ${nation.name}")
    }

    /**
     * @command /nation invite [town]
     * Invite another town to join your nation. Leader of capital town only.
     */
    private fun inviteToNation(player: Player?, args: Array<String>) {
        if ( player == null ) {
            return
        }

        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }

        val nation = resident.nation
        if ( nation == null ) {
            Message.error(player,"You do not belong to a nation")
            return
        }

        val leader = nation.capital.leader
        if ( resident !== leader ) {
            Message.error(player, "Only nation leaders can invite new towns")
            return
        }

        if ( args.size < 2 ) {
            Message.print(player, "Usage: ${ChatColor.WHITE}/nation invite [town]")
            return
        }

        val inviteeTown = Nodes.getTownFromName(args[1])
        if ( inviteeTown == null) {
            Message.error(player, "That town does not exist")
            return
        }
        if ( inviteeTown.nation != null ) {
            Message.error(player, "That town already belongs to another nation")
            return
        }

        val inviteeResident = inviteeTown.leader
        if ( inviteeResident == null ) {
            Message.error(player, "That town has no leader (?)")
            return
        }
        val invitee: Player? = Bukkit.getPlayer(inviteeResident?.name)
        if ( invitee == null) {
            Message.error(player, "That town's leader is not online")
            return
        }

        Message.print(player, "${inviteeTown.name} has been invited to your nation.")
        Message.print(invitee, "Your town has been invited to join the nation of ${nation.name} by ${player.name}. \nType \"/n accept\" to agree or \"/n reject\" to refuse the offer.")
        inviteeResident.invitingNation = nation
        inviteeResident.invitingTown = inviteeTown
        inviteeResident.invitingPlayer = player
        inviteeResident.inviteThread = Bukkit.getScheduler().runTaskLaterAsynchronously(Nodes.plugin!!, object: Runnable {
            override fun run() {
                Bukkit.getScheduler().runTask(Nodes.plugin!!, object: Runnable {
                    override fun run() {
                        if ( inviteeResident.invitingPlayer == player ) {
                            Message.print(player, "${invitee.name} didn't respond to your nation invitation!")
                            inviteeResident.invitingNation = null
                            inviteeResident.invitingTown = null
                            inviteeResident.invitingPlayer = null
                            inviteeResident.inviteThread = null
                        }
                    }
                })
            }
        }, 1200)
    }

    private fun accept(player: Player?) {
        if ( player == null ) {
            return
        }

        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }

        if ( resident.town != resident.invitingTown ) {
            Message.error(player, "Invite invalid")
            return
        }

        if ( resident.invitingNation == null ) {
            Message.error(player,"You have not been invited to any nation")
            return
        }

        Message.print(player,"${resident.town?.name} is now a jurisdiction of ${resident.invitingNation?.name}!")
        Message.print(resident.invitingPlayer, "${resident.town?.name} has accepted your authority!")

        Nodes.addTownToNation(resident.invitingNation!!,resident.town!!)
        resident.invitingNation = null
        resident.invitingTown = null
        resident.invitingPlayer = null
        resident.inviteThread = null
    }

    private fun deny(player: Player?) {
        if ( player == null ) {
            return
        }

        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }

        if ( resident.invitingNation == null ) {
            Message.error(player,"You have not been invited to any nation")
            return
        }

        Message.print(player,"You have rejected the invitation to ${resident.invitingNation?.name}!")
        Message.print(resident.invitingPlayer, "${resident.town?.name} has rejected your authority!")

        resident.invitingNation = null
        resident.invitingTown = null
        resident.invitingPlayer = null
        resident.inviteThread = null
    }

    /**
     * @command /nation list
     * View list of all established nations and their towns
     */
    private fun listNations(player: Player?) {
        Message.print(player, "${ChatColor.BOLD}Nation - Population - Towns")
        val nationsList = ArrayList(Nodes.nations.values)
        nationsList.sortByDescending { it.residents.size }
        for ( n in nationsList ) {
            val townsList = ArrayList(n.towns)
            townsList.sortByDescending { it.residents.size }
            var towns = ""
            for ( (i, t) in townsList.withIndex() ) {
                towns += t.name
                towns += " (${t.residents.size})"
                if ( i < n.towns.size - 1 ) {
                    towns += ", "
                }
            }
            Message.print(player, "${n.name} ${ChatColor.WHITE}- ${n.residents.size} - ${towns}")
        }
    }

    /**
     * @command /nation color [r] [g] [b]
     * Set territory color on dynmap for all towns in nation. Leader of capital town only.
     */
    private fun setColor(player: Player?, args: Array<String>) {
        if ( player == null ) {
            return
        }

        if ( args.size < 4 ) {
            Message.print(player, "Usage: ${ChatColor.WHITE}/nation color [r] [g] [b]")
            return
        }

        // check if player is town leader
        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }

        val nation = resident.nation
        if ( nation == null ) {
            return
        }

        val leader = nation.capital.leader
        if ( resident !== leader ) {
            Message.error(player,"Only nation leaders can do this")
            return
        }

        // parse color
        try {
            val r = args[1].toInt().coerceIn(0, 255)
            val g = args[2].toInt().coerceIn(0, 255)
            val b = args[3].toInt().coerceIn(0, 255)
            
            Nodes.setNationColor(nation, r, g, b)
            Message.print(player, "Nation color set: ${ChatColor.WHITE}${r} ${g} ${b}")
        }
        catch (e: NumberFormatException) {
            Message.error(player, "Invalid color (must be [r] [g] [b] in range 0-255)")
        }
    }

    /**
     * @command /nation rename [name]
     * Renames your nation. Leader of capital town only.
     */
    private fun renameNation(player: Player?, args: Array<String>) {
        if ( player == null ) {
            return
        }

        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }

        if ( args.size == 1 ) {
            Message.print(player, "Usage: /n rename [new_name]")
            return
        }

        val nation = resident.nation
        if ( nation == null ) {
            Message.error(player, "You do not belong to a nation")
            return
        }

        if ( resident != nation.capital.leader ) {
            Message.error(player, "Only nation leaders can do this")
            return
        }

        val name = args[1]
        if ( !stringInputIsValid(name) ) {
            Message.error(player, "Invalid nation name")
            return
        }

        if ( nation.name.lowercase() == args[1].lowercase() ) {
            Message.error(player, "Your nation is already named ${nation.name}")
            return
        }

        if ( Nodes.nations.containsKey(args[1]) ) {
            Message.error(player, "There is already a nation with this name")
            return
        }

        Nodes.renameNation(nation,name)
        Message.print(player, "Nation renamed to ${nation.name}!")
    }

    /**
     * @command /nation online
     * View your nation's online players
     * @subcommand /nation online [nation]
     * View another nation's online players
     */
    private fun getOnline(player: Player?, args: Array<String>) {
        if ( player == null ) {
            return
        }

        val resident = Nodes.getResident(player)
        if ( resident == null) {
            return
        }

        var nation: Nation? = null
        if ( args.size == 1 ) {
            if ( resident.nation == null ) {
                Message.error(player, "You do not belong to a nation")
                return
            }
            nation = resident.nation
        } else if ( args.size == 2 ) {
            if ( !Nodes.nations.containsKey(args[1]) ) {
                Message.error(player, "That nation does not exist")
                return
            }
            nation = Nodes.getNationFromName(args[1])
        } else {
            Message.error(player, "Usage: /nation online [nation]")
            return
        }

        if ( nation == null ) {
            return
        }

        val numPlayersOnline = nation.playersOnline.size
        val playersOnline = nation.playersOnline.map({p -> p.name}).joinToString(", ")
        Message.print(player, "Players online in nation ${nation.name} [${numPlayersOnline}]: ${ChatColor.WHITE}${playersOnline}")
    }

    /**
     * @command /nation info [nation]
     * View nation's info
     */
    private fun getInfo(player: Player?, args: Array<String>) {
        if ( player == null ) {
            return
        }

        val resident = Nodes.getResident(player)
        if ( resident == null) {
            return
        }

        var nation: Nation? = null
        if ( args.size == 1 ) {
            if ( resident.nation == null ) {
                Message.error(player, "You do not belong to a nation")
                return
            }
            nation = resident.nation
        } else if ( args.size == 2 ) {
            if ( !Nodes.nations.containsKey(args[1]) ) {
                Message.error(player, "That nation does not exist")
                return
            }
            nation = Nodes.getNationFromName(args[1])
        } else {
            Message.error(player, "Usage: /nation info [nation]")
            return
        }

        nation?.printInfo(player)
    }

    /**
     * @command /nation spawn [town]
     * Teleport to town inside your nation. May cost items to use.
     */
    private fun goToNationTownSpawn(player: Player?, args: Array<String>) {
        if ( player == null ) {
            return
        }

        if ( !Config.allowNationTownSpawn ) {
            Message.error(player, "Server has disabled teleporting to other towns in your nation")
            return
        }

        if ( args.size < 2 ) {
            Message.error(player, "Usage: /nation spawn [town]")
            return
        }

        val resident = Nodes.getResident(player)
        if ( resident === null ) {
            return
        }

        val town = resident.town
        if ( town === null ) {
            Message.error(player, "You are not a member of a town")
            return
        }

        val nation = town.nation
        if ( nation === null ) {
            Message.error(player, "You are not a member of a nation")
            return
        }

        val destinationName = args[1]
        val destinationTown = Nodes.getTownFromName(destinationName)
        if ( destinationTown === null ) {
            Message.error(player, "Destination town does not exist: ${destinationName}")
            return
        }

        if ( destinationTown === town ) {
            Message.error(player, "Destination town is your town, use /town spawn")
            return
        }

        val destinationNation = destinationTown.nation
        if ( nation !== destinationNation ) {
            Message.error(player, "Destination town is not in the same nation: ${destinationName}")
            return
        }
        
        // already teleporting
        if ( resident.teleportThread !== null ) {
            Message.error(player, "You are already trying to teleport")
            return
        }

        // pay item cost to teleport
        if ( Config.nationTownTeleportCost.size > 0 ) {
            val inventory = player.getInventory()
            for ( (material, amount) in Config.nationTownTeleportCost ) {
                val items = ItemStack(material)
                if ( !inventory.containsAtLeast(items, amount) ) {
                    Message.error(player, "You do not have required payment to teleport: ${Config.nationTownTeleportCostString}")
                    return
                }
            }

            // subtract cost
            for ( (material, amount) in Config.nationTownTeleportCost ) {
                val items = ItemStack(material, amount)
                inventory.removeItem(items)
            }
        }

        // location destination
        val destination = destinationTown.spawnpoint

        // ticks before teleport timer runs
        val teleportTimerTicks: Long = Math.max(0, Config.townSpawnTime * 20).toLong()

        resident.isTeleportingToNationTown = true

        resident.teleportThread = Bukkit.getScheduler().runTaskLaterAsynchronously(Nodes.plugin!!, object: Runnable {
            override fun run() {
                Bukkit.getScheduler().runTask(Nodes.plugin!!, object: Runnable {
                    override fun run() {
                        player.teleport(destination)
                        resident.teleportThread = null
                        resident.isTeleportingToNationTown = false
                    }
                })
            }
        }, teleportTimerTicks)

        if ( teleportTimerTicks > 0 ) {
            Message.print(player, "Teleporting to ${destinationName} in ${Config.townSpawnTime} seconds. Don't move...")
        }
    }
}