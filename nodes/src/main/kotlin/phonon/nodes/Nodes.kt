/*
 * Nodes Engine/API
 */

@file:Suppress("NAME_SHADOWING")

package phonon.nodes

import kotlin.system.measureNanoTime
import java.text.SimpleDateFormat
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.Future
import java.io.IOException
import java.io.File
import java.nio.file.*
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.StandardCopyOption
import java.util.logging.Logger
import org.bukkit.plugin.Plugin
import org.bukkit.event.HandlerList
import org.bukkit.entity.Player
import org.bukkit.entity.EntityType
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.block.Chest
import org.bukkit.block.DoubleChest
import org.bukkit.Particle
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.DoubleChestInventory
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.configuration.file.YamlConfiguration
import com.earth2me.essentials.Essentials
import phonon.nodes.constants.*
import phonon.nodes.objects.*
import phonon.nodes.serdes.*
import phonon.nodes.event.*
import phonon.nodes.tasks.*
import phonon.nodes.utils.*
import phonon.nodes.utils.Color
import phonon.nodes.chat.Chat
import phonon.nodes.chat.ChatMode
import phonon.nodes.listeners.NodesPlayerChestProtectListener
import phonon.nodes.war.FlagWar
import phonon.nodes.war.Truce
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashMap
import kotlin.math.ceil
import kotlin.math.floor

// backup format
private val BACKUP_DATE_FORMATTER = SimpleDateFormat("yyyy.MM.dd.HH.mm.ss")

/**
 * Nodes container
 */
object Nodes {
    
    // version string
    internal const val version: String = "1.12.2 v0.0.0"

    // library of resource node definitions
    internal val resourceNodes: HashMap<String, ResourceNode> = hashMapOf()

    // world grid that maps coord -> territory chunk wrapper
    internal val territoryChunks: HashMap<Coord, TerritoryChunk> = hashMapOf()

    // map territory id -> Territory
    internal val territories: HashMap<Int, Territory> = hashMapOf()

    // list of all towns
    internal val towns: LinkedHashMap<String, Town> = LinkedHashMap()

    // list of all nations
    internal val nations: LinkedHashMap<String, Nation> = LinkedHashMap()
    
    // map player UUID -> Resident player wrapper
    internal val residents: LinkedHashMap<UUID, Resident> = LinkedHashMap()

    // last time backup occurred: NOTE this is accessed async
    internal var lastBackupTime: Long = 0 // milliseconds

    // last time income occurred: NOTE this is accessed async
    internal var lastIncomeTime: Long = 0 // milliseconds

    // war manager
    val war = FlagWar

    // flag that world was updated and needs save
    private var needsSave: Boolean = false

    // set of invalid block locations for hidden ore drops
    internal val hiddenOreInvalidBlocks: OreBlockCache = OreBlockCache(2000)

    // hooks to other plugins
    internal var essentials: Essentials? = null
    internal var protocolLib: Boolean = false // flag that protocolLib is loaded
    internal var dynmap: Boolean = false // simple flag
    internal val DYNMAP_DIR: Path = Paths.get("plugins/dynmap/web/nodes")
    internal val DYNMAP_PATH_NODES_CONFIG: Path = Paths.get("plugins/dynmap/web/nodes/config.json")
    internal val DYNMAP_PATH_WORLD: Path = Paths.get("plugins/dynmap/web/nodes/world.json")
    internal val DYNMAP_PATH_TOWNS: Path = Paths.get("plugins/dynmap/web/nodes/towns.json")
    internal val dynmapWriteTask = NodesDynmapJsonWriter(Config.pathTowns, DYNMAP_DIR, DYNMAP_PATH_TOWNS)

    // minecraft plugin variable
    internal var plugin: Plugin? = null
    internal var logger: Logger? = null

    // flag that plugin successfully initialized
    internal var initialized: Boolean = false
    
    // flag that plugin is running
    internal var enabled: Boolean = false
    
    // initialization:
    // - set links to plugin variables
    fun initialize(plugin: Plugin) {
        Nodes.plugin = plugin
        logger = plugin.logger
    }

    /**
     * reload config file
     */
    fun reloadConfig() {
        val plugin = plugin
        if ( plugin === null ) {
            return
        }

        val logger = plugin.logger
        
        // get config file
        val configFile = File(plugin.dataFolder.path, "config.yml")
        if ( !configFile.exists() ) {
            logger.info("No config found: generating default config.yml")
            plugin.saveDefaultConfig()
        }
        
        val config = YamlConfiguration.loadConfiguration(configFile)
        Config.load(config)
    }

    /**
     * Reload background managers/tasks
     */
    fun reloadManagers() {
        SaveManager.stop()
        PeriodicTickManager.stop()
        OverMaxClaimsReminder.stop()
        Nametag.stop()

        val plugin = plugin
        if ( plugin === null ) {
            return
        }

        SaveManager.start(plugin, Config.savePeriod)
        PeriodicTickManager.start(plugin, Config.mainPeriodicTick)
        OverMaxClaimsReminder.start(plugin, Config.overMaxClaimsReminderPeriod)
        Nametag.start(plugin, Config.nametagUpdatePeriod)
    }

    // mark all current players in game as online
    // needed to correctly mark online players after reloading plugin
    fun initializeOnlinePlayers() {
        for ( player in Bukkit.getOnlinePlayers() ) {
            // create resident for player if it does not exist
            createResident(player)

            // mark player online
            val resident = getResident(player)!!
            setResidentOnline(resident, player)

            // create nametag (only when town exists)
            if ( resident.town !== null && protocolLib == true && Config.useNametags ) {
                Nametag.create(player)
            }
        }
    }

    // clean up any world details
    // run when plugin disabled
    fun cleanup() {

        // cleanup residents
        for ( r in residents.values ) {
            // remove minimaps
            r.destroyMinimap()

            // stop chest protect events
            val chestProtectListener = r.chestProtectListener
            if ( chestProtectListener !== null ) {
                HandlerList.unregisterAll(chestProtectListener)
                r.chestProtectListener = null
            }
        }
        
        // force push all town income items from inventory gui
        // back to storage data structure
        for ( town in towns.values ) {
            val result = town.income.pushToStorage(true)
            if ( result == true ) { // has moved items
                town.needsUpdate()
            }
        }

        // cleanup war if its enabled
        if ( war.enabled ) {
            war.cleanup()
        }

        // cleanup nametags
        if ( protocolLib == true && Config.useNametags ) {
            Nametag.clear()
        }

        // save backup, income current time
        val currTimeString = System.currentTimeMillis().toString()
        saveStringToFile(currTimeString, Config.pathLastBackupTime)
        saveStringToFile(currTimeString, Config.pathLastIncomeTime)
    }

    // load world from path
    // returns status of world load:
    // true - successful load
    // false - failed
    fun loadWorld(): Boolean {
        // clear storage
        territoryChunks.clear()
        territories.clear()
        towns.clear()
        nations.clear()
        residents.clear()
        
        // load world from JSON storage
        if ( Files.exists(Config.pathWorld) ) {
            Deserializer.worldFromJson(Config.pathWorld)
            
            // load world.json to dynmap folder
            if ( dynmap == true || Config.dynmapCopyTowns ) {
                Files.createDirectories(DYNMAP_DIR)
                Files.copy(Config.pathWorld, DYNMAP_PATH_WORLD, StandardCopyOption.REPLACE_EXISTING)
            }
            
            // load towns from json after main world load finishes
            if ( Files.exists(Config.pathTowns) ) {                
                Deserializer.townsFromJson(Config.pathTowns)

                // pre-generate initial json strings for all world objects
                // (speeds up first save)
                for ( resident in residents.values ) {
                    resident.getSaveState()
                }
                for ( town in towns.values ) {
                    town.getSaveState()
                }
                for ( nation in nations.values ) {
                    nation.getSaveState()
                }

                // load towns.json to dynmap folder
                if ( dynmap ) {
                    Files.copy(Config.pathTowns, DYNMAP_PATH_TOWNS, StandardCopyOption.REPLACE_EXISTING)
                }

                // load war state
                war.load()
            }
            else {
                System.err.println("No towns found: ${Config.pathTowns}")
                return true
            }
        }
        else {
            System.err.println("Failed to load world: ${Config.pathWorld}")
            return false
        }

        return true
    }

    fun copyWorldtoDynmap() {
        // copy towns to dynmap folder
        if ( dynmap || Config.dynmapCopyTowns ) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin!!, dynmapWriteTask)
        }
    }

    // alternative save world method
    // 1. updates individual object JSON strings on main thread (for thread safety)
    // 2. combines into a full json string on async thread
    fun saveWorldAsync(checkIfNeedsSave: Boolean = true) {
        if ( checkIfNeedsSave == false || needsSave == true ) {

            // world pre-processing
            saveWorldPreprocess()

            val timeUpdate = measureNanoTime {
                for ( v in residents.values ) {
                    v.getSaveState()
                }
                for ( v in towns.values ) {
                    v.getSaveState()
                }
                for ( v in nations.values ) {
                    v.getSaveState()
                }
            }

            println("[Nodes] Saving world: ${timeUpdate}ns")

            // write file in async thread
            // callback: copy to dynmap folder when save done
            // val pathTest = Paths.get(Config.pathPlugin, "towns_test.json").normalize()
            Bukkit.getScheduler().runTaskAsynchronously(plugin!!, object: Runnable {
                val residents = Nodes.residents.values.toList()
                val towns = Nodes.towns.values.toList()
                val nations = Nodes.nations.values.toList()

                override fun run() {
                    val json = Serializer.worldToJson(
                        residents,
                        towns,
                        nations
                    )

                    saveStringToFile(json, Config.pathTowns)

                    copyWorldtoDynmap()
                }
            })

            needsSave = false
        }
    }

    // performs synchronous save of the world
    // much slower, but needed on events that cannot
    // use threads (e.g. on plugin shutdown, scheduler cancelled)
    fun saveWorldSync(): Boolean {
        // world pre-processing
        saveWorldPreprocess()

        // get json string
        val json: String = Serializer.worldToJson(
            residents.values.toList(),
            towns.values.toList(),
            nations.values.toList()
        )

        // write main file
        val fileChannel: AsynchronousFileChannel = AsynchronousFileChannel.open(Config.pathTowns, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        val buffer = ByteBuffer.wrap(json.toByteArray())
        val operation: Future<Int> = fileChannel.write(buffer, 0)
        operation.get()
        
        // copy to dynmap folder
        if ( dynmap ) {
            Files.copy(Config.pathTowns, DYNMAP_PATH_TOWNS, StandardCopyOption.REPLACE_EXISTING)
        }

        // save truce state
        FileWriteTask(Truce.toJsonString(), Config.pathTruce).run()

        return true
    }

    // handle any pre-processing, town/nation modifications before
    // saving to json
    internal fun saveWorldPreprocess() {
        // move all town income items from inventory gui
        // back to storage data structure
        for ( town in towns.values ) {
            val result = town.income.pushToStorage(false)
            if ( result == true ) { // has moved items
                town.needsUpdate()
            }
        }
    }

    /**
     * Run backup
     */
    fun doBackup() {
        val pathTowns = Config.pathTowns
        if ( Files.exists(pathTowns) ) {
            val pathBackupDir = Config.pathBackup
            Files.createDirectories(pathBackupDir) // create backup folder if it does not exist

            // save vehicle file backup
            val date = Date()
            val backupName = "towns.${BACKUP_DATE_FORMATTER.format(date)}.json"
            val pathBackup = pathBackupDir.resolve(backupName)
            Files.copy(pathTowns, pathBackup)
        }
    }

    // initialization function,
    // loads diplomatic relations (allies, enemies, truce)
    // requires all towns, nations already be created
    internal fun loadDiplomacy(
        towns: ArrayList<Town>,
        townAllies: ArrayList<ArrayList<String>>,
        townEnemies: ArrayList<ArrayList<String>>,
//        nations: ArrayList<Nation>,
//        nationAllies: ArrayList<ArrayList<String>>,
//        nationEnemies: ArrayList<ArrayList<String>>
    ) {

        // perform fixes on diplomacy during loading:
        // 1. if towns are in nation, ignore pairs between towns
        //    in nation. instead add nations to nationAlliancePairs 
        // 2. if either town NOT in nation, add to townAlliancePairs

        val townAlliancePairs: HashSet<TownPair> = hashSetOf()
        val nationAlliancePairs: HashSet<NationPair> = hashSetOf()

        val townEnemyPairs: HashSet<TownPair> = hashSetOf()
        val nationEnemyPairs: HashSet<NationPair> = hashSetOf()
        
        for ( (i, town) in towns.withIndex() ) {
            val allies = townAllies[i]
            val enemies = townEnemies[i]

            val townNation = town.nation

            for ( name in allies ) {
                val other = Nodes.towns.get(name)
                if ( other !== null ) {
                    val otherNation = other.nation

                    // only add individual town pair if either does not have a nation
                    if ( townNation === null || otherNation === null ) {
                        townAlliancePairs.add(TownPair(town, other))
                    }
                    // add nation pairs
                    else if ( town === townNation.capital && other === otherNation.capital ) {
                        nationAlliancePairs.add(NationPair(townNation, otherNation))
                    }
                }
            }

            for ( name in enemies ) {
                val other = Nodes.towns.get(name)
                if ( other !== null ) {
                    val otherNation = other.nation

                    // only add individual town pair if either does not have a nation
                    if ( townNation === null || otherNation === null ) {
                        townEnemyPairs.add(TownPair(town, other))
                    }
                    // add nation pairs
                    else if ( town === townNation.capital && other === otherNation.capital ) {
                        nationEnemyPairs.add(NationPair(townNation, otherNation))
                    }
                }
            }
        }

        // apply ally pairs
        for ( townPair in townAlliancePairs ) {
            val town1 = townPair.town1
            val town2 = townPair.town2

            town1.allies.add(town2)
            town2.allies.add(town1)
        }
        
        for ( nationPair in nationAlliancePairs ) {
            val nation1 = nationPair.nation1
            val nation2 = nationPair.nation2

            nation1.allies.add(nation2)
            nation2.allies.add(nation1)

            for ( town1 in nation1.towns ) {
                for ( town2 in nation2.towns ) {
                    town1.allies.add(town2)
                    town2.allies.add(town1)
                }
            }
        }

        // apply enemy pairs
        for ( townPair in townEnemyPairs ) {
            val town1 = townPair.town1
            val town2 = townPair.town2

            town1.enemies.add(town2)
            town2.enemies.add(town1)
        }

        for ( nationPair in nationEnemyPairs ) {
            val nation1 = nationPair.nation1
            val nation2 = nationPair.nation2

            nation1.enemies.add(nation2)
            nation2.enemies.add(nation1)

            for ( town1 in nation1.towns ) {
                for ( town2 in nation2.towns ) {
                    town1.enemies.add(town2)
                    town2.enemies.add(town1)
                }
            }
        }

        // apply alliances between towns in nations
        // for ( nation in Nodes.nations.values ) {
        //     for ( town1 in nation.towns ) {
        //         for ( town2 in nation.towns ) {
        //             if ( town1 !== town2 ) {
                        
        //             }
        //         }
        //     }
        // }

        // load truce from truce config file
        if ( Files.exists(Config.pathTruce) ) {
            try {
                val truceString = String(Files.readAllBytes(Config.pathTruce))
                Truce.fromJsonString(truceString)
            }
            catch ( e: IOException ) {
                e.printStackTrace()
            }
        }
    }

    // ==============================================
    // Resource Node functions
    // ==============================================

    fun createResourceNode(
        name: String,
        icon: String?,
        income: EnumMap<Material, Double>,
        incomeSpawnEgg: EnumMap<EntityType, Double>,
        ores: EnumMap<Material, OreDeposit>,
        crops: EnumMap<Material, Double>,
        animals: EnumMap<EntityType, Double>,
        costConstant: Int,
        costScale: Double
    ) {
        if ( !resourceNodes.containsKey(name) ) {
            resourceNodes.put(name, ResourceNode(name, icon, income, incomeSpawnEgg, ores, crops, animals, costConstant, costScale))
        }
    }

    // return number of resource node types
    fun getResourceNodeCount(): Int {
        return resourceNodes.size
    }

    // forces re-render of online player minimaps
    fun renderMinimaps() {
        for ( player in Bukkit.getOnlinePlayers() ) {
            val resident = getResident(player)
            if ( resident?.minimap != null ) {
                // get current location coord
                val loc = player.location
                val coordX = floor(loc.x).toInt()
                val coordZ = floor(loc.z).toInt()
                val coord = Coord.fromBlockCoords(coordX, coordZ)
                resident.updateMinimap(coord)
            }
        }
    }

    // ==============================================
    // Territory Chunk functions
    // ==============================================
    fun getTerritoryChunkFromBlock(blockX: Int, blockZ: Int): TerritoryChunk? {
        val coord = Coord.fromBlockCoords(blockX, blockZ)
        return territoryChunks.get(coord)
    }

    fun getTerritoryChunkFromCoord(coord: Coord): TerritoryChunk? {
        return territoryChunks.get(coord)
    }

    // ==============================================
    // Territory functions
    // ==============================================

    // create new territory
    // id: identifier of territory (unique)
    // core: chunk coordinate "core" of territory to capture during war
    // chunks: array of chunks in territory
    // resourceNodes: array of string names of resource nodes
    fun createTerritory(
        id: Int,
        name: String,
        color: Int,
        core: Coord,
        chunks: ArrayList<Coord>,
        bordersWilderness: Boolean,
        resourceNodes: ArrayList<String>
    ) {

        // ensure coreChunk inside chunks
        if ( !chunks.contains(core) ) {
            System.err.println("[Nodes] Territory chunks does not contain core")
            return
        }

        // calculate net resources by combining resource nodes
        // use a local temporary resource node for merging
        // use globalResources as baseline resources
        val territoryResources = Config.globalResources.clone()

        resourceNodes.forEach { type -> 
            Nodes.resourceNodes.get(type)?.let { resource ->
                territoryResources.merge(resource)
            }
        }

        // create OreSampler from ores map
        val oresAsArray = ArrayList(territoryResources.ores.values)
        val ores = OreSampler(oresAsArray)

        // calculate territory cost
        val cost = calculateTerritoryCost(chunks.size, resourceNodes)
        
        // create territory
        val territory = Territory(
            id,
            name,
            color,
            core,
            chunks,
            bordersWilderness,
            resourceNodes,
            territoryResources.income,
            territoryResources.incomeSpawnEgg,
            ores,
            territoryResources.crops,
            territoryResources.animals,
            cost
        )
        
        // set territory
        territories.put(id, territory)

        // create territory chunks in world grid and map to territory
        chunks.forEach { c -> 
            territoryChunks.put(c, TerritoryChunk(c, territory))
        }
    }

    // goes through territories and establishes territory-territory
    // neighbor links
    fun setTerritoryNeighbors(neighborGraph: HashMap<Int, List<Int>>) {
        neighborGraph.forEach { id, neighborList -> 
            val territory = territories.get(id)
            if ( territory != null ) {
                territory.neighbors.clear() // clear existing
                neighborList.forEach { neighborId -> 
                    val neighbor = territories.get(neighborId)
                    if ( neighbor != null ) {
                        territory.neighbors.add(neighbor)
                    }
                }
            }
        }
    }

    // calculate territory cost from:
    // 1. territory size in chunks
    // 2. list of resource names
    fun calculateTerritoryCost(size: Int, resourceNodes: ArrayList<String>): Int {
        // initialize with global cost rates
        var costConstant = Config.territoryCostBase
        var costScale = Config.territoryCostScale

        for ( type in resourceNodes ) {
            val resource = Nodes.resourceNodes.get(type)
            if ( resource != null ) {
                costConstant += resource.costConstant
                costScale *= resource.costScale
            }
        }

        val cost = costConstant + Math.round(costScale * size.toDouble()).toInt()
        
        return cost
    }

    // return number of territories in world
    fun getTerritoryCount(): Int {
        return territories.size
    }

    fun getTerritoryFromId(id: Int): Territory? {
        return territories.get(id)
    }

    fun getTerritoryFromBlock(blockX: Int, blockZ: Int): Territory? {
        val coord = Coord.fromBlockCoords(blockX, blockZ)
        return territoryChunks.get(coord)?.territory
    }

    fun getTerritoryFromPlayer(player: Player): Territory? {
        val loc = player.location
        val coord = Coord.fromBlockCoords(loc.x.toInt(), loc.z.toInt())
        return territoryChunks.get(coord)?.territory
    }

    fun getTerritoryFromCoord(coord: Coord): Territory? {
        return territoryChunks.get(coord)?.territory
    }

    fun getTerritoryFromChunk(chunk: Chunk): Territory? {
        val coord = Coord(chunk.x, chunk.z)
        return territoryChunks.get(coord)?.territory
    }

    fun getTerritoryFromChunkCoords(cx: Int, cz: Int): Territory? {
        val coord = Coord(cx, cz)
        return territoryChunks.get(coord)?.territory
    }

    // default spawn location: returns region ~center of
    // core chunk of home territory
    // y-level is first empty air block
    fun getDefaultSpawnLocation(territory: Territory): Location {
        // get from ~middle of territory home chunk
        val homeChunk = territory.core
        val x = homeChunk.x * 16 + 8
        val z = homeChunk.z * 16 + 8

        // iterate up in y to find first empty block
        val world = Bukkit.getWorlds().get(0)
        var y = 0
        while ( y < 255 ) {
            if ( world.getBlockAt(x, y, z).isEmpty) {
                break
            }
            y += 1
        }

        return Location(world, x.toDouble(), y.toDouble(), z.toDouble())
    }

    // ==============================================
    // Resident functions
    // ==============================================
    fun createResident(player: Player) {
        val uuid = player.uniqueId
        if ( !residents.containsKey(uuid) ) {
            val resident = Resident(uuid, player.name)
            residents.put(uuid, resident)

            needsSave = true
        }
    }

    // loads a resident from UUID and other parameters
    // used for deserializing from towns.json
    fun loadResident(
        uuid: UUID,
        claims: Int,
        claimsTime: Long,
        prefix: String,
        suffix: String,
        trusted: Boolean,
        townCreateCooldown: Long
    ) {
        
        // get player name from UUID
        // use online player if exists, else get from OfflinePlayer
        val playerName: String? = Bukkit.getPlayer(uuid)?.name ?: run {
            Bukkit.getOfflinePlayer(uuid).name
        }

        // if player name null, player does not exist
        if ( playerName == null ) {
            return
        }

        // create and add resident
        val resident = Resident(uuid, playerName)
        resident.prefix = prefix
        resident.suffix = suffix
        resident.claims = Math.min(claims, Config.playerClaimsMax) // in case config changed
        resident.claimsTime = claimsTime

        // resident trusted status
        resident.trusted = trusted

        // mark resident needs update
        resident.needsUpdate()

        // set resident town create cooldown
        resident.townCreateCooldown = townCreateCooldown

        residents[uuid] = resident
    }

    fun getResidentCount(): Int {
        return residents.size
    }

    fun getResident(player: Player): Resident? {
        return residents[player.uniqueId]
    }

    fun getResidentFromName(name: String): Resident? {
        // get player from bukkit
        val player = Bukkit.getPlayer(name)
        if ( player != null ) {
            return residents[player.uniqueId]
        }
        // search through residents and try to match name
        else {
            val playerNameLowercase = name.lowercase(Locale.getDefault())
            for ( r in residents.values ) {
                if ( r.name.lowercase(Locale.getDefault()) == playerNameLowercase ) {
                    return r
                }
            }
        }
        
        return null
    }

    fun getResidentFromUUID(uuid: UUID): Resident? {
        return residents.get(uuid)
    }

    fun setResidentPrefix(resident: Resident, s: String) {
        resident.prefix = sanitizeString(s)
        resident.needsUpdate()
        needsSave = true
    }

    fun setResidentSuffix(resident: Resident, s: String) {
        resident.suffix = sanitizeString(s)
        resident.needsUpdate()
        needsSave = true
    }

    // marks player as online
    fun setResidentOnline(resident: Resident, player: Player) {
        val town = resident.town
        if ( town != null ) {
            town.playersOnline.add(player)

            val nation = town.nation
            if ( nation != null ) {
                nation.playersOnline.add(player)
            }
        }
    }

    // marks player as offline
    // force player as input in case resident cannot access player
    // if player already offline
    fun setResidentOffline(resident: Resident, player: Player) {
        val town = resident.town
        if ( town != null ) {
            town.playersOnline.remove(player)

            val nation = town.nation
            if ( nation != null ) {
                nation.playersOnline.remove(player)
            }
        }
    }

    // toggle chat mode:
    // if already in mode, return to default (global)
    // else, set to new mode
    // return chatmode this is set to
    fun toggleChatMode(resident: Resident, mode: ChatMode): ChatMode {
        if ( resident.chatMode == mode ) {
            resident.chatMode = ChatMode.GLOBAL
        }
        else {
            resident.chatMode = mode
        }
        
        return resident.chatMode
    }

    // set player's town create cooldown
    fun setResidentTownCreateCooldown(resident: Resident, cooldown: Long) {
        resident.townCreateCooldown = cooldown
        resident.needsUpdate()
        needsSave = true
    }

    // set resident's current claims time progress
    fun setResidentClaimTimer(resident: Resident, time: Long) {
        resident.claimsTime = time
        resident.needsUpdate()
        needsSave = true
    }

    // update players online in each town, nation
    fun refreshPlayersOnline() {
        // remove players online in nations
        for ( nation in nations.values ) {
            nation.playersOnline.clear()
        }

        for ( town in towns.values ) {
            town.playersOnline.clear()
            for ( r in town.residents ) {
                val player = r.player()
                if ( player !== null ) {
                    town.playersOnline.add(player)

                    val nation = r.nation
                    if ( nation !== null ) {
                        nation.playersOnline.add(player)
                    }
                }
            }
        }
    }

    // ==============================================
    // Town functions
    // ==============================================

    // create town at player location:
    // - player becomes town leader
    // - territory at player's location becomes town home
    fun createTown(name: String, territory: Territory, leader: Resident?): Result<Town> {

        // get resident and spawn coordinate from leader if exists
        val leaderPlayer = leader?.player()
        val spawnpoint = if ( leaderPlayer != null ) {
            leaderPlayer.location
        } else {
            getDefaultSpawnLocation(territory)
        }

        if ( getTownFromName(name) != null ) {
            return Result.failure(ErrorTownExists)
        }

        if ( territory.town != null ) {
            return Result.failure(ErrorTerritoryOwned)
        }

        if ( leader?.town != null ) {
            return Result.failure(ErrorPlayerHasTown)
        }

        val town = Town(UUID.randomUUID(), name, territory, leader, spawnpoint)
        town.claimsUsed = territory.cost

        if ( leader != null ) {
            leader.town = town

            // check how much player is over town claim limit
            val overClaimsPenalty: Int = Math.max(0, Config.initialOverClaimsAmountScale * (territory.cost - Config.townInitialClaims))
            
            // give initial leader their max claims
            leader.claims = Math.max(0, Math.max(Config.playerClaimsMax, Config.townInitialClaims) - overClaimsPenalty)
            leader.claimsTime = 0L

            // create nametag
            if ( leaderPlayer !== null ) {
                Nametag.create(leaderPlayer)
            }

            leader.needsUpdate()
        }

        town.claimsMax = calculateMaxClaims(town)

        towns.put(name, town)
        needsSave = true
        
        // re-render minimaps
        renderMinimaps()
        
        return Result.success(town)
    }

    // load town from data
    // used for deserializing from towns.json
    fun loadTown(
        uuid: UUID,
        name: String,
        leader: UUID?,
        homeId: Int,
        spawn: Location?,
        color: Color?,
        residents: ArrayList<UUID>,
        officers: ArrayList<UUID>,
        territoryIds: ArrayList<Int>,
        capturedTerritoryIds: ArrayList<Int>,
        annexedTerritoryIds: ArrayList<Int>,
        claimsBonus: Int,
        claimsAnnexed: Int,
        claimsPenalty: Int,
        claimsPenaltyTime: Long,
        income: EnumMap<Material, Int>,
        incomeSpawnEgg: EnumMap<EntityType, Int>,
        permissions: EnumMap<TownPermissions, EnumSet<PermissionsGroup>>,
        isOpen: Boolean,
        protectedBlocks: HashSet<Block>,
        moveHomeCooldown: Long,
        outposts: HashMap<String, Pair<Int, Location>>
    ): Town? {

        val leaderAsResident = if ( leader != null ) {
            getResidentFromUUID(leader)
        } else {
            null
        }

        // make sure home territory exists
        val home = getTerritoryFromId(homeId)
        if ( home == null ) {
            System.err.println("Failed to create town ${name} with home (id = ${homeId})")
            return null
        }

        // get spawn point
        val spawnpoint = if ( spawn != null ) {
            spawn
        }
        else { // get default value
            val homeTerritory = territories.get(homeId)
            if ( homeTerritory != null ) {
                getDefaultSpawnLocation(homeTerritory)
            }
            else {
                Location(Bukkit.getWorlds().get(0), 0.0, 255.0, 0.0)
            }
        }

        val town = Town(uuid, name, home, leaderAsResident, spawnpoint)
        leaderAsResident?.town = town

        // add residents
        for ( id in residents ) { 
            getResidentFromUUID(id)?.let { r ->
                town.residents.add(r)
                r.town = town
                r.needsUpdate()
            }
        }

        // add officers
        for ( id in officers ) {
            getResidentFromUUID(id)?.let { r ->
                town.officers.add(r)
            }
        }

        // add territory claims and claims power used
        for ( id in territoryIds ) { 
            getTerritoryFromId(id)?.let { terr ->
                town.territories.add(terr)
                terr.town = town
                town.claimsUsed += terr.cost
            }
        }
        
        // add annexed territories
        // (duplicated in territoryIds, so just add ids)
        for ( id in annexedTerritoryIds ) { 
            getTerritoryFromId(id)?.let { terr ->
                if ( town.territories.contains(terr) ) {
                    town.annexed.add(terr)
                    town.claimsUsed -= terr.cost
                }
            }
        }

        // add captured territories
        for ( id in capturedTerritoryIds ) { 
            getTerritoryFromId(id)?.let { terr ->
                // check if territory already occupied, remove current occupier
                // should never occur, but just in case
                val currentOccupier: Town? = terr.occupier
                if ( currentOccupier != null ) {
                    currentOccupier.captured.remove(terr)
                }

                // capture territory for town
                town.captured.add(terr)
                terr.occupier = town
            }
        }

        // add saved income
        town.income.storage.putAll(income)
        town.income.storageSpawnEgg.putAll(incomeSpawnEgg)
        
        // set town color
        if ( color != null ) {
            town.color = color
        }

        // add permission flags
        for ( (type, groups) in permissions ) {
            town.permissions.get(type)!!.clear()
            town.permissions.get(type)!!.addAll(groups)
        }

        // calculate max claims
        town.claimsBonus = claimsBonus
        town.claimsAnnexed = claimsAnnexed
        town.claimsPenalty = claimsPenalty
        town.claimsPenaltyTime = claimsPenaltyTime
        town.claimsMax = calculateMaxClaims(town)
        
        // set isOpen
        town.isOpen = isOpen

        // add protected blocks
        town.protectedBlocks.addAll(protectedBlocks)

        // set move home cooldown
        town.moveHomeCooldown = moveHomeCooldown

        // load outposts
        for ( (name, outpostData) in outposts ) {
            val terrId = outpostData.first
            val spawn = outpostData.second
            val terr = getTerritoryFromId(terrId)
            if ( terr !== null && town.territories.contains(terr) ) {
                town.outposts.put(name, TownOutpost(
                    name,
                    terr,
                    spawn
                ))
            }
        }

        // save new town
        towns.put(name, town)

        // mark dirty
        town.needsUpdate()

        return town
    }

    fun destroyTown(town: Town) {
        // remove town links backwards from creation:

        // check if town last in nation, destroy nation first if last
        val nation = town.nation
        if ( nation !== null ) {
            if ( nation.towns.size == 1 ) { // last town in nation
                destroyNation(nation)
            }
            else {
                removeTownFromNation(nation, town)
            }
        }

        // remove territory claim links
        town.territories.forEach { terr ->
            terr.town = null
        }

        // remove occupied territories
        town.captured.forEach { terr ->
            terr.occupier = null
        }

        // remove resident town links
        town.residents.forEach { r ->
            r.town = null
            r.nation = null
            r.needsUpdate()

            // remove resident nametag, and remove from nation players online list
            val player = r.player()
            if ( player !== null ) {
                Nametag.destroy(player)

                if ( nation !== null ) {
                    nation.playersOnline.remove(player)
                }
            }
        }
        
        // remove all town alliances
        for ( ally in town.allies ) {
            ally.allies.remove(town)
            ally.needsUpdate()
        }

        // remove all town enemies
        for ( enemy in town.enemies ) {
            enemy.enemies.remove(town)
            enemy.needsUpdate()
        }

        // remove town from global
        towns.remove(town.name)

        needsSave = true

        // re-render minimaps
        renderMinimaps()
    }

    fun getTownCount(): Int {
        return towns.size
    }

    fun getTownFromName(name: String): Town? {
        return towns.get(name)
    }

    fun getTownFromPlayer(player: Player): Town? {
        // get resident
        val resident = getResident(player)
        if ( resident !== null ) {
            return resident.town
        }
        
        return null
    }

    /**
     * Return town that owns chunk if it exists
     */
    fun getTownAtChunkCoord(cx: Int, cz: Int): Town? {
        val terr = getTerritoryFromChunkCoords(cx, cz)
        if ( terr !== null ) {
            return terr.town
        }

        return null
    }

    // set town's bonus claims
    fun setClaimsBonus(town: Town, num: Int) {
        town.claimsBonus = num
        town.claimsMax = calculateMaxClaims(town)
        town.needsUpdate()
        needsSave = true
    }

    // set town's claims penalty
    fun setClaimsPenalty(town: Town, num: Int) {
        town.claimsPenalty = num
        town.claimsMax = calculateMaxClaims(town)
        town.needsUpdate()
        needsSave = true
    }

    // set town's annexed claims penalty
    fun setClaimsAnnexed(town: Town, num: Int) {
        town.claimsAnnexed = num
        town.claimsMax = calculateMaxClaims(town)
        town.needsUpdate()
        needsSave = true
    }

    // calculates and returns town's max claims
    // NOTE: DOES NOT actually set town's max claims
    // called should do:
    // town.claimsMax = Nodes.calculateMaxClaims(town)
    fun calculateMaxClaims(town: Town): Int {
        var maxClaims = Config.townClaimsBase - town.claimsPenalty
        for ( r in town.residents ) {
            maxClaims += r.claims
        }
        
        // apply bonus
        maxClaims += town.claimsBonus

        // penalty from annexed territories
        maxClaims -= town.claimsAnnexed

        // clamp to max
        if ( Config.townClaimsMax > 0 ) {
            maxClaims = Math.min(Config.townClaimsMax, maxClaims)
        }

        // check if town over max claims
        town.isOverClaimsMax = town.claimsUsed > maxClaims

        return maxClaims
    }

    // reduces claim penalty from all towns by 1
    // scheduled by ClaimPenaltyDecayManager
    fun claimsPenaltyDecay(dt: Long) {
        for ( town in towns.values ) {
            if ( town.claimsPenalty > 0 ) {
                // update tick, run update if passed period
                val elapsedTime = town.claimsPenaltyTime + dt
                if ( elapsedTime >= Config.townClaimsPenaltyDecayPeriod ) {
                    town.claimsPenaltyTime = 0L
                    town.claimsPenalty = Math.max(0, town.claimsPenalty - Config.townPenaltyDecay)
                    town.claimsMax = calculateMaxClaims(town)
                    town.needsUpdate()
                    needsSave = true
                }
                else {
                    town.claimsPenaltyTime = elapsedTime
                    town.needsUpdate()
                    needsSave = true
                }
            }
        }
    }

    // tick player claim power contributions and
    // re-calculate town max claims
    // -> apply only for online players
    fun claimsPowerRamp(dt: Long) {
        for ( player in Bukkit.getOnlinePlayers() ) {
            val resident = getResident(player)
            val town = resident?.town
            if ( town !== null && resident.claims < Config.playerClaimsMax ) {
                // update tick, run update if passed period
                val elapsedTime = resident.claimsTime + dt
                if ( elapsedTime >= Config.playerClaimsIncreasePeriod ) {
                    resident.claimsTime = 0L
                    resident.claims = Math.min(Config.playerClaimsMax, resident.claims + Config.playerClaimsIncrease)
                    town.claimsMax = calculateMaxClaims(town)
                    resident.needsUpdate()
                    town.needsUpdate()
                    needsSave = true
                }
                else {
                    resident.claimsTime = elapsedTime
                    resident.needsUpdate()
                    needsSave = true
                }
            }
        }
    }

    /**
     * Runs in bukkit task periodically, sends message to town
     * players if their town is over max claims
     */
    fun overMaxClaimsReminder() {
        val resourcePenalty = "${(Config.overClaimsMaxPenalty * 100).toInt()}% resource penalty"

        for ( town in towns.values ) {
            if ( town.isOverClaimsMax ) {
                val msg = "${ChatColor.DARK_RED}Your town is over max claims: ${town.claimsUsed}/${town.claimsMax}, you have a $resourcePenalty"
                for ( r in town.residents ) {
                    val player = r.player()
                    if ( player !== null ) {
                        Message.print(player, msg)
                    }
                }
            }
        }
    }

    // claim territory for a town
    // returns TerritoryClaim status of result
    fun claimTerritory(town: Town, territory: Territory): Result<Territory> {

        // check if territory already claimed
        if ( territory.town != null ) {
            return Result.failure(ErrorTerritoryHasClaim)
        }

        // check if territory is connected to town's existing territories
        // iterate this territory neighbors, check if any link to town
        var isNeighbor = false
        for ( neighbor in territory.neighbors ) {
            if ( neighbor.town === town ) {
                isNeighbor = true
                break
            }
        }
        if ( !isNeighbor ) {
            return Result.failure(ErrorTerritoryNotConnected)
        }

        // check if town has claims available
        if ( territory.cost > town.claimsMax - town.claimsUsed ) {
            return Result.failure(ErrorTooManyClaims)
        }

        // passed checks, add territory to town
        town.territories.add(territory)
        territory.town = town

        // increase claims power used
        town.claimsUsed += territory.cost

        // mark dirty
        town.needsUpdate()
        needsSave = true

        // re-render minimaps
        renderMinimaps()

        return Result.success(territory)
    }

    fun unclaimTerritory(town: Town, territory: Territory): Result<Territory> {
        
        // check if town owns territory
        if ( !town.territories.contains(territory) ) {
            return Result.failure(ErrorTerritoryNotInTown)
        }

        // check if territory is town's home territory
        if ( town.home == territory ) {
            return Result.failure(ErrorTerritoryIsTownHome)
        }

        // passed checks, remove territory from town
        town.territories.remove(territory)
        territory.town = null
        
        // remove any outposts with this territory
        town.outposts.entries.removeAll { (_, v) -> v.territory === territory }

        // if territory was not annexed, remove territory cost
        // from claims used, add to penalty until it decays
        if ( !town.annexed.contains(territory) ) {
            town.claimsUsed -= territory.cost
            town.claimsPenalty += territory.cost
            town.claimsMax = calculateMaxClaims(town)
        }
        else {
            town.annexed.remove(territory)
        }

        // mark dirty
        town.needsUpdate()
        needsSave = true
        
        // re-render minimaps
        renderMinimaps()

        return Result.success(territory)
    }

    // adds a territory to town and bypasses standard claim checks
    // (e.g. territory must be connected, town has claims available, ...)
    // if successful, returns added territory
    fun addTerritoryToTown(town: Town, territory: Territory): Result<Territory> {
        
        // check territory not already occupied
        if ( territory.town != null ) {
            return Result.failure(ErrorTerritoryOwned)
        }

        // add territory to town
        town.territories.add(territory)
        territory.town = town

        // increase claims power used
        town.claimsUsed += territory.cost

        // mark dirty
        town.needsUpdate()
        needsSave = true

        // re-render minimaps
        renderMinimaps()
        
        return Result.success(territory)
    }

    // makes territory occupied by town
    fun captureTerritory(town: Town, territory: Territory) {
        // check if territory already occupied, remove current occupier
        val currentOccupier: Town? = territory.occupier
        if ( currentOccupier != null ) {
            currentOccupier.captured.remove(territory)
            territory.occupier = null

            currentOccupier.needsUpdate()
        }

        // handle capturing enemy territory
        if ( territory.town != town ) {
            town.captured.add(territory)
            territory.occupier = town
        }

        town.needsUpdate()
        needsSave = true

        // re-render minimaps
        renderMinimaps()
    }
    
    // release territory from town occupation
    fun releaseTerritory(territory: Territory) {
        // check if territory currently occupied, remove current occupier
        val currentOccupier: Town? = territory.occupier
        if ( currentOccupier != null ) {
            currentOccupier.captured.remove(territory)
            territory.occupier = null

            currentOccupier.needsUpdate()
            needsSave = true

            // re-render minimaps
            renderMinimaps()
        }
    }

    /**
     * Town annexes a territory:
     * - add to town's territories and town's annexed territories
     * - does not cost claim power (no need to recalculate max claims)
     * Returns boolean on success
     */
    fun annexTerritory(town: Town, territory: Territory): Boolean {
        val occupier: Town? = territory.occupier
        if ( occupier !== town ) {
            return false
        }

        val oldTown = territory.town
        if ( oldTown === town ) {
            return false
        }
        
        // remove from old town
        if ( oldTown !== null ) {
            // check if this is their home territory
            if ( territory === oldTown.home ) {
                // can only annex home territory last
                if ( oldTown.territories.size > 1 ) {
                    return false
                }

                // destroy town and broadcast
                destroyTown(oldTown)

                Message.broadcast("${ChatColor.DARK_RED}${ChatColor.BOLD}Town ${oldTown.name} was completely annexed by ${town.name}")
            }
            // else, just a normal territory
            else {
                if ( oldTown.annexed.contains(territory) ) {
                    oldTown.annexed.remove(territory)
                }
                // if territory was not an annexed territory, adjust claims
                else {
                    oldTown.claimsUsed -= territory.cost
                    oldTown.claimsAnnexed += territory.cost
                }

                // remove territory and re-calculate claims
                oldTown.territories.remove(territory)
                oldTown.claimsMax = calculateMaxClaims(town)
    
                oldTown.needsUpdate()
            }
        }

        // add territory to town and annexed territories
        town.territories.add(territory)
        town.annexed.add(territory)
        town.captured.remove(territory)

        // update territory
        territory.town = town
        territory.occupier = null

        town.needsUpdate()
        needsSave = true

        // re-render minimaps
        renderMinimaps()

        return true
    }

    // adds items to town's income
    // used by taxation events in occupied/captured territories
    fun addToIncome(town: Town, material: Material, amount: Int, meta: Int = 0) {
        town.income.add(material, amount, meta)
        town.needsUpdate()
        needsSave = true
    }

    fun setTownColor(town: Town, r: Int, g: Int, b: Int) {
        town.color = Color(r, g, b)
        town.needsUpdate()
        needsSave = true
    }

    fun setTownSpawn(town: Town, spawnpoint: Location): Boolean {
        // enforce spawnpoint in town's home territory
        val territory = getTerritoryFromChunk(spawnpoint.chunk)
        if ( territory !== town.home ) {
            return false
        }

        town.spawnpoint = spawnpoint
        town.needsUpdate()
        needsSave = true

        return true
    }

    fun addResidentToTown(town: Town, resident: Resident) {
        town.residents.add(resident)
        resident.town = town
        
        // update resident max claims
        resident.claims = Config.playerClaimsInitial
        resident.claimsTime = 0L
        town.claimsMax = calculateMaxClaims(town)

        // initialize player as untrusted
        resident.trusted = false

        // add player to town online players
        val player = resident.player()
        if ( player !== null ) {
            town.playersOnline.add(player)
        }

        // add town nation to resident
        val nation = town.nation
        if ( nation !== null ) {
            resident.nation = nation
            nation.residents.add(resident)

            // add player to nation online players
            if ( player !== null ) {
                nation.playersOnline.add(player)
            }
        }

        town.needsUpdate()
        resident.needsUpdate()
        needsSave = true

        // create nametag for player
        if ( player !== null ) {
            Nametag.create(player)
        }
    }

    fun removeResidentFromTown(town: Town, resident: Resident) {
        if ( town.officers.contains(resident) ) {
            town.officers.remove(resident)
        }
        town.residents.remove(resident)
        resident.town = null

        val player = resident.player()

        // remove town nation from resident
        val nation = town.nation
        if ( nation !== null ) {
            resident.nation = null
            nation.residents.remove(resident)

            // remove player from nation online players
            if ( player !== null ) {
                nation.playersOnline.remove(player)
            }
        }

        // update max claims
        town.claimsMax = calculateMaxClaims(town)

        // remove player to town online players
        if ( player !== null ) {
            town.playersOnline.remove(player)
        }

        town.needsUpdate()
        resident.needsUpdate()
        needsSave = true

        // delete player nametag
        if ( player !== null ) {
            Nametag.destroy(player)
        }
    }

    // make player in town an officer
    fun townAddOfficer(town: Town, resident: Resident): Boolean {
        if ( resident.town !== town ) {
            return false
        }

        if ( town.officers.contains(resident) ) {
            return true
        }

        town.officers.add(resident)
        
        town.needsUpdate()
        needsSave = true

        return true
    }

    // remove player from officer status
    fun townRemoveOfficer(town: Town, resident: Resident): Boolean {
        if ( resident.town !== town ) {
            return false
        }

        town.officers.remove(resident)

        town.needsUpdate()
        needsSave = true

        return true
    }

    fun playerIsOfficer(town: Town, player: Player): Boolean {
        val resident = getResident(player)
        if ( resident === town.leader || town.officers.contains(resident) ) {
            return true
        }
        return false
    }

    // set town's leader
    fun townSetLeader(town: Town, resident: Resident?) {
        if ( resident?.town !== town ) {
            return
        }
        
        // same town leader, ignore
        if ( town.leader === resident ) {
            return
        }

        // remove resident from officers if there
        town.officers.remove(resident)

        town.leader = resident
        town.needsUpdate()
        needsSave = true
    }

    fun renameTown(town: Town, s: String): Boolean {
        // check that new name not used
        if ( towns.contains(s) ) {
            return false
        }

        towns.remove(town.name)
        town.name = s
        town.updateNametags()
        
        towns.put(s, town)
        town.needsUpdate()

        // update residents in town and nation
        town.nation?.needsUpdate()
        for ( r in town.residents ) {
            r.needsUpdate()
        }

        // update town allies/enemies
        for ( t in town.enemies ) {
            t.needsUpdate()
        }
        for ( t in town.allies ) {
            t.needsUpdate()
        }

        needsSave = true

        return true
    }

    // view town income inventory gui
    fun getTownIncomeInventory(town: Town): Inventory {
        // mark dirty if inventory not empty (player could take items)
        if ( !town.income.empty() ) {
            town.needsUpdate()
        }
        return town.income.inventory
    }

    /**
     * Set town permissions
     */
    fun setTownPermissions(town: Town, permissions: TownPermissions, group: PermissionsGroup, flag: Boolean) {
        // add perms
        if ( flag == true ) {
            town.permissions[permissions]!!.add(group)
        }
        else { // remove perms
            town.permissions[permissions]!!.remove(group)
        }

        town.needsUpdate()
        needsSave = true
    }

    /**
     * set town's home territory
     */
    fun setTownHomeTerritory(town: Town, territory: Territory) {

        // set town home territory
        if ( town !== territory.town ) {
            return
        }
        if ( town.home === territory ) {
            return
        }

        // set town home
        town.home = territory

        // set town spawn to new home territory
        town.spawnpoint = getDefaultSpawnLocation(territory)

        // set cooldown
        town.moveHomeCooldown = Config.townMoveHomeCooldown

        // re-render minimaps
        renderMinimaps()

        town.needsUpdate()
        needsSave = true
    }

    // set town's move home cooldown period 
    fun setTownHomeMoveCooldown(town: Town, time: Long) {
        town.moveHomeCooldown = time
        town.needsUpdate()
        needsSave = true
    }

    /**
     * Create an outpost for a town
     * Returns true on success, false on failure
     */
    fun createTownOutpost(town: Town, name: String, territory: Territory): Boolean {
        // town must own the territory
        if ( !town.territories.contains(territory) ) {
            return false
        }

        // town must not have outpost of same name
        if ( town.outposts.contains(name) ) {
            return false
        }

        val spawn = getDefaultSpawnLocation(territory)

        town.outposts.put(name, TownOutpost(
            name,
            territory,
            spawn
        ))

        town.needsUpdate()
        needsSave = true

        return true
    }

    /**
     * Remove town outpost with given name
     * Returns true on success, false on failure
     */
    fun destroyTownOutpost(town: Town, name: String): Boolean {

        val outpost = town.outposts.remove(name)
        if ( outpost === null ) {
            return false
        }

        town.needsUpdate()
        needsSave = true
        
        return true
    }

    /**
     * Set a town outpost's spawn location.
     * Returns true on success, false on failure
     */
    fun setOutpostSpawn(town: Town, outpost: TownOutpost, spawn: Location): Boolean {
        // verify location is inside territory
        if ( getTerritoryFromChunk(spawn.chunk) !== outpost.territory ) {
            return false
        }

        outpost.spawn = spawn
        
        town.needsUpdate()
        needsSave = true

        return true
    }

    // when inventory close, require save because items could have
    // been moved
    internal fun onTownIncomeInventoryClose() {
        needsSave = true
    }

    // set town's isOpen state
    internal fun setTownOpen(town: Town, isOpen: Boolean) {
        town.isOpen = isOpen
        town.needsUpdate()
        needsSave = true
    }

    /**
     * Run cooldown tick, with change in time dt
     */
    internal fun townMoveHomeCooldownTick(dt: Long) {
        // reduce town move home territory cooldown
        for ( town in towns.values ) {
            if ( town.moveHomeCooldown > 0 ) {
                town.moveHomeCooldown = Math.max(0, town.moveHomeCooldown - dt)

                town.needsUpdate()
                needsSave = true
            }
        }
    }

    /**
     * Reduce resident town create cooldown tick by dt
     */
    internal fun residentTownCreateCooldownTick(dt: Long) {
        // reduce player create town cooldown
        for ( resident in residents.values ) {
            if ( resident.townCreateCooldown > 0 ) {
                resident.townCreateCooldown = Math.max(0, resident.townCreateCooldown - dt)

                resident.needsUpdate()
                needsSave = true
            }
        }
    }

    // ==============================================
    // Nation functions
    // ==============================================
    fun createNation(name: String, town: Town, leader: Resident? = null): Result<Nation> {

        if ( town.nation != null ) {
            return Result.failure(ErrorTownHasNation)
        }

        if ( leader?.nation != null ) {
            return Result.failure(ErrorPlayerHasNation)
        }

        if ( leader != null && !town.residents.contains(leader) ) {
            return Result.failure(ErrorPlayerNotInTown)
        }

        if ( getNationFromName(name) != null ) {
            return Result.failure(ErrorNationExists)
        }
        
        val nation = Nation(UUID.randomUUID(), name, town)
        nations.put(name, nation)
        
        // add town to nation
        nation.towns.add(town)
        town.nation = nation

        // remove all pre-existing town alliances, but do not change enemies
        for ( ally in town.allies ) {
            ally.allies.remove(town)
            ally.needsUpdate()
        }
        town.allies.clear()

        // add nation to all residents in creator's town
        for ( r in town.residents ) {
            r.nation = nation
            r.needsUpdate()
        }

        town.needsUpdate()
        nation.needsUpdate()
        needsSave = true

        // re-render minimaps
        renderMinimaps()

        return Result.success(nation)
    }

    // load nation from data
    // used for deserializing from towns.json
    fun loadNation(
        uuid: UUID,
        name: String,
        capitalName: String, // name of capital city
        color: Color?,
        towns: ArrayList<String>): Nation
    {
        val capital = getTownFromName(capitalName)
        if ( capital == null ) {
            throw ErrorTownDoesNotExist
        }
        
        val nation = Nation(uuid, name, capital)

        // set nation color
        if ( color != null ) {
            nation.color = color
        }

        // add towns
        for ( townName in towns ) {
            val town = getTownFromName(townName)
            if ( town != null ) {
                nation.towns.add(town)
                town.nation = nation
                town.needsUpdate()

                for ( r in town.residents ) { 
                    r.nation = nation
                    nation.residents.add(r)
                    r.needsUpdate()
                }
            }
        }

        // mark dirty
        nation.needsUpdate()

        // save new nation
        nations.put(name, nation)

        return nation
    }

    fun destroyNation(nation: Nation) {
        // remove nation level alliances and enemies
        for ( ally in nation.allies ) {
            ally.allies.remove(nation)
            ally.needsUpdate()
        }
        for ( enemy in nation.enemies ) {
            enemy.enemies.remove(nation)
            enemy.needsUpdate()
        }

        // remove town links
        for ( town in nation.towns ) { 
            // remove all residents links
            for ( r in town.residents ) {
                r.nation = null
                r.needsUpdate()
            }

            // remove all town alliances and enemies
            for ( ally in town.allies ) {
                ally.allies.remove(town)
                ally.needsUpdate()
            }
            town.allies.clear()

            for ( enemy in town.enemies ) {
                enemy.enemies.remove(town)
                enemy.needsUpdate()
            }
            town.enemies.clear()

            town.nation = null
            town.needsUpdate()
        }

        nations.remove(nation.name)

        needsSave = true

        // re-render minimaps
        renderMinimaps()
    }

    fun getNationCount(): Int {
        return nations.size
    }

    fun getNationFromName(name: String): Nation? {
        return nations.get(name)
    }

    fun addTownToNation(nation: Nation, town: Town): Result<Town> {

        // check town does not belong to nation
        if ( town.nation != null ) {
            return Result.failure(ErrorTownHasNation)
        }

        // add town to nation
        nation.towns.add(town)
        town.nation = nation
        town.needsUpdate()

        // add nation to residents
        for ( r in town.residents ) {
            r.nation = nation
            nation.residents.add(r)
            val player = r.player()
            if ( player !== null ) {
                nation.playersOnline.add(player)
            }
            r.needsUpdate()
        }

        // remove current town alliances and enemies, set equal to nation capital
        for ( ally in town.allies ) {
            ally.allies.remove(town)
            ally.needsUpdate()
        }
        for ( enemy in town.enemies ) {
            enemy.enemies.remove(town)
            enemy.needsUpdate()
        }

        town.allies.clear()
        town.enemies.clear()

        for ( ally in nation.capital.allies ) {
            town.allies.add(ally)
            ally.allies.add(town)
            ally.needsUpdate()
        }
        for ( enemy in nation.capital.enemies ) {
            town.enemies.add(enemy)
            enemy.enemies.add(town)
            enemy.needsUpdate()
        }

        nation.needsUpdate()
        needsSave = true

        // re-render minimaps
        renderMinimaps()

        return Result.success(town)
    }

    fun removeTownFromNation(nation: Nation, town: Town): Result<Town> {

        // check town belongs to nation
        if ( town.nation !== nation ) {
            return Result.failure(ErrorNationDoesNotHaveTown)
        }

        // remove town to nation
        nation.towns.remove(town)
        town.nation = null

        // remove nation from residents
        for ( r in town.residents ) {
            r.nation = null
            nation.residents.remove(r)
            r.needsUpdate()
        }

        // if nation has no more towns, destroy nation
        // -> cleans up allies, enemies, etc... and global references
        if ( nation.towns.size == 0 ) {
            destroyNation(nation)
        }
        // set new leader for nation if this was capital
        else {
            if ( town === nation.capital ) {
                val newCapital: Town = nation.towns.first()
                nation.capital = newCapital
                // print message to players in new capital
                for ( r in newCapital.residents ) {
                    val player = r.player()
                    if ( player !== null ) {
                        Message.print(player, "Your town is now the capital of ${nation.name}")
                    }
                }
            }
        }

        // remove all town alliances and enemies
        for ( ally in town.allies ) {
            ally.allies.remove(town)
            ally.needsUpdate()
        }
        town.allies.clear()

        for ( enemy in town.enemies ) {
            enemy.enemies.remove(town)
            enemy.needsUpdate()
        }
        town.enemies.clear()

        town.needsUpdate()
        nation.needsUpdate()
        needsSave = true
        
        // re-render minimaps
        renderMinimaps()
        
        return Result.success(town)
    }

    fun setNationColor(nation: Nation, r: Int, g: Int, b: Int) {
        nation.color = Color(r, g, b)
        nation.needsUpdate()
        needsSave = true
    }

    fun renameNation(nation: Nation, s: String): Boolean {
        // check that new name not used
        if ( nations.contains(s) ) {
            return false
        }

        nations.remove(nation.name)
        nation.name = s
        nations.put(s, nation)
        nation.needsUpdate()

        // update nation towns and residents
        for ( town in nation.towns ) {
            town.needsUpdate()
            for ( r in town.residents ) {
                r.needsUpdate()
            }
        }
        
        // update nation allies/enemies
        for ( n in nation.enemies ) {
            n.needsUpdate()
        }
        for ( n in nation.allies ) {
            n.needsUpdate()
        }
        
        needsSave = true

        return true
    }

    /**
     * Set nation capital to a new town. Town must be in the nation already
     */
    fun setNationCapital(nation: Nation, town: Town) {
        if ( town.nation !== nation || nation.capital === town ) {
            return
        }

        nation.capital = town

        nation.needsUpdate()
        needsSave = true
    }

    // ==============================================
    // Territory income cycle functions
    // ==============================================
    
    // add income from each territory,
    // occupied territories 
    fun runIncome() {

        // converts item rate as Double to item count as Int
        // (can be < 1.0 for random, or >1.0 for fixed amount)
        fun rateToAmount(rate: Double): Int {
            if ( rate < 1.0 ) {
                val roll = ThreadLocalRandom.current().nextDouble()
                if ( roll < rate ) {
                    return 1
                } else {
                    return 0
                }
            }
            else {
                return rate.toInt()
            }
        }

        val taxRate = Config.taxIncomeRate

        for ( town in towns.values ) {
            for ( territory in town.territories ) {
                val occupier = territory.occupier
                if ( occupier != null ) {
                    // regular item income
                    for ( (material, rate) in territory.income ) {
                        val amount: Int = rateToAmount(rate)
                        if ( amount > 1 ) {
                            val amountTaxed: Int = ceil(taxRate * amount).toInt()
                            occupier.income.add(material, amountTaxed)

                            val amountKept: Int = amount - amountTaxed
                            
                            // apply over claims penalty
                            if (town.isOverClaimsMax) {
                                val newAmountKept: Int = floor(amountKept.toDouble() * (1.0 - Config.overClaimsMaxPenalty)).toInt()
                                town.income.add(material, newAmountKept)
                            }
                            else {
                                town.income.add(material, amountKept)
                            }
                        }
                        // if <= 1, give all to occupier
                        else {
                            occupier.income.add(material, amount)
                        }
                    }

                    // 1.12 spawn egg income
                    // for ( (entityType, rate) in territory.incomeSpawnEgg ) {
                    //     val amount: Int = rateToAmount(rate)
                    //     if ( amount > 1 ) {
                    //         val amountTaxed: Int = Math.ceil(taxRate * amount).toInt()
                    //         occupier.income.add(Material.MONSTER_EGG, amountTaxed, entityType.ordinal)

                    //         val amountKept: Int = amount - amountTaxed
                            
                    //         // apply over claims penalty
                    //         if ( town.isOverClaimsMax == true ) {
                    //             val newAmountKept: Int = Math.floor(amountKept.toDouble() * (1.0 - Config.overClaimsMaxPenalty)).toInt()
                    //             town.income.add(Material.MONSTER_EGG, newAmountKept, entityType.ordinal)
                    //         }
                    //         else {
                    //             town.income.add(Material.MONSTER_EGG, amountKept, entityType.ordinal)
                    //         }
                    //     }
                    //     // if <= 1, give all to occupier
                    //     else {
                    //         occupier.income.add(Material.MONSTER_EGG, amount, entityType.ordinal)
                    //     }
                    // }

                    occupier.needsUpdate()
                }
                else {
                    // regular item income
                    for ( (material, rate) in territory.income ) {
                        var amount: Int = rateToAmount(rate)

                        // over max claims penalty
                        if ( town.isOverClaimsMax == true ) {
                            amount = Math.floor(amount.toDouble() * (1.0 - Config.overClaimsMaxPenalty)).toInt()
                        }

                        town.income.add(material, amount)
                    }

                    // 1.12 spawn egg income
                    // for ( (entityType, rate) in territory.incomeSpawnEgg ) {
                    //     var amount: Int = rateToAmount(rate)

                    //     // over max claims penalty
                    //     if ( town.isOverClaimsMax == true ) {
                    //         amount = Math.floor(amount.toDouble() * (1.0 - Config.overClaimsMaxPenalty)).toInt()
                    //     }

                    //     town.income.add(Material.MONSTER_EGG, amount, entityType.ordinal)
                    // }
                }
            }

            town.needsUpdate()
        }

        // message players ingame that income collected
        Message.broadcast("Towns have collected income (use \"/t income\" to get)")
    }

    // ==============================================
    // Handle war and diplomatic relations
    // 
    // Rules for declaring war:
    // 1. who can declare war:
    //    - town without nation: town
    //    - town with nation:
    //       - capital town declare war against other towns
    //       - internal town declare war on other towns in nation
    //    - cannot war an ally or town/nation with truce
    // 2. war on enemy town:
    //    - if enemy town has no nation, only war against town
    //    - if enemy town has nation, war defaults against enemy nation
    //    - if enemy town is in your nation, civil war boogaloo
    // 3. war on enemy nation:
    //    - all towns in enemy nation become enemies of all
    //      towns in your nation
    //
    // Same rules apply for making allies.
    // ==============================================

    fun enableWar(
        canAnnexTerritories: Boolean,
        canOnlyAttackBorders: Boolean,
        destructionEnabled: Boolean
    ) {
        war.enable(canAnnexTerritories, canOnlyAttackBorders, destructionEnabled)
    }

    fun disableWar() {
        war.disable()
        
        // re-render minimaps
        renderMinimaps()
    }
    
    /**
     * Set two towns as enemies and their nations if needed, order does not matter.
     * This should be the main function used.
     */
    fun addEnemy(town: Town, enemy: Town): Result<Boolean> {
        // make sure towns are not allies or in truce
        if ( town.allies.contains(enemy) || Truce.contains(town, enemy) ) {
            return Result.failure(ErrorWarAllyOrTruce)
        }

        // check if towns already enemies
        if ( town.enemies.contains(enemy) || enemy.enemies.contains(town) ) {
            return Result.failure(ErrorAlreadyEnemies)
        }

        val townNation = town.nation
        val enemyNation = enemy.nation

        if ( townNation !== null ) {
            // nation-nation war
            if ( enemyNation !== null ) {
                // civil war boogaloo
                if ( enemyNation === townNation ) {
                    town.enemies.add(enemy)
                    enemy.enemies.add(town)
    
                    // remove ally status
                    town.allies.remove(enemy)
                    enemy.allies.remove(town)
                }
                // default to nation-nation war
                else {
                    if ( town === townNation.capital ) { // only nation leading town declare war
                        return nationAddEnemy(townNation, enemyNation)
                    }
                }
            }
            // nation-town war
            else {
                for ( t in townNation.towns ) {
                    t.enemies.add(enemy)
                    enemy.enemies.add(t)

                    t.needsUpdate()
                }
            }
        }
        // town declaring war without nation
        else {
            // town-nation war
            if ( enemyNation !== null ) {
                for ( t in enemyNation.towns ) {
                    t.enemies.add(town)
                    town.enemies.add(t)

                    t.needsUpdate()
                }
            }
            // town-town war
            else {
                town.enemies.add(enemy)
                enemy.enemies.add(town)
            }
        }

        town.needsUpdate()
        enemy.needsUpdate()
        needsSave = true

        // re-render minimaps
        renderMinimaps()

        return Result.success(true)
    }

    /**
     * Removes enemy status between two towns (and nations if needed)
     * Order does not matter.
     */
    fun removeEnemy(town: Town, enemy: Town): Result<Boolean> {

        val townNation = town.nation
        val enemyNation = enemy.nation

        if ( townNation !== null ) {
            // nation-nation war
            if ( enemyNation !== null ) {
                // civil war boogaloo
                if ( enemyNation === townNation ) {
                    town.enemies.remove(enemy)
                    enemy.enemies.remove(town)

                    town.allies.add(enemy)
                    enemy.allies.add(town)
                }
                // default to nation-nation war
                else {
                    return nationRemoveEnemy(townNation, enemyNation)
                }
            }
            // nation-town war
            else {
                for ( t in townNation.towns ) {
                    t.enemies.remove(enemy)
                    enemy.enemies.remove(t)

                    t.needsUpdate()
                }
            }
        }
        // town declaring war without nation
        else {
            // town-nation war
            if ( enemyNation !== null ) {
                for ( t in enemyNation.towns ) {
                    t.enemies.remove(town)
                    town.enemies.remove(t)

                    t.needsUpdate()
                }
            }
            // town-town war
            else {
                town.enemies.remove(enemy)
                enemy.enemies.remove(town)
            }
        }

        town.needsUpdate()
        enemy.needsUpdate()
        needsSave = true

        // re-render minimaps
        renderMinimaps()

        return Result.success(true)
    }

    /**
     * Set two towns as allies (bidirectional), order does not matter.
     * Handle town-town, town-nation, nation-town, nation-nation cases
     */
    fun addAlly(town: Town, other: Town): Result<Boolean> {
        val pm = Bukkit.getPluginManager()

        // towns already allies
        if ( town.allies.contains(other) && other.allies.contains(town) ) {
            return Result.failure(ErrorAlreadyAllies)
        }

        // cannot ally enemies
        if ( town.enemies.contains(other) || other.enemies.contains(town) ) {
            return Result.failure(ErrorAlreadyEnemies)
        }

        val townNation = town.nation
        val otherNation = other.nation

        if ( townNation !== null ) {
            // nation-nation
            if ( otherNation !== null && townNation !== otherNation) {
                nationAddAlly(townNation, otherNation)
            }
            // nation-town
            else {
                for ( t in townNation.towns ) {
                    t.allies.add(other)
                    other.allies.add(town)

                    t.needsUpdate()
                    pm.callEvent(AllianceCreatedEvent(t, other))
                }
            }
        }
        // town allying without nation
        else {
            // town-nation
            if ( otherNation !== null ) {
                for ( t in otherNation.towns ) {
                    t.allies.add(town)
                    town.allies.add(t)

                    t.needsUpdate()
                    pm.callEvent(AllianceCreatedEvent(town, t))
                }
            }
            // town-town
            else {
                town.allies.add(other)
                other.allies.add(town)
                pm.callEvent(AllianceCreatedEvent(town, other))
            }
        }

        town.needsUpdate()
        other.needsUpdate()
        needsSave = true

        // re-render minimaps
        renderMinimaps()

        return Result.success(true)
    }

    fun removeAlly(town: Town, other: Town): Result<Boolean> {
        // not currently allies
        if ( !town.allies.contains(other) || !other.allies.contains(town) ) {
            return Result.failure(ErrorNotAllies)
        }
        
        val townNation = town.nation
        val otherNation = other.nation

        if ( townNation !== null ) {
            // nation-nation
            if ( otherNation !== null && townNation !== otherNation) {
                nationRemoveAlly(townNation, otherNation)
            }
            // nation-town
            else {
                for ( t in townNation.towns ) {
                    t.allies.remove(other)
                    other.allies.remove(town)

                    t.needsUpdate()
                }
            }
        }
        // town allying without nation
        else {
            // town-nation
            if ( otherNation !== null ) {
                for ( t in otherNation.towns ) {
                    t.allies.remove(town)
                    town.allies.remove(t)

                    t.needsUpdate()
                }
            }
            // town-town
            else {
                town.allies.remove(other)
                other.allies.remove(town)
            }
        }

        town.needsUpdate()
        other.needsUpdate()
        needsSave = true

        // re-render minimaps
        renderMinimaps()

        return Result.success(true)
    }

    fun addTruce(town: Town, other: Town): Result<Boolean> {
        // towns already under truce
        if ( Truce.contains(town, other) ) {
            return Result.failure(ErrorAlreadyTruce)
        }

        // truce start time
        val time = System.currentTimeMillis()

        val townNation = town.nation
        val otherNation = other.nation

        if ( townNation !== null ) {
            // nation-nation
            if ( otherNation !== null && townNation !== otherNation) {
                for ( t in townNation.towns ) {
                    for ( other in otherNation.towns ) {
                        Truce.create(t, other, time)
                    }
                }
            }
            // nation-town
            else {
                for ( t in townNation.towns ) {
                    Truce.create(t, other, time)
                }
            }
        }
        // town allying without nation
        else {
            // town-nation
            if ( otherNation !== null ) {
                for ( t in otherNation.towns ) {
                    Truce.create(town, t, time)
                }
            }
            // town-town
            else {
                Truce.create(town, other, time)
            }
        }
        
        return Result.success(true)
    }

    /**
     * Truces all handled between town pairs, so removeTruce should only
     * affect towns involved and not nations.
     * 
     * addTruce creates truces for all town pairs in nations,
     * truce expiration tick will call this removeTruce for all those
     * town pairs individually.
     */
    fun removeTruce(town: Town, other: Town): Result<Boolean> {
        Truce.remove(town, other)

        Bukkit.getPluginManager().callEvent(TruceExpiredEvent(town, other))

        return Result.success(true)
    }

    // set two nations as enemies (bidirectional), order does not matter
    // -> sets all towns in each nation as enemies
    // returns true on success
    private fun nationAddEnemy(nation: Nation, enemy: Nation): Result<Boolean> {
        // make sure nations are not allies or in truce
        if ( nation.allies.contains(enemy) || Truce.contains(nation.capital, enemy.capital) ) {
            return Result.failure(ErrorWarAllyOrTruce)
        }

        // check if nations already enemies
        if ( nation.enemies.contains(enemy) && enemy.enemies.contains(nation) ) {
            return Result.failure(ErrorAlreadyEnemies)
        }

        nation.enemies.add(enemy)
        enemy.enemies.add(nation)

        // mark all towns in each nation as enemies
        for ( nationTown in nation.towns ) {
            for ( enemyTown in enemy.towns ) {
                nationTown.enemies.add(enemyTown)
                enemyTown.enemies.add(nationTown)

                enemyTown.needsUpdate()
            }
            nationTown.needsUpdate()
        }
        
        needsSave = true

        // re-render minimaps
        renderMinimaps()

        return Result.success(true)
    }

    private fun nationRemoveEnemy(nation: Nation, enemy: Nation): Result<Boolean> {
        nation.enemies.remove(enemy)
        enemy.enemies.remove(nation)

        // remove enemy status between towns in each nation
        for ( nationTown in nation.towns ) {
            for ( enemyTown in enemy.towns ) {
                nationTown.enemies.remove(enemyTown)
                enemyTown.enemies.remove(nationTown)

                enemyTown.needsUpdate()
            }
            nationTown.needsUpdate()
        }
        
        needsSave = true

        // re-render minimaps
        renderMinimaps()

        return Result.success(true)
    }

    // add alliance between two nations and their towns (bidirectional)
    private fun nationAddAlly(nation: Nation, ally: Nation): Result<Boolean> {
        val pm = Bukkit.getPluginManager()

        // make sure nations are not enemies
        if ( nation.enemies.contains(ally) || ally.enemies.contains(nation) ) {
            return Result.failure(ErrorAlreadyEnemies)
        }

        // check if nations already have alliance
        if ( nation.allies.contains(ally) && ally.allies.contains(nation) ) {
            return Result.failure(ErrorAlreadyAllies)
        }

        nation.allies.add(ally)
        ally.allies.add(nation)

        // mark all towns in each nation as enemies
        for ( nationTown in nation.towns ) {
            for ( allyTown in ally.towns ) {
                nationTown.allies.add(allyTown)
                allyTown.allies.add(nationTown)

                allyTown.needsUpdate()

                pm.callEvent(AllianceCreatedEvent(nationTown, allyTown))
            }
            nationTown.needsUpdate()
        }
        
        needsSave = true

        // re-render minimaps
        renderMinimaps()

        return Result.success(true)
    }

    private fun nationRemoveAlly(nation: Nation, ally: Nation): Result<Boolean> {
        nation.allies.remove(ally)
        ally.allies.remove(nation)

        // mark all towns in each nation as enemies
        for ( nationTown in nation.towns ) {
            for ( allyTown in ally.towns ) {
                nationTown.allies.remove(allyTown)
                allyTown.allies.remove(nationTown)

                allyTown.needsUpdate()
            }
            nationTown.needsUpdate()
        }
        
        needsSave = true

        // re-render minimaps
        renderMinimaps()

        return Result.success(true)
    }

    /**
     * Run the truce tick
     */
    fun truceTick() {
        // current time
        val time = System.currentTimeMillis()

        // get list of expired truces
        val expired: List<TownPair> = Truce.truces.asSequence()
        .filter { (_, startTime) ->
            (time - startTime) > Config.trucePeriod
        }
            .map { (towns) ->
            towns
        }
            .toList()

        // remove expired truces
        for ( towns in expired ) {
            removeTruce(towns.town1, towns.town2)
        }
        
        // save truce.json file
        if ( Truce.needsUpdate == true ) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin!!, FileWriteTask(Truce.toJsonString(), Config.pathTruce, null))
            Truce.needsUpdate = false
        }
    }

    /**
     * Get diplomatic relationship between two towns
     */
    fun getRelationshipOfTownToTown(playerTown: Town?, otherTown: Town?): DiplomaticRelationship {
        if ( playerTown !== null && otherTown !== null ) {
            if ( playerTown === otherTown ) {
                return DiplomaticRelationship.TOWN
            }

            val playerNation = playerTown.nation
            val otherNation = otherTown.nation
            if ( playerNation !== null && playerNation === otherNation ) {
                return DiplomaticRelationship.NATION
            }
            
            if ( playerTown.allies.contains(otherTown) ) {
                return DiplomaticRelationship.ALLY
            }

            if ( playerTown.enemies.contains(otherTown) ) {
                return DiplomaticRelationship.ENEMY
            }
        }

        return DiplomaticRelationship.NEUTRAL
    }

    // ==============================================
    // Chest protection functions
    // ==============================================
    
    /**
     * Sets resident trust
     */
    internal fun setResidentTrust(resident: Resident, trust: Boolean) {
        resident.trusted = trust
        resident.needsUpdate()
        needsSave = true
    }

    /**
     * Add event listener for protecting/unprotecting chests
     * with mouse clicks
     */
    internal fun startProtectingChests(resident: Resident) {
        val player = resident.player()
        if ( player === null || resident.chestProtectListener !== null ) {
            return
        }

        val town = resident.town
        if ( town === null ) {
            return
        }

        // check that resident is leader or officer
        if ( resident !== town.leader && !town.officers.contains(resident) ) {
            return
        }

        // create chest protection listener, attach to player
        val chestProtectListener = NodesPlayerChestProtectListener(player, resident, town)
        resident.isProtectingChests = true
        resident.chestProtectListener = chestProtectListener

        // register event handler
        val pluginManager = plugin!!.server.pluginManager
        pluginManager.registerEvents(chestProtectListener, plugin!!)
    }

    /**
     * Remove event listener for protecting chests
     */
    internal fun stopProtectingChests(resident: Resident) {
        val player = resident.player()
        val chestProtectListener = resident.chestProtectListener
        if ( player === null || chestProtectListener === null ) {
            return
        }

        // unregister event handler
        HandlerList.unregisterAll(chestProtectListener)
        
        // remove resident links
        resident.isProtectingChests = false
        resident.chestProtectListener = null
    }

    /**
     * Mark town chest as protected by town
     * Handles checking for connected chests.
     * Protect: true/false setting for protecting or unprotecting
     */
    internal fun protectTownChest(town: Town, block: Block, protect: Boolean) {
        
        // get connected chest blocks
        fun getConnectedBlocks(block: Block): List<Block> {
            val type = block.type
            if ( type == Material.CHEST || type == Material.TRAPPED_CHEST ) {
                val blockState = block.state
                if ( blockState is Chest ) {
                    val chest = blockState
                    val inventory = chest.inventory
                    if ( inventory is DoubleChestInventory ){
                        val doubleChest = inventory.holder as DoubleChest
                        
                        // get sides and add to blocks
                        val leftSide: Block = (doubleChest.leftSide as Chest).block
                        val rightSide: Block = (doubleChest.rightSide as Chest).block
                        
                        return listOf(leftSide, rightSide)
                    }
                }
            }

            return listOf(block)
        }
        
        // adding protection: get connected blocks, else only use block
        val blocks: List<Block> = if ( protect == true ) {
            getConnectedBlocks(block)
        } else {
            listOf(block)
        }

        if ( protect == true ) {
            for ( block in blocks ) {
                town.protectedBlocks.add(block)
            }
        }
        else {
            for ( block in blocks ) {
                town.protectedBlocks.remove(block)
            }
        }

        town.needsUpdate()
        needsSave = true
    }

    /**
     * Generate particles at town's protected chests viewed by
     * input resident
     */
    internal fun showProtectedChests(town: Town, resident: Resident) {
        val player = resident.player()
        if ( player === null ) {
            return
        }

        val protectedBlocks = town.protectedBlocks

        // create repeating event to spawn particles each second
        val task = object: BukkitRunnable() {
            private val particle = Particle.VILLAGER_HAPPY
            private val particleCount = 3
            private val randomOffsetXZ = 0.05
            private val randomOffsetY = 0.1

            val MAX_RUNS = 10
            var runCount = 0

            override fun run() {
                for ( block in protectedBlocks ) {

                    // corners
                    val location1 = Location(block.world, block.x.toDouble() + 0.1, block.y.toDouble() + 0.5, block.z.toDouble() + 0.1)
                    val location2 = Location(block.world, block.x.toDouble() + 0.1, block.y.toDouble() + 0.5, block.z.toDouble() + 0.9)
                    val location3 = Location(block.world, block.x.toDouble() + 0.9, block.y.toDouble() + 0.5, block.z.toDouble() + 0.1)
                    val location4 = Location(block.world, block.x.toDouble() + 0.9, block.y.toDouble() + 0.5, block.z.toDouble() + 0.9)

                    // centers
                    val location5 = Location(block.world, block.x.toDouble() + 0.5, block.y.toDouble() + 0.5, block.z.toDouble())
                    val location6 = Location(block.world, block.x.toDouble(), block.y.toDouble() + 0.5, block.z.toDouble() + 0.5)
                    val location7 = Location(block.world, block.x.toDouble() + 0.5, block.y.toDouble() + 0.5, block.z.toDouble() + 1.0)
                    val location8 = Location(block.world, block.x.toDouble() + 1.0, block.y.toDouble() + 0.5, block.z.toDouble() + 0.5)
                    
                    player.spawnParticle(particle, location1, particleCount, randomOffsetXZ, randomOffsetY, randomOffsetXZ)
                    player.spawnParticle(particle, location2, particleCount, randomOffsetXZ, randomOffsetY, randomOffsetXZ)
                    player.spawnParticle(particle, location3, particleCount, randomOffsetXZ, randomOffsetY, randomOffsetXZ)
                    player.spawnParticle(particle, location4, particleCount, randomOffsetXZ, randomOffsetY, randomOffsetXZ)
                    player.spawnParticle(particle, location5, particleCount, randomOffsetXZ, randomOffsetY, randomOffsetXZ)
                    player.spawnParticle(particle, location6, particleCount, randomOffsetXZ, randomOffsetY, randomOffsetXZ)
                    player.spawnParticle(particle, location7, particleCount, randomOffsetXZ, randomOffsetY, randomOffsetXZ)
                    player.spawnParticle(particle, location8, particleCount, randomOffsetXZ, randomOffsetY, randomOffsetXZ)
                }

                runCount += 1
                if ( runCount > MAX_RUNS ) {
                    Bukkit.getScheduler().cancelTask(this.taskId)
                    return
                }
            }
        }

        task.runTaskTimer(plugin!!, 0, 20)
        
    }


    // ==============================================
    // Hooks to external functions
    // ==============================================
    internal fun hookEssentials(essentials: Essentials) {
        Nodes.essentials = essentials

        // save op color
        try {
            val colorName = essentials.settings.operatorColor
            Chat.colorPlayerOp = ChatColor.valueOf(colorName)
        }
        catch ( err: Exception ) {
            System.err.println("Nodes.hookEssentials(): failed to match op color")
        }
    }

    // mark that protocol lib exists
    internal fun hookProtocolLib() {
        protocolLib = true
    }

    // just sets a flag that dynmap exists
    internal fun hookDynmap() {
        dynmap = true
    }
}
