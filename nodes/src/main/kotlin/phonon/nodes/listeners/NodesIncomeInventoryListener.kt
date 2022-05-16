package phonon.nodes.listeners

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import phonon.nodes.Nodes

public class NodesIncomeInventoryListener: Listener {

    // allow removing items from town income inventory
    // but cancel all possible ways to insert items back in
    // CASE THAT ISNT HANDLED: if there is a partial stack
    // in inventory, player can shift click add items in
    @EventHandler
    public fun onInventoryClick(event: InventoryClickEvent) {
        val inventoryClicked = event.getClickedInventory()
        val inventoryView = event.getView()
        val clickSlot = event.getSlot()

        if ( inventoryClicked != null ) {
            // if player clicks to try inserting item into null slot
            if ( inventoryView.title == "Town Income" && inventoryClicked.getItem(clickSlot) == null ) {
                event.setCancelled(true)
            }
            // if shift click and top view is income, cancel
            // player add item to top inventory
            else if ( inventoryView.title == "Town Income" && event.isShiftClick() && inventoryView.title != "Town Income" ) {
                event.setCancelled(true)
            }
        }
    }

    @EventHandler
    public fun onInventoryDrag(event: InventoryDragEvent) {
        val inventoryView = event.getView()
        if ( inventoryView.title == "Town Income" ) {
            event.setCancelled(true)
        }
    }

    @EventHandler
    public fun onInventoryClose(event: InventoryCloseEvent) {
        val inventoryView = event.getView()
        if ( inventoryView.title == "Town Income" ) {
            Nodes.onTownIncomeInventoryClose()
        }
    }
    
}
