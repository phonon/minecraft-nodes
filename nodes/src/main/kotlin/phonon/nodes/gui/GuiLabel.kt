/**
 * GUI Label Element
 * Display a single item material in a slot with name + lore
 * and no click actions.
 * (Wrapper around item stack and item meta)
 */

package phonon.nodes.gui

import org.bukkit.inventory.ItemStack

public class GuiLabel(
    val x: Int,
    val y: Int,
    val icon: ItemStack,
    val title: String,
    val tooltip: List<String>
): GuiElement {
    
    // set icon meta
    init {
        val itemMeta = icon.getItemMeta()
        itemMeta.setDisplayName(title)
        itemMeta.setLore(tooltip)
        icon.setItemMeta(itemMeta)
    }
    
    override public fun render(screen: GuiWindow) {
        screen.draw(this, x, y, icon)
    }
}
