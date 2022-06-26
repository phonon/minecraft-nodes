/**
 * Main listener for Nodes world:
 * - town permissions and protections
 * - flag war events
 * - hidden ore
 * - ore, crop harvest taxation
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
import phonon.nodes.Config
import phonon.nodes.Message
import phonon.nodes.Nodes
import phonon.nodes.constants.*
import phonon.nodes.objects.Resident
import phonon.nodes.objects.Territory
import phonon.nodes.objects.TerritoryChunk
import phonon.nodes.objects.Town
import phonon.nodes.war.FlagWar
import phonon.nodes.war.Attack

public class NodesWorldListener: Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public fun onBlockBreak(event: BlockBreakEvent) {
        val player: Player = event.player
        val block: Block = event.block
        val territoryChunk = Nodes.getTerritoryChunkFromBlock(block.x, block.z)
        
        // if war enabled, do flag break check
        if ( Nodes.war.enabled && territoryChunk?.attacker !== null ) {
            var attack = FlagWar.blockToAttacker.get(block)
            
            // check if block above was flag block (e.g. destroying fence)
            if ( attack === null ) {
                attack = FlagWar.blockToAttacker.get(block.getRelative(0, 1, 0))
            }

            if ( attack !== null ) {
                event.setCancelled(true)
                attack.cancel()
                Message.broadcast("${ChatColor.GOLD}[War] Attack at (${block.x}, ${block.y}, ${block.z}) defeated by ${player.name}")
            }
        }
        
        // block permissions

        // op bypass
        if ( player.isOp ) {
            return
        }

        val territory: Territory? = Nodes.getTerritoryFromChunk(block.chunk)
        val town: Town? = territory?.town
        val resident = Nodes.getResident(player)

        // interacting in areas with no territory or no town
        if ( town === null ) {
            if ( hasWildernessPermissions(territory) ) {
                return
            }

            event.setCancelled(true)
            Message.error(player, "You cannot destroy here!")
            return
        }

        // interacting in a town
        if ( resident !== null ) {
            if ( hasTownPermissions(TownPermissions.DESTROY, town, resident) ) {
                return
            }

            // territory occupier permissions
            val occupier: Town? = territory.occupier
            if ( occupier !== null && hasOccupierPermissions(TownPermissions.DESTROY, town, occupier, resident) ) {
                return
            }

            // war permissions
            if ( hasWarPermissions(resident, territory, territoryChunk!!) ) {
                return
            }
        }

        event.setCancelled(true)
        Message.error(player, "You cannot destroy here!")
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public fun onBlockBreakSuccess(event: BlockBreakEvent) {
        if ( event.isCancelled() ) {
            return
        }

        val player = event.player
        val block = event.block
        val blockMaterial = block.getType()

        // handle hidden ore mining
        if ( Config.oreBlocks.contains(blockMaterial) ) {
            if ( !Nodes.hiddenOreInvalidBlocks.contains(block) ) {
                handleHiddenOre(player, block)

                // temporarily invalide block location
                Nodes.hiddenOreInvalidBlocks.add(block)
            }
        }
        // handle crop harvest
        else if ( Config.cropTypes.contains(blockMaterial) ) {
            handleCropHarvest(block)
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public fun onBlockPlace(event: BlockPlaceEvent) {
        val block: Block = event.block
        val player: Player = event.player

        // war specific tasks
        if ( Nodes.war.enabled ) {
            val territoryChunk = Nodes.getTerritoryChunkFromBlock(block.x, block.z)
            if ( territoryChunk !== null ) {
                // disable block placement in flag no build distance
                if ( territoryChunk.attacker !== null ) {
                    // op bypass
                    if ( player.isOp() ) {
                        return
                    }

                    var attack = FlagWar.chunkToAttacker.get(territoryChunk.coord)
                    if ( attack !== null ) {
                        if ( blockInWarFlagNoBuildRegion(block, attack) ) {
                            event.setCancelled(true)
                            Message.error(player, "[War] Cannot build within ${Config.flagNoBuildDistance} blocks of war flags")
                            return
                        }
                    }
                }
                // check if this is flag placement
                else if ( FlagWar.flagMaterials.contains(block.getType()) ) {
                    // get player and town
                    val resident = Nodes.getResident(player)
                    if ( resident !== null ) {
                        val town = resident.town
                        if ( town !== null ) {
                            val result = FlagWar.beginAttack(player.getUniqueId(), town, territoryChunk, block)
                            if ( result.isSuccess ) {
                                // get town being attacked
                                val townAttacked = territoryChunk.territory.town!!
                    
                                // reclaiming your town
                                if ( townAttacked === town ) {
                                    Message.broadcast("${ChatColor.DARK_RED}[War] ${event.player.name} is liberating ${townAttacked.name} at (${block.x}, ${block.y}, ${block.z})")
                                }
                                else { // attacking enemy
                                    Message.broadcast("${ChatColor.DARK_RED}[War] ${event.player.name} is attacking ${townAttacked.name} at (${block.x}, ${block.y}, ${block.z})")
                                }
                            }
                            else {
                                when ( result.exceptionOrNull() ) {
                                    ErrorNoTerritory -> Message.error(player, "[War] There is no territory here")
                                    ErrorAlreadyUnderAttack -> Message.error(player, "[War] Chunk already under attack")
                                    ErrorAlreadyCaptured -> Message.error(player, "[War] Chunk already captured by town or allies")
                                    ErrorTownBlacklisted -> Message.error(player, "[War] Cannot attack this town (blacklisted)")
                                    ErrorTownNotWhitelisted -> Message.error(player, "[War] Cannot attack this town (not whitelisted)")
                                    ErrorNotEnemy -> Message.error(player, "[War] Chunk does not belong to an enemy")
                                    ErrorNotBorderTerritory -> Message.error(player, "[War] You can only attack border territories")
                                    ErrorChunkNotEdge -> Message.error(player, "[War] Must attack from territory edge or from captured chunk")
                                    ErrorFlagTooHigh -> Message.error(player, "[War] Flag placement too high, cannot create flag")
                                    ErrorSkyBlocked -> Message.error(player, "[War] Flag must see the sky")
                                    ErrorTooManyAttacks -> Message.error(player, "[War] You cannot attack any more chunks at the same time")
                                }
                    
                                // cancel event
                                event.setCancelled(true)
                            }
                        }
                        else {
                            Message.error(player, "[War] Cannot claim unless you are part of a town")
                            event.setCancelled(true)
                        }
                    } else {
                        event.setCancelled(true)
                    }
                }
            }
        }

        // op bypass
        if ( player.isOp() ) {
            return
        }
        
        val territory: Territory? = Nodes.getTerritoryFromChunk(block.chunk)
        val territoryChunk = Nodes.getTerritoryChunkFromBlock(block.x, block.z)
        val resident = Nodes.getResident(player)
        val town: Town? = territory?.town
        
        // interacting in areas with no territory or no town
        if ( town === null ) {
            if ( hasWildernessPermissions(territory) ) {
                return
            }

            event.setCancelled(true)
            Message.error(player, "You cannot build here!")
            return
        }

        // interacting in a town
        if ( resident !== null ) {
            if ( hasTownPermissions(TownPermissions.BUILD, town, resident) ) {
                return
            }

            // territory occupier permissions
            val occupier: Town? = territory.occupier
            if ( occupier !== null && hasOccupierPermissions(TownPermissions.BUILD, town, occupier, resident) ) {
                return
            }

            // war permissions
            if ( hasWarPermissions(resident, territory, territoryChunk!!) ) {
                return
            }

            // ignore if war enabled and item in hand is a flag material
            if ( Nodes.war.enabled && Config.flagMaterials.contains(block.type) ) {
                return
            }
        }

        event.setCancelled(true)
        Message.error(player, "You cannot build here!")
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public fun onBlockPlaceSuccess(event: BlockPlaceEvent) {
        if ( event.isCancelled() ) {
            return
        }

        val block = event.block
        val blockMaterial = block.type
        
        // invalide hidden ore blocks
        if ( Config.oreBlocks.contains(blockMaterial) ) {
            Nodes.hiddenOreInvalidBlocks.add(block)
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public fun onPlayerBucketEmpty(event: PlayerBucketEmptyEvent) {
        val block = event.getBlockClicked().getRelative(event.getBlockFace())
        val territory: Territory? = Nodes.getTerritoryFromChunk(block.chunk)
        val territoryChunk = Nodes.getTerritoryChunkFromBlock(block.x, block.z)
        val player: Player = event.player
        val resident = Nodes.getResident(player)
        val town: Town? = territory?.town

        // op bypass
        if ( player.isOp() ) {
            return
        }
        
        // interacting in areas with no territory or no town
        if ( town === null ) {
            if ( hasWildernessPermissions(territory) ) {
                return
            }

            event.setCancelled(true)
            Message.error(player, "You cannot use buckets here")
            return
        }

        // interacting in a town
        if ( resident !== null ) {
            if ( hasTownPermissions(TownPermissions.BUILD, town, resident) ) {
                return
            }

            // territory occupier permissions
            val occupier: Town? = territory.occupier
            if ( occupier !== null && hasOccupierPermissions(TownPermissions.BUILD, town, occupier, resident) ) {
                return
            }

            // war permissions
            if ( hasWarPermissions(resident, territory, territoryChunk!!) ) {
                return
            }
        }

        event.setCancelled(true)
        Message.error(player, "You cannot use buckets here")
    }

    // handle placing water/lava onto block (can be used to destroy harvest crops)
    @EventHandler(priority = EventPriority.MONITOR)
    public fun onPlayerBucketEmptySuccess(event: PlayerBucketEmptyEvent) {
        if ( event.isCancelled() ) {
            return
        }

        // gets block where water actually placed
        val block = event.getBlockClicked().getRelative(event.getBlockFace())
        val blockMaterial = block.type

        // handle crop harvest
        if ( Config.cropTypes.contains(blockMaterial) ) {
            handleCropHarvest(block)
        }
    }

    // handle fluids moving to block (for water auto-farms)
    // NOTE: there are edge cases that are not triggered:
    // - when water falling vertically this is not triggered
    // just ignoring that because very limited use ingame of vertical water harvesting
    @EventHandler(priority = EventPriority.MONITOR)
    public fun onBlockFromToSuccess(event: BlockFromToEvent) {
        if ( event.isCancelled() ) {
            return
        }

        val block = event.getToBlock()
        val blockMaterial = block.type

        // handle crop harvest
        if ( Config.cropTypes.contains(blockMaterial) ) {
            handleCropHarvest(block)
        }
    }

    // handle pistons breaking block (for auto-farms)
    // handle harvest tax for any blocks moved by piston event
    // this only hnadles objects directly moved by piston, not things like
    // dirt underneath a block
    @EventHandler(priority = EventPriority.MONITOR)
    public fun onPistonExtendSuccess(event: BlockPistonExtendEvent) {
        if ( event.isCancelled() ) {
            return
        }

        val blockMovedList = event.getBlocks()
        for ( block in blockMovedList ) {
            val blockMaterial = block.type

            // handle crop harvest
            if ( Config.cropTypes.contains(blockMaterial) ) {
                handleCropHarvest(block)
            }
        }
    }

    @EventHandler
    public fun onBlockInteract(event: PlayerInteractEvent) {
        if ( event.clickedBlock === null) {
            return
        }

        val player = event.player
        val action = event.action

        // op bypass
        if ( player.isOp() ) {
            return
        }

        val block = event.getClickedBlock()
        if ( block === null ) {
            return
        }
        
        val territory: Territory? = Nodes.getTerritoryFromChunk(block.chunk)
        val territoryChunk = Nodes.getTerritoryChunkFromBlock(block.x, block.z)
        val resident = Nodes.getResident(player)
        val town: Town? = territory?.town

        // interacting in areas with no territory or no town
        // DO NOT USE WILDERNESS PERMISSIONS
        if ( territory === null ) {
            return
        }
        if ( town === null ) {
            return
        }
        
        if ( resident !== null ) {
            // ignore if war enabled and item in hand is a flag material
            if ( Nodes.war.enabled && Config.flagMaterials.contains(event.getMaterial()) && action == Action.RIGHT_CLICK_BLOCK ) {
                // dont allow using block
                val clickedBlock = event.getClickedBlock()
                if ( clickedBlock !== null && INTERACTIVE_BLOCKS.contains(clickedBlock.getType()) ) {
                    Message.error(player, "You cannot interact here!")
                    event.setUseInteractedBlock(Result.DENY)
                }
                return
            }

            // special permissions for using chests, furnaces, etc...
            if ( PROTECTED_BLOCKS.contains(block.type) ) {

                // war permissions override
                if ( hasWarPermissions(resident, territory, territoryChunk!!) ) {
                    return
                }

                // normal town permissions
                if ( hasTownPermissions(TownPermissions.CHESTS, town, resident) ) {
                    
                    // check if chest protected
                    if ( town.protectedBlocks.contains(block) && !hasTownProtectedChestPermissions(town, resident) ) {
                        event.setCancelled(true)
                        Message.error(player, "This chest is for trusted residents only")
                    }

                    return
                }
                
                event.setCancelled(true)
                Message.error(player, "You cannot use chests here!")
                return
            }

            // general interact permissions
            if ( hasTownPermissions(TownPermissions.INTERACT, town, resident) ) {
                return
            }

            // territory occupier permissions
            val occupier: Town? = territory.occupier
            if ( occupier !== null && hasOccupierPermissions(TownPermissions.INTERACT, town, occupier, resident) ) {
                return
            }

            // war permissions
            if ( hasWarPermissions(resident, territory, territoryChunk!!) ) {
                return
            }
        }

        event.setUseInteractedBlock(Result.DENY)

        if ( event.isBlockInHand() ) {
            event.setUseItemInHand(Result.DENY)
        }
        else {
            event.setUseItemInHand(Result.DEFAULT)
        }
        
        if ( action == Action.RIGHT_CLICK_BLOCK ) {
            Message.error(player, "You cannot interact here!")
        }
    }

    // disable normal animal interactions (e.g. animal on pressure plate)
    @EventHandler
    public fun onEntityInteract(event: EntityInteractEvent) {
        val entity = event.getEntity()

        // check if animal has passenger that is player
        val passengers = entity.getPassengers()
        var player: Player? = null
        for ( p in passengers ) {
            if ( p is Player ) {
                player = p
                break
            }
        }

        // if player member of town in territory, allow access
        if ( player !== null ) {
            // op bypass
            if ( player.isOp() ) {
                return
            }
            
            val block: Block = event.block
            val territory: Territory? = Nodes.getTerritoryFromChunk(block.chunk)
            val territoryChunk = Nodes.getTerritoryChunkFromBlock(block.x, block.z)
            val resident = Nodes.getResident(player)
            val town: Town? = territory?.town

            // allow interacting in areas with no territory or no town
            // DO NOT USE WILDERNESS PERMISSIONS
            if ( territory === null ) {
                return
            }
            if ( town === null ) {
                return
            }
            
            if ( resident !== null ) {
                if ( hasTownPermissions(TownPermissions.INTERACT, town, resident) ) {
                    return
                }

                // territory occupier permissions
                val occupier: Town? = territory.occupier
                if ( occupier !== null && hasOccupierPermissions(TownPermissions.INTERACT, town, occupier, resident) ) {
                    return
                }

                // war permissions
                if ( hasWarPermissions(resident, territory, territoryChunk!!) ) {
                    return
                }
            }
        }

        event.setCancelled(true)
    }
    
    /**
     * Handle entity explosion (i.e. tnt, creeper) and
     * destroying flag -> stop attack
     */
    @EventHandler(priority = EventPriority.LOW)
    public fun onEntityExplode(event: EntityExplodeEvent) {
        // check if explosion allowed
        if ( Config.restrictExplosions ) {
            if ( !Nodes.war.enabled ) {
                if ( Config.onlyAllowExplosionsDuringWar ) {
                    event.setCancelled(true)
                    return
                }
            }
            // war on, check town blacklist/whitelist
            else {
                if ( Config.warUseWhitelist ) {
                    val chunk = event.entity.getLocation().chunk
                    val town = Nodes.getTownAtChunkCoord(chunk.x, chunk.z)
                    if ( town !== null && !Config.warWhitelist.contains(town.uuid) ) {
                        event.setCancelled(true)
                        return
                    }
                }

                if ( Config.warUseBlacklist ) {
                    val chunk = event.entity.getLocation().chunk
                    val town = Nodes.getTownAtChunkCoord(chunk.x, chunk.z)
                    if ( town !== null && Config.warBlacklist.contains(town.uuid) ) {
                        event.setCancelled(true)
                        return
                    }
                }
            }
        }

        if ( Nodes.war.enabled ) {
            // check if flag destroyed
            for ( block in event.blockList() ) {
                // check if chunk under attack
                val chunk = Nodes.getTerritoryChunkFromBlock(block.x, block.z)
                if ( chunk?.attacker !== null ) {
                    var attack = FlagWar.blockToAttacker.get(block)

                    // check if block above was flag block (e.g. destroying fence)
                    if ( attack === null ) {
                        attack = FlagWar.blockToAttacker.get(block.getRelative(0, 1, 0))
                    }
                    
                    if ( attack !== null ) {
                        attack.cancel()
                        Message.broadcast("${ChatColor.GOLD}[War] Attack at (${block.x}, ${block.y}, ${block.z}) stopped by an explosion")
                    }
                }
            }
        }
    }

    /**
     * Handle block explosion (i.e. end crystals) and
     * destroying flag -> stop attack
     */
    @EventHandler(priority = EventPriority.LOW)
    public fun onBlockExplode(event: BlockExplodeEvent) {
        // check if explosion allowed
        if ( Config.restrictExplosions ) {
            if ( !Nodes.war.enabled ) {
                if ( Config.onlyAllowExplosionsDuringWar ) {
                    event.setCancelled(true)
                    return
                }
            }
            // war on, check town blacklist/whitelist
            else {
                if ( Config.warUseWhitelist ) {
                    val chunk = event.block.chunk
                    val town = Nodes.getTownAtChunkCoord(chunk.x, chunk.z)
                    if ( town !== null && !Config.warWhitelist.contains(town.uuid) ) {
                        event.setCancelled(true)
                        return
                    }
                }

                if ( Config.warUseBlacklist ) {
                    val chunk = event.block.chunk
                    val town = Nodes.getTownAtChunkCoord(chunk.x, chunk.z)
                    if ( town !== null && Config.warBlacklist.contains(town.uuid) ) {
                        event.setCancelled(true)
                        return
                    }
                }
            }
        }

        if ( Nodes.war.enabled ) {
            // check if flag destroyed
            for ( block in event.blockList() ) {
                // check if chunk under attack
                val chunk = Nodes.getTerritoryChunkFromBlock(block.x, block.z)
                if ( chunk?.attacker !== null ) {
                    var attack = FlagWar.blockToAttacker.get(block)

                    // check if block above was flag block (e.g. destroying fence)
                    if ( attack === null ) {
                        attack = FlagWar.blockToAttacker.get(block.getRelative(0, 1, 0))
                    }
                    
                    if ( attack !== null ) {
                        attack.cancel()
                        Message.broadcast("${ChatColor.GOLD}[War] Attack at (${block.x}, ${block.y}, ${block.z}) stopped by an explosion")
                    }
                }
            }
        }
    }
}

