package phonon.refinery

import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

public val REFINERY_COMMANDS: List<String> = listOf(
    "help",
    "recipe",
    "type",
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
            // TODO: open gui
            return true
        }

        // parse subcommand
        val arg = args[0].lowercase()
        when ( arg ) {
            "help" -> printHelp(sender)
            "recipe" -> printRecipeInfo(sender, args)
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
            }
        }
        
        return listOf()
    }

    private fun printHelp(sender: CommandSender) {
        Message.print(sender, "${ChatColor.BOLD}[nodes-refinery]")
        Message.print(sender, "/refinery help${ChatColor.WHITE}: help")
        Message.print(sender, "/refinery recipe [name]${ChatColor.WHITE}: print refinery recipe info")
        Message.print(sender, "/refinery type [name]${ChatColor.WHITE}: print refinery type info")
        return
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
}
