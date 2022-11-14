/**
 * Event listener to block world interactions (interact,
 * create/destroy blocks, use entities, etc.). This is used to
 * stop world interactions when Nodes fails to load (e.g. due to
 * world configuration error). Towns will not be loaded so town
 * protections will be disabled, letting players interact anywhere.
 * This safeguards world in case when world/towns fail to load.
 * 
 * This prevents the MOST COMMON player-world interactions, but does
 * not prevent complicated cases like pistons, droppers, redstone, etc.
 */

package phonon.nodes.listeners

import java.util.concurrent.ThreadLocalRandom
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack
import org.bukkit.entity.Player
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.Event.Result
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.BlockFromToEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.EntityInteractEvent
import org.bukkit.event.player.PlayerBucketEmptyEvent
import org.bukkit.event.player.PlayerInteractEvent
import phonon.nodes.Message

val MESSAGE_DISABLED: String = "${ChatColor.DARK_RED}${ChatColor.BOLD}Nodes plugin failed to load: world is disabled..."

public class DisabledWorldListener: Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public fun onBlockBreak(event: BlockBreakEvent) {
        event.setCancelled(true)
        Message.print(event.player, MESSAGE_DISABLED)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public fun onBlockPlace(event: BlockPlaceEvent) {
        event.setCancelled(true)
        Message.print(event.player, MESSAGE_DISABLED)
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public fun onPlayerBucketEmpty(event: PlayerBucketEmptyEvent) {
        event.setCancelled(true)
        Message.print(event.player, MESSAGE_DISABLED)
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public fun onBlockInteract(event: PlayerInteractEvent) {
        event.setCancelled(true)
        Message.print(event.player, MESSAGE_DISABLED)
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public fun onEntityInteract(event: EntityInteractEvent) {
        event.setCancelled(true)
        val entity = event.entity
        if ( entity is Player ) {
            Message.print(entity, MESSAGE_DISABLED)
        }
    }
}
