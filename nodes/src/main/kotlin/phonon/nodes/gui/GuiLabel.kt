/**
 * GUI Label Element
 * Display a single item material in a slot with name + lore
 * and no click actions.
 * (Wrapper around item stack and item meta)
 */

package phonon.nodes.gui

import org.bukkit.inventory.ItemStack

class GuiLabel(
    val x: Int,
    val y: Int,
    val icon: ItemStack,
    val title: String,
    val tooltip: List<String>
): GuiElement {
    
    // set icon meta
    init {
        val itemMeta = icon.itemMeta
        itemMeta!!.setDisplayName(title)
        itemMeta.lore = tooltip
        icon.itemMeta = itemMeta
    }
    
    override fun render(screen: GuiWindow) {
        screen.draw(this, x, y, icon)
    }
}
