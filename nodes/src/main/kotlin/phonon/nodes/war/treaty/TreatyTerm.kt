/**
 * Interface and implementation for peace treaty terms
 */

package phonon.nodes.war

import java.util.EnumMap
import org.bukkit.ChatColor
import org.bukkit.inventory.ItemStack
import org.bukkit.entity.Player
import phonon.nodes.Nodes
import phonon.nodes.Message
import phonon.nodes.gui.Gui
import phonon.nodes.objects.Resident
import phonon.nodes.objects.Territory
import phonon.nodes.objects.Town
import phonon.nodes.objects.TownPair

// interface for a treaty term item
interface TreatyTerm {
    val provider: Town // offering term
    val receiver: Town // receiving term

    // runs this treaty term and affect world
    fun execute()

    // cancel this treaty term
    fun cancel()
}

// treaty term for receiver to occupy a territory
data class TreatyTermOccupation(
    override val provider: Town,
    override val receiver: Town,
    val territory: Territory
): TreatyTerm {
    
    override fun execute() {
        if ( provider === territory.town ) {
            Nodes.captureTerritory(receiver, territory)
            
            val territoryName = if ( territory.name !== "" ) "${territory.name} " else territory.name

            Message.broadcast("${ChatColor.BOLD}${receiver.name} is occupying territory ${territoryName}(id=${territory.id}) by treaty with ${provider.name}")
        }
    }

    override fun cancel() {}
}

// treaty term for provider to release a territory
data class TreatyTermRelease(
    override val provider: Town,
    override val receiver: Town,
    val territory: Territory
): TreatyTerm {
    
    override fun execute() {
        if ( provider === territory.occupier ) {
            Nodes.releaseTerritory(territory)

            val territoryName = if ( territory.name !== "" ) "${territory.name} " else territory.name

            if ( territory.town != null ) {
                Message.broadcast("${ChatColor.BOLD}${provider.name} returned captured territory ${territoryName}(id=${territory.id}) to ${territory.town?.name} by treaty with ${receiver.name}")
            }
            else {
                Message.broadcast("${ChatColor.BOLD}${provider.name} released captured territory ${territoryName}(id=${territory.id}) by treaty with ${receiver.name}")
            }
        }
    }

    override fun cancel() {}
}

// treaty term for receiver to get provider's items
// items: itemStack
// player: player who provided items
// town: town player belongs to
class TreatyTermItems(
    override val provider: Town,
    override val receiver: Town,
    val items: ItemStack,
    val player: Player?
): TreatyTerm {
    // give items to other town's /town income chest
    // gets rid of any item metadata, so only works for basic items
    override fun execute() {
        Nodes.addToIncome(receiver, items.type, items.amount)
    }

    // return items to player or /town income chest if no room
    override fun cancel() {
        if ( this.player !== null ) {
            val leftover = player.inventory.addItem(items)

            // drop remaining items at player
            val world = player.world
            val location = player.location
            for ( items in leftover.values ) {
                world.dropItem(location, items)
            }
        }
        // otherwise return to town income chest
        else {
            Nodes.addToIncome(provider, items.type, items.amount)
        }
        
    }
}