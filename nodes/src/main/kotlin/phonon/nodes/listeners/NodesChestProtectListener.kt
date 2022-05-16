/**
 * Handlers for town chest protection actions:
 * 
 * NodesChestProtectListener:
 * - handler for clicking chest for protecting it, created
 *   dynamically per player
 * 
 * NodesProtectedChestDestructionListener:
 * - handle detecting chest destruction to remove protected blocks
 */

package phonon.nodes.listeners

import org.bukkit.ChatColor
import org.bukkit.block.Block
import org.bukkit.block.Hopper
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerInteractEvent
import phonon.nodes.Message
import phonon.nodes.Nodes
import phonon.nodes.constants.PROTECTED_BLOCKS
import phonon.nodes.constants.NODES_SOUND_CHEST_PROTECT
import phonon.nodes.objects.Resident
import phonon.nodes.objects.Territory
import phonon.nodes.objects.Town

public fun NodesPlayerChestProtectListener(player: Player, resident: Resident, town: Town): Listener {
    return object: Listener {

        @EventHandler(priority = EventPriority.HIGHEST)
        public fun onBlockInteract(event: PlayerInteractEvent) {
            val eventPlayer: Player = event.player
            if ( eventPlayer !== player || resident.isProtectingChests == false ) {
                return
            }

            val block = event.getClickedBlock()
            if ( block !== null ) {
                if ( PROTECTED_BLOCKS.contains(block.type) ) {
                    val territory: Territory? = Nodes.getTerritoryFromChunk(block.chunk)
                    val territoryTown: Town? = territory?.town
                    
                    if ( town !== territoryTown ) {
                        Message.error(player, "This is not your town (stopping, use /t protect to start protecting again)")
                        Nodes.stopProtectingChests(resident)
                        return
                    }
                    
                    // unprotect
                    if ( town.protectedBlocks.contains(block) ) {
                        Nodes.protectTownChest(town, block, false)
        
                        player.playSound(player.location, NODES_SOUND_CHEST_PROTECT, 1.0f, 0.5f)
                        Message.print(player, "${ChatColor.DARK_AQUA}Removed chest protection")
                    }
                    // protect
                    else {
                        Nodes.protectTownChest(town, block, true)
        
                        player.playSound(player.location, NODES_SOUND_CHEST_PROTECT, 1.0f, 1.0f)
                        Message.print(player, "You have protected this chest")
                    }
                    
                    event.setCancelled(true)
                    return
                }
            }

            Message.error(player, "Not a chest (stopping, use /t protect to start protecting again)")
            Nodes.stopProtectingChests(resident)
        }
    }
}


/**
 * Listener for any special chest protection 
 */
public class NodesChestProtectionListener: Listener {
    
    // disable hopper events
    @EventHandler(priority = EventPriority.LOW)
    public fun onHopperMove(event: InventoryMoveItemEvent) {
        // check if destination is hopper
        val dest = event.getDestination()
        if ( dest.holder is Hopper ) {

            // check if source is a protected block
            val source = event.getSource()
            if ( source.type == InventoryType.CHEST ) {
                val block = source.location?.getBlock()
                if ( block !== null ) {
                    val town: Town? = Nodes.getTerritoryFromChunk(block.chunk)?.town
                    if ( town !== null && town.protectedBlocks.contains(block) ) {
                        event.setCancelled(true)
                    }
                }
            }
        }
    }
}

/**
 * Listen to block destruction, unprotect chests if occurs
 */
public class NodesChestProtectionDestroyListener: Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public fun onBlockBreak(event: BlockBreakEvent) {
        val block: Block = event.block
        val town: Town? = Nodes.getTerritoryFromChunk(block.chunk)?.town
        if ( town !== null ) {
            Nodes.protectTownChest(town, block, false)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public fun onEntityExplode(event: EntityExplodeEvent) {
        val blockList = event.blockList()
        for ( block in blockList ) {
            val town: Town? = Nodes.getTerritoryFromChunk(block.chunk)?.town
            if ( town !== null ) {
                Nodes.protectTownChest(town, block, false)
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public fun onBlockExplode(event: BlockExplodeEvent) {
        val blockList = event.blockList()
        for ( block in blockList ) {
            val town: Town? = Nodes.getTerritoryFromChunk(block.chunk)?.town
            if ( town !== null ) {
                Nodes.protectTownChest(town, block, false)
            }
        }
    }
}