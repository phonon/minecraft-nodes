package phonon.refinery

import kotlin.system.measureNanoTime
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import phonon.nodes.Nodes

public val REFINERY_COMMANDS: List<String> = listOf(
    "help",
    "recipe",
    "reload",
    "save",
    "type",
)

public val RELOAD_SUBCOMMANDS: List<String> = listOf(
    "config",
    "world",
)

/**
 * Refinery command executor.
 */
public class RefineryCommand(
    val plugin: RefineryPlugin,
): CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, cmd: Command, commandLabel: String, args: Array<String>): Boolean {
        // no args, try to open refinery gui in player territory
        if ( args.size == 0 ) {
            openRefineryGui(sender)
            return true
        }

        // parse subcommand
        val arg = args[0].lowercase()
        when ( arg ) {
            "help" -> printHelp(sender)
            "recipe" -> printRecipeInfo(sender, args)
            "reload" -> runReload(sender, args)
            "save" -> runSave(sender, args)
            "type" -> printRefineryTypeInfo(sender, args)
            else -> {
                Message.error(sender, "Invalid /refinery command")
            }
        }

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        if ( args.size == 1 ) {
            return REFINERY_COMMANDS
        }
        // match each subcommand format
        else if ( args.size > 1 ) {
            // handle specific subcommands
            when ( args[0].lowercase() ) {
                "recipe" -> {
                    if ( args.size == 2 ) {
                        return plugin.recipeNames
                    }
                }

                "type" -> {
                    if ( args.size == 2 ) {
                        return plugin.refineryTypeNames
                    }
                }

                "reload" -> {
                    if ( args.size == 2 ) {
                        return RELOAD_SUBCOMMANDS
                    }
                }
            }
        }
        
        return listOf()
    }

    private fun printHelp(sender: CommandSender) {
        Message.print(sender, "${ChatColor.BOLD}[nodes-refinery]")
        Message.print(sender, "/refinery${ChatColor.WHITE}: open refinery gui for territory you are standing in")
        Message.print(sender, "/refinery help${ChatColor.WHITE}: help")
        Message.print(sender, "/refinery recipe [name]${ChatColor.WHITE}: print refinery recipe info")
        Message.print(sender, "/refinery type [name]${ChatColor.WHITE}: print refinery type info")
        
        Message.print(sender, "/refinery reload config|world${ChatColor.WHITE}: (admin only) reload data")
        Message.print(sender, "/refinery save${ChatColor.WHITE}: (admin only) run save")
        return
    }

    /**
     * Tries to open refinery gui for refinery in territory player is 
     * standing in.
     */
    private fun openRefineryGui(sender: CommandSender) {
        if ( sender !is Player ) {
            Message.error(sender, "Only ingame players can use this command")
            return
        }

        val player = sender
        val terr = Nodes.getTerritoryFromPlayer(player)
        if ( terr === null ) {
            Message.error(sender, "You are not standing in a territory")
            return
        }

        val refinery = plugin.territoryToRefinery[terr.id]
        if ( refinery === null ) {
            Message.error(sender, "No refinery in this territory")
            return
        }

        val town = terr.town
        if ( town !== null ) {
            val resident = Nodes.getResident(player)
            if ( resident === null || resident.town !== town ) {
                Message.error(sender, "You are not a member of this town")
                return
            }
        }

        player.openInventory(refinery.inv)
    }

    /**
     * Print refinery recipe info for player.
     */
    private fun printRecipeInfo(sender: CommandSender, args: Array<String>) {
        if ( args.size < 2 ) {
            Message.error(sender, "Invalid usage: /refinery recipe [name]")
            return
        }

        val recipeName = args[1]
        val recipe = plugin.recipes.get(recipeName)
        if ( recipe == null ) {
            Message.error(sender, "Invalid recipe name: ${recipeName}")
            return
        }

        Message.print(sender, "${ChatColor.BOLD}[nodes-refinery] Recipe: ${recipe.name}")
        Message.print(sender, "Inputs:")
        for ( input in recipe.inputs ) {
            Message.print(sender, "- ${input.type} x${input.amount}")
        }
        Message.print(sender, "Outputs:")
        for ( output in recipe.outputs ) {
            Message.print(sender, "- ${output.type} x${output.amount}")
        }
        Message.print(sender, "Time required: ${recipe.timeRequiredMillis.toDouble() / 1000.0} seconds")
    }

    /**
     * Print refinery type info for player.
     */
    private fun printRefineryTypeInfo(sender: CommandSender, args: Array<String>) {
        if ( args.size < 2 ) {
            Message.error(sender, "Invalid usage: /refinery type [name]")
            return
        }

        val typeName = args[1]
        val type = plugin.refineryTypes.get(typeName)
        if ( type == null ) {
            Message.error(sender, "Invalid refinery type name: ${typeName}")
            return
        }

        Message.print(sender, "${ChatColor.BOLD}[nodes-refinery] Refinery type: ${type.title}")
        Message.print(sender, "Resource: ${type.resourceName}")
        Message.print(sender, "Recipes:")
        for ( recipe in type.recipes ) {
            Message.print(sender, "- ${recipe.name}")
        }
    }

    /**
     * Run plugin reload, usage:
     *     /refinery reload config: just reload configs
     *     /refinery reload world: just reload refineries in world
     */
    private fun runReload(sender: CommandSender, args: Array<String>) {
        if ( sender is Player && !sender.hasPermission("refinery.admin") ) {
            Message.error(sender, "No refinery.admin permission")
            return
        }

        if ( args.size < 2 ) {
            Message.error(sender, "Invalid usage: /refinery reload config|world")
            return
        }

        val arg = args[1].lowercase()
        when ( arg ) {
            "config" -> {
                Message.print(sender, "Reloading config")
                plugin.reloadConfigAndTasks()
            }
            "world" -> {
                Message.print(sender, "Reloading world")
                plugin.reloadWorld()
            }
            else -> {
                Message.error(sender, "Invalid usage: /refinery reload config|world")
            }
        }
    }

    /**
     * Run plugin save, can use to test save time. Usage:
     *    /refinery save: synchronous save
     *    /refinery save async: asynchronous save (json saving on async thread)
     */
    private fun runSave(sender: CommandSender, args: Array<String>) {
        if ( sender is Player && !sender.hasPermission("refinery.admin") ) {
            Message.error(sender, "No refinery.admin permission")
            return
        }

        val tSave = measureNanoTime {
            if ( args.size < 2 ) {
                plugin.save()
            }
            else {
                val arg = args[1].lowercase()
                when ( arg ) {
                    "async" -> {
                        plugin.save(async = true)
                    }
                    else -> {
                        plugin.save()
                    }
                }
            }
        }

        plugin.logger.info("Saved in ${tSave.toDouble() / 1000000.0} ms")
    }
}
