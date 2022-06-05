/**
 * Flag war implementation conceptually based on
 * Towny flag war:
 * https://github.com/TownyAdvanced/Towny/tree/master/src/com/palmergames/bukkit/towny/war/flagwar
 * 
 * War handled by placing a "flag" block onto a chunk to start
 * a "conquer" timer. When timer ends, the chunk is claimed
 * by the attacker's town.
 * 
 * When a territory's core is taken, the territory is converted
 * to "occupied" status by the attacking town.
 * 
 * Flag block object:
 *     i       <- torch for light (so players can see it)
 *    [ ]      <- wool beacon block (destroy to cancel)
 *     |       <- initial item placed to start claim
 * 
 * ----
 * i dont even fully understand save architecture anymore after 6 months
 * hope this doesnt break during war time :^)
 * t. xeth
 */

package phonon.nodes.war

import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.nio.file.Files
import kotlin.system.measureNanoTime
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.ChatColor
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.boss.*
import org.bukkit.entity.Player
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.event.Listener
import org.bukkit.scheduler.BukkitTask
import org.bukkit.command.CommandSender
import phonon.nodes.Nodes
import phonon.nodes.Message
import phonon.nodes.Config
import phonon.nodes.objects.Coord
import phonon.nodes.objects.Territory
import phonon.nodes.objects.TerritoryChunk
import phonon.nodes.objects.Town
import phonon.nodes.event.*
import phonon.nodes.constants.*
import phonon.blockedit.FastBlockEditSession

// beacon color: wool material data values
// corresponding to each 10% progress interval
// 1.12 wool block data
private val WOOL_COLORS: Array<Byte> = arrayOf(
    15,  // black         [0.0, 0.1]
    7,   // gray          [0.1, 0.2]
    8,   // light gray    [0.2, 0.3]
    11,  // blue          [0.3, 0.4]
    10,  // purple        [0.4, 0.5]
    2,   // magenta       [0.5, 0.6]
    6,   // pink          [0.6, 0.7]
    14,  // red           [0.7, 0.8]
    1,   // orange        [0.8, 0.9]
    4    // yellow        [0.9, 1.0]
)

// 1.16 direct material refs
private val FLAG_COLORS: Array<Material> = arrayOf(
    Material.BLACK_WOOL,      // [0.0, 0.1]
    Material.GRAY_WOOL,       // [0.1, 0.2]
    Material.LIGHT_GRAY_WOOL, // [0.2, 0.3]
    Material.BLUE_WOOL,       // [0.3, 0.4]
    Material.PURPLE_WOOL,     // [0.4, 0.5]
    Material.MAGENTA_WOOL,    // [0.5, 0.6]
    Material.PINK_WOOL,       // [0.6, 0.7]
    Material.RED_WOOL,        // [0.7, 0.8]
    Material.ORANGE_WOOL,     // [0.8, 0.9]
    Material.YELLOW_WOOL      // [0.9, 1.0]
)

// private val BEACON_COLOR_BLOCK = Material.WOOL     // 1.12 only
// private val BEACON_EDGE_BLOCK = Material.GLOWSTONE // 1.12 use glowstone
private val SKY_BEACON_FRAME_BLOCK = Material.MAGMA_BLOCK // 1.16 use magma

// contain all flag materials for sky beacon
private val SKY_BEACON_MATERIALS: EnumSet<Material> = EnumSet.of(
    SKY_BEACON_FRAME_BLOCK,
    Material.BLACK_WOOL,      // flag wool stuff
    Material.GRAY_WOOL, 
    Material.LIGHT_GRAY_WOOL,
    Material.BLUE_WOOL,  
    Material.PURPLE_WOOL,
    Material.MAGENTA_WOOL,
    Material.PINK_WOOL, 
    Material.RED_WOOL,  
    Material.ORANGE_WOOL, 
    Material.YELLOW_WOOL  
)

/**
 * Set flag colored block
 * 
 * Color based on attack progress
 */
private fun setFlagAttackColorBlock(block: Block, progress: Int) {
    if ( progress < 0 || progress > 9 ) {
        return
    }

    // 1.12
    // block.setType(Material.WOOL)
    // block.setData(WOOL_COLORS[progress])

    block.setType(FLAG_COLORS[progress])
}

public object FlagWar {

    // ============================================
    // war settings
    // ============================================
    // flag that war is turned on
    internal var enabled: Boolean = false

    // allow annexing territories during war (disable for border skirmishes)
    internal var canAnnexTerritories: Boolean = false

    // only allow attacking border territories, cannot go deeper in
    internal var canOnlyAttackBorders: Boolean = false

    // TODO: war permissions, can create/destroy during war
    internal var destructionEnabled: Boolean = false

    // ticks for the save task
    public var saveTaskPeriod: Long = 20
    // ============================================

    // minecraft plugin variable
    internal var plugin: JavaPlugin? = null

    // flag items that can be used to claim during war
    internal val flagMaterials: EnumSet<Material> = EnumSet.noneOf(Material::class.java)

    // flag sky beacon size, must be in [2, 16]
    internal var skyBeaconSize: Int = 6

    // map attacker UUID -> List of Attack instances
    internal val attackers: HashMap<UUID, ArrayList<Attack>> = hashMapOf()

    // map chunk -> Attack instance
    internal val chunkToAttacker: ConcurrentHashMap<Coord, Attack> = ConcurrentHashMap()

    // map flag block -> Attack instance (for cancelling attacks)
    internal val blockToAttacker: HashMap<Block, Attack> = hashMapOf()

    // set of all occupied chunks
    internal val occupiedChunks: MutableSet<Coord> = ConcurrentHashMap.newKeySet() // create concurrent set from ConcurrentHashMap

    // attack/flag update tick interval
    internal val ATTACK_TICK: Long = 20

    // flag that save required
    internal var needsSave: Boolean = false

    // periodic task to check for save
    internal var saveTask: BukkitTask? = null

    public fun initialize(flagMaterials: EnumSet<Material>) {
        FlagWar.flagMaterials.addAll(flagMaterials)
        FlagWar.skyBeaconSize = Math.max(2, Math.min(16, Config.flagBeaconSize))
    }

    /**
     * Print info to sender about current war state
     */
    public fun printInfo(sender: CommandSender, detailed: Boolean = false) {
        val status = if ( Nodes.war.enabled == true ) "enabled" else "${ChatColor.GRAY}disabled"
        Message.print(sender, "${ChatColor.BOLD}Nodes war status: ${status}")
        if ( Nodes.war.enabled ) {
            Message.print(sender, "- Can Annex Territories${ChatColor.WHITE}: ${Nodes.war.canAnnexTerritories}")
            Message.print(sender, "- Can Only Attack Borders${ChatColor.WHITE}: ${Nodes.war.canOnlyAttackBorders}")
            Message.print(sender, "- Destruction Enabled${ChatColor.WHITE}: ${Nodes.war.destructionEnabled}")
            if ( detailed ) {
                Message.print(sender, "- Using Towns Whitelist${ChatColor.WHITE}: ${Config.warUseWhitelist}")
                Message.print(sender, "- Can leave town${ChatColor.WHITE}: ${Config.canLeaveTownDuringWar}")
                Message.print(sender, "- Can create town${ChatColor.WHITE}: ${Config.canCreateTownDuringWar}")
                Message.print(sender, "- Can destroy town${ChatColor.WHITE}: ${Config.canDestroyTownDuringWar}")
                Message.print(sender, "- Annex disabled${ChatColor.WHITE}: ${Config.annexDisabled}")
            }
        }
    }

    /**
     * Async save loop task
     */
    internal object SaveLoop: Runnable {
        override public fun run() {
            if ( FlagWar.needsSave ) {
                FlagWar.needsSave = false
                WarSerializer.save(true)
            }
        }
    }

    /**
     * Load war state from .json file
     */
    internal fun load() {
        // clear all maps
        FlagWar.attackers.clear()
        FlagWar.chunkToAttacker.clear()
        FlagWar.blockToAttacker.clear()
        FlagWar.occupiedChunks.clear()

        if ( Files.exists(Config.pathWar) ) {
            WarDeserializer.fromJson(Config.pathWar)
        }

        if ( FlagWar.enabled ) {
            if ( FlagWar.canOnlyAttackBorders ) {
                Message.broadcast("${ChatColor.DARK_RED}${ChatColor.BOLD}Nodes border skirmishing enabled")
            }
            else {
                Message.broadcast("${ChatColor.DARK_RED}${ChatColor.BOLD}Nodes war enabled")
            }
        }
    }

    /**
     * Load an occupied chunk from json
     */
    internal fun loadOccupiedChunk(townName: String, coord: Coord) {
        // get town
        val town = Nodes.towns.get(townName)
        if ( town == null ) {
            return
        }

        // get territory chunk
        val terrChunk = Nodes.getTerritoryChunkFromCoord(coord)
        if ( terrChunk == null ) {
            return
        }

        // mark chunk occupied
        terrChunk.occupier = town
        FlagWar.occupiedChunks.add(terrChunk.coord)
    }

    // load an in-progress attack from json
    // attacker - player UUID
    // coord - chunk coord
    // flagBase - flag fence block
    // progress - current progress in ticks
    internal fun loadAttack(
        attacker: UUID,
        coord: Coord,
        flagBase: Block,
        progress: Long
    ) {
        val skyBeaconColorBlocks: MutableList<Block> = mutableListOf()
        val skyBeaconWireframeBlocks: MutableList<Block> = mutableListOf()

        val progressNormalized = progress.toDouble() / Config.chunkAttackTime.toDouble()
        val progressColor = FlagWar.getProgressColor(progressNormalized)

        // recreate sky beacon
        FlagWar.createAttackBeacon(
            skyBeaconColorBlocks,
            skyBeaconWireframeBlocks,
            flagBase.world,
            coord,
            flagBase.y,
            progressColor,
            true,
            true,
            true
        )

        // get resident and their town
        val attackerResident = Nodes.getResidentFromUUID(attacker)
        if ( attackerResident == null ) {
            return
        }
        val attackingTown = attackerResident.town
        if ( attackingTown == null ) {
            return
        }

        // get territory chunk
        val chunk = Nodes.getTerritoryChunkFromCoord(coord)
        if ( chunk == null ) {
            return
        }

        // create attack
        FlagWar.createAttack(
            attacker,
            attackingTown,
            chunk,
            flagBase,
            progress,
            skyBeaconColorBlocks,
            skyBeaconWireframeBlocks
        )

    }

    // cleanup when Nodes plugin disabled
    internal fun cleanup() {
        // stop save task
        FlagWar.saveTask?.cancel()
        FlagWar.saveTask = null

        // remove all progress bars from players
        for ( attack in FlagWar.chunkToAttacker.values ) {
            attack.progressBar.removeAll()
        }

        // save current war state
        if ( Nodes.initialized && FlagWar.enabled ) {
            WarSerializer.save(false)
        }

        // disable war
        FlagWar.enabled = false

        // iterate chunks and stop current attacks
        for ( (coord, attack) in FlagWar.chunkToAttacker ) {
            val chunk = Nodes.getTerritoryChunkFromCoord(coord)
            if ( chunk !== null ) {
                chunk.attacker = null
                chunk.occupier = null
            }
            attack.thread.cancel()
            FlagWar.cancelAttack(attack)
        }

        // clear occupied chunks
        for ( coord in FlagWar.occupiedChunks ) {
            val chunk = Nodes.getTerritoryChunkFromCoord(coord)
            if ( chunk !== null ) {
                chunk.attacker = null
                chunk.occupier = null
            }
        }

        // clear all maps
        FlagWar.attackers.clear()
        FlagWar.chunkToAttacker.clear()
        FlagWar.blockToAttacker.clear()
        FlagWar.occupiedChunks.clear()
    }

    /**
     * Enable war, set war state flags
     */
    internal fun enable(canAnnexTerritories: Boolean, canOnlyAttackBorders: Boolean, destructionEnabled: Boolean) {
        FlagWar.enabled = true
        FlagWar.canAnnexTerritories = canAnnexTerritories
        FlagWar.canOnlyAttackBorders = canOnlyAttackBorders
        FlagWar.destructionEnabled = destructionEnabled

        // create task
        FlagWar.saveTask?.cancel()
        FlagWar.saveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(Nodes.plugin!!, FlagWar.SaveLoop, FlagWar.saveTaskPeriod, FlagWar.saveTaskPeriod)
    }

    /**
     * Disable war, cleanup war state
     */
    internal fun disable() {
        FlagWar.enabled = false
        FlagWar.canAnnexTerritories = false
        
        // kill save task
        FlagWar.saveTask?.cancel()
        FlagWar.saveTask = null
        
        // iterate chunks and stop current attacks
        for ( (coord, attack) in FlagWar.chunkToAttacker ) {
            val chunk = Nodes.getTerritoryChunkFromCoord(coord)
            if ( chunk !== null ) {
                chunk.attacker = null
                chunk.occupier = null
            }
            attack.thread.cancel()
            FlagWar.cancelAttack(attack)
        }

        // clear occupied chunks
        for ( coord in FlagWar.occupiedChunks ) {
            val chunk = Nodes.getTerritoryChunkFromCoord(coord)
            if ( chunk !== null ) {
                chunk.attacker = null
                chunk.occupier = null
            }
        }

        // clear all maps
        FlagWar.attackers.clear()
        FlagWar.chunkToAttacker.clear()
        FlagWar.blockToAttacker.clear()
        FlagWar.occupiedChunks.clear()

        // save war.json (empty)
        WarSerializer.save(true)
    }

    // initiate attack on a territory chunk:
    // 1. check chunk is valid target, flag placement valid,
    //    and player can attack
    // 2. create and run attack timer thread
    internal fun beginAttack(attacker: UUID, attackingTown: Town, chunk: TerritoryChunk, flagBase: Block): Result<Attack> {
        val world = flagBase.getWorld()
        val flagBaseX = flagBase.x
        val flagBaseY = flagBase.y
        val flagBaseZ = flagBase.z
        val territory = chunk.territory
        val territoryTown = territory.town

        // run checks that chunk attack is valid
        
        // check chunk has a town
        if ( territoryTown === null ) {
            return Result.failure(ErrorNotEnemy)
        }

        // check if town blacklisted
        if ( Config.warUseBlacklist && Config.warBlacklist.contains(territoryTown.uuid) ) {
            return Result.failure(ErrorTownBlacklisted)
        }

        // check if town not whitelisted
        if ( Config.warUseWhitelist ) {
            if ( !Config.warWhitelist.contains(territoryTown.uuid) || (Config.onlyWhitelistCanClaim && !Config.warWhitelist.contains(attackingTown.uuid)) ) {
                return Result.failure(ErrorTownNotWhitelisted)
            }
        }

        // check chunk not currently under attack
        if ( chunk.attacker !== null ) {
            return Result.failure(ErrorAlreadyUnderAttack)
        }
        
        // check chunk not already captured by town or allies
        if ( chunkAlreadyCaptured(chunk, territory, attackingTown) ) {
            return Result.failure(ErrorAlreadyCaptured)
        }

        // check chunk either:
        // 1. belongs to enemy
        // 2. town chunk occupied by enemy
        // 3. allied chunk occupied by enemy
        if ( chunkIsEnemy(chunk, territory, attackingTown) ) {

            // check for only attacking border territories
            if ( FlagWar.canOnlyAttackBorders && !FlagWar.isBorderTerritory(territory) ) {
                return Result.failure(ErrorNotBorderTerritory)
            }

            // check that chunk valid, either:
            // 1. next to wilderness
            // 2. next to occupied chunk (by town or allies)
            if ( FlagWar.chunkIsAtEdge(chunk, attackingTown) == false ) {
                return Result.failure(ErrorChunkNotEdge)
            }

            // check that there is room to create flag
            if ( flagBaseY >= 253 ) { // need room for wool + torch
                return Result.failure(ErrorFlagTooHigh)
            }

            // check flag has vision to sky
            for ( y in flagBaseY+1..255 ) {
                if ( !world.getBlockAt(flagBaseX, y, flagBaseZ).isEmpty() ) {
                    return Result.failure(ErrorSkyBlocked)
                }
            }

            // attacker's current attacks (if any exist)
            var currentAttacks = FlagWar.attackers.get(attacker)
            if ( currentAttacks == null ) {
                currentAttacks = ArrayList(Config.maxPlayerChunkAttacks) // set initial capacity = max attacks
                FlagWar.attackers.put(attacker, currentAttacks)
            }
            else if ( currentAttacks.size >= Config.maxPlayerChunkAttacks ) {
                return Result.failure(ErrorTooManyAttacks)
            }

            // send attack event, allow other plugins to custom cancel flag attack
            val event = WarAttackStartEvent(
                attacker,
                attackingTown,
                territory,
                flagBase
            )
            Bukkit.getPluginManager().callEvent(event);
            if ( event.isCancelled() ) {
                return Result.failure(ErrorAttackCustomCancel)
            }

            val attack = createAttack(
                attacker,
                attackingTown,
                chunk,
                flagBase,
                0L
            )
            
            // mark that save required
            FlagWar.needsSave = true

            return Result.success(attack)
        }
        else {
            return Result.failure(ErrorNotEnemy)
        }
    }
    
    // actually creates attack instance
    // shared between beginAttack() and loadAttack()
    internal fun createAttack(
        attacker: UUID,
        attackingTown: Town,
        chunk: TerritoryChunk,
        flagBase: Block,
        progress: Long,
        skyBeaconColorBlocksInput: MutableList<Block>? = null,
        skyBeaconWireframeBlocksInput: MutableList<Block>? = null
    ): Attack {

        val world = flagBase.getWorld()
        val flagBaseX = flagBase.x
        val flagBaseY = flagBase.y
        val flagBaseZ = flagBase.z
        val territory = chunk.territory

        val flagBlock = world.getBlockAt(flagBaseX, flagBaseY + 1, flagBaseZ)
        val flagTorch = world.getBlockAt(flagBaseX, flagBaseY + 2, flagBaseZ)
        val progressBar = Bukkit.getServer().createBossBar("Attacking ${territory.town!!.name} at (${flagBaseX}, ${flagBaseY}, ${flagBaseZ})", BarColor.YELLOW, BarStyle.SOLID)
        
        // calculate max attack time based on chunk
        var attackTime = Config.chunkAttackTime.toDouble()
        if ( territory.bordersWilderness ) {
            attackTime *= Config.chunkAttackFromWastelandMultiplier
        }
        if ( territory.id == territory.town?.home ) {
            attackTime *= Config.chunkAttackHomeMultiplier
        }

        // get sky beacon blocks
        val skyBeaconColorBlocks: MutableList<Block> = if ( skyBeaconColorBlocksInput === null ) {
            mutableListOf()
        } else {
            skyBeaconColorBlocksInput
        }
        val skyBeaconWireframeBlocks: MutableList<Block> = if ( skyBeaconWireframeBlocksInput === null ) {
            mutableListOf()
        } else {
            skyBeaconWireframeBlocksInput
        }

        if ( skyBeaconColorBlocksInput === null || skyBeaconWireframeBlocksInput === null ) {
            FlagWar.createAttackBeacon(
                skyBeaconColorBlocks,
                skyBeaconWireframeBlocks,
                world,
                chunk.coord,
                flagBaseY,
                0,
                true, // create frame
                true, // create color
                true  // lighting update
            )
        }
        
        // no flag base block, set to default
        if ( !Config.flagMaterials.contains(flagBase.type) ) {
            flagBase.setType(Config.flagMaterialDefault)
        }

        // initialize flag blocks
        setFlagAttackColorBlock(flagBlock, 0)
        flagTorch.setType(Material.TORCH)

        // create new attack instance
        val attack = Attack(
            attacker,
            attackingTown,
            chunk.coord,
            flagBase,
            flagBlock,
            flagTorch,
            skyBeaconColorBlocks.toList(),
            skyBeaconWireframeBlocks.toList(),
            progressBar,
            attackTime.toLong(),
            progress
        )

        // mark territory chunk under attack
        chunk.attacker = attackingTown

        // enable boss bar for player
        val player = Bukkit.getPlayer(attacker)
        if ( player != null ) {
            attack.progressBar.addPlayer(player)
        }

        // add attack to list of attacks by attacker
        var currentAttacks = FlagWar.attackers.get(attacker)
        if ( currentAttacks == null ) {
            currentAttacks = ArrayList(Config.maxPlayerChunkAttacks) // set initial capacity = max attacks
            FlagWar.attackers.put(attacker, currentAttacks)
        }
        currentAttacks.add(attack)

        // map chunk to the attack
        FlagWar.chunkToAttacker.put(chunk.coord, attack)

        // map flag block to attack (for breaking)
        FlagWar.blockToAttacker.put(flagBlock, attack)

        return attack
    }
    
    // check if territory is a border territory of a town, requirements:
    // any adjacent territory is not of the same town
    internal fun isBorderTerritory(territory: Territory): Boolean {
        // do not allow attacking home territory
        val territoryTown = territory.town
        if ( territoryTown !== null && territoryTown.home == territory.id ) {
            return false
        }

        // territory borders wilderness (no territories)
        if ( territory.bordersWilderness ) {
            return true
        }

        // otherwise, check if any neighbor territory is not owned by the town
        for ( neighborTerritoryId in territory.neighbors ) {
            val neighborTerritory = Nodes.territories[neighborTerritoryId]
            if ( neighborTerritory !== null && neighborTerritory.town !== territoryTown ) {
                return true
            }
        }

        return false
    }

    // check if chunk was already captured
    // 1. territory occupied by town or allies and chunk not occupied
    // 2. chunk occupied by town or allies
    internal fun chunkAlreadyCaptured(chunk: TerritoryChunk, territory: Territory, attackingTown: Town): Boolean {
        val territoryOccupier = territory.occupier
        val chunkOccupier = chunk.occupier

        if ( territoryOccupier === attackingTown || attackingTown.allies.contains(territoryOccupier) ) {
            if ( !attackingTown.enemies.contains(chunkOccupier) ) {
                return true
            }
        }
        
        if ( chunkOccupier !== null ) {
            if ( chunkOccupier === attackingTown || attackingTown.allies.contains(chunkOccupier) ) {
                return true
            }
        }

        return false
    }

    // check chunk belongs to an enemy and can be attacked:
    // 1. belongs to enemy town
    // 2. town chunk occupied by enemy
    // 3. allied chunk occupied by enemy
    // 4. town's occupied territory, chunk occupied by enemy
    // 5. ally's occupied territory, chunk occupied by enemy
    internal fun chunkIsEnemy(chunk: TerritoryChunk, territory: Territory, attackingTown: Town): Boolean {
        if ( attackingTown.enemies.contains(territory.town) ) {
            return true
        }

        val attackingNation = attackingTown.nation
        val territoryNation = territory.town?.nation

        // your town, nation, or ally town chunk occupied by enemy
        if ( ( territory.town === attackingTown ) ||
             ( attackingNation !== null && attackingNation === territoryNation) ||
             ( attackingTown.allies.contains(territory.town) )
        ) {
            if ( attackingTown.enemies.contains(territory.occupier) ) {
                return true
            }
            if ( attackingTown.enemies.contains(chunk.occupier) ) {
                return true
            }
        }

        // your occupied territory or ally's occupied territory
        // chunk occupied by enemy
        val occupier = territory.occupier
        val occupierNation = occupier?.nation
        if ( occupier === attackingTown ||
             (attackingNation !== null && attackingNation === occupierNation ) ||
             attackingTown.allies.contains(occupier)
        ) {
            if ( attackingTown.enemies.contains(chunk.occupier) ) {
                return true
            }
        }

        return false
    }

    // check that chunk valid, either:
    // 1. next to wilderness
    // 2. next to occupied chunk (by town or allies)
    internal fun chunkIsAtEdge(chunk: TerritoryChunk, attackingTown: Town): Boolean {
        val coord = chunk.coord

        val chunkNorth = Nodes.getTerritoryChunkFromCoord(Coord(coord.x, coord.z - 1))
        val chunkSouth = Nodes.getTerritoryChunkFromCoord(Coord(coord.x, coord.z + 1))
        val chunkWest = Nodes.getTerritoryChunkFromCoord(Coord(coord.x - 1, coord.z))
        val chunkEast = Nodes.getTerritoryChunkFromCoord(Coord(coord.x + 1, coord.z))

        if ( canAttackFromNeighborChunk(chunkNorth, attackingTown) ||
             canAttackFromNeighborChunk(chunkSouth, attackingTown) ||
             canAttackFromNeighborChunk(chunkWest, attackingTown) ||
             canAttackFromNeighborChunk(chunkEast, attackingTown) ) {
            return true
        }

        return false
    }

    /**
     * conditions for attacking a chunk relative to a neighbor chunk
     */
    internal fun canAttackFromNeighborChunk(neighborChunk: TerritoryChunk?, attacker: Town): Boolean {
        
        // no territory here
        if ( neighborChunk === null ) {
            return true
        }

        val attackerNation = attacker.nation

        val neighborTerritory = neighborChunk.territory
        val neighborTown = neighborTerritory.town
        val neighborTerritoryOccupier = neighborTerritory.occupier
        val neighborChunkOccupier = neighborChunk.occupier

        // territory is unoccupied
        if ( neighborTown === null ) {
            return true
        }

        // neighbor is your town and occupier is friendly
        if ( neighborTown === attacker ) {
            if ( neighborTerritoryOccupier === null ) {
                return true
            }
            else if ( attacker.allies.contains(neighborTerritoryOccupier) ) {
                return true
            }
        }
        
        // you are neighbor territory occupier or an ally is the occupier
        if ( neighborTerritoryOccupier === attacker || attacker.allies.contains(neighborTerritoryOccupier) ) {
            return true
        }

        // you or an ally is occupying the neighboring chunk
        if ( neighborChunkOccupier === attacker || attacker.allies.contains(neighborChunkOccupier) == true ) {
            return true
        }

        if ( attackerNation !== null ) {
            val neighborNation = neighborTown.nation
            val neighborTerritoryOccupierNation = neighborTerritoryOccupier?.nation
            val neighborChunkOccupierNation = neighborChunk.occupier?.nation

            // additional neighbor town check, when occupier is in same nation (somehow)
            if ( neighborTown === attacker && neighborNation === neighborTerritoryOccupierNation ) {
                return true
            }

            // neighboring chunk belongs to nation and occupied by friendly
            if ( attackerNation === neighborNation ) {
                if ( neighborTerritoryOccupier === null ) {
                    return true
                }
                else if ( attacker.allies.contains(neighborTerritoryOccupier) ) {
                    return true
                }
            }

            if ( attackerNation === neighborTerritoryOccupierNation ) {
                return true
            }

            if ( attackerNation === neighborChunkOccupierNation ) {
                return true
            }
        }

        return false
    }

    /**
     * Create/update a flag attack beacon
     * Uses an fast editing session with single packet send and
     * no lighting updates
     * - with block.setType(): takes ~2 ms to update
     * - with edit session: takes ~200-300 us to update
     */
    internal fun createAttackBeacon(
        skyBeaconColorBlocks: MutableList<Block>,
        skyBeaconWireframeBlocks: MutableList<Block>,
        world: World,
        coord: Coord,
        flagBaseY: Int,
        progress: Int,
        createFrame: Boolean,
        createColor: Boolean,
        updateLighting: Boolean
    ) {
        // create edit session
        val edit = FastBlockEditSession(world)

        // get starting corner
        val size = FlagWar.skyBeaconSize
        val startPositionInChunk: Int = (16 - size)/2
        val x0: Int = coord.x * 16 + startPositionInChunk
        val z0: Int = coord.z * 16 + startPositionInChunk
        val y0: Int = Math.max(flagBaseY + Config.flagBeaconSkyLevel, Config.flagBeaconMinSkyLevel)
        val xEnd: Int = x0 + size - 1
        val zEnd: Int = z0 + size - 1
        val yEnd: Int = Math.min(255, y0 + size) // truncate at map limit
        
        // max color
        val progressColor = Math.min(progress, FLAG_COLORS.size - 1)

        for ( y in y0..yEnd ) {
            for ( x in x0..xEnd ) {
                for ( z in z0..zEnd ) {
                    val block = world.getBlockAt(x, y, z)
                    val mat = block.getType()
                    if ( mat == Material.AIR || SKY_BEACON_MATERIALS.contains(mat) ) {
                        if ( (( y == y0 || y == yEnd ) && ( x == x0 || x == xEnd || z == z0 || z == zEnd )) || // end caps, edges glowstone
                             (( x == x0 || x == xEnd ) && ( z == z0 || z == zEnd )) ) {  // middle section corners glowstone
                            // block.setType(BEACON_EDGE_BLOCK) // slow
                            skyBeaconWireframeBlocks.add(block)
                            if ( createFrame ) {
                                edit.setBlock(x, y, z, SKY_BEACON_FRAME_BLOCK)
                            }
                        }
                        else { // color block
                            // setFlagAttackColorBlock(block, progress) // slow
                            skyBeaconColorBlocks.add(block)
                            if ( createColor ) {
                                edit.setBlock(x, y, z, FLAG_COLORS[progressColor])
                            }
                        }
                    }
                }
            }
        }

        if ( createFrame || createColor ) {
            edit.update(updateLighting)
        }
    }

    /**
     * Update flag colors blocks based on progress color
     */
    internal fun updateAttackFlag(
        flagBlock: Block,
        skyBeaconColorBlocks: List<Block>,
        progressColor: Int
    ) {
        val world = flagBlock.getWorld()

        // create edit session
        val edit = FastBlockEditSession(world)

        edit.setBlock(flagBlock.x, flagBlock.y, flagBlock.z, FLAG_COLORS[progressColor])
        for ( block in skyBeaconColorBlocks ) {
            edit.setBlock(block.x, block.y, block.z, FLAG_COLORS[progressColor])
        }

        // dont do lighting update
        edit.update(false)
    }

    // cleanup attack instance, then dispatch signal
    // that attack cancelled (was defended)
    // (runs on main thread)
    // TODO: signal event that chunk defended (broadcast message)
    internal fun cancelAttack(attack: Attack) {
        // remove status from territory chunk
        val chunk = Nodes.getTerritoryChunkFromCoord(attack.coord)
        chunk?.attacker = null

        // remove progress bar from player
        attack.progressBar.removeAll()

        // remove claim flag
        attack.flagTorch.setType(Material.AIR)
        attack.flagBlock.setType(Material.AIR)
        attack.flagBase.setType(Material.AIR)

        // remove sky beacon
        for ( block in attack.skyBeaconWireframeBlocks ) {
            block.setType(Material.AIR)
        }
        for ( block in attack.skyBeaconColorBlocks ) {
            block.setType(Material.AIR)
        }

        // remove attack instance references
        FlagWar.attackers.get(attack.attacker)?.remove(attack)
        FlagWar.chunkToAttacker.remove(attack.coord)
        FlagWar.blockToAttacker.remove(attack.flagBlock)
        
        // mark save needed
        FlagWar.needsSave = true

        // run cancel attack event
        if ( chunk !== null ) {
            val event = WarAttackCancelEvent(
                attack.attacker,
                attack.town,
                chunk.territory,
                attack.flagBase
            )
            Bukkit.getPluginManager().callEvent(event)
        }
    }

    /**
     * finish attack instance and capture chunk
     * - calls event which may cancel the capture
     * - set chunk occupation status
     * - dispatch signal that attack finished
     * (runs on main thread)
     * different results:
     *   1. attacking enemy chunk -> capture chunk
     *   2. attacking enemy home chunk -> capture territory
     *   3. attacking town/ally occupied chunk -> capture chunk
     *   4. attacking town/ally home chunk -> recapture territory
     * TODO: signal event that chunk captured (broadcast message)
     */
    internal fun finishAttack(attack: Attack) {
        // remove progress bar from player
        attack.progressBar.removeAll()

        // remove claim flag
        attack.flagTorch.setType(Material.AIR)
        attack.flagBlock.setType(Material.AIR)
        attack.flagBase.setType(Material.AIR)

        // remove sky beacon
        for ( block in attack.skyBeaconWireframeBlocks ) {
            block.setType(Material.AIR)
        }
        for ( block in attack.skyBeaconColorBlocks ) {
            block.setType(Material.AIR)
        }

        // remove attack instance references
        FlagWar.attackers.get(attack.attacker)?.remove(attack)
        FlagWar.chunkToAttacker.remove(attack.coord)
        FlagWar.blockToAttacker.remove(attack.flagBlock)

        // mark that save required
        FlagWar.needsSave = true
        
        // chunk should not be null unless territory swapped
        // out during attack and chunks were modified in new territory
        val chunk = Nodes.getTerritoryChunkFromCoord(attack.coord)
        if ( chunk == null ) {
            Nodes.logger?.severe("finishAttack(): TerritoryChunk at ${attack.coord} is null")
            return
        }

        // check if attack finish is cancelled
        val event = WarAttackFinishEvent(
            attack.attacker,
            attack.town,
            chunk.territory,
            attack.flagBase
        )
        Bukkit.getPluginManager().callEvent(event);
        if ( event.isCancelled() ) {
            chunk.attacker = null
            return
        }

        // handle occupation state of chunk
        // if chunk is core chunk of territory, attacking town occupies territory
        if ( chunk.coord == chunk.territory.core ) {
            val territory = chunk.territory
            val territoryTown = territory.town
            val attacker = Nodes.getResidentFromUUID(attack.attacker)
            val attackerTown = attack.town
            val attackerNation = attackerTown.nation

            // cleanup territory chunks
            for ( coord in territory.chunks ) {
                val territoryChunk = Nodes.getTerritoryChunkFromCoord(coord)
                if ( territoryChunk != null ) {
                    // cancel any concurrent attacks in this territory
                    FlagWar.chunkToAttacker.get(territoryChunk.coord)?.cancel()

                    // clear occupy/attack status from chunks
                    territoryChunk.attacker = null
                    territoryChunk.occupier = null
                    
                    // remove from internal list of occupied chunks
                    FlagWar.occupiedChunks.remove(territoryChunk.coord)
                }
            }

            // handle re-capturing your own territory, nation territory, or ally territory from enemy
            if ( territoryTown === attackerTown ||
                 ( attackerNation !== null && attackerNation === territoryTown?.nation ) ||
                 attackerTown.allies.contains(territoryTown)
            ) {
                val occupier = territory.occupier
                Nodes.releaseTerritory(territory)
                Message.broadcast("${ChatColor.DARK_RED}[War] ${attacker?.name} liberated territory (id=${territory.id}) from ${occupier?.name}!")
            }
            // captured enemy territory
            else {
                Nodes.captureTerritory(attackerTown, territory)
                Message.broadcast("${ChatColor.DARK_RED}[War] ${attacker?.name} captured territory (id=${territory.id}) from ${territory.town?.name}!")
            }
            
        }
        // else, attacking normal chunk cases:
        // 1. your town, chunk captured by enemy -> liberating, remove flag
        // 2. your town (occupied) -> liberating, put flag
        // 3. territory occupied by your town, captured -> liberating, remove flag
        // 4. enemy town, empty chunk -> attacking, put flag
        else {
            val town = chunk.territory.town
            val occupier = chunk.territory.occupier
            val attacker = Nodes.getResidentFromUUID(attack.attacker)

            chunk.attacker = null

            if ( town === attack.town ) {                
                // re-capturing territory from occupier
                if ( occupier !== null ) {
                    chunk.occupier = town
                    FlagWar.occupiedChunks.add(chunk.coord)

                    Message.broadcast("${ChatColor.DARK_RED}[War] ${attacker?.name} liberated chunk (${chunk.coord.x}, ${chunk.coord.z}) from ${occupier.name}!")
                }
                // must be defending captured chunk
                else {
                    val chunkOccupier = chunk.occupier

                    chunk.occupier = null
                    FlagWar.occupiedChunks.remove(chunk.coord)

                    if ( chunkOccupier !== null ) {
                        Message.broadcast("${ChatColor.DARK_RED}[War] ${attacker?.name} defended chunk (${chunk.coord.x}, ${chunk.coord.z}) against ${chunkOccupier.name}!")
                    }
                }
            }
            else if ( occupier === attack.town && chunk.occupier !== null ) {
                chunk.occupier = null
                FlagWar.occupiedChunks.remove(chunk.coord)

                Message.broadcast("${ChatColor.DARK_RED}[War] ${attacker?.name} defended chunk (${chunk.coord.x}, ${chunk.coord.z}) against ${chunk.occupier!!.name}!")
            }
            else {
                chunk.occupier = attack.town
                FlagWar.occupiedChunks.add(chunk.coord)

                Message.broadcast("${ChatColor.DARK_RED}[War] ${attacker?.name} captured chunk (${chunk.coord.x}, ${chunk.coord.z}) from ${chunk.territory.town?.name}!")
            }
            
            // update minimaps
            Nodes.renderMinimaps()
        }
    }

    // update tick for attack instance
    // NOTE: this runs in separate thread
    internal fun attackTick(attack: Attack) {
        val progress = attack.progress + FlagWar.ATTACK_TICK

        if ( progress >= attack.attackTime ) {
            // cancel thread, then schedule finalization function on main thread
            attack.thread.cancel()
            Bukkit.getScheduler().runTask(Nodes.plugin!!, object: Runnable {
                override fun run() {
                    FlagWar.finishAttack(attack)
                }
            })
        }
        else { // update
            attack.progress = progress

            // update boss bar progress
            val progressNormalized: Double = progress.toDouble() / attack.attackTime.toDouble()
            attack.progressBar.setProgress(progressNormalized)
            
            val progressColor = (progressNormalized * WOOL_COLORS.size.toDouble()).toInt()
            if ( progressColor != attack.progressColor ) {
                attack.progressColor = progressColor

                // update attack flag + block beacon indicator
                // -> must schedule sync task on main thread
                Bukkit.getScheduler().runTask(Nodes.plugin!!, object: Runnable {
                    override fun run() {
                        FlagWar.updateAttackFlag(
                            attack.flagBlock,
                            attack.skyBeaconColorBlocks,
                            progressColor
                        )
                    }
                })
            }
        }
    }

    // intended to run on PlayerJoin event
    // if war enabled and player has attacks,
    // send progress bars to player
    public fun sendWarProgressBarToPlayer(player: Player) {
        val uuid = player.getUniqueId()
        
        // add attack to list of attacks by attacker
        var currentAttacks = FlagWar.attackers.get(uuid)
        if ( currentAttacks != null ) {
            for ( attack in currentAttacks ) {
                attack.progressBar.addPlayer(player)
            }
        }
    }

    /**
     * Return progress color from normalized progress in [0.0, 1.0]
     */
    internal fun getProgressColor(progressNormalized: Double): Int {
        if ( progressNormalized < 0.0 ) {
            return 0
        } else if ( progressNormalized > 1.0 ) {
            return WOOL_COLORS.size - 1
        } else {
            return (progressNormalized * WOOL_COLORS.size.toDouble()).toInt()
        }
    }
}