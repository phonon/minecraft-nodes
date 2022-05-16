/**
 * Wrapper around setting item metadata
 */

package phonon.nodes.gui

import org.bukkit.inventory.ItemStack

public fun ItemIcon(
    icon: ItemStack,
    title: String,
    tooltip: List<String>
): ItemStack {

    val itemMeta = icon.getItemMeta()
    itemMeta.setDisplayName(title)
    itemMeta.setLore(tooltip)
    icon.setItemMeta(itemMeta)

    return icon
}