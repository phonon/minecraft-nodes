package phonon.refinery

import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

public val REFINERY_COMMANDS: List<String> = listOf(
    "help",
)

/**
 * Refinery command executor.
 */
public object RefineryCommand: CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, cmd: Command, commandLabel: String, args: Array<String>): Boolean {
        // no args, print help
        if ( args.size == 0 ) {
            this.printHelp(sender)
            return true
        }

        // parse subcommand
        val arg = args[0].lowercase()
        when ( arg ) {
            "help" -> printHelp(sender)
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
                
            }
        }
        
        return listOf()
    }

    private fun printHelp(sender: CommandSender) {
        Message.print(sender, "${ChatColor.AQUA}${ChatColor.BOLD}[nodes-refinery]")
        Message.print(sender, "${ChatColor.AQUA}/refinery help${ChatColor.WHITE}: help")
        return
    }

}
