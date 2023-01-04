package phonon.nodes.listeners

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryAction
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

        if ( inventoryClicked !== null && inventoryView.title == "Town Income" ) {
            // disable actions that move items into top view
            // https://hub.spigotmc.org/javadocs/spigot/org/bukkit/event/inventory/InventoryAction.html
            if ( inventoryClicked === inventoryView.getTopInventory() ) {
                when ( event.getAction() ) {
                    InventoryAction.HOTBAR_MOVE_AND_READD,
                    InventoryAction.HOTBAR_SWAP,
                    InventoryAction.PLACE_ALL,
                    InventoryAction.PLACE_ONE,
                    InventoryAction.PLACE_SOME,
                    InventoryAction.SWAP_WITH_CURSOR,
                        -> { event.setCancelled(true) }
                    else -> {}
                }
            }
            else { // bottom inventory
                when ( event.getAction() ) {
                    InventoryAction.MOVE_TO_OTHER_INVENTORY,
                        -> { event.setCancelled(true) }
                    else -> {}
                }
            }
        }
    }

    @EventHandler
    public fun onInventoryDrag(event: InventoryDragEvent) {
        val inventoryView = event.getView()
        if ( inventoryView.title == "Town Income" ) {
            // if any slots involved were in top inventory, cancel event
            // use raw inventory slots: large double chest inventory are index range [0, 53]
            for ( slot in event.getRawSlots() ) {
                if ( slot < 54 ) {
                    event.setCancelled(true)
                    break
                }
            }
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
