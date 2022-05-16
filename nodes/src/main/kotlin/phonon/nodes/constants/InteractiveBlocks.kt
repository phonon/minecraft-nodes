/**
 * All blocks that have an interactive event that must be
 * manually cancelled.
 * 
 * -> When player has fence in hand, must allow interact event
 * -> Force cancel when target block is in this list
 */

package phonon.nodes.constants

import java.util.EnumSet
import org.bukkit.Material


val INTERACTIVE_BLOCKS: EnumSet<Material> = EnumSet.of(
    Material.CHEST,
    Material.TRAPPED_CHEST,
    Material.BARREL,
    Material.FURNACE,
    Material.BLAST_FURNACE,
    Material.WHITE_SHULKER_BOX,
    Material.ORANGE_SHULKER_BOX,
    Material.MAGENTA_SHULKER_BOX,
    Material.LIGHT_BLUE_SHULKER_BOX,
    Material.YELLOW_SHULKER_BOX,
    Material.LIME_SHULKER_BOX,
    Material.PINK_SHULKER_BOX,
    Material.GRAY_SHULKER_BOX,
    Material.LIGHT_GRAY_SHULKER_BOX,
    Material.CYAN_SHULKER_BOX,
    Material.PURPLE_SHULKER_BOX,
    Material.BLUE_SHULKER_BOX,
    Material.BROWN_SHULKER_BOX,
    Material.GREEN_SHULKER_BOX,
    Material.RED_SHULKER_BOX,
    Material.BLACK_SHULKER_BOX,
    Material.STONE_BUTTON,
    Material.OAK_BUTTON,
    Material.SPRUCE_BUTTON,
    Material.BIRCH_BUTTON,
    Material.JUNGLE_BUTTON,
    Material.ACACIA_BUTTON,
    Material.DARK_OAK_BUTTON,
    Material.CRIMSON_BUTTON,
    Material.WARPED_BUTTON,
    Material.POLISHED_BLACKSTONE_BUTTON,
    Material.OAK_DOOR,
    Material.SPRUCE_DOOR,
    Material.BIRCH_DOOR,
    Material.JUNGLE_DOOR,
    Material.ACACIA_DOOR,
    Material.DARK_OAK_DOOR,
    Material.CRIMSON_DOOR,
    Material.WARPED_DOOR,
    Material.OAK_TRAPDOOR,
    Material.SPRUCE_TRAPDOOR,
    Material.BIRCH_TRAPDOOR,
    Material.JUNGLE_TRAPDOOR,
    Material.ACACIA_TRAPDOOR,
    Material.DARK_OAK_TRAPDOOR,
    Material.CRIMSON_TRAPDOOR,
    Material.WARPED_TRAPDOOR,
    Material.OAK_FENCE_GATE,
    Material.SPRUCE_FENCE_GATE,
    Material.BIRCH_FENCE_GATE,
    Material.JUNGLE_FENCE_GATE,
    Material.ACACIA_FENCE_GATE,
    Material.DARK_OAK_FENCE_GATE,
    Material.CRIMSON_FENCE_GATE,
    Material.WARPED_FENCE_GATE,
    Material.REPEATER,
    Material.COMPARATOR,
    Material.LEVER,
    Material.DAYLIGHT_DETECTOR,
    Material.DROPPER,
    Material.DISPENSER
)