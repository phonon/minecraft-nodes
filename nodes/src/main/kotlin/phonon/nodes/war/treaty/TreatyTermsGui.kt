/**
 * Main treaty GUI window, shows terms on both side
 */

package phonon.nodes.war

import kotlin.system.measureNanoTime
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.ChatColor
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import phonon.nodes.Nodes
import phonon.nodes.gui.*
import phonon.nodes.objects.Territory
import phonon.nodes.objects.Town

// constant array for (x,y) slots for your town's treaty side
val TOWN_TERM_SLOTS: Array<GuiSlot> = arrayOf(
    GuiSlot(0, 0), GuiSlot(1, 0), GuiSlot(2, 0), GuiSlot(3, 0), 
    GuiSlot(0, 1), GuiSlot(1, 1), GuiSlot(2, 1), GuiSlot(3, 1), 
    GuiSlot(0, 2), GuiSlot(1, 2), GuiSlot(2, 2), GuiSlot(3, 2), 
    GuiSlot(0, 3), GuiSlot(1, 3), GuiSlot(2, 3), GuiSlot(3, 3), 
    GuiSlot(0, 4), GuiSlot(1, 4), GuiSlot(2, 4), GuiSlot(3, 4)
)

// constant array of (x,y) slots for other town's treaty side
val OTHER_TERM_SLOTS: Array<GuiSlot> = arrayOf(
    GuiSlot(5, 0), GuiSlot(6, 0), GuiSlot(7, 0), GuiSlot(8, 0), 
    GuiSlot(5, 1), GuiSlot(6, 1), GuiSlot(7, 1), GuiSlot(8, 1), 
    GuiSlot(5, 2), GuiSlot(6, 2), GuiSlot(7, 2), GuiSlot(8, 2), 
    GuiSlot(5, 3), GuiSlot(6, 3), GuiSlot(7, 3), GuiSlot(8, 3), 
    GuiSlot(5, 4), GuiSlot(6, 4), GuiSlot(7, 4), GuiSlot(8, 4)
)

// constant icons

// empty slot
val ICON_EMPTY = ItemStack(Material.AIR, 1)

// icon for letting other party occupy your territory
val ICON_OCCUPY = ItemIcon(
    ItemStack(Material.DIRT, 1, 2.toShort()),
    "${ChatColor.RED}${ChatColor.BOLD}Territory Occupation",
    listOf(
        "${ChatColor.WHITE}Give territories for occupation, or transfer occupied territories",
        "${ChatColor.WHITE}${ChatColor.BOLD}Click to select territory."
    )
)

// icon for letting other party annex your territory
val ICON_ANNEX = ItemIcon(
    ItemStack(Material.GRASS, 1),
    "${ChatColor.RED}${ChatColor.BOLD}Cede Territory",
    listOf(
        "${ChatColor.WHITE}Other town will annex this territory",
        "${ChatColor.WHITE}${ChatColor.BOLD}Click to select territory."
    )
)

// icon for rejecting deal
val ICON_REJECT = ItemIcon(
    GUI_CONCRETE[GuiColor.RED.ordinal].clone(),
    "${ChatColor.DARK_RED}${ChatColor.BOLD}Reject Treaty",
    listOf(
        "${ChatColor.WHITE}Rejects and deletes this peace treaty.",
        "${ChatColor.WHITE}If you want to continue negotiating later, DO NOT PRESS THIS, instead close inventory with [Escape]."
    )
)

// icon for offering terms
val ICON_OFFER = ItemIcon(
    GUI_CONCRETE[GuiColor.GREEN.ordinal].clone(),
    "${ChatColor.GREEN}${ChatColor.BOLD}Offer Treaty",
    listOf(
        "${ChatColor.WHITE}Click to offer treaty terms.",
        "${ChatColor.WHITE}When both parties offer, treaty will be locked for final confirmation."
    )
)

// icon for revising terms
val ICON_REVISE = ItemIcon(
    GUI_CONCRETE[GuiColor.ORANGE.ordinal].clone(),
    "${ChatColor.WHITE}${ChatColor.BOLD}Revise Treaty",
    listOf(
        "${ChatColor.WHITE}Click to edit treaty terms"
    )
)

