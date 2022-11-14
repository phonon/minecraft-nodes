/**
 * An inventory gui window instance
 * 
 * Draws items at (x,y) coordinate in inventory from top-left
 * (0, 0) ------->
 *   |                  Same as HTML DOM screen
 *   |      (+x,+y)
 *   v
 */

package phonon.nodes.gui

import org.bukkit.Bukkit
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

class GuiWindow(val size: Int, val title: String): InventoryHolder {

    // screen size
    val cols: Int = 9
    val rows: Int = size / 9

    // inventory backend
    val _inventory: Inventory = Bukkit.createInventory(this, size, title)

    // map inventory slot index -> GuiElement
    val elements: Array<GuiElement?> = Array(size, {i -> null})

    // inventory event handlers
    private val _onCloseHandlers: ArrayList<() -> Unit> = ArrayList()
    private val _onItemDepositHandlers: ArrayList<(InventoryClickEvent) -> Unit> = ArrayList()

    // implement getInventory for InventoryHolder
    override fun getInventory(): Inventory {
        return _inventory
    }

    // add handler for inventory window close handler
    fun onClose(callback: () -> Unit) {
        this._onCloseHandlers.add(callback)
    }

    // add handler depositing item into inventory
    fun onItemDeposit(callback: (InventoryClickEvent) -> Unit) {
        this._onItemDepositHandlers.add(callback)
    }

    // route onCick to gui object
    fun runOnClick(event: InventoryClickEvent) {
        
        // check if this is the clicked inventory
        val view = event.view
        val inventory = event.clickedInventory

        // println("runOnClick(): ${event.action}, ${inventory.type}")

        if ( inventory == this._inventory ) {
            // cancel event by default, element callback can override
            event.isCancelled = true
            
            val index = event.slot
            val elem = this.elements[index]
            if ( elem != null ) {
                elem.onClick(event)
            }
        }
    }

    fun runOnItemDeposit(event: InventoryClickEvent) {
        // cancel event by default, any callback can override
        event.isCancelled = true

        for ( callback in this._onItemDepositHandlers ) {
            callback(event)
        }
    }

    // cancel drag events for now
    fun runOnDrag(event: InventoryDragEvent) {
        // check if this is the clicked inventory
        val inventory = event.inventory
        if ( inventory == this._inventory ) {
            event.isCancelled = true
        }
    }

    // add inventory window close handler
    fun runOnClose(event: InventoryCloseEvent) {
        for ( callback in this._onCloseHandlers ) {
            callback()
        }
    }

    // draw item at location
    fun draw(element: GuiElement, x: Int, y: Int, items: ItemStack) {
        val index = y * 9 + x
        if ( index < this.size ) {
            this.elements[index] = element
            this._inventory.setItem(index, items)
        }
    }

}