/**
 * Interface for all gui objects
 */

package phonon.nodes.gui

import org.bukkit.event.inventory.InventoryClickEvent

public interface GuiElement {

    // render object to Inventory screen container
    public fun render(screen: GuiWindow)

    // onClick handler
    public fun onClick(event: InventoryClickEvent) {}
}