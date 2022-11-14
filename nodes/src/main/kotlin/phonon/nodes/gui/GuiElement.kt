/**
 * Interface for all gui objects
 */

package phonon.nodes.gui

import org.bukkit.event.inventory.InventoryClickEvent

interface GuiElement {

    // render object to Inventory screen container
    fun render(screen: GuiWindow)

    // onClick handler
    fun onClick(event: InventoryClickEvent) {}
}