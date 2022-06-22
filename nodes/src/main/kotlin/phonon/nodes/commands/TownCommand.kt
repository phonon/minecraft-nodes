/**
 * /town (/s) command
 */

package phonon.nodes.commands

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import phonon.nodes.Config
import phonon.nodes.Message
import phonon.nodes.Nodes
import phonon.nodes.WorldMap
import phonon.nodes.constants.*
import phonon.nodes.objects.Coord
import phonon.nodes.objects.Resident
import phonon.nodes.objects.Town
import phonon.nodes.utils.sanitizeString
import phonon.nodes.utils.string.filterByStart
import phonon.nodes.utils.string.filterResident
import phonon.nodes.utils.string.filterTown
import phonon.nodes.utils.string.filterTownResident
import phonon.nodes.utils.stringInputIsValid

// list of all subcommands, used for onTabComplete
private val SUBCOMMANDS: List<String> = listOf(
    "help",
    "create",
    "new",
    "delete",
    "disband",
    "officer",
    "promote",
    "demote",
    "leader",
    "apply",
    "join",
    "invite",
    "accept",
    "deny",
    "reject",
    "leave",
    "kick",
    "spawn",
    "setspawn",
    "list",
    "info",
    "online",
    "color",
    "claim",
    "unclaim",
    "income",
    "prefix",
    "suffix",
    "rename",
    "map",
    "minimap",
    "permissions",
    "protect",
    "trust",
    "untrust",
    "capital",
    "annex",
    "outpost"
)

private val OUTPOST_SUBCOMMANDS: List<String> = listOf(
    "list",
    "setspawn"
)

// town permissions types
private val PERMISSIONS_TYPES: List<String> = listOf(
    "build",
    "destroy",
    "interact",
    "chests",
    "items",
    "income"
)

// town permissions types
private val PERMISSIONS_GROUPS: List<String> = listOf(
    "town",
    "nation",
    "ally",
    "outsider",
    "trusted"
)

// town permissions flag
private val PERMISSIONS_FLAGS: List<String> = listOf(
    "allow",
    "deny"
)


// ==================================================
// Constants for /t map
// 
// symbols
val SHADE = "\u2592"      // medium shade
val HOME = "\u2588"       // full solid block
val CORE = "\u256B"       // core chunk H
val CONQUERED0 = "\u2561" // captured chunk
val CONQUERED1 = "\u255F" // other chunk flag symbol
val SPACER = "\u17f2"     // spacer

val MAP_STR_BEGIN = "    "

val MAP_STR_END = arrayOf(
    "",
    "       ${ChatColor.GOLD}N",
    "     ${ChatColor.GOLD}W + E",
    "       ${ChatColor.GOLD}S",
    "",
    "  ${ChatColor.GRAY}${SHADE}${ChatColor.DARK_GRAY}${SHADE} ${ChatColor.GRAY}- Unclaimed",
    "  ${ChatColor.GREEN}${SHADE}${ChatColor.DARK_GREEN}${SHADE} - Town",
    "  ${ChatColor.YELLOW}${SHADE}${ChatColor.GOLD}${SHADE} - Neutral",
    "  ${ChatColor.AQUA}${SHADE}${ChatColor.DARK_AQUA}${SHADE} - Ally",
    "  ${ChatColor.RED}${SHADE}${ChatColor.DARK_RED}${SHADE} - Enemy",
    "",
    "  ${ChatColor.WHITE}${HOME} - Home territory",
    "  ${ChatColor.WHITE}${CORE} - Core chunk",
    "  ${ChatColor.WHITE}${CONQUERED0},${CONQUERED1} - Captured",
    "",
    "",
    "",
    ""
)
// ==================================================


public class TownCommand : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, cmd: Command, commandLabel: String, args: Array<String>): Boolean {
        
        val player = if ( sender is Player ) sender else null
    
        // no args, print current town info
        if ( args.size == 0 ) {
            if ( player != null ) {
                // print player's town info
                val resident = Nodes.getResident(player)
                if ( resident != null && resident.town != null ) {
                    resident.town!!.printInfo(player)
                }
                Message.print(player, "Use \"/town help\" to view commands")
            }
            return true
        }

        // parse subcommand
        when ( args[0].lowercase() ) {
            "help" -> printHelp(sender)
            "create" -> createTown(player, args)
            "new" -> createTown(player, args)
            "delete" -> deleteTown(player)
            "disband" -> deleteTown(player)
            "officer" -> setOfficer(player, args, null)
            "promote" -> setOfficer(player, args, true)
            "demote" -> setOfficer(player, args, false)
            "leader" -> setLeader(player, args)
            "apply" -> appToTown(player, args)
            "join" -> appToTown(player, args)
            "invite" -> invite(player, args)
            "accept" -> accept(player, args)
            "deny" -> deny(player, args)
            "reject" -> deny(player, args)
            "leave" -> leaveTown(player)
            "kick" -> kickFromTown(player, args)
            "spawn" -> goToSpawn(player, args)
            "setspawn" -> setSpawn(player)
            "list" -> listTowns(player)
            "info" -> getInfo(player, args)
            "online" -> getOnline(player, args)
            "color" -> setColor(player, args)
            "claim" -> claimTerritory(player)
            "unclaim" -> unclaimTerritory(player)
            "income" -> getIncome(player)
            "prefix" -> prefix(player, args)
            "suffix" -> suffix(player, args)
            "rename" -> renameTown(player, args)
            "map" -> printMap(player, args)
            "minimap" -> minimap(player, args)
            "perms",
            "permissions" -> setPermissions(player, args)
            "protect" -> protectChests(player, args)
            "trust" -> trustPlayer(player, args, true)
            "untrust" -> trustPlayer(player, args, false)
            "capital" -> setCapital(player, args)
            "annex" -> annexTerritory(player, args)
            "outpost" -> manageOutpost(sender, args)
            else -> { Message.error(sender, "Invalid command, use /town help") }
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
            return filterByStart(SUBCOMMANDS, args[0])
        }
        // match each subcommand format
        else if ( args.size > 1 ) {
            // handle specific subcommands
            when ( args[0].lowercase() ) {

                // /town [subcommand] [resident]
                "officer",
                "promote",
                "demote",
                "leader" -> {
                    if ( args.size == 2 ) {
                        val town = Nodes.getResident(player)?.town
                        if ( town != null ) {
                            return filterTownResident(town, args[1])
                        }
                    }
                }
                
                // /town [subcommand] [player]
                "invite" -> {
                    if ( args.size == 2 ) {
                        return filterResident(args[1])
                    }
                }

                // /town [subcommand] [town]
                "apply",
                "join",
                "accept",
                "reject",
                "deny" -> {
                    if ( args.size == 2 ) {
                        return filterTown(args[1])
                    }
                }

                // /town [subcommand] [resident]
                "kick",
                "trust",
                "untrust",
                "prefix",
                "suffix" -> {
                    if ( args.size == 2 ) {
                        val town = Nodes.getResident(player)?.town
                        if ( town != null ) {
                            return filterTownResident(town, args[1])
                        }
                    }
                }

                // /town info name
                "info" -> {
                    if ( args.size == 2 ) {
                        return filterTown(args[1])
                    }
                }

                // /town online name
                "online" -> {
                    if ( args.size == 2 ) {
                        return filterTown(args[1])
                    }
                }
                
                // /town permissions [type] [group] [flag]
                "permissions" -> {
                    if ( args.size == 2 ) {
                        return filterByStart(PERMISSIONS_TYPES, args[1])
                    }
                    else if ( args.size == 3 ) {
                        return filterByStart(PERMISSIONS_GROUPS, args[2])
                    }
                    else if ( args.size == 4 ) {
                        return filterByStart(PERMISSIONS_FLAGS, args[3])
                    }
                }

                // chest protection
                "protect" -> {
                    if ( args.size == 2 ) {
                        return listOf("show")
                    }
                }

                // spawn command
                "spawn" -> {
                    if ( args.size == 2 ) {
                        val town = Nodes.getResident(player)?.town
                        if ( town != null ) {
                            return filterByStart(town.outposts.keys.toList(), args[1])
                        }
                    }
                }

                // outpost subcommand
                "outpost" -> {
                    if ( args.size == 2 ) {
                        return filterByStart(OUTPOST_SUBCOMMANDS, args[1])
                    }
                }
            }
        }
        