// icon for confirming terms
val ICON_CONFIRM = ItemIcon(
    GUI_CONCRETE[GuiColor.GREEN.ordinal].clone(),
    "${ChatColor.GREEN}${ChatColor.BOLD}Confirm Treaty",
    listOf(
        "${ChatColor.WHITE}Finalize and accept treaty terms",
        "${ChatColor.WHITE}When both parties confirm, treaty will execute."
    )
)

// render a territory occupation treaty term
public fun renderTreatyTermOccupation(screen: GuiWindow, x: Int, y: Int, treaty: Treaty, term: TreatyTermOccupation, cancellable: Boolean) {
    val terrId = term.territoryId
    val terr = Nodes.getTerritoryFromId(terrId)
    if ( terr === null ) {
        Nodes.logger?.severe("renderTreatyTermOccupation(): territory ${terrId} not found")
        return
    }

    val title = if ( terr.name != "" ) {
        "${ChatColor.RED}${ChatColor.BOLD}${term.receiver.name} will occupy ${terr.name} (ID: ${terr.id})"
    } else {
        "${ChatColor.RED}${ChatColor.BOLD}${term.receiver.name} will occupy ID: ${terr.id}"
    }
    
    val propertiesList: List<String> = listOf(
        "${ChatColor.AQUA}Size: ${ChatColor.WHITE}${terr.chunks.size}",
        "${ChatColor.AQUA}Core: ${ChatColor.WHITE}(${terr.core.x}, ${terr.core.z})",
        "${ChatColor.AQUA}Resources:"
    )
    val resourcesList: List<String> = terr.resourceNodes.map({name ->
        "${ChatColor.WHITE}- ${name}"
    })
    
    if ( cancellable ) {
        GuiButton(
            x,
            y,
            ItemStack(Material.DIRT, 1, 2.toShort()),
            title,
            propertiesList + resourcesList,
            { treaty.remove(term) }
        ).render(screen)
    }
    else {
        GuiLabel(
            x,
            y,
            ItemStack(Material.DIRT, 1, 2.toShort()),
            title,
            propertiesList + resourcesList
        ).render(screen)
    }
    
}

// render a territory release treaty term
public fun renderTreatyTermRelease(screen: GuiWindow, x: Int, y: Int, treaty: Treaty, term: TreatyTermRelease, cancellable: Boolean) {
    val terrId = term.territoryId
    val terr = Nodes.getTerritoryFromId(terrId)
    if ( terr === null ) {
        Nodes.logger?.severe("renderTreatyTermRelease(): territory ${terrId} not found")
        return
    }

    val title = if ( terr.name != "" ) {
        "${ChatColor.RED}${ChatColor.BOLD}${term.receiver.name} will release ${terr.name} (ID: ${terr.id})"
    } else {
        "${ChatColor.RED}${ChatColor.BOLD}${term.receiver.name} will release ID: ${terr.id}"
    }
    
    val propertiesList: List<String> = listOf(
        "${ChatColor.AQUA}Size: ${ChatColor.WHITE}${terr.chunks.size}",
        "${ChatColor.AQUA}Core: ${ChatColor.WHITE}(${terr.core.x}, ${terr.core.z})",
        "${ChatColor.AQUA}Resources:"
    )
    val resourcesList: List<String> = terr.resourceNodes.map({name ->
        "${ChatColor.WHITE}- ${name}"
    })
    
    if ( cancellable ) {
        GuiButton(
            x,
            y,
            ItemStack(Material.DIRT, 1, 2.toShort()),
            title,
            propertiesList + resourcesList,
            { treaty.remove(term) }
        ).render(screen)
    }
    else {
        GuiLabel(
            x,
            y,
            ItemStack(Material.DIRT, 1, 2.toShort()),
            title,
            propertiesList + resourcesList
        ).render(screen)
    }
}

// render treaty cede territory term
public fun renderTreatyTermItems(screen: GuiWindow, x: Int, y: Int, treaty: Treaty, term: TreatyTermItems, cancellable: Boolean) {
    if ( cancellable ) {
        GuiButton(
            x,
            y,
            term.items.clone(), // make clone to not disturb internal items
            null,
            null,
            { treaty.remove(term) }
        ).render(screen)
    }
    else {
        GuiLabel(
            x,
            y,
            term.items.clone(), // make clone to not disturb internal items
            " ",
            listOf()
        ).render(screen)
    }
}

