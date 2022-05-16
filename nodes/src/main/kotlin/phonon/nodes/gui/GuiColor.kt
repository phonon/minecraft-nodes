/**
 * Minecraft item colors in order of data value
 */

package phonon.nodes.gui

import org.bukkit.Material
import org.bukkit.inventory.ItemStack

public enum class GuiColor {
    WHITE,
    ORANGE,
    MAGENTA,
    LIGHT_BLUE,
    YELLOW,
    LIME,
    PINK,
    GRAY,
    LIGHT_GRAY,
    CYAN,
    PURPLE,
    BLUE,
    BROWN,
    GREEN,
    RED,
    BLACK
}

private val STAINED_GLASS_LIST = listOf(
    Material.WHITE_STAINED_GLASS_PANE,
    Material.ORANGE_STAINED_GLASS_PANE,
    Material.MAGENTA_STAINED_GLASS_PANE,
    Material.LIGHT_BLUE_STAINED_GLASS_PANE,
    Material.YELLOW_STAINED_GLASS_PANE,
    Material.LIME_STAINED_GLASS_PANE,
    Material.PINK_STAINED_GLASS_PANE,
    Material.GRAY_STAINED_GLASS_PANE,
    Material.LIGHT_GRAY_STAINED_GLASS_PANE,
    Material.CYAN_STAINED_GLASS_PANE,
    Material.PURPLE_STAINED_GLASS_PANE,
    Material.BLUE_STAINED_GLASS_PANE,
    Material.BROWN_STAINED_GLASS_PANE,
    Material.GREEN_STAINED_GLASS_PANE,
    Material.RED_STAINED_GLASS_PANE,
    Material.BLACK_STAINED_GLASS_PANE
)

private val CONCRETE_LIST = listOf(
    Material.WHITE_CONCRETE,
    Material.ORANGE_CONCRETE,
    Material.MAGENTA_CONCRETE,
    Material.LIGHT_BLUE_CONCRETE,
    Material.YELLOW_CONCRETE,
    Material.LIME_CONCRETE,
    Material.PINK_CONCRETE,
    Material.GRAY_CONCRETE,
    Material.LIGHT_GRAY_CONCRETE,
    Material.CYAN_CONCRETE,
    Material.PURPLE_CONCRETE,
    Material.BLUE_CONCRETE,
    Material.BROWN_CONCRETE,
    Material.GREEN_CONCRETE,
    Material.RED_CONCRETE,
    Material.BLACK_CONCRETE
)

// pre-generated item stacks
public val GUI_STAINED_GLASS: List<ItemStack> = enumValues<GuiColor>().map({ c: GuiColor -> 
    // 1.16
    val item = ItemStack(STAINED_GLASS_LIST[c.ordinal], 1)

    // 1.12
    // val item = ItemStack(Material.STAINED_GLASS_PANE, 1, c.ordinal.toShort())

    val meta = item.getItemMeta()
    meta.setDisplayName(" ")
    item.setItemMeta(meta)

    item
})

public val GUI_CONCRETE: List<ItemStack> = enumValues<GuiColor>().map({ c: GuiColor -> 
    // 1.16
    val item = ItemStack(CONCRETE_LIST[c.ordinal], 1)

    // 1.12
    // val item = ItemStack(Material.CONCRETE, 1, c.ordinal.toShort())

    val meta = item.getItemMeta()
    meta.setDisplayName(" ")
    item.setItemMeta(meta)

    item
})