        return listOf()
    }

    private fun printHelp(sender: CommandSender?) {
        Message.print(sender, "${ChatColor.BOLD}[Nodes] Town commands:")
        Message.print(sender, "/town create${ChatColor.WHITE}: Create town with name at location")
        Message.print(sender, "/town delete${ChatColor.WHITE}: Delete your town")
        Message.print(sender, "/town promote${ChatColor.WHITE}: Give officer rank to resident")
        Message.print(sender, "/town demote${ChatColor.WHITE}: Remove officer rank from resident")
        Message.print(sender, "/town apply${ChatColor.WHITE}: Apply to join a town")
        Message.print(sender, "/town invite${ChatColor.WHITE}: Invite a player to your town")
        Message.print(sender, "/town leave${ChatColor.WHITE}: Leave your town")
        Message.print(sender, "/town kick${ChatColor.WHITE}: Kick player from your town")
        Message.print(sender, "/town spawn${ChatColor.WHITE}: Teleport to your town spawnpoint")
        Message.print(sender, "/town setspawn${ChatColor.WHITE}: Set a new town spawnpoint")
        Message.print(sender, "/town list${ChatColor.WHITE}: List all towns")
        Message.print(sender, "/town info${ChatColor.WHITE}: View town details")
        Message.print(sender, "/town online${ChatColor.WHITE}: View town's online players")
        Message.print(sender, "/town color${ChatColor.WHITE}: Set town color on map")
        Message.print(sender, "/town claim${ChatColor.WHITE}: Claim territory at current location")
        Message.print(sender, "/town unclaim${ChatColor.WHITE}: Unclaim territory at current location")
        Message.print(sender, "/town prefix${ChatColor.WHITE}: Set player name prefix")
        Message.print(sender, "/town suffix${ChatColor.WHITE}: Set player name suffix")
        Message.print(sender, "/town rename${ChatColor.WHITE}: Rename town")
        Message.print(sender, "/town map${ChatColor.WHITE}: View world map")
        Message.print(sender, "/town minimap${ChatColor.WHITE}: Toggle sidebar world minimap")
        Message.print(sender, "/town permissions${ChatColor.WHITE}: Set town protection permissions")
        Message.print(sender, "/town protect${ChatColor.WHITE}: Protect town chests")
        Message.print(sender, "/town trust${ChatColor.WHITE}: Mark player as trusted")
        Message.print(sender, "/town untrust${ChatColor.WHITE}: Remove player from trusted")
        Message.print(sender, "/town capital${ChatColor.WHITE}: Set your town's home territory")
        Message.print(sender, "/town annex${ChatColor.WHITE}: Annex an occupied territory")
        Message.print(sender, "/town outpost${ChatColor.WHITE}: Town outpost commands")
        return
    }

    /**
     * @command /town create [name]
     * Create a new town with the specified name at location.
     */
    private fun createTown(player: Player?, args: Array<String>) {
        if ( player == null ) {
            return
        }

        if ( args.size == 1 ) {
            Message.print(player, "Usage: ${ChatColor.WHITE}/town create [name]")
            return
        }
        
        // do not allow during war
        if ( !Config.canCreateTownDuringWar && Nodes.war.enabled == true ) {
            Message.error(player, "Cannot create towns during war")
            return
        }

        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }

        // check if player has cooldown
        if ( resident.townCreateCooldown > 0 ) {
            val remainingTime = resident.townCreateCooldown
            val remainingTimeString = if ( remainingTime > 0 ) {
                val hour: Long = remainingTime/3600000L
                val min: Long = 1L + (remainingTime - hour * 3600000L)/60000L
                "${hour}hr ${min}min"
            }
            else {
                "0hr 0min"
            }

            Message.error(player, "You cannot create another town for: ${remainingTimeString} ")
            return
        }
        
        val name = args[1]
        if ( !stringInputIsValid(name) ) {
            Message.error(player, "Invalid town name")
            return
        }

        val territory = Nodes.getTerritoryFromPlayer(player)
        if ( territory == null ) {
            Message.error(player, "This chunk has no territory")
            return
        }

        val result = Nodes.createTown(sanitizeString(name), territory, resident)
        if ( result.isSuccess ) {
            Message.broadcast("${ChatColor.BOLD}${player.name} has created the town \"${name}\"")

            // check how much player is over town claim limit
            val overClaimsPenalty: Int = Math.max(0, Config.initialOverClaimsAmountScale * (territory.cost - Config.townInitialClaims))
            if ( overClaimsPenalty > 0 ) {
                Message.print(player, "${ChatColor.DARK_RED}Your town is over the initial town claim amount ${Config.townInitialClaims}: you are receiving a -${overClaimsPenalty} starting power penalty")
            }
        }
        else {
            when ( result.exceptionOrNull() ) {
                ErrorTownExists -> Message.error(player, "Town \"${name}\" already exists")
                ErrorPlayerHasTown -> Message.error(player, "You already belong to a town")
                ErrorTerritoryOwned -> Message.error(player, "Territory is already claimed by a town")
            }
        }
    }

    /**
     * @command /town delete
     * Delete your town. Town leaders only.
     */
    private fun deleteTown(player: Player?) {
        if ( player == null ) {
            return
        }

        // check if player is town leader
        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }

        val town = resident.town
        if ( town == null ) {
            Message.error(player, "You are not a member of a town")
            return
        }

        val leader = town.leader
        if ( resident !== leader ) {
            Message.error(player, "You are not the town leader")
            return
        }

        val nation = town.nation
        if ( nation !== null && town === nation.capital ) {
            Message.error(player, "You cannot destroy your town as the nation capital, use /n delete first")
            return
        }

        // do not allow during war
        if ( !Config.canDestroyTownDuringWar && Nodes.war.enabled == true ) {
            Message.error(player, "Cannot delete your town during war")
            return
        }

        Nodes.destroyTown(town)

        // add player penalty for destroying town
        Nodes.setResidentTownCreateCooldown(resident, Config.townCreateCooldown)

        Message.broadcast("${ChatColor.DARK_RED}${ChatColor.BOLD}The town \"${town.name}\" has been destroyed...")
    }

    /**
     * @command /town promote [name]
     * Makes player a town officer.
     * 
     * @command /town demote [name]
     * Removes player from town officers.
     */
    private fun setOfficer(player: Player?, args: Array<String>, toggle: Boolean?) {
        if ( player == null || args.size < 2 ) {
            Message.error(player, "Usage: /t promote/demote [player]")
            return
        }

        // check if player is town leader
        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }

        val town = resident.town
        if ( town == null ) {
            Message.error(player, "You are not a member of a town")
            return
        }

        val leader = town.leader
        if ( resident !== leader ) {
            Message.error(player, "You are not the town leader")
            return
        }

        // get other resident
        val target = Nodes.getResidentFromName(args[1])
        if ( target === null ) {
            Message.error(player, "Player not found")
            return
        }
        if ( target === resident ) {
            Message.error(player, "You are already the town leader")
            return
        }

        val targetTown = target.town
        if ( targetTown !== town ) {
            Message.error(player, "Player is not in this town")
            return
        }

        val targetPlayer = target.player()

        // if null, toggle officer
        if ( toggle === null ) {
            if ( town.officers.contains(target) ) {
                Nodes.townRemoveOfficer(town, target)
                Message.print(player, "Removed ${target.name} from town officers")

                if ( targetPlayer !== null ) {
                    Message.error(targetPlayer, "You are no longer a town officer")
                }
            }
            else {
                Nodes.townAddOfficer(town, target)
                Message.print(player, "Made ${target.name} a town officer")

                if ( targetPlayer !== null ) {
                    Message.print(targetPlayer, "You are now a town officer")
                }
            }
        }
        // add officer
        else if ( toggle === true ) {
            if ( !town.officers.contains(target) ) {
                Nodes.townAddOfficer(town, target)
                Message.print(player, "Made ${target.name} a town officer")

                if ( targetPlayer !== null ) {
                    Message.print(targetPlayer, "You are now a town officer")
                }
            }
        }
        // remove officer
        else if ( toggle === false ) {
            if ( town.officers.contains(target) ) {
                Nodes.townRemoveOfficer(town, target)
                Message.print(player, "Removed ${target.name} from town officers")

                if ( targetPlayer !== null ) {
                    Message.error(targetPlayer, "You are no longer a town officer")
                }
            }
        }
        
    }

    /**
     * @command /town leader [name]
     * Give town leadership to another player in town.
     */
    private fun setLeader(player: Player?, args: Array<String>) {
        if ( player == null || args.size < 2 ) {
            Message.error(player, "Usage: /t leader [player]")
            return
        }

        // check if player is town leader
        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }

        val town = resident.town
        if ( town == null ) {
            Message.error(player, "You are not a member of a town")
            return
        }

        val leader = town.leader
        if ( resident !== leader ) {
            Message.error(player, "You are not the town leader")
            return
        }

        // get other resident
        val target = Nodes.getResidentFromName(args[1])
        if ( target === null ) {
            Message.error(player, "This player does not exist")
            return
        }
        if ( target === resident ) {
            Message.error(player, "You are already the town leader")
            return
        }

        val targetTown = target.town
        if ( targetTown !== town ) {
            Message.error(player, "Player is not in this town")
            return
        }

        Nodes.townSetLeader(town, target)
        Message.print(player, "You made ${target.name} the new leader of ${town.name}")
        
        val targetPlayer = target.player()
        if ( targetPlayer !== null ) {
            Message.print(targetPlayer, "You are now the leader of ${town.name}")
        }
    }

    /**
     * @command /town apply [town]
     * Ask to join a town.
     */
    private fun appToTown(player: Player?, args: Array<String>) {
        if (player == null) {
            return
        }

        if ( args.size != 2) {
            Message.print(player, "Usage: /town apply [town]")
            return
        }

        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }

        if ( resident.town != null ) {
            Message.error(player, "You are already a member of a town")
            return
        }

        val town = Nodes.getTownFromName(args[1])
        if ( town == null ) {
            Message.error(player,"That town does not exist")
            return
        }

        if ( town.isOpen == true ) {
            Nodes.addResidentToTown(town,resident)
            Message.print(player, "You are now a resident of ${town.name}!")
            return
        }

        if ( town.applications.containsKey(resident) ) {
            Message.error(player, "You have already applied to ${town.name}")
            return
        }

        val approvers: ArrayList<Player> = ArrayList()
        Bukkit.getPlayer(town.leader!!.name)?.let { player ->
            approvers.add(player)
        }
        town.officers.forEach() { officer ->
            Bukkit.getPlayer(officer.name)?.let { player ->
                approvers.add(player)
            }
        }

        if ( approvers.isEmpty() ) {
            Message.error(player, "There are no officers online from ${town.name} to receive your application")
            return
        }

        approvers.forEach() { approver ->
            Message.print(approver, "${resident.name} has applied to join to your town. \nType \"/t accept\" to let them in or \"/t reject\" to refuse the offer.")
        }
        Message.print(player, "Your application has been sent")

        town.applications.put(resident,
                Bukkit.getScheduler().runTaskLaterAsynchronously(Nodes.plugin!!, object: Runnable {
                    override fun run() {
                        Bukkit.getScheduler().runTask(Nodes.plugin!!, object: Runnable {
                            override fun run() {
                                if ( resident.town == null ) {
                                    Message.print(player, "No one in ${town.name} responded to your application!")
                                    town.applications.remove(resident)
                                }
                            }
                        })
                    }
                }, 1200)
        )
    }

    /**
     * @command /town invite [player]
     * Invite a player to join your town. Town leader and officers only.
     */
    private fun invite(player: Player?, args: Array<String>) {
        if (player == null) {
            return
        }

        if ( args.size != 2) {
            Message.print(player, "Usage: /town invite [player]")
            return
        }

        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }
        val town = resident.town
        if ( town == null ) {
            Message.error(player, "You are not a member of a town")
            return
        }

        val invitee: Player? = Bukkit.getPlayer(args[1])
        if ( invitee == null ) {
            Message.error(player, "That player is not online")
            return
        } else if ( invitee == player ) {
            Message.error(player, "You're already in your town")
            return
        }

        val inviteeResident = Nodes.getResident(invitee)
        if ( inviteeResident == null ) {
            return
        }
        if ( inviteeResident.invitingTown == town) {
            Message.error(player, "This player has already been invited to the town")
            return
        } else if ( inviteeResident.invitingTown != null) {
            Message.error(player, "This player is considering another town invitation")
            return
        }
        val inviteeTown = inviteeResident.town
        if ( inviteeTown != null ) {
            Message.error(player, "This player is already a member of a town")
            return
        }

        if ( town.leader === resident || town.officers.contains(resident) ) {
            Message.print(player, "${invitee.name} has been invited to your town.")
            Message.print(invitee, "You have been invited to become a member of ${town.name}.\nType \"/t accept\" to join the town or \"/t reject\" to refuse the offer.")
            inviteeResident.invitingTown = town
            inviteeResident.invitingPlayer = player
            inviteeResident.inviteThread = Bukkit.getScheduler().runTaskLaterAsynchronously(Nodes.plugin!!, object: Runnable {
                override fun run() {
                    Bukkit.getScheduler().runTask(Nodes.plugin!!, object: Runnable {
                        override fun run() {
                            if ( inviteeResident.invitingPlayer == player ) {
                                Message.print(player, "${invitee.name} didn't respond to your town invitation!")
                                inviteeResident.invitingTown = null
                                inviteeResident.invitingPlayer = null
                                inviteeResident.inviteThread = null
                            }
                        }
                    })
                }
            }, 1200)
        } else {
            Message.error(player, "You are not allowed to invite new members")
        }
    }

    private fun accept(player: Player?, args: Array<String>) {
        if ( player == null ) {
            return
        }

        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }

        val town = resident.town
        if ( town == null ) {
            if ( resident.invitingTown == null ) {
                Message.error(player,"You have not been invited to any town or your invitation expired")
                return
            }

            Message.print(player,"You are now a member of ${resident.invitingTown?.name}! Type \"/t spawn\" to teleport to your new town.")
            Message.print(resident.invitingPlayer, "${resident.name} has accepted your invitation!")

            Nodes.addResidentToTown(resident.invitingTown!!, resident)
            resident.invitingTown = null
            resident.invitingPlayer = null
            resident.inviteThread = null
        } else {
            if ( town.leader != resident && !town.officers.contains(resident) ) {
                Message.error(player, "You aren't allowed to consider town applications")
                return
            }

            if ( town.applications.isEmpty() ) {
                Message.error(player, "There are no active applications")
                return
            }

            var applicant: Resident = resident
            if ( town.applications.size == 1 ) {
                town.applications.forEach { k, v ->
                    applicant = k
                }
                if ( args.size > 1 && args[1].lowercase() != applicant.name.lowercase()) {
                    Message.error(player, "That player has not applied or their application has expired")
                    return
                }
            } else {
                if ( args.size == 1) {
                    val applicantsString = town.applications.map {application -> application.key.name}.joinToString(", ")
                    Message.print(player, "There are multiple town applications. Please use \"/town accept [player]\".\nCurrent applicants: ${applicantsString}")
                    return
                }

                applicant = Nodes.getResidentFromName(args[1])!!
                if ( !town.applications.containsKey(applicant!!)) {
                    Message.error(player, "That player has not applied or their application has expired")
                    return
                }
            }

            Message.print(player, "${applicant.name} has been accepted into your town!")
            val applicantPlayer = Bukkit.getPlayer(applicant.name)
            if ( applicantPlayer != null ) {
                Message.print(applicantPlayer, "You have been accepted into ${town.name}!")
            }

            Nodes.addResidentToTown(town, applicant)
            town.applications.remove(applicant)
        }
    }

    private fun deny(player: Player?, args: Array<String>) {
        if ( player == null ) {
            return
        }

        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }

        val town = resident.town
        if ( town == null ) {
            if ( resident.invitingTown == null ) {
                Message.error(player,"You have not been invited to any town or your invitation expired")
                return
            }

            Message.print(player,"You have rejected the invitation to join ${resident.invitingTown?.name}")
            Message.print(resident.invitingPlayer, "${resident.name} has rejected your invitation!")
            resident.invitingTown = null
            resident.invitingPlayer = null
            resident.inviteThread = null
        } else {
            if ( town.leader != resident && !town.officers.contains(resident) ) {
                Message.error(player, "You aren't allowed to consider town applications")
                return
            }

            if ( town.applications.isEmpty() ) {
                Message.error(player, "There are no active applications")
                return
            }

            var applicant: Resident = resident
            if ( town.applications.size == 1 ) {
                town.applications.forEach { k, v ->
                    applicant = k
                }
                if ( args.size > 1 && args[1] != applicant.name) {
                    Message.error(player, "That player has not applied or their application has expired")
                    return
                }
            } else {
                if ( args.size == 1) {
                    val applicantsString = town.applications.map {application -> application.key.name}.joinToString(", ")
                    Message.print(player, "There are multiple town applications. Please use \"/town accept [player]\".\nCurrent applicants: ${applicantsString}")
                    return
                }

                applicant = Nodes.getResidentFromName(args[1])!!
                if ( !town.applications.containsKey(applicant!!)) {
                    Message.error(player, "That player has not applied or their application has expired")
                    return
                }
            }

            Message.print(player, "${applicant.name} has been denied residence in your town!")
            val applicantPlayer = Bukkit.getPlayer(applicant.name)
            if ( applicantPlayer != null ) {
                Message.print(applicantPlayer, "Your application to ${town.name} has been rejected!")
            }

            town.applications.remove(applicant)
        }
    }

    /**
     * @command /town leave
     * Abandon membership in your town.
     */
    private fun leaveTown(player: Player?) {
        if ( player == null ) {
            return
        }

        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }

        val town = resident.town
        if ( town == null ) {
            Message.error(player, "You are not a member of a town")
            return
        }

        if ( town.leader == resident ) {
            Message.error(player, "You must transfer leadership before leaving the town")
            return
        }

        // do not allow during war?
        if ( !Config.canLeaveTownDuringWar && Nodes.war.enabled == true ) {
            Message.error(player, "Cannot leave your town during war")
            return
        }
        
        Message.print(player,"You have left ${town.name}")
        Nodes.removeResidentFromTown(town,resident)
    }

    /**
     * @command /town kick [player]
     * Remove another player from your town. Town leader and officers only.
     */
    private fun kickFromTown(player: Player?, args: Array<String>) {
        if ( player === null || args.size < 2 ) {
            Message.error(player, "Usage: /t kick [player]")
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

        // check if player is leader or officer
        val leader = town.leader
        if ( resident !== leader && !town.officers.contains(resident) ) {
            Message.error(player, "Only leaders and officers can kick players")
            return
        }

        // get other resident
        val target = Nodes.getResidentFromName(args[1])
        if ( target === null ) {
            Message.error(player, "Player not found")
            return
        }

        val targetTown = target.town
        if ( targetTown !== town ) {
            Message.error(player, "Player is not in this town")
            return
        }

        // cannot kick leaders or officers
        if ( target === leader || town.officers.contains(target) ) {
            Message.error(player, "You cannot kick the leader or other officers")
            return
        }

        Message.print(player, "You have kicked ${target.name} from the town")

        val targetPlayer = target.player()
        if ( targetPlayer !== null ) {
            Message.print(targetPlayer, "${ChatColor.DARK_RED}You have been kicked from ${town.name}")
        }
        
        Nodes.removeResidentFromTown(town, target)
    }

    /**
     * @command /town spawn
     * Teleport to your town's main spawnpoint.
     * 
     * @subcommand /town spawn [outpost]
     * Teleport to an outpost's spawn point.
     */
    private fun goToSpawn(player: Player?, args: Array<String>) {
        if ( player === null ) {
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

        // check if already trying to teleport
        if ( resident.teleportThread !== null ) {
            Message.error(player, "You are already trying to teleport")
            return
        }

        // parse spawn destination
        val destination = if ( args.size > 1 ) {
            val outpostName = args[1]
            val outpost = town.outposts.get(outpostName)
            if ( outpost !== null ) {
                // pay item cost to teleport
                if ( Config.outpostTeleportCost.size > 0 ) {
                    val inventory = player.getInventory()
                    for ( (material, amount) in Config.outpostTeleportCost ) {
                        val items = ItemStack(material)
                        if ( !inventory.containsAtLeast(items, amount) ) {
                            Message.error(player, "You do not have required payment to teleport to an outpost: ${Config.outpostTeleportCostString}")
                            return
                        }
                    }

                    // subtract cost
                    for ( (material, amount) in Config.outpostTeleportCost ) {
                        val items = ItemStack(material, amount)
                        inventory.removeItem(items)
                    }

                    Message.print(player, "Teleporting to an outpost, this will cost: ${Config.outpostTeleportCostString}")
                }

                resident.isTeleportingToOutpost = true
                outpost.spawn
            }
            else {
                if ( town.outposts.size > 0 ) {
                    Message.error(player, "Town does not have outpost named: ${outpostName}")
                    Message.error(player, "Available outposts: ${town.outposts.keys}")
                }
                else {
                    Message.error(player, "Your town has no outposts")
                }

                return
            }
        }
        // go to main town spawn
        else {
            resident.isTeleportingToOutpost = false
            town.spawnpoint
        }

        // ticks before teleport timer runs
        var teleportTimerTicks = Math.max(0.0, Config.townSpawnTime * 20.0)

        // multiplier during war and if home occupied
        if ( Nodes.war.enabled && Nodes.getTerritoryFromId(town.home)?.occupier !== null ) {
            Message.error(player, "${ChatColor.BOLD}Your home is occupied, town spawn will take much longer...")
            teleportTimerTicks *= Config.occupiedHomeTeleportMultiplier
        }

        resident.teleportThread = Bukkit.getScheduler().runTaskLaterAsynchronously(Nodes.plugin!!, object: Runnable {
            override fun run() {
                Bukkit.getScheduler().runTask(Nodes.plugin!!, object: Runnable {
                    override fun run() {
                        player.teleport(destination)
                        resident.teleportThread = null
                        resident.isTeleportingToOutpost = false
                    }
                })
            }
        }, teleportTimerTicks.toLong())

        if ( teleportTimerTicks > 0 ) {
            val seconds = teleportTimerTicks.toInt() / 20

            if ( resident.isTeleportingToOutpost ) {
                Message.print(player, "Teleporting to an outpost in ${seconds} seconds. Don't move...")
            }
            else {
                Message.print(player, "Teleporting to town spawn in ${seconds} seconds. Don't move...")
            }
        }
    }

    /**
     * @command /town setspawn
     * Change your town's spawnpoint to another location in the home territory. Town leader only.
     */
    private fun setSpawn(player: Player?) {
        if ( player == null ) {
            return
        }

        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }

        val town = resident.town
        if ( town == null ) {
            Message.error(player, "You are not a member of a town")
            return
        }

        val leader = town.leader
        if ( resident !== leader && !town.officers.contains(resident) ) {
            Message.error(player, "You are not a town leader or officer")
            return
        }

        val result = Nodes.setTownSpawn(town, player.location)
        
        if ( result == true ) {
            Message.print(player, "Town spawn set to current location")
        }
        else {
            Message.error(player,"Spawn location must be within town's home territory")
        }
    }
    /**
     * @command /town list
     * View list of all established towns
     */
    private fun listTowns(player: Player?) {
        if ( player == null ) {
            return
        }

        val resident = Nodes.getResident(player)
        if ( resident == null) {
            return
        }

        Message.print(player,"${ChatColor.BOLD}Town - Population")
        val townsList = ArrayList(Nodes.towns.values)
        townsList.sortByDescending { it.residents.size }
        townsList.forEach { town ->
            Message.print(player, "${town.name}${ChatColor.WHITE} - ${town.residents.size}")
        }
    }


    /**
     * @command /town info
     * View your town's name, leader, officers, residents, and claims.
     * @subcommand /town info [town]
     * View details of another town
     */
    private fun getInfo(player: Player?, args: Array<String>) {
        if ( player == null ) {
            return
        }

        val resident = Nodes.getResident(player)
        if ( resident == null) {
            return
        }

        var town: Town? = null
        if ( args.size == 1 ) {
            if ( resident.town == null ) {
                Message.error(player, "You do not belong to a town")
                return
            }
            town = resident.town
        } else if ( args.size == 2 ) {
            if (!Nodes.towns.containsKey(args[1])) {
                Message.error(player, "That town does not exist")
                return
            }
            town = Nodes.getTownFromName(args[1])
        } else {
            Message.error(player, "Usage: /town info [town]")
            return
        }

        town?.printInfo(player)
    }

    /**
     * @command /town online
     * View your town's online players
     * @subcommand /town online [town]
     * View another town's online players
     */
    private fun getOnline(player: Player?, args: Array<String>) {
        if ( player == null ) {
            return
        }

        val resident = Nodes.getResident(player)
        if ( resident == null) {
            return
        }

        var town: Town? = null
        if ( args.size == 1 ) {
            if ( resident.town == null ) {
                Message.error(player, "You do not belong to a town")
                return
            }
            town = resident.town
        } else if ( args.size == 2 ) {
            if ( !Nodes.towns.containsKey(args[1]) ) {
                Message.error(player, "That town does not exist")
                return
            }
            town = Nodes.getTownFromName(args[1])
        } else {
            Message.error(player, "Usage: /town online [town]")
            return
        }

        if ( town == null ) {
            return
        }

        val numPlayersOnline = town.playersOnline.size
        val playersOnline = town.playersOnline.map({p -> p.name}).joinToString(", ")
        Message.print(player, "Players online in town ${town.name} [${numPlayersOnline}]: ${ChatColor.WHITE}${playersOnline}")
    }

    /**
     * @command /town color [r] [g] [b]
     * Set town territory color for dynmap. Town leader only.
     */
    private fun setColor(player: Player?, args: Array<String>) {
        if ( player == null ) {
            return
        }

        if ( args.size < 4 ) {
            Message.print(player, "Usage: ${ChatColor.WHITE}/town color [r] [g] [b]")
            return
        }

        // check if player is town leader
        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }

        val town = resident.town
        if ( town == null ) {
            return
        }

        val leader = town.leader
        if ( resident !== leader ) {
            Message.error(player,"Only town leaders can do this")
            return
        }

        // parse color
        try {
            val r = args[1].toInt().coerceIn(0, 255)
            val g = args[2].toInt().coerceIn(0, 255)
            val b = args[3].toInt().coerceIn(0, 255)
            
            Nodes.setTownColor(town, r, g, b)
            Message.print(player, "Town color set: ${ChatColor.WHITE}${r} ${g} ${b}")
        }
        catch (e: NumberFormatException) {
            Message.error(player, "Invalid color (must be [r] [g] [b] in range 0-255)")
        }
        
    }

    /**
     * @command /town claim
     * Claim a contiguous territory for your town. Town leader and officers only.
     */
    private fun claimTerritory(player: Player?) {
        if ( player == null ) {
            return
        }

        // get town from player
        val resident = Nodes.getResident(player)
        val town = resident?.town
        if ( town == null ) {
            Message.error(player, "Cannot claim without being in a town")
            return
        }
        
        if ( resident !== town.leader && !town.officers.contains(resident) ) {
            Message.error(player, "You are not a town leader or officer")
            return
        }

        // get territory from chunk and run claim process
        val loc = player.getLocation()
        val territory = Nodes.getTerritoryFromBlock(loc.x.toInt(), loc.z.toInt())
        if ( territory == null ) {
            Message.error(player, "This chunk has no territory")
            return
        }
        
        val result = Nodes.claimTerritory(town, territory)
        if ( result.isSuccess ) {
            Message.print(player, "Territory(id=${territory.id}) claimed")
        }
        else {
            when ( result.exceptionOrNull() ) {
                ErrorTooManyClaims -> Message.error(player, "Not enough claim power")
                ErrorTerritoryNotConnected -> Message.error(player, "Territory must neighbor existing claims")
                ErrorTerritoryHasClaim -> Message.error(player, "Territory is already claimed by a town")
            }
        }
    }

    /**
     * @command /town unclaim
     * Abandon your town's claim over a territory
     */
    private fun unclaimTerritory(player: Player?) {
        if ( player == null ) {
            return
        }

        // get town from player
        val resident = Nodes.getResident(player)
        val town = resident?.town
        if ( town == null ) {
            Message.error(player, "You do not belong to a town")
            return
        }
        
        if ( resident !== town.leader && !town.officers.contains(resident) ) {
            Message.error(player, "You are not a town leader or officer")
            return
        }
        
        // get territory from chunk and run claim process
        val loc = player.getLocation()
        val territory = Nodes.getTerritoryFromBlock(loc.x.toInt(), loc.z.toInt())
        if ( territory == null ) {
            Message.error(player, "This chunk has no territory")
            return
        }
        
        val result = Nodes.unclaimTerritory(town, territory)
        if ( result.isSuccess ) {
            Message.print(player, "Territory(id=${territory.id}) unclaimed, claim power used will regenerate over time")
        }
        else {
            when ( result.exceptionOrNull() ) {
                ErrorTerritoryNotInTown -> Message.error(player, "Territory not owned by town")
                ErrorTerritoryIsTownHome -> Message.error(player, "Cannot unclaim home territory")
            }
        }
    }

    /**
     * @command /town income
     * Collect income from territory bonuses. Town leader and officers only.
     */
    // TODO: check if player is leader or officer to collect income
    private fun getIncome(player: Player?) {
        if ( player == null ) {
            return
        }
        
        // get town from player
        val resident = Nodes.getResident(player)
        val town = resident?.town
        if ( town == null ) {
            Message.error(player, "You do not belong to a town")
            return
        }

        // check player permissions
        val hasPermissions = if ( resident === town.leader || town.officers.contains(resident) ) {
            true
        }
        else if ( town.permissions[TownPermissions.INCOME].contains(PermissionsGroup.TOWN) && resident.town === town ) {
            true
        }
        else if ( town.permissions[TownPermissions.INCOME].contains(PermissionsGroup.TRUSTED) && resident.town === town && resident.trusted ) {
            true
        }
        else {
            false
        }

        // open town inventory
        if ( hasPermissions ) {
            player.openInventory(Nodes.getTownIncomeInventory(town))
        }
        else {
            Message.error(player, "You do not have permissions to view town income")
        }
    }

    /**
     * @command /town prefix [prefix]
     * Set personal chat prefix
     * 
     * @subcommand /town prefix [player] [prefix]
     * Set a player's prefix (leader and officers only)
     */
    private fun prefix(player: Player?, args: Array<String>) {
        if ( player === null ) {
            return
        }

        val resident = Nodes.getResident(player)
        if ( resident === null ) {
            return
        }
        
        // reset prefix
        if ( args.size == 1 ) {            
            // print usage
            Message.error(player, "Usage: \"/town prefix [name]\" to set your prefix")
            Message.error(player, "Usage: \"/town prefix remove\" to remove your prefix")
            Message.error(player, "Usage: \"/town prefix [player] [name]\" to set a player's prefix")
            Message.error(player, "Usage: \"/town prefix [player] remove\" to remove a player's prefix")
        }
        // setting personal prefix
        else if ( args.size == 2 ) {
            val prefix = args[1]
            if ( prefix.lowercase() == "remove" ) {
                Nodes.setResidentPrefix(resident, "")
                Message.print(player, "Removed your prefix.")
            }
            else {
                Nodes.setResidentPrefix(resident, args[1])
                Message.print(player, "Your prefix set to: ${args[1]}")
            }
        }
        // setting a player's prefix in town
        else if ( args.size > 2 ) {
            val town = resident.town
            if ( town == null ) {
                Message.error(player, "You are not a member of a town")
                return
            }

            // check if player is leader or officer
            val leader = town.leader
            if ( resident !== leader && !town.officers.contains(resident) ) {
                Message.error(player, "Only leaders and officers can set other player's prefix/suffix")
                return
            }

            // get other resident
            val target = Nodes.getResidentFromName(args[1])
            if ( target === null ) {
                Message.error(player, "Player not found")
                return
            }

            val targetTown = target.town
            if ( targetTown !== town ) {
                Message.error(player, "Player is not in this town")
                return
            }

            val prefix = args[2]
            if ( prefix.lowercase() == "remove" ) {
                Nodes.setResidentPrefix(target, "")
                Message.print(player, "Removed ${target.name} prefix.")
            }
            else {
                Nodes.setResidentPrefix(target, args[2])
                Message.print(player, "${target.name} prefix set to: ${args[2]}")
            }
        }
    }

    /**
     * @command /town suffix [suffix]
     * Set personal chat suffix
     * 
     * @subcommand /town suffix [player] [suffix]
     * Set a player's suffix (leader and officers only)
     */
    private fun suffix(player: Player?, args: Array<String>) {
        if ( player === null ) {
            return
        }

        val resident = Nodes.getResident(player)
        if ( resident === null ) {
            return
        }
        
        // reset prefix
        if ( args.size == 1 ) {
            // print usage
            Message.error(player, "Usage: \"/town suffix [name]\" to set your suffix")
            Message.error(player, "Usage: \"/town suffix remove\" to remove your suffix")
            Message.error(player, "Usage: \"/town suffix [player] [name]\" to set a player's suffix")
            Message.error(player, "Usage: \"/town suffix [player] remove\" to remove a player's suffix")
        }
        // setting personal prefix
        else if ( args.size == 2 ) {
            val prefix = args[1]
            if ( prefix.lowercase() == "remove" ) {
                Nodes.setResidentSuffix(resident, "")
                Message.print(player, "Removed your suffix.")
            }
            else {
                Nodes.setResidentSuffix(resident, args[1])
                Message.print(player, "Your suffix set to: ${args[1]}")
            }
        }
        // setting a player's prefix in town
        else if ( args.size > 2 ) {
            val town = resident.town
            if ( town == null ) {
                Message.error(player, "You are not a member of a town")
                return
            }

            // check if player is leader or officer
            val leader = town.leader
            if ( resident !== leader && !town.officers.contains(resident) ) {
                Message.error(player, "Only leaders and officers can set other player's prefix/suffix")
                return
            }

            // get other resident
            val target = Nodes.getResidentFromName(args[1])
            if ( target === null ) {
                Message.error(player, "Player not found")
                return
            }

            val targetTown = target.town
            if ( targetTown !== town ) {
                Message.error(player, "Player is not in this town")
                return
            }

            val prefix = args[2]
            if ( prefix.lowercase() == "remove" ) {
                Nodes.setResidentSuffix(target, "")
                Message.print(player, "Removed ${target.name} suffix.")
            }
            else {
                Nodes.setResidentSuffix(target, args[2])
                Message.print(player, "${target.name} suffix set to: ${args[2]}")
            }
        }
    }

    /**
     * @command /town rename [new name]
     * Rename your town. Town leader only.
     */
    private fun renameTown(player: Player?, args: Array<String>) {
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

        val town = resident.town
        if ( town == null ) {
            Message.error(player, "You do not belong to a town")
            return
        }

        if ( resident != town.leader ) {
            Message.error(player, "Only town leaders can do this")
            return
        }

        val name = args[1]
        if ( !stringInputIsValid(name) ) {
            Message.error(player, "Invalid town name")
            return
        }

        if ( town.name.lowercase() == args[1].lowercase() ) {
            Message.error(player, "Your town is already named ${town.name}")
            return
        }

        if ( Nodes.towns.containsKey(args[1]) ) {
            Message.error(player, "There is already a town with this name")
            return
        }

        Nodes.renameTown(town,name)
        Message.print(player, "Town renamed to ${town.name}!")
    }

    /**
     * @command /town map
     * Prints territory map into chat for player
     */
    private fun printMap(player: Player?, args: Array<String>) {
        if ( player == null ) {
            return
        }

        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }
        
        val loc = player.getLocation()
        val coordX = kotlin.math.floor(loc.x).toInt()
        val coordZ = kotlin.math.floor(loc.z).toInt()
        val coord = Coord.fromBlockCoords(coordX, coordZ)
        
        // minimap size
        val sizeY = 8
        val sizeX = 10

        Message.print(player, "\n${ChatColor.WHITE}--------------- Territory Map ---------------")
        for ( (i, y) in (sizeY downTo -sizeY).withIndex() ) {
            val renderedLine = WorldMap.renderLine(resident, coord, coord.z - y, coord.x - sizeX, coord.x + sizeX)
            Message.print(player, MAP_STR_BEGIN + renderedLine + MAP_STR_END[i])
        }
        Message.print(player, "")
    }

    /**
     * @command /town minimap [3|4|5]
     * Turns on/off territory chunks minimap on sidebar.
     * Optionally specify size value: 3, 4, or 5.
     */
    private fun minimap(player: Player?, args: Array<String>) {
        if ( player == null ) {
            return
        }

        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }

        // if size input, create new minimap of that size
        // note: minimap creation internally handles removing old minimaps
        if ( args.size >= 2 ) {
            val size = try {
                Math.min(5, Math.max(3, args[1].toInt()))
            } catch (e: NumberFormatException) {
                Message.error(player, "Invalid minimap size: ${args[1]}, must be number in range 3-5. Using default 5")
                5
            }
            resident.createMinimap(player, size)
            Message.print(player, "Minimap enabled (size = ${size})")
        }
        else { // toggle minimap
            if ( resident.minimap != null ) {
                resident.destroyMinimap()
                Message.print(player, "Minimap disabled")
            }
            else {
                val size = 5
                resident.createMinimap(player, size)
                Message.print(player, "Minimap enabled (size = ${size})")
            }
        }
    }

    /**
     * @command /town permissions [type] [group] [allow/deny]
     * Set permissions for interacting in town territory.
     * [type] can be: interact, build, destroy, chests, items
     * [group] can be: nation, ally, outsider.
     * Last entry is either "allow" or "deny"
     */
    private fun setPermissions(player: Player?, args: Array<String>) {
        if ( player == null ) {
            return
        }

        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }

        val town = resident.town
        if ( town == null ) {
            Message.error(player, "You do not belong to a town")
            return
        }

        if ( args.size < 4 ) {
            // print current town permissions
            Message.print(player, "Town Permissions:")
            for ( perm in enumValues<TownPermissions>() ) {
                val groups = town.permissions[perm]
                Message.print(player, "- ${perm}${ChatColor.WHITE}: ${groups}")
            }

            // print usage for leader, officers
            if ( resident === town.leader || town.officers.contains(resident) ) {
                Message.error(player, "Usage: /town permissions [type] [group] [allow/deny]")
                Message.error(player, "[type]: build, destroy, interact, chests, items, income")
                Message.error(player, "[group]: town, nation, ally, outsider, trusted")
                Message.error(player, "[allow/deny]: either \"allow\" or \"deny\"")
            }
            
            return
        }

        if ( resident !== town.leader && !town.officers.contains(resident) ) {
            Message.error(player, "Only the town leader or officers can do this")
            return
        }

        // match permissions and group
        val permissions: TownPermissions = when ( args[1].lowercase() ) {
            "build" -> TownPermissions.BUILD
            "destroy" -> TownPermissions.DESTROY
            "interact" -> TownPermissions.INTERACT
            "chests" -> TownPermissions.CHESTS
            "items" -> TownPermissions.USE_ITEMS
            "income" -> TownPermissions.INCOME
            else -> { 
                Message.error(player, "Invalid permissions type ${args[1]}. Valid options: build, destroy, interact, items, income")
                return
            }
        }

        val group: PermissionsGroup = when ( args[2].lowercase() ) {
            "town" -> PermissionsGroup.TOWN
            "nation" -> PermissionsGroup.NATION
            "ally" -> PermissionsGroup.ALLY
            "outsider" -> PermissionsGroup.OUTSIDER
            "trusted" -> PermissionsGroup.TRUSTED
            else -> { 
                Message.error(player, "Invalid permissions group ${args[2]}. Valid options: town, nation, ally, outsider, trusted")
                return
            }
        }

        // get flag state (allow/deny)
        val flag = when ( args[3].lowercase() ) {
            "allow",
            "true" -> { true }
            
            "deny",
            "false" -> { false }
            
            else -> { 
                Message.error(player, "Invalid permissions flag ${args[3]}. Valid options: allow, deny")
                return
            }
        }

        Nodes.setTownPermissions(town, permissions, group, flag)

        Message.print(player, "Set permissions for ${town.name}: ${permissions} ${group} ${flag}")
    }

    /**
     * @command /town protect
     * Toggle protecting/unprotecting chests with mouse click.
     * 
     * @subcommand /town protect show
     * Shows protected chests with particles
     */
    private fun protectChests(player: Player?, args: Array<String>) {
        if ( player == null ) {
            return
        }

        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }

        val town = resident.town
        if ( town == null ) {
            Message.error(player, "You are not a member of a town")
            return
        }
        
        if ( args.size > 1 ) {
            if ( args[1].lowercase() == "show" ) {
                Message.print(player, "Protected chests:")
                // print protected chests
                for ( block in town.protectedBlocks ) {
                    Message.print(player, "${ChatColor.WHITE}${block.type}: x: ${block.x}, y: ${block.y}, z: ${block.z}")
                }

                Nodes.showProtectedChests(town, resident)
            }
            else {
                Message.error(player, "Usage: \"/t protect\" to toggle protecting chests")
                Message.error(player, "Usage: \"/t protect show\" to show protected chests")
            }
            return
        }

        // check if player is town leader or officer
        val leader = town.leader
        if ( resident !== leader && !town.officers.contains(resident) ) {
            Message.error(player, "Only leaders and officers can protect chests")
            return
        }

        if ( resident.isProtectingChests ) {
            Nodes.stopProtectingChests(resident)
            Message.print(player, "${ChatColor.DARK_AQUA}Stopped protecting chests.")
        }
        else {
            Nodes.startProtectingChests(resident)
            player.playSound(player.location, NODES_SOUND_CHEST_PROTECT, 1.0f, 1.0f)
            Message.print(player, "Click on a chest to protect or unprotect it. Use \"/t protect\" again to stop protecting, or click a non-chest block to stop.")
        }
    }

    /**
     * @command /town trust [name]
     * Mark player in town as trusted. Leader and officers only.
     * 
     * @command /town untrust [name]
     * Mark player in town as untrusted. Leader and officers only.
     */
    private fun trustPlayer(player: Player?, args: Array<String>, trust: Boolean) {
        if ( player == null || args.size < 2 ) {
            Message.error(player, "Usage: /t trust/untrust [player]")
            return
        }

        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }

        val town = resident.town
        if ( town == null ) {
            Message.error(player, "You are not a member of a town")
            return
        }

        // check if player is leader or officer
        val leader = town.leader
        if ( resident !== leader && !town.officers.contains(resident) ) {
            Message.error(player, "Only leaders and officers can trust/untrust players")
            return
        }

        // get other resident
        val target = Nodes.getResidentFromName(args[1])
        if ( target == null ) {
            Message.error(player, "Player not found")
            return
        }

        val targetTown = target.town
        if ( targetTown !== town ) {
            Message.error(player, "Player is not in this town")
            return
        }
        
        // set player trust
        if ( trust ) {
            Nodes.setResidentTrust(target, true)
            Message.print(player, "${target.name} is now marked as trusted")
        }
        else {
            Nodes.setResidentTrust(target, false)
            Message.print(player, "${ChatColor.DARK_AQUA}${target.name} is marked as untrusted")
        }

    }

    /**
     * @command /town capital
     * Move town home territory to your current player location.
     * (This also changes town spawn location.)
     */
    private fun setCapital(player: Player?, args: Array<String>) {
        if ( player == null ) {
            return
        }

        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }

        val town = resident.town
        if ( town == null ) {
            Message.error(player, "You are not a member of a town")
            return
        }

        // check if player is leader or officer
        val leader = town.leader
        if ( resident !== leader && !town.officers.contains(resident) ) {
            Message.error(player, "Only leaders and officers can move the town's home capital territory")
            return
        }

        // check if territory belongs to town and isnt home already
        val territory = Nodes.getTerritoryFromPlayer(player)
        if ( territory == null ) {
            Message.error(player, "This region has no territory")
            return
        }
        if ( town !== territory.town ) {
            Message.error(player, "This is not your territory")
            return
        }
        if ( town.home == territory.id ) {
            Message.error(player, "This is already your home territory")
            return
        }
        if ( town.moveHomeCooldown > 0 ) {
            val remainingTime = town.moveHomeCooldown
            val remainingTimeString = if ( remainingTime > 0 ) {
                val hour: Long = remainingTime/3600000L
                val min: Long = 1L + (remainingTime - hour * 3600000L)/60000L
                "${hour}hr ${min}min"
            }
            else {
                "0hr 0min"
            }

            Message.error(player, "You cannot move the town's home territory for: ${remainingTimeString} ")
            return
        }

        // move home territory
        Nodes.setTownHomeTerritory(town, territory)
        Message.print(player, "You have moved the town's home territory to id = ${territory.id} (do not forget to update /t setspawn)")
    }

    /**
     * @command /town annex
     * Annex an occupied territory and add it to your town
     */
    private fun annexTerritory(player: Player?, args: Array<String>) {
        if ( player == null ) {
            return
        }

        if ( Config.annexDisabled ) {
            Message.error(player, "Annexing disabled")
            return
        }

        if ( !Nodes.war.enabled || !Nodes.war.canAnnexTerritories ) {
            Message.error(player, "You can only annex territories during war")
            return
        }

        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }

        val town = resident.town
        if ( town == null ) {
            Message.error(player, "You are not a member of a town")
            return
        }

        // check if player is leader or officer
        val leader = town.leader
        if ( resident !== leader && !town.officers.contains(resident) ) {
            Message.error(player, "Only leaders and officers can annex territories")
            return
        }

        // check if territory belongs to town and isnt home already
        val territory = Nodes.getTerritoryFromPlayer(player)
        if ( territory == null ) {
            Message.error(player, "This region has no territory")
            return
        }

        val territoryTown = territory.town
        if ( territoryTown === null ) {
            Message.error(player, "There is no town here")
            return
        }
        
        // check blacklist
        if ( Config.warUseBlacklist && Config.warBlacklist.contains(territoryTown.uuid) ) {
            Message.error(player, "Cannot annex this town (blacklisted)")
            return
        }
        if ( Config.useAnnexBlacklist && Config.annexBlacklist.contains(territoryTown.uuid) ) {
            Message.error(player, "Cannot annex this town (blacklisted)")
            return
        }

        // check whitelist
        if ( Config.warUseWhitelist ) {
            if ( !Config.warWhitelist.contains(territoryTown.uuid) ) {
                Message.error(player, "Cannot annex this town (not whitelisted)")
                return
            }
            else if ( Config.onlyWhitelistCanAnnex && !Config.warWhitelist.contains(town.uuid) ) {
                Message.error(player, "Cannot annex territories because your town is not white listed")
                return
            }
        }

        if ( town === territoryTown ) {
            Message.error(player, "This already your territory")
            return
        }
        if ( territory.occupier !== town ) {
            Message.error(player, "You have not occupied this territory")
            return
        }
        if ( territoryTown.home == territory.id && territoryTown.territories.size > 1 ) {
            Message.error(player, "You must annex all of this town's other territories before you can annex its home territory")
            return
        }

        val result = Nodes.annexTerritory(town, territory)
        if ( result == true ) {
            Message.print(player, "Annexed territory (id = ${territory.id})")
        }
        else {
            Message.error(player, "Failed to annex territory")
        }
    }

    // =====================================
    // town outpost management subcommands
    // 
    // general usage: /town outpost [subcommand] [args]
    // =====================================
    
    /**
     * @command /town outpost
     * Commands to manage town outposts.
     */
    private fun manageOutpost(sender: CommandSender, args: Array<String>) {
        val player = if ( sender is Player ) sender else null
        if ( player == null ) {
            printOutpostHelp(sender)
            return
        }

        if ( args.size < 2 ) {
            printOutpostHelp(sender)
        }
        else {
            // route subcommand function
            when ( args[1].lowercase() ) {
                "list" -> outpostList(player, args)
                "setspawn" -> outpostSetSpawn(player, args)
                else -> { printOutpostHelp(sender) }
            }
        }
    }

    private fun printOutpostHelp(sender: CommandSender) {
        Message.print(sender, "${ChatColor.BOLD}[Nodes] Town outpost management:")
        Message.print(sender, "/town outpost list${ChatColor.WHITE}: List town's outposts")
        Message.print(sender, "/town outpost setspawn${ChatColor.WHITE}: Set town outpost spawn to your current location")
        Message.print(sender, "Run a command with no args to see usage.")
    }
    
    /**
     * @subcommand /town outpost list
     * Print list of town's outposts.
     */
    private fun outpostList(player: Player, args: Array<String>) {
        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }

        val town = resident.town
        if ( town == null ) {
            Message.error(player, "You are not a member of a town")
            return
        }

        if ( town.outposts.size > 0 ) {
            Message.print(player, "Town outposts:")
            for ( (name, outpost) in town.outposts ) {
                val spawn = outpost.spawn
                Message.print(player, "- ${name}${ChatColor.WHITE}: Territory (id=${outpost.territory}, Spawn = (${spawn.x}, ${spawn.y}, ${spawn.z})")
            }
        }
        else {
            Message.error(player, "Town has no outposts")
        }
    }

    /**
     * @subcommand /town outpost setspawn
     * Set an outpost's spawn point. Player must be in the outpost territory.
     */
    private fun outpostSetSpawn(player: Player, args: Array<String>) {
        val resident = Nodes.getResident(player)
        if ( resident == null ) {
            return
        }

        val town = resident.town
        if ( town == null ) {
            Message.error(player, "You are not a member of a town")
            return
        }

        // check if player is leader or officer
        val leader = town.leader
        if ( resident !== leader && !town.officers.contains(resident) ) {
            Message.error(player, "Only leaders and officers can move an outpost's spawn location")
            return
        }

        // check if territory belongs to town and isnt home already
        val territory = Nodes.getTerritoryFromPlayer(player)
        if ( territory == null ) {
            Message.error(player, "This region has no territory")
            return
        }
        if ( town !== territory.town ) {
            Message.error(player, "This is not your territory")
            return
        }
        
        // match outpost to territory
        for ( outpost in town.outposts.values ) {
            if ( outpost.territory == territory.id ) {
                val result = Nodes.setOutpostSpawn(town, outpost, player.location)
                if ( result == true ) {
                    Message.print(player, "Set outpost \"${outpost.name}\" spawn to current location")
                }
                else {
                    Message.error(player, "Failed to set outpost spawn in current location")
                }
                return
            }
        }

        // failed to match, return error
        Message.error(player, "Your town has no outpost in this location")
    }
}