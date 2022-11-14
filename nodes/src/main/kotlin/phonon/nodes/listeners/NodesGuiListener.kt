/**
 * Listener for inventory GUI events,
 * route events to GUI manager
 */

package phonon.nodes.listeners

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import phonon.nodes.gui.GuiWindow

class NodesGuiListener: Listener {

    // 
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        // println("GUI EVENT: ${event.action}")

        val inventory = event.clickedInventory
        val holder = inventory?.holder
        val view = event.view
        val topHolder = view.topInventory.holder

        // clicked inventory is the gui window
        if ( topHolder is GuiWindow ) {
            if ( holder != null && holder is GuiWindow ) {
                holder.runOnClick(event)
            }
            // top inventory is gui window, player inv/hotbar was clicked
            else {
                // println("GUIWINDOW EVENT, CLICKED BOTTOM")
                when ( event.action ) {
                    InventoryAction.MOVE_TO_OTHER_INVENTORY -> {
                        // println("MOVING ITEM INTO GUI WINDOW")
                        topHolder.runOnItemDeposit(event)
                    }

                    // cancel unsafe actions
                    InventoryAction.COLLECT_TO_CURSOR -> {
                        event.isCancelled = true
                    }
                }
            }
        }
        
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        // println("GUI DRAG")

        val inventory = event.inventory
        val holder = inventory.holder

        if ( holder != null && holder is GuiWindow ) {
            holder.runOnDrag(event)
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val inventory = event.inventory
        val holder = inventory.holder
        if ( holder != null && holder is GuiWindow ) {
            holder.runOnClose(event)
        }
    }
    
}