// middle bar, 4 down
val guiMiddleBar4 = GuiElementList(listOf(
    GuiFillColor(4, 0, GuiColor.BLACK),
    GuiFillColor(4, 1, GuiColor.BLACK),
    GuiFillColor(4, 2, GuiColor.BLACK),
    GuiFillColor(4, 3, GuiColor.BLACK),
    GuiFillColor(4, 4, GuiColor.BLACK)
))

// middle bar, 5 down
val guiMiddleBar5 = GuiElementList(listOf(
    GuiFillColor(4, 0, GuiColor.BLACK),
    GuiFillColor(4, 1, GuiColor.BLACK),
    GuiFillColor(4, 2, GuiColor.BLACK),
    GuiFillColor(4, 3, GuiColor.BLACK),
    GuiFillColor(4, 4, GuiColor.BLACK),
    GuiFillColor(4, 5, GuiColor.BLACK)
))

// fill bottom row on other side's terms gray
val otherSideFillBottomGray = GuiElementList(listOf(
    GuiFillColor(5, 5, GuiColor.GRAY),
    GuiFillColor(6, 5, GuiColor.GRAY),
    GuiFillColor(7, 5, GuiColor.GRAY),
    GuiFillColor(8, 5, GuiColor.GRAY)
))

// fill bottom row on other side's terms green
val otherSideFillBottomGreen = GuiElementList(listOf(
    GuiFillColor(5, 5, GuiColor.GREEN),
    GuiFillColor(6, 5, GuiColor.GREEN),
    GuiFillColor(7, 5, GuiColor.GREEN),
    GuiFillColor(8, 5, GuiColor.GREEN)
))