/**
 * Permissions for unclaimed territories or empty areas (no territories)
 */
private fun hasWildernessPermissions(territory: Territory?): Boolean {
    if ( territory !== null && Config.canInteractInUnclaimed ) {
        return true
    }
    else if ( Config.canInteractInEmpty ) {
        return true
    }

    return false
}

/**
 * Default permissions check for town:
 * perms: town permissions type
 * town: town
 * player: player interacting in town
 */
private fun hasTownPermissions(perms: TownPermissions, town: Town, player: Resident): Boolean {
    if ( town.permissions[perms].contains(PermissionsGroup.TOWN) && player.town === town ) {
        return true
    }
    else if ( town.permissions[perms].contains(PermissionsGroup.TRUSTED) && player.town === town && player.trusted ) {
        return true
    }
    else if ( town.permissions[perms].contains(PermissionsGroup.NATION) && town.nation !== null && player.nation === town.nation ) {
        return true
    }
    else if ( town.permissions[perms].contains(PermissionsGroup.ALLY) && (town.allies.contains(player.town) == true) ) {
        return true
    }
    else if ( town.permissions[perms].contains(PermissionsGroup.OUTSIDER) ) {
        return true
    }

    return false
}

/**
 * Permissions check for a town's territory occupied by another town:
 * perms: town permissions type
 * town: town that owns the territory
 * occupier: town that is occupier of the territory
 * player: player interacting in the territory
 */
