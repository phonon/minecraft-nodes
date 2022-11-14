/**
 * GUI Button element, run specified callback on click
 */

package phonon.nodes.gui

import org.bukkit.inventory.ItemStack
import org.bukkit.event.inventory.InventoryClickEvent

class GuiButton(
    val x: Int,
    val y: Int, 
    val icon: ItemStack,
    val title: String?,
    val tooltip: List<String>?,
    val callback: (InventoryClickEvent) -> Unit
): GuiElement {
    
    // set icon meta
    init {
        if ( title != null || tooltip != null ) {
            val itemMeta = icon.itemMeta

            if ( title != null ) {
                itemMeta!!.setDisplayName(title)
            }
            if ( tooltip != null ) {
                itemMeta!!.lore = tooltip
            }

            icon.itemMeta = itemMeta
        }
        
    }
    
    override fun onClick(event: InventoryClickEvent) {
        callback(event)
    }

    override fun render(screen: GuiWindow) {
        screen.draw(this, x, y, icon)
    }
}