// ==================================
// Actual GUI
// ==================================
public class TreatyTermsGui(
    val player: Player,
    val treaty: Treaty,
    val town: Town
): GuiElement {
    
    val guiButtonOccupyTerritory = GuiButton(0, 5, ICON_OCCUPY, null, null, {
        treaty.setPlayerGuiView(TreatyGuiView.SELECT_TERRITORY_OCCUPY, player, treaty, town)
    })
    
    val guiButtonReject = GuiButton(2, 5, ICON_REJECT, null, null,
        { treaty.reject(this.town) }
    )

    val guiButtonOffer = GuiButton(3, 5, ICON_OFFER, null, null,
        { treaty.offer(this.town) }
    )

    val guiButtonConfirm = GuiButton(4, 5, ICON_CONFIRM, null, null,
        { treaty.confirm(this.town) }
    )

    val guiButtonRevise = GuiButton(3, 5, ICON_REVISE, null, null,
        { treaty.revise(this.town) }
    )
    
    /**
     * Render terms screen
     * 
     * NOTE: as coded, it will not work if there are more than 20 terms,
     * will cutoff and not show terms on other side
     * Ignoring for now but in future will need to restructure logic.
     * Should be unlikely to have >20 terms
     */
    override public fun render(screen: GuiWindow) {
        // render treaty options
        var sideLocked = false
        var sideConfirmed = false
        var otherConfirmed = false
        val receiver: Town = if ( town === treaty.town1 ) {
            sideLocked = treaty.town1Locked
            sideConfirmed = treaty.town1Confirmed
            otherConfirmed = treaty.town2Confirmed
            treaty.town2
        } else if ( town === treaty.town2 ) {
            sideLocked = treaty.town2Locked
            sideConfirmed = treaty.town2Confirmed
            otherConfirmed = treaty.town1Confirmed
            treaty.town1
        } else {
            return
        }

        // handle depositing item
        screen.onItemDeposit({ event: InventoryClickEvent -> 
            if ( !sideLocked ) {
                val items = event.getCurrentItem()
                if ( items != null && items.type != Material.AIR ) {
                    val itemsClone = items.clone()
                    this.player.inventory.removeItem(items)
                    this.treaty.add(TreatyTermItems(town, receiver, itemsClone, player))
                }
            }
        })

        // render bottom button panel
        if ( sideLocked ) {
            guiButtonRevise.render(screen)

            // render middle bar and confirm button
            if ( treaty.town1Locked && treaty.town2Locked ) {
                guiMiddleBar4.render(screen)
                guiButtonConfirm.render(screen)
            }
            else {
                guiMiddleBar5.render(screen)
            }

            if ( sideConfirmed ) {
                GuiFillColor(0, 5, GuiColor.GREEN).render(screen)
                GuiFillColor(1, 5, GuiColor.GREEN).render(screen)
                GuiFillColor(2, 5, GuiColor.GREEN).render(screen)
            }
            else {
                GuiFillColor(0, 5, GuiColor.GRAY).render(screen)
                GuiFillColor(1, 5, GuiColor.GRAY).render(screen)
                GuiFillColor(2, 5, GuiColor.GRAY).render(screen)
            }
        }
        else {
            guiMiddleBar5.render(screen)
            guiButtonOccupyTerritory.render(screen)
            guiButtonReject.render(screen)
            guiButtonOffer.render(screen)
        }
        
        // combined terms
        val termsSequence = treaty.termsLand.asSequence() + treaty.termsItems.asSequence()

        // ======================================================
        // render town side's treaty land occupation/cede terms
        // ======================================================
        var termsIter = termsSequence.iterator()
        var index = 0
        // first render terms
        while ( termsIter.hasNext() && index < TOWN_TERM_SLOTS.size ) {
            val term = termsIter.next()
            if ( term.provider === this.town ) {
                val slot = TOWN_TERM_SLOTS[index]
                val x = slot.x
                val y = slot.y
                when ( term ) {
                    is TreatyTermOccupation -> renderTreatyTermOccupation(screen, x, y, treaty, term, true)
                    is TreatyTermRelease -> renderTreatyTermRelease(screen, x, y, treaty, term, true)
                    is TreatyTermItems -> renderTreatyTermItems(screen, x, y, treaty, term, true)
                }

                index += 1
            }
        }

        // fill in rest with filler or slots to drop items
        for ( i in index until TOWN_TERM_SLOTS.size ) {
            val slot = TOWN_TERM_SLOTS[i]
            val x = slot.x
            val y = slot.y

            // offer locked/confirmed, fill empty slots with color
            if ( sideLocked ) {
                if ( sideConfirmed ) {
                    GuiFillColor(x, y, GuiColor.GREEN).render(screen)
                }
                else {
                    GuiFillColor(x, y, GuiColor.GRAY).render(screen)
                }
            }
            // negotiating terms
            // add callbacks for depositing item using mouseclick
            else {
                GuiButton(x, y, ICON_EMPTY, null, null, { event: InventoryClickEvent ->
                    val items = event.getCursor()?.clone()
                    if ( items !== null && items.type !== Material.AIR ) {
                        this.player.setItemOnCursor(null)
                        this.treaty.add(TreatyTermItems(town, receiver, items, player))
                    }
                }).render(screen)
            }
        }

        // ======================================================
        // render other side's terms
        // ======================================================
        termsIter = termsSequence.iterator()
        index = 0
        // first render terms
        while ( termsIter.hasNext() && index < OTHER_TERM_SLOTS.size ) {
            val term = termsIter.next()
            if ( term.provider !== this.town ) {
                val slot = OTHER_TERM_SLOTS[index]
                val x = slot.x
                val y = slot.y
                when ( term ) {
                    is TreatyTermOccupation -> renderTreatyTermOccupation(screen, x, y, treaty, term, false)
                    is TreatyTermRelease -> renderTreatyTermRelease(screen, x, y, treaty, term, false)
                    is TreatyTermItems -> renderTreatyTermItems(screen, x, y, treaty, term, false)
                }

                index += 1
            }
        }

        // fill in rest with gray filler or slots to drop items
        val fillColor = if ( otherConfirmed ) {
            GuiColor.GREEN
        } else {
            GuiColor.GRAY
        }

        for ( i in index until OTHER_TERM_SLOTS.size ) {
            val slot = OTHER_TERM_SLOTS[i]
            val x = slot.x
            val y = slot.y
            GuiFillColor(x, y, fillColor).render(screen)
        }

        // fill bottom row
        if ( otherConfirmed ) {
            otherSideFillBottomGreen.render(screen)
        } else {
            otherSideFillBottomGray.render(screen)
        }

    }

}