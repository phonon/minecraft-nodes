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

public class NodesGuiListener: Listener {

    // 
    @EventHandler
    public fun onInventoryClick(event: InventoryClickEvent) {
        // println("GUI EVENT: ${event.action}")

        val inventory = event.getClickedInventory()
        val holder = inventory?.holder
        val view = event.getView()
        val topHolder = view.getTopInventory().holder

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
                    InventoryAction.COLLECT_TO_CURSOR -> { event.setCancelled(true) }
                }
            }
        }
        
    }

    @EventHandler
    public fun onInventoryDrag(event: InventoryDragEvent) {
        // println("GUI DRAG")

        val inventory = event.getInventory()
        val holder = inventory?.holder

        if ( holder != null && holder is GuiWindow ) {
            holder.runOnDrag(event)
        }
    }

    @EventHandler
    public fun onInventoryClose(event: InventoryCloseEvent) {
        val inventory = event.getInventory()
        val holder = inventory?.holder
        if ( holder != null && holder is GuiWindow ) {
            holder.runOnClose(event)
        }
    }
    
}
