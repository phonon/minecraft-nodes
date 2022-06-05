/**
 * Gui for selecting territory, callbacks with id
 * 
 * TODO: add pages to handle more territories
 */

package phonon.nodes.war

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.ChatColor
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.entity.Player
import phonon.nodes.Nodes
import phonon.nodes.gui.*
import phonon.nodes.objects.Territory
import phonon.nodes.objects.TerritoryId
import phonon.nodes.objects.Town

// constant icons

// icon for going back
val ICON_BACK = ItemIcon(
    GUI_CONCRETE[GuiColor.RED.ordinal].clone(),
    "${ChatColor.DARK_RED}${ChatColor.BOLD}Back",
    listOf()
)

val ICON_TERRITORY = ItemStack(Material.GRASS, 1)

val ICON_OCCUPIED = ItemStack(Material.DIRT, 1, 2.toShort()) // podzol

// render a territory button with territory info and callback on press
public fun TerritoryButton(
    screen: GuiWindow,
    x: Int,
    y: Int,
    terr: Territory,
    terrId: TerritoryId,
    title: String,
    icon: ItemStack,
    callback: (TerritoryId) -> Unit
) {
    val propertiesList: List<String> = listOf(
        "${ChatColor.AQUA}Size: ${ChatColor.WHITE}${terr.chunks.size}",
        "${ChatColor.AQUA}Core: ${ChatColor.WHITE}(${terr.core.x}, ${terr.core.z})",
        "${ChatColor.AQUA}Resources:"
    )
    val resourcesList: List<String> = terr.resourceNodes.map({name ->
        "${ChatColor.WHITE}- ${name}"
    })

    GuiButton(
        x,
        y,
        icon,
        title,
        propertiesList + resourcesList,
        { callback(terrId) },
    ).render(screen)
}

public class TerritorySelectGui(
    val player: Player,
    val treaty: Treaty,
    val town: Town,
    val ignored: HashSet<Int>,
    val callback: (TerritoryId) -> Unit
): GuiElement {
    
    override public fun render(screen: GuiWindow) {

        // render cancel button
        val guiButtonBack = GuiButton(8, 5, ICON_BACK, null, null, {
            treaty.setPlayerGuiView(TreatyGuiView.MAIN, player, treaty, town)
        }).render(screen)

        // index for inventory slot
        var index = 0

        // render town territories
        val terrIter = this.town.territories.iterator()
        while ( terrIter.hasNext() && index < GUI_SLOTS.size-2 ) {
            val terrId = terrIter.next()
            val terr = Nodes.getTerritoryFromId(terrId)
            
            // if occupied, skip (cannot give away occupied territories)
            if ( terr === null || terr.occupier !== null ) {
                continue
            }
            
            val slot = GUI_SLOTS[index]
            val x = slot.x
            val y = slot.y

            val title = if ( terr.name != "" ) {
                "${ChatColor.WHITE}${ChatColor.BOLD}${terr.name} (ID: ${terr.id})"
            } else {
                "${ChatColor.WHITE}${ChatColor.BOLD}ID: ${terr.id}"
            }

            // render territory button
            TerritoryButton(
                screen,
                x,
                y,
                terr,
                terr.id,
                title,
                ICON_TERRITORY,
                callback,
            )

            // track render index
            index += 1
        }

        // render occupied territories
        val occupiedIter = this.town.captured.iterator()
        while ( occupiedIter.hasNext() && index < GUI_SLOTS.size-2 ) {
            val terrId = occupiedIter.next()
            val terr = Nodes.getTerritoryFromId(terrId)

            // should never occur
            if ( terr === null || terr.occupier !== this.town ) {
                continue
            }
            
            val slot = GUI_SLOTS[index]
            val x = slot.x
            val y = slot.y

            val title = if ( terr.name != "" ) {
                "${ChatColor.GREEN}${ChatColor.BOLD}${terr.name} (ID: ${terr.id}) (Occupied)"
            } else {
                "${ChatColor.GREEN}${ChatColor.BOLD}ID: ${terr.id} (Occupied)"
            }

            // render territory button
            TerritoryButton(
                screen,
                x,
                y,
                terr,
                terr.id,
                title,
                ICON_OCCUPIED,
                callback,
            )

            // track render index
            index += 1
        }
    }
    
}
