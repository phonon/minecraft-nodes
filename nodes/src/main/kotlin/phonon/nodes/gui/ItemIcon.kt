/**
 * Wrapper around setting item metadata
 */

package phonon.nodes.gui

import org.bukkit.inventory.ItemStack

fun ItemIcon(
    icon: ItemStack,
    title: String,
    tooltip: List<String>
): ItemStack {

    val itemMeta = icon.itemMeta
    itemMeta?.setDisplayName(title)
    itemMeta?.lore = tooltip
    icon.itemMeta = itemMeta

    return icon
}