private fun hasOccupierPermissions(perms: TownPermissions, town: Town, occupier: Town, player: Resident): Boolean {
    if ( Config.allowControlInOccupiedTownList.contains(town.uuid)  ) {
        return hasTownPermissions(perms, occupier, player)
    } else {
        return false
    }
}

/**
 * Permissions for town protected chests
 * Currently just returns if player.trusted flag if player in town
 */
private fun hasTownProtectedChestPermissions(town: Town, player: Resident): Boolean {
    if ( player.town === town ) {
        if ( player === town.leader ) {
            return true
        }
        else if ( town.officers.contains(player) ) {
            return true
        }
        else {
            return player.trusted
        }
    }
    
    return false
}

// bypass permissions and allow all interaction in
// captured chunks/territories during wartime
private fun hasWarPermissions(resident: Resident, territory: Territory, territoryChunk: TerritoryChunk): Boolean {
    if ( Nodes.war.enabled ) {
        val residentTown = resident.town
        val territoryTown = territory.town

        if ( residentTown !== null ) {
            // extended permissions for allies
            if ( Config.warPermissions ) {
                val residentNation = residentTown.nation
                
                if ( territory.occupier === residentTown ||
                    residentTown.allies.contains(territory.occupier) ||
                    territoryChunk.occupier === residentTown ||
                    territoryChunk.attacker === residentTown ||
                    residentTown.allies.contains(territoryTown) ||
                    residentTown.allies.contains(territoryChunk.occupier) ||
                    residentTown.allies.contains(territoryChunk.attacker)
                ) {
                    return true
                }

                if ( residentNation !== null ) {
                    if ( residentNation === territoryChunk.occupier?.nation ||
                        residentNation === territory.occupier?.nation ||
                        residentNation === territoryChunk.attacker?.nation
                    ) {
                        return true
                    }
                }
            }
            // only let town/nation by default
            else {
                if ( territory.occupier === residentTown || territoryChunk.occupier === residentTown || territoryChunk.attacker === residentTown ) {
                    return true
                }

                val residentNation = residentTown.nation
                if ( residentNation !== null ) {
                    if ( residentNation === territoryChunk.occupier?.nation ||
                        residentNation === territory.occupier?.nation ||
                        residentNation === territoryChunk.attacker?.nation
                    ) {
                        return true
                    }
                }
            }
        }
    }
    
    return false
}

// handle crop harvest and tax event
private fun handleCropHarvest(block: Block) {
    val blockX = block.location.blockX
    val blockZ = block.location.blockZ
    val territory = Nodes.getTerritoryFromBlock(blockX, blockZ)

    if ( territory !== null ) {
        val random = ThreadLocalRandom.current()

        // do tax event check
        val territoryOccupier = territory.occupier
        if ( territoryOccupier !== null && random.nextDouble() <= Config.taxFarmRate ) {
            val items = block.getDrops()
            for ( itemStack in items ) {
                Nodes.addToIncome(territoryOccupier, itemStack.type, itemStack.amount)
            }

            block.setType(Material.AIR)
        }
        // handle town over max claims penalty
        else if ( territory.town?.isOverClaimsMax == true ) {
            if ( random.nextDouble() < Config.overClaimsMaxPenalty ) {
                block.setType(Material.AIR)
            }
        }
    }
}

// handle hidden ore generation during mining
private fun handleHiddenOre(player: Player, block: Block) {
    // ignore hidden ore for silk touch tools
    val inMainHand: ItemStack? = player.inventory.itemInMainHand
    if ( inMainHand?.hasItemMeta() == true && inMainHand.enchantments?.containsKey(Enchantment.SILK_TOUCH) == true ) {
        return
    }

    val blockX = block.location.blockX
    val blockZ = block.location.blockZ
    val blockY = block.location.blockY
    val blockWorld = block.world

    val territory = Nodes.getTerritoryFromBlock(blockX, blockZ)
    
    if ( territory !== null ) {
        val random = ThreadLocalRandom.current()

        val territoryTown = territory.town
        val territoryNation = territoryTown?.nation

        val playerTown = Nodes.getTownFromPlayer(player)
        val playerNation = playerTown?.nation

        // conditions allowed for mining ore
        if ( ( Config.allowOreInWilderness && territoryTown === null ) ||
             ( territoryTown !== null && territoryTown === playerTown ) ||
             ( Config.allowOreInNationTowns && territoryNation !== null && territoryNation === playerNation ) ||
             ( Config.allowOreInCaptured && territory.occupier === playerTown )
        ) {
            val itemDrops = territory.ores.sample(blockY)

            // do tax event check
            val territoryOccupier = territory.occupier
            if ( territoryOccupier !== null && random.nextDouble() <= Config.taxMineRate ) {
                for ( itemStack in itemDrops ) {
                    Nodes.addToIncome(territoryOccupier, itemStack.type, itemStack.amount)
                }
            }
            // else, drop items normally
            else {
                // check if town has claims penalty
                if ( playerTown?.isOverClaimsMax == true ) {
                    if ( random.nextDouble() < Config.overClaimsMaxPenalty ) {
                        return
                    }
                }
                
                for ( itemStack in itemDrops ) {
                    blockWorld.dropItem(block.location, itemStack)
                }
            }
        }
    }
}

/**
 * Return if a block is within a war attack flag's no build region
 */
private fun blockInWarFlagNoBuildRegion(block: Block, attack: Attack): Boolean {
    val x = block.x
    val y = block.y
    val z = block.z

    if ( x < attack.noBuildXMin || x > attack.noBuildXMax ) {
        return false
    }
    if ( y < attack.noBuildYMin || y > attack.noBuildYMax ) {
        return false
    }
    if ( z < attack.noBuildZMin || z > attack.noBuildZMax ) {
        return false
    }

    return true
}