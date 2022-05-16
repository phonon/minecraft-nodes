/**
 * Commands for declaring war on other towns, nations
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
import phonon.nodes.objects.Nation
import phonon.nodes.objects.Town
import phonon.nodes.utils.string.filterTownOrNation
import phonon.nodes.war.Truce

/**
 * @command /war
 * Declare war on a town or nation
 * 
 * @subcommand /war [town]
 * Declares war on a town
 * 
 * @subcommand /war [nation]
 * Declares war on a nation
 */
public class WarCommand : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, cmd: Command, commandLabel: String, args: Array<String>): Boolean {
        
        // no args, print help
        if ( args.size == 0 ) {
            Nodes.war.printInfo(sender, false)
            printHelp(sender)
            return true
        }

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
            Message.error(player, "You do not belong to a town")
            return true
        }

        val nation = town.nation
        if ( nation !== null && town !== nation.capital ) {
            Message.error(player, "Only the nation's capital town can declare war")
            return true
        }

        if ( resident !== town.leader && !town.officers.contains(resident) ) {
            Message.error(player, "Only the leader and officers can declare war")
            return true
        }

        // parse target
        parseTownOrNationName(player, args, town, nation)

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        if ( args.size > 0 ) {
            return filterTownOrNation(args[0])
        }
        
        return listOf()
    }

    private fun printHelp(sender: CommandSender) {
        Message.print(sender, "${ChatColor.BOLD}[Nodes] War commands:")
        Message.print(sender, "/war [town]${ChatColor.WHITE}: Declare war on a town")
        Message.print(sender, "/war [nation]${ChatColor.WHITE}: Declare war on a nation")
        return
    }

    private fun parseTownOrNationName(player: Player, args: Array<String>, town: Town, townNation: Nation?) {
        val target = args[0]

        // 1. try war on nation
        var enemyNation = Nodes.nations.get(target)
        if ( enemyNation !== null ) {
            declareWar(player, town, enemyNation.capital, townNation, enemyNation)
            return
        }

        // 2. try war on town
        //    if town has nation, auto declare war on nation
        val enemyTown = Nodes.towns.get(target)
        if ( enemyTown !== null ) {
            if ( enemyTown === town ) {
                Message.error(player, "You cannot declare war on yourself")
                return
            }

            enemyNation = enemyTown.nation
            if ( enemyNation !== null ) {
                declareWar(player, town, enemyNation.capital, townNation, enemyNation)
            }
            else {
                declareWar(player, town, enemyTown, townNation, enemyNation)
            }
            return
        }
        
        Message.error(player, "Town or nation \"${target}\" does not exist")
    }

    // tries to declare war on another town
    private fun declareWar(player: Player, town: Town, enemy: Town, townNation: Nation?, enemyNation: Nation?) {
        // cannot declare war on self
        if ( town === enemy ) {
            Message.error(player, "You cannot declare war on yourself")
            return
        }

        // cannot war allies or truce
        if ( town.allies.contains(enemy) || enemy.allies.contains(town) ) {
            Message.error(player, "You cannot declare war on an ally")
            return
        }
        if ( Truce.contains(town, enemy) ) {
            Message.error(player, "You are in a truce with ${enemy.name}")
            return
        }

        // check if already in war
        if ( town.enemies.contains(enemy) || enemy.enemies.contains(town) ) {
            Message.error(player, "You are already at war with ${enemy.name}")
            return
        }

        // make enemies
        val result = Nodes.addEnemy(town, enemy)
        if ( result.isSuccess ) {
            if ( townNation !== null ) {
                if ( enemyNation !== null ) {
                    if ( townNation === enemyNation ) {
                        Message.broadcast("${ChatColor.DARK_RED}${ChatColor.BOLD}${town.name} has declared war on ${enemy.name}!")
                    }
                    else {
                        Message.broadcast("${ChatColor.DARK_RED}${ChatColor.BOLD}${townNation.name} has declared war on ${enemyNation.name}!")
                    }
                }
                else {
                    Message.broadcast("${ChatColor.DARK_RED}${ChatColor.BOLD}${townNation.name} has declared war on ${enemy.name}!")
                }
            }
            else {
                if ( enemyNation !== null ) {
                    Message.broadcast("${ChatColor.DARK_RED}${ChatColor.BOLD}${town.name} has declared war on ${enemyNation.name}!")
                }
                else {
                    Message.broadcast("${ChatColor.DARK_RED}${ChatColor.BOLD}${town.name} has declared war on ${enemy.name}!")
                }
            }
        }

    }

}