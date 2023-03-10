/**
 * Ports system
 * 
 * Note: theres multithreaded read to save ports state with
 * non concurrent/synchronized HashMap. Other thread only reads
 * to save state, no structural change to HashMap, so mostly ok
 * but may potentially cause mismatched save
 */

package phonon.ports

import java.io.File
import java.io.FileReader
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import java.nio.ByteBuffer
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.nio.channels.AsynchronousFileChannel
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.Location
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.plugin.Plugin
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.entity.Player

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.player.PlayerBucketEmptyEvent

import org.bukkit.scheduler.BukkitTask
import org.bukkit.scheduler.BukkitRunnable

import org.bukkit.craftbukkit.v1_18_R2.entity.CraftEntity
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftPlayer
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket

import phonon.nodes.Nodes
import phonon.nodes.constants.DiplomaticRelationship
import phonon.nodes.objects.Town


/**
 * Chunk coordinate
 */
data class ChunkCoord(
    val x: Int,
    val z: Int
) {
    // bernstein djb2 hash using magic number 33:
    // hash = 33 * x + z
    override public fun hashCode(): Int {
        return ((this.x shl 5) + this.x) + z
    }

    companion object {
        fun fromBlockCoords(x: Int, z: Int): ChunkCoord = ChunkCoord(Math.floorDiv(x, 16), Math.floorDiv(z, 16))
    }
}

/**
 * Player warpable port
 */
public data class Port(
    val name: String,
    val locX: Int,
    val locZ: Int,
    val maxWarpDistance: Double,
    val protectDistance: Int,
    val warpTime: Double,
    val groups: List<Int>,    // indices of group port belongs to
    val isPublic: Boolean,
    val costMaterial: Material,
    var costAlly: Int,
    var costNeutral: Int,
    var costEnemy: Int,
    var allowAlly: Boolean,
    var allowNeutral: Boolean,
    var allowEnemy: Boolean
) {
    val chunkX = Math.floorDiv(locX, 16)
    val chunkZ = Math.floorDiv(locZ, 16)

    val warpXMin = locX - maxWarpDistance
    val warpXMax = locX + maxWarpDistance
    val warpZMin = locZ - maxWarpDistance
    val warpZMax = locZ + maxWarpDistance

    val protectXMin = locX - protectDistance
    val protectXMax = locX + protectDistance
    val protectZMin = locZ - protectDistance
    val protectZMax = locZ + protectDistance
}

public class PortSaveState(
    val name: String,
    val costs: IntArray,     // [ally, neutral, enemy]
    val access: BooleanArray // [ally, neutral, enemy]
)

public class SavedPorts(
    val ports: List<PortSaveState>
)

public val PORT_ACCESS_GROUPS: List<String> = listOf(
    "ally",
    "neutral",
    "enemy"
)

/**
 * Group of ports
 */
public data class PortGroup(
    val name: String,
    val ports: List<String>
)

internal fun playerHasPortModifyPermission(player: Player, port: Port): Boolean {
    if ( player.isOp() ) {
        return true
    }

    // check if player is in town and is an officer
    val town = Nodes.getTownFromPlayer(player)
    val owner = Ports.getPortOwner(port)
    if ( town !== null && owner !== null && Nodes.playerIsOfficer(town, player) && town.uuid == owner.uuid ) {
        return true
    }

    return false
}

public val PORT_COMMANDS: List<String> = listOf(
    "help",
    "list",
    "info",
    "allow",
    "fee",
    "warp"
)

public val PORT_ADMIN_COMMANDS: List<String> = listOf(
    "help",
    "reload",
    "createbeacon",
    "removebeacon"
)

public object PortCommand: CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, cmd: Command, commandLabel: String, args: Array<String>): Boolean {
        // no args, print help
        if ( args.size == 0 ) {
            PortCommand.printHelp(sender)
            return true
        }

        // parse subcommand
        val arg = args[0].lowercase()
        when ( arg ) {
            "help" -> printHelp(sender)
            "list" -> list(sender)
            "info" -> info(sender, args)
            "allow" -> allow(sender, args)
            "fee" -> fee(sender, args)
            "warp" -> doWarp(sender, args)
            else -> {
                Message.error(sender, "Invalid port command")
            }
        }

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        if ( args.size == 1 ) {
            return filterByStart(PORT_COMMANDS, args[0])
        }
        // match each subcommand format
        else if ( args.size > 1 ) {
            // handle specific subcommands
            when ( args[0].lowercase() ) {
                "info" -> {
                    if ( args.size == 2 ) {
                        return filterByStart(Ports.portNames, args[1])
                    }
                }

                "fee" -> {
                    if ( args.size == 2 ) {
                        return filterByStart(Ports.portNames, args[1])
                    }
                    else if ( args.size == 3 ) {
                        return filterByStart(PORT_ACCESS_GROUPS, args[2])
                    }
                }

                "allow" -> {
                    if ( args.size == 2 ) {
                        return filterByStart(Ports.portNames, args[1])
                    }
                    else if ( args.size == 3 ) {
                        return filterByStart(PORT_ACCESS_GROUPS, args[2])
                    }
                    else if ( args.size == 4 ) {
                        return filterByStart(listOf("true", "false"), args[3])
                    }
                }

                "warp" -> {
                    if ( args.size == 2 ) {
                        return filterByStart(Ports.portNames, args[1])
                    }
                    else if ( args.size == 3 ) {
                        return listOf("confirm")
                    }
                }
            }
        }
        
        return listOf()
    }

    private fun printHelp(sender: CommandSender) {
        Message.print(sender, "${ChatColor.AQUA}${ChatColor.BOLD}[Ports] for Mineman 1.18.2")
        Message.print(sender, "${ChatColor.AQUA}/port help${ChatColor.WHITE}: help")
        Message.print(sender, "${ChatColor.AQUA}/port list${ChatColor.WHITE}: list all ports")
        Message.print(sender, "${ChatColor.AQUA}/port info${ChatColor.WHITE}: print info about a port")
        Message.print(sender, "${ChatColor.AQUA}/port allow${ChatColor.WHITE}: set who can warp to port (neutral, ally, enemy)")
        Message.print(sender, "${ChatColor.AQUA}/port fee${ChatColor.WHITE}: set port warp fee")
        Message.print(sender, "${ChatColor.AQUA}/port warp${ChatColor.WHITE}: warp to a port")
        return
    }

    /**
     * Print list of all ports
     */
    private fun list(sender: CommandSender) {
        Message.print(sender, "${ChatColor.AQUA}${ChatColor.BOLD}List of port groups:")
        for ( group in Ports.portGroups ) {
            Message.print(sender, "${ChatColor.AQUA}${group.name}:")
            for ( portName in group.ports ) {
                val port = Ports.ports.get(portName)
                if ( port !== null ) {
                    if ( port.isPublic ) {
                        Message.print(sender, "- ${ChatColor.AQUA}${portName} (public)")
                    }
                    else {
                        Message.print(sender, "- ${ChatColor.AQUA}${portName} ${ChatColor.BOLD}(owned)")
                    }
                }
            }
        }
        Message.print(sender, "${ChatColor.DARK_AQUA}Use \"/port info [name]\" for details")
    }

    /**
     * Print port info
     */
    private fun info(sender: CommandSender, args: Array<String>) {
        if ( args.size < 2 ) {
            Message.error(sender, "Usage: /port info [name]")
            return
        }

        val portName = args[1].lowercase()
        val port = Ports.ports.get(portName)
        if ( port !== null ) {
            Message.print(sender, "${ChatColor.AQUA}${ChatColor.BOLD}Port ${port.name}:")
            Message.print(sender, "${ChatColor.AQUA}- (x,z): (${port.locX}, ${port.locZ})")
            Message.print(sender, "${ChatColor.AQUA}- Groups:")
            for ( groupIndex in port.groups ) {
                val group = Ports.portGroups.getOrNull(groupIndex)
                if ( group !== null ) {
                    Message.print(sender, "${ChatColor.AQUA}  - ${group.name}")
                }
            }

            if ( port.isPublic ) {
                Message.print(sender, "${ChatColor.AQUA}- Public")
            }
            else {
                // get owner and print costs
                val owner = Ports.getPortOwner(port)
                val ownerName = if ( owner !== null ) {
                    owner.name
                } else {
                    "${ChatColor.GRAY}None"
                }
                Message.print(sender, "${ChatColor.AQUA}- Owner: ${ownerName}")
                Message.print(sender, "${ChatColor.AQUA}- Access:")
                if ( port.allowAlly ) {
                    Message.print(sender, "${ChatColor.AQUA}  - Ally: ${ChatColor.WHITE}true (Cost: ${port.costAlly} ${port.costMaterial})")
                }
                else {
                    Message.print(sender, "${ChatColor.AQUA}  - Ally: ${ChatColor.RED}false")
                }

                if ( port.allowNeutral ) {
                    Message.print(sender, "${ChatColor.AQUA}  - Neutral: ${ChatColor.WHITE}true (Cost: ${port.costNeutral} ${port.costMaterial})")
                }
                else {
                    Message.print(sender, "${ChatColor.AQUA}  - Neutral: ${ChatColor.RED}false")
                }

                if ( port.allowEnemy ) {
                    Message.print(sender, "${ChatColor.AQUA}  - Enemy: ${ChatColor.WHITE}true (Cost: ${port.costEnemy} ${port.costMaterial})")
                }
                else {
                    Message.print(sender, "${ChatColor.AQUA}  - Enemy: ${ChatColor.RED}false")
                }
            }
        }
    }

    /**
     * Change port access flags
     */
    private fun allow(sender: CommandSender, args: Array<String>) {
        if ( args.size < 4 ) {
            Message.error(sender, "Usage: /port allow [name] [group] [true|false]")
            Message.error(sender, "Groups are: ally, neutral, enemy")
            return
        }

        val portName = args[1].lowercase()
        val group = args[2].lowercase()
        val setting = args[3].lowercase().toBoolean()

        val player = if ( sender is Player ) sender else null
        if ( player === null ) {
            return
        }

        val port = Ports.ports.get(portName)
        if ( port === null ) {
            Message.error(sender, "Invalid port name: ${args[1]}")
            return
        }

        if ( port.isPublic ) {
            Message.error(sender, "This port is public")
            return
        }

        if ( !PORT_ACCESS_GROUPS.contains(group) ) {
            Message.error(sender, "Invalid group ${group}, available are: ${PORT_ACCESS_GROUPS}")
            return
        }
        
        if ( !playerHasPortModifyPermission(player, port) ) {
            Message.error(sender, "No permissions in port ${args[1]}: you must be an officer of the town that owns the port")
            return
        }

        Ports.setPortAccess(port, group, setting)
        Message.print(sender, "Port ${args[1]}: access for ${group} set to ${ChatColor.BOLD}${setting}")
    }

    /**
     * Set port warp fee
     */
    private fun fee(sender: CommandSender, args: Array<String>) {
        if ( args.size < 4 ) {
            Message.error(sender, "Usage: /port fee [name] [group] [amount]")
            Message.error(sender, "Groups are: ally, neutral, enemy")
            return
        }

        val portName = args[1].lowercase()
        val group = args[2].lowercase()
        val fee = args[3].toInt().coerceIn(0, Ports.maxCost)

        val player = if ( sender is Player ) sender else null
        if ( player === null ) {
            return
        }

        val port = Ports.ports.get(portName)
        if ( port === null ) {
            Message.error(sender, "Invalid port name: ${args[1]}")
            return
        }

        if ( port.isPublic ) {
            Message.error(sender, "This port is public")
            return
        }

        if ( !PORT_ACCESS_GROUPS.contains(group) ) {
            Message.error(sender, "Invalid group ${group}, available are: ${PORT_ACCESS_GROUPS}")
            return
        }
        
        if ( !playerHasPortModifyPermission(player, port) ) {
            Message.error(sender, "No permissions in port ${args[1]}: you must be an officer of the town that owns the port")
            return
        }

        Ports.setPortCost(port, group, fee)
        Message.print(sender, "Port ${args[1]}: fee for ${group} set to ${fee}")
    }

    /**
     * Initiate port warp
     */
    private fun doWarp(sender: CommandSender, args: Array<String>) {
        val player = if ( sender is Player ) sender else null
        if ( player === null ) {
            Message.error(sender, "Only players in game can use")
            return
        }

        if ( args.size < 2 ) {
            Message.error(sender, "Usage: /port warp [destination]")
            return
        }

        val portName = args[1].lowercase()
        val confirmation = args.getOrNull(2)?.lowercase() == "confirm"

        Ports.doWarp(player, portName, confirmation)
    }
}


/**
 * Admin commands
 */
public object PortAdminCommand: CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, cmd: Command, commandLabel: String, args: Array<String>): Boolean {
        // no args, print help
        if ( args.size == 0 ) {
            PortAdminCommand.printHelp(sender)
            return true
        }

        // parse subcommand
        val arg = args[0].lowercase()
        when ( arg ) {
            "help" -> printHelp(sender)
            "reload" -> reload(sender)
            "createbeacon" -> createbeacon(sender)
            "removebeacon" -> removebeacon(sender)
            else -> {
                Message.error(sender, "Invalid port admin command")
            }
        }

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        if ( args.size == 1 ) {
            return filterByStart(PORT_ADMIN_COMMANDS, args[0])
        }

        return listOf()
    }

    private fun printHelp(sender: CommandSender) {
        Message.print(sender, "${ChatColor.AQUA}${ChatColor.BOLD}[Ports] for Mineman 1.18.2")
        Message.print(sender, "${ChatColor.AQUA}/portadmin help${ChatColor.WHITE}: help")
        Message.print(sender, "${ChatColor.AQUA}/portadmin reload${ChatColor.WHITE}: reload config (admin only)")
        Message.print(sender, "${ChatColor.AQUA}/portadmin createbeacon${ChatColor.WHITE}: create port beacons")
        Message.print(sender, "${ChatColor.AQUA}/portadmin removebeacon${ChatColor.WHITE}: remove port beacons")
        return
    }

    /**
     * Reload plugin config (op only)
     */
    private fun reload(sender: CommandSender) {
        Ports.reload()
        Message.print(sender, "[Ports] reloaded")
    }
    
    /**
     * Create block beacon at port locations
     */
    private fun createbeacon(sender: CommandSender) {
        Ports.createBeacons()
        Message.print(sender, "[Ports] Created port beacons")
    }

    /**
     * Remove block beacon at port locations
     */
    private fun removebeacon(sender: CommandSender) {
        Ports.removeBeacons()
        Message.print(sender, "[Ports] Removed port beacons")
    }
}


/**
 * Protect ports from damage
 */
public class PortsProtectionListener: Listener {

    /**
     * prevent breaking blocks near port
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public fun onBlockBreak(event: BlockBreakEvent) {
        // op bypass
        val player = event.player
        if ( player.isOp() ) {
            return
        }
        
        val block = event.block
        val chunk = block.chunk
        val port = Ports.chunkToPort.get(ChunkCoord(chunk.x, chunk.z))
        if ( port === null ) {
            return
        }

        if ( block.x < port.protectXMin || block.x > port.protectXMax || block.z < port.protectZMin || block.z > port.protectZMax ) {
            return
        }

        Message.error(player, "Cannot destroy within ${Ports.protectDistance} blocks of the port")
        event.setCancelled(true)
    }

    /**
     * prevent breaking blocks near port
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public fun onBlockPlace(event: BlockPlaceEvent) {
        // op bypass
        val player = event.player
        if ( player.isOp() ) {
            return
        }
        
        val block = event.block
        val chunk = block.chunk
        val port = Ports.chunkToPort.get(ChunkCoord(chunk.x, chunk.z))
        if ( port === null ) {
            return
        }

        if ( block.x < port.protectXMin || block.x > port.protectXMax || block.z < port.protectZMin || block.z > port.protectZMax ) {
            return
        }

        Message.error(player, "Cannot place within ${Ports.protectDistance} blocks of the port")
        event.setCancelled(true)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public fun onPlayerBucketEmpty(event: PlayerBucketEmptyEvent) {
        // op bypass
        val player = event.player
        if ( player.isOp() ) {
            return
        }

        val block = event.getBlockClicked().getRelative(event.getBlockFace())
        val chunk = block.chunk
        val port = Ports.chunkToPort.get(ChunkCoord(chunk.x, chunk.z))
        if ( port === null ) {
            return
        }

        if ( block.x < port.protectXMin || block.x > port.protectXMax || block.z < port.protectZMin || block.z > port.protectZMax ) {
            return
        }

        Message.error(player, "Cannot place within ${Ports.protectDistance} blocks of the port")
        event.setCancelled(true)
    }

    /**
     * Disable pistons
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public fun onPistonExtend(event: BlockPistonExtendEvent) {
        val block = event.block
        val chunk = block.chunk
        val port = Ports.chunkToPort.get(ChunkCoord(chunk.x, chunk.z))
        if ( port === null ) {
            return
        }

        if ( block.x < port.protectXMin || block.x > port.protectXMax || block.z < port.protectZMin || block.z > port.protectZMax ) {
            return
        }

        event.setCancelled(true)
    }

    /**
     * Disable pistons
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public fun onPistonRetract(event: BlockPistonRetractEvent) {
        val block = event.block
        val chunk = block.chunk
        val port = Ports.chunkToPort.get(ChunkCoord(chunk.x, chunk.z))
        if ( port === null ) {
            return
        }

        if ( block.x < port.protectXMin || block.x > port.protectXMax || block.z < port.protectZMin || block.z > port.protectZMax ) {
            return
        }

        event.setCancelled(true)
    }

    /**
     * Shitty handler, just check if exploding block inside range
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public fun onEntityExplode(event: EntityExplodeEvent) {
        val exploded = event.getEntity().location
        val chunk = exploded.chunk
        val port = Ports.chunkToPort.get(ChunkCoord(chunk.x, chunk.z))
        if ( port === null ) {
            return
        }

        if ( exploded.x < port.protectXMin || exploded.x > port.protectXMax || exploded.z < port.protectZMin || exploded.z > port.protectZMax ) {
            return
        }

        event.setCancelled(true)
    }

    /**
     * Shitty handler, just check if exploding block inside range
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public fun onBlockExplode(event: BlockExplodeEvent) {
        val exploded = event.getBlock()
        val chunk = exploded.chunk
        val port = Ports.chunkToPort.get(ChunkCoord(chunk.x, chunk.z))
        if ( port === null ) {
            return
        }

        if ( exploded.x < port.protectXMin || exploded.x > port.protectXMax || exploded.z < port.protectZMin || exploded.z > port.protectZMax ) {
            return
        }

        event.setCancelled(true)
    }
}

/*
 * Main manager + implement bukkit plugin interface
 */
public object Ports {
    // default constants
    public val BASE_WARP_TIME: Double = 5.0

    // ==============================
    // config
    // ==============================
    // debug mode
    public var debug: Boolean = false

    // sea level
    public var seaLevel: Double = 40.0

    // how close to port player needs to be
    public var maxDistanceFromPortToWarp: Double = 8.0
    public var maxDistanceFromPortToWarpSquared: Double = 49.0

    // time to warp to a port in seconds
    public var baseWarpTime: Double = BASE_WARP_TIME * 20.0

    // allow port warp without a boat
    public var allowWarpWithoutBoat: Boolean = false
    
    // protect port blocks in distance around center
    public var protectDistance: Int = 6

    // default flags for letting groups warp to port
    public var defaultAllowAlly: Boolean = true
    public var defaultAllowNeutral: Boolean = false
    public var defaultAllowEnemy: Boolean = false
    
    // default material and fee cost
    public var defaultCostMaterial: Material = Material.GOLD_INGOT
    public var defaultCostAlly: Int = 0
    public var defaultCostNeutral: Int = 1
    public var defaultCostEnemy: Int = 3

    // max fee players can set for warp
    public var maxCost: Int = 64

    // port warp beacon indicator
    public var beaconPillarMaterial: Material = Material.BEDROCK
    public var beaconPillarHeight: Int = 30
    public var beaconPillarStartY: Int = 30
    public var beaconLightMaterial: Material = Material.BEACON
    public var beaconLightHeight: Int = 1

    // ticks period to check if save required
    public var checkSavePeriod: Long = 1200L

    // save path
    public var dirSave: Path = Paths.get("plugins", "ports")
    public var pathSave: Path = dirSave.resolve("save.json")

    // dynmap path
    public var pathDynmap: Path = Paths.get("plugins", "dynmap", "web", "nodes").normalize()

    // ==============================
    // engine internal
    // ==============================
    // default minecraft world
    private var defaultWorld: World = Bukkit.getWorlds().get(0)

    // reusable Gson instance
    private val gson: Gson = Gson()

    // bukkit plugin binding
    private var plugin: Plugin? = null

    // main ports container
    public var ports: HashMap<String, Port> = hashMapOf()
    
    // saved list of all port names (i.e. keys to ports hashmap)
    public var portNames: List<String> = listOf()
    
    // port groups, PortGroup only contains list of port names not objects themselves
    public var portGroups: List<PortGroup> = listOf()
    
    // map chunk coords -> port, assumes one chunk only has 1 port
    public var chunkToPort: HashMap<ChunkCoord, Port> = hashMapOf()
    
    // lookup for chunks that contain a port
    // private var chunksWithPort: HashSet<Coord> = hashSetOf()
    
    // map of player -> task for warping
    public var playerWarpTasks: HashMap<UUID, BukkitTask> = hashMapOf()
    
    // require save
    private var needsSave: Boolean = false

    // periodic save task
    private var saveTask: BukkitTask? = null

    /**
     * Bind to plugin
     */
    public fun initialize(plugin: Plugin) {
        Ports.plugin = plugin
    }

    /**
     * Run when plugin disables
     */
    public fun onDisable() {
        Ports.saveTask?.cancel()
        Ports.savePortData(Ports.pathSave)
    }
    
    /**
     * Load engine settings config, update settings
     */
    public fun loadConfig() {
        val plugin = Ports.plugin
        if ( plugin === null ) {
            return
        }

        // get config file
        val configFile = File(plugin.getDataFolder().getPath(), "config.yml")
        if ( !configFile.exists() ) {
            plugin.getLogger().info("No config found: generating default config.yml")
            plugin.saveDefaultConfig()
        }

        val config = YamlConfiguration.loadConfiguration(configFile)

        Ports.debug = config.getBoolean("debug", false)

        Ports.seaLevel = config.getDouble("seaLevel", Ports.seaLevel)

        Ports.maxDistanceFromPortToWarp = config.getDouble("maxDistanceFromPortToWarp", Ports.maxDistanceFromPortToWarp)
        Ports.maxDistanceFromPortToWarpSquared = Ports.maxDistanceFromPortToWarp * Ports.maxDistanceFromPortToWarp
        
        Ports.baseWarpTime = config.getDouble("baseWarpTime", Ports.BASE_WARP_TIME) * 20.0 // time is in seconds, convert to ticks
        Ports.allowWarpWithoutBoat = config.getBoolean("allowWarpWithoutBoat", Ports.allowWarpWithoutBoat)
        
        Ports.protectDistance = config.getInt("protectDistance", Ports.protectDistance)

        Ports.defaultAllowAlly = config.getBoolean("defaultAllowAlly", Ports.defaultAllowAlly)
        Ports.defaultAllowNeutral = config.getBoolean("defaultAllowNeutral", Ports.defaultAllowNeutral)
        Ports.defaultAllowEnemy = config.getBoolean("defaultAllowEnemy", Ports.defaultAllowEnemy)

        Ports.defaultCostMaterial = Material.matchMaterial(config.getString("defaultCostMaterial") ?: Ports.defaultCostMaterial.toString()) ?: Ports.defaultCostMaterial
        Ports.defaultCostAlly = config.getInt("defaultCostAlly", Ports.defaultCostAlly)
        Ports.defaultCostNeutral = config.getInt("defaultCostNeutral", Ports.defaultCostNeutral)
        Ports.defaultCostEnemy = config.getInt("defaultCostEnemy", Ports.defaultCostEnemy)
        Ports.maxCost = config.getInt("maxCost", Ports.maxCost)

        Ports.beaconPillarMaterial = Material.matchMaterial(config.getString("beaconPillarMaterial") ?: Ports.beaconPillarMaterial.toString()) ?: Ports.beaconPillarMaterial
        Ports.beaconPillarHeight = config.getInt("beaconPillarHeight", Ports.beaconPillarHeight)
        Ports.beaconPillarStartY = config.getInt("beaconPillarStartY", Ports.beaconPillarStartY)
        Ports.beaconLightMaterial = Material.matchMaterial(config.getString("beaconLightMaterial") ?: Ports.beaconLightMaterial.toString()) ?: Ports.beaconPillarMaterial
        Ports.beaconLightHeight = config.getInt("beaconLightHeight", Ports.beaconLightHeight)

        Ports.checkSavePeriod = config.getLong("checkSavePeriod", Ports.checkSavePeriod)

        Ports.dirSave = config.getString("dirSave")?.let{ p -> Paths.get(p) } ?: Ports.dirSave
        Ports.pathSave = config.getString("pathSave")?.let{ p -> Ports.dirSave.resolve(p) } ?: Ports.pathSave
    }

    /**
     * Load ports config
     */
    public fun loadPorts() {
        val plugin = Ports.plugin
        if ( plugin === null ) {
            return
        }

        // get port config file
        val configFile = File(plugin.getDataFolder().getPath(), "ports.yml")
        if ( !configFile.exists() ) {
            plugin.getLogger().info("Saving default ports.yml")
            plugin.saveResource("ports.yml", false)
            return
        }

        val config = YamlConfiguration.loadConfiguration(configFile)

        try {
            // structures to load
            val ports: HashMap<String, Port> = hashMapOf()
            val portNames: ArrayList<String> = ArrayList()
            val portGroups: ArrayList<PortGroup> = ArrayList()
            val chunkToPort: HashMap<ChunkCoord, Port> = hashMapOf()

            // load port groups
            val groupNames: List<String> = config.getList("groups")?.map{ v -> v.toString().lowercase() }?.toList() ?: listOf()
            
            // temp structure to hold ports for each group
            val portGroupsList: ArrayList<ArrayList<String>> = ArrayList(groupNames.size)

            // map port group name -> array index
            val groupNameToIndex: HashMap<String, Int> = hashMapOf()
            for ( (i, name) in groupNames.withIndex() ) {
                groupNameToIndex.put(name, i)
                portGroupsList.add(ArrayList())
            }

            // load ports
            val portsSection = config.getConfigurationSection("ports")
            if ( portsSection !== null ) {
                for ( portNameRaw in portsSection.getKeys(false) ) {
                    val portConfig = portsSection.getConfigurationSection(portNameRaw)
                    if ( portConfig === null ) {
                        continue
                    }

                    // convert raw key to lowercase
                    val portName = portNameRaw.lowercase()

                    if ( !portConfig.isInt("x") || !portConfig.isInt("z") ) {
                        continue
                    }
                    val locX = portConfig.getInt("x")
                    val locZ = portConfig.getInt("z")
                    
                    // convert group names to lowercase
                    val groupNames: List<String> = if ( portConfig.isList("group") ) {
                        portConfig.getStringList("group").map{ s -> s.lowercase() }
                    } else if ( portConfig.isString("group") ) {
                        listOf(portConfig.getString("group")!!.lowercase())
                    } else {
                        listOf()
                    }
                    
                    // map group names to group indices
                    val groups: ArrayList<Int> = ArrayList()
                    for ( name in groupNames ) {
                        val index = groupNameToIndex.get(name)
                        if ( index !== null ) {
                            groups.add(index)
                            portGroupsList.getOrNull(index)?.add(portName)
                        } else {
                            plugin.getLogger().warning("Port group '${name}' not found")
                        }
                    }

                    val isPublic = portConfig.getBoolean("public", true)

                    val warpTimeModifier = portConfig.getDouble("warpTimeModifier", 0.0) * 20.0 // time in seconds, convert to ticks

                    val costMaterial = Material.matchMaterial(config.getString("costMaterial") ?: Ports.defaultCostMaterial.toString()) ?: Ports.defaultCostMaterial
                    val costAlly = config.getInt("costAlly", Ports.defaultCostAlly).coerceIn(0, Ports.maxCost)
                    val costNeutral = config.getInt("costNeutral", Ports.defaultCostNeutral).coerceIn(0, Ports.maxCost)
                    val costEnemy = config.getInt("costEnemy", Ports.defaultCostEnemy).coerceIn(0, Ports.maxCost)

                    val allowAlly = portConfig.getBoolean("allowAlly", Ports.defaultAllowAlly)
                    val allowNeutral = portConfig.getBoolean("allowNeutral", Ports.defaultAllowNeutral)
                    val allowEnemy = portConfig.getBoolean("allowEnemy", Ports.defaultAllowEnemy)

                    // create port
                    val port = Port(
                        portName,
                        locX,
                        locZ,
                        Ports.maxDistanceFromPortToWarp,
                        Ports.protectDistance,
                        Ports.baseWarpTime + warpTimeModifier,
                        groups.toList(),
                        isPublic,
                        costMaterial,
                        costAlly,
                        costNeutral,
                        costEnemy,
                        allowAlly,
                        allowNeutral,
                        allowEnemy
                    )

                    // for each port, map the chunks its in to it (square AABB around port center)
                    val maxDist = Ports.maxDistanceFromPortToWarp.toInt()
                    val c0 = ChunkCoord.fromBlockCoords(locX, locZ)
                    val c1 = ChunkCoord.fromBlockCoords(locX - maxDist, locZ - maxDist)
                    val c2 = ChunkCoord.fromBlockCoords(locX + maxDist, locZ - maxDist)
                    val c3 = ChunkCoord.fromBlockCoords(locX - maxDist, locZ + maxDist)
                    val c4 = ChunkCoord.fromBlockCoords(locX + maxDist, locZ + maxDist)
                    chunkToPort.put(c0, port)
                    chunkToPort.put(c1, port)
                    chunkToPort.put(c2, port)
                    chunkToPort.put(c3, port)
                    chunkToPort.put(c4, port)
                    
                    ports.put(portName, port)
                    portNames.add(portName)
                }

                // finish port groups
                for ( (i, portList) in portGroupsList.withIndex() ) {
                    portGroups.add(PortGroup(
                        groupNames[i],
                        portList.toList()
                    ))
                }
            }

            Ports.ports = ports
            Ports.portNames = portNames.toList()
            Ports.portGroups = portGroups.toList()
            Ports.chunkToPort = chunkToPort

            // save to json
            Ports.saveToJson(Ports.pathDynmap)
        }
        catch ( err: Exception ) {
            err.printStackTrace()
        }
    }

    /**
     * Save port json data
     * (For nodes dynmap renderer)
     */
    public fun saveToJson(dir: Path) {
        // create save directory
        if ( !Files.exists(dir) ) {
            return
        }
        var i = 0
        // create json string
        val portsJson = StringBuilder("{\"meta\":{\"type\":\"ports\"},\"ports\":{")
        for ( (name, port) in Ports.ports ) {
            val portGroupNames = port.groups.map{x -> Ports.portGroups.get(x)}.map{x -> "\"${x.name}\""}.joinToString(",")
            portsJson.append("\"${name}\":{")
            portsJson.append("\"groups\":[${portGroupNames}],")
            portsJson.append("\"x\":${port.locX},")
            portsJson.append("\"z\":${port.locZ}")
            portsJson.append("}")
            if ( i < Ports.ports.size - 1 ) {
                portsJson.append(",")
            }
            i += 1
        }
        portsJson.append("}}")

        // save
        saveStringToFile(portsJson.toString(), dir.resolve("ports.json"))
    }

    /**
     * Reload config and background tasks
     */
    public fun reload() {
        // create save path
        Files.createDirectories(Ports.dirSave)

        // stop old shit
        val saveTask = Ports.saveTask
        if ( saveTask !== null ) {
            saveTask.cancel()
            Ports.saveTask = null
        }
        
        // sync save
        if ( Ports.needsSave ) {
            Ports.savePortData(Ports.pathSave)
            Ports.needsSave = false
        }

        // clear data structures
        Ports.ports.clear()
        Ports.portNames = listOf()
        Ports.portGroups = listOf()

        Ports.loadConfig()
        Ports.loadPorts()
        Ports.loadPortData(Ports.pathSave)
        Ports.startSaveTask()

        // print loaded info
        val logger = Ports.plugin!!.getLogger()
        logger.info("Reloaded")
        logger.info("Num ports: ${Ports.portNames.size}")
        logger.info("Num port groups: ${Ports.portGroups.size}")
    }

    /**
     * begin periodic save task
     */
    public fun startSaveTask() {
        Ports.saveTask?.cancel()
        
        Ports.saveTask = Bukkit.getScheduler().runTaskTimer(Ports.plugin!!, object: Runnable {
            override public fun run() {
                Ports.saveTick()
            }
        }, Ports.checkSavePeriod, Ports.checkSavePeriod)
    }

    /**
     * Periodic save tick, schedules an async save if needsSave == true
     */
    public fun saveTick() {
        if ( Ports.needsSave ) {
            Ports.needsSave = false

            Bukkit.getScheduler().runTaskAsynchronously(Ports.plugin!!, object: Runnable {
                override public fun run() {
                    Ports.savePortData(Ports.pathSave)
                }
            })
        }
    }

    /**
     * Saves data for non public ports (owned by towns):
     * - flags for who can use
     * - warp costs
     */
    public fun savePortData(path: Path) {
        val portsToSave: ArrayList<PortSaveState> = arrayListOf()

        for ( name in Ports.portNames ) {
            val port = ports.get(name)
            if ( port !== null && !port.isPublic ) {
                val saveState = PortSaveState(
                    name,
                    intArrayOf(port.costAlly, port.costNeutral, port.costEnemy),
                    booleanArrayOf(port.allowAlly, port.allowNeutral, port.allowEnemy)
                )
                portsToSave.add(saveState)
            }
        }

        val json = gson.toJson(SavedPorts(portsToSave.toList()))
        val buffer = ByteBuffer.wrap(json.toString().toByteArray())
        val fileChannel = AsynchronousFileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        val operation = fileChannel.write(buffer, 0);
        operation.get()
    }

    /**
     * Load user config port data from .json
     */
    public fun loadPortData(path: Path) {
        try {
            if ( !Files.exists(path) ) {
                System.err.println("No saved data at ${path}")
                return
            }

            val json = JsonParser().parse(FileReader(path.toString()))

            val gson = Ports.gson
            val savedPortData = gson.fromJson(json, SavedPorts::class.java)

            for ( portData in savedPortData.ports ) {
                try {
                    val portName = portData.name
                    Ports.ports.get(portName)?.let{ p -> 
                        p.costAlly = portData.costs[0]
                        p.costNeutral = portData.costs[1]
                        p.costEnemy = portData.costs[2]       
                        p.allowAlly = portData.access[0]
                        p.allowNeutral = portData.access[1]
                        p.allowEnemy = portData.access[2]       
                    }
                }
                catch ( err: Exception ) {
                    err.printStackTrace()
                }
            }
        }
        catch ( err: Exception ) {
            err.printStackTrace()
        }
    }

    /**
     * Allow group to warp to port
     */
    public fun setPortAccess(port: Port, group: String, setting: Boolean) {
        if ( group == "ally" ) {
            port.allowAlly = setting
            Ports.needsSave = true
        }
        else if ( group == "neutral" ) {
            port.allowNeutral = setting
            Ports.needsSave = true
        }
        else if ( group == "enemy" ) {
            port.allowEnemy = setting
            Ports.needsSave = true
        }
    }

    /**
     * Set port warp fee for group
     */
    public fun setPortCost(port: Port, group: String, fee: Int) {
        if ( group == "ally" ) {
            port.costAlly = fee
            Ports.needsSave = true
        }
        else if ( group == "neutral" ) {
            port.costNeutral = fee
            Ports.needsSave = true
        }
        else if ( group == "enemy" ) {
            port.costEnemy = fee
            Ports.needsSave = true
        }
    }

    /**
     * Check if two ports share a group
     */
    public fun shareGroups(port1: Port, port2: Port): Boolean {
        val shared: BooleanArray = BooleanArray(Ports.portGroups.size, {_ -> false})
        for ( i in port1.groups ) {
            shared[i] = true
        }
        for ( i in port2.groups ) {
            if ( shared[i] ) {
                return true
            }
        }
        return false
    }

    /**
     * Get port owner based on who owns chunk
     * If no owner or if port is public, return null
     * If territory occupied, return occupier
     * Else, return territory town (may be null)
     */
    public fun getPortOwner(port: Port): Town? {
        if ( port.isPublic ) {
            return null
        }

        val territory = Nodes.getTerritoryFromChunkCoords(port.chunkX, port.chunkZ)
        if ( territory === null ) {
            return null
        }
        
        val occupier = territory.occupier
        if ( occupier !== null ) {
            return occupier
        }
        
        return territory.town
    }

    /**
     * Create port beacon pillars on port locations
     */
    public fun createBeacons() {
        // get default world for now
        val world = Bukkit.getWorlds().getOrNull(0)
        if ( world === null ) {
            return
        }

        for ( port in Ports.ports.values ) {
            val x = port.locX
            val z = port.locZ
            val yStart = Ports.beaconPillarStartY
            val yEnd = Ports.beaconPillarStartY + Ports.beaconPillarHeight
            val yLightEnd = yEnd + Ports.beaconLightHeight
            
            println("[Ports] creating beacon at ${x} ${z} (from ${yStart} to ${yLightEnd}")
            
            // create pillar
            for ( y in yStart until yEnd ) {
                val block = world.getBlockAt(x, y, z)
                block.setType(Ports.beaconPillarMaterial)
            }

            // create light
            for ( y in yEnd until yLightEnd ) {
                val block = world.getBlockAt(x, y, z)
                block.setType(Ports.beaconLightMaterial)
            }
        }
    }

    /**
     * Removeport beacon pillars on port locations
     */
    public fun removeBeacons() {
        // get default world for now
        val world = Bukkit.getWorlds().getOrNull(0)
        if ( world === null ) {
            return
        }

        for ( port in Ports.ports.values ) {
            val x = port.locX
            val z = port.locZ
            val yStart = Ports.beaconPillarStartY
            val yLightEnd = Ports.beaconPillarStartY + Ports.beaconPillarHeight + Ports.beaconLightHeight

            println("[Ports] removing beacon at ${x} ${z} (from ${yStart} to ${yLightEnd}")

            // remove pillar + light
            for ( y in yStart until yLightEnd ) {
                val block = world.getBlockAt(x, y, z)
                val adjacent = block.getRelative(-1, 0, 0)
                if ( adjacent.type == Material.WATER ) {
                    block.setType(Material.WATER)
                }
                else {
                    block.setType(Material.AIR)
                }
            }
        }
    }

    /**
     * Player try to warp to a port
     */
    public fun doWarp(
        player: Player,
        destinationName: String,
        confirmed: Boolean
    ) {
        val playerUuid = player.getUniqueId()

        // check if player is already warping
        if ( playerWarpTasks.contains(playerUuid) ) {
            Message.print(player, "${ChatColor.RED}You are already warping somewhere")
            return
        }

        // get port player is at
        val locPlayer = player.location
        val chunkCoord = ChunkCoord.fromBlockCoords(locPlayer.getBlockX(), locPlayer.getBlockZ())
        val source = Ports.chunkToPort.get(chunkCoord)
        if ( source === null ) {
            Message.print(player, "${ChatColor.RED}You must be at a port to warp to another port")
            return
        }
        else {
            val px = player.location.x
            val pz = player.location.z
            if ( px < source.warpXMin || px > source.warpXMax || pz < source.warpZMin || pz > source.warpZMax ) {
                Message.error(player, "You must be <${maxDistanceFromPortToWarp} of the port to warp")
                return
            }
        }

        // check if port exists
        val destination = Ports.ports.get(destinationName.lowercase())
        if ( destination === null ) {
            Message.error(player, "Port does not exist...")
            return
        }

        // check if port is same
        if ( source === destination ) {
            Message.error(player, "You are already at this port...")
            return
        }

        // verify ports share groups
        if ( !Ports.shareGroups(source, destination) ) {
            Message.error(player, "These ports are not in the same region group...")
            return
        }

        // acquire costs and access based on port
        var cost = 0
        var costMaterial = Material.AIR

        // owned port, check permissions
        if ( !destination.isPublic ) {
            val owner = Ports.getPortOwner(destination)
            if ( owner === null ) {
                // allow warp as if public for now
            }
            else {
                val relation = Nodes.getRelationshipOfPlayerToTown(player, owner)

                val access: Boolean
                when ( relation ) {
                    DiplomaticRelationship.TOWN,
                    DiplomaticRelationship.NATION -> {
                        access = true
                        cost = 0
                    }
                    
                    DiplomaticRelationship.ALLY -> {
                        access = destination.allowAlly
                        cost = destination.costAlly
                    }

                    DiplomaticRelationship.NEUTRAL -> {
                        access = destination.allowNeutral
                        cost = destination.costNeutral
                    }

                    DiplomaticRelationship.ENEMY -> {
                        access = destination.allowEnemy
                        cost = destination.costEnemy
                    }
                }
                
                costMaterial = destination.costMaterial

                if ( access == false ) {
                    Message.error(player, "Port ${destinationName}'s owner ${owner.name} has closed access to you (${relation})")
                    return
                }
                
                // require confirmation if port has a fee
                if ( cost > 0 && destination.costMaterial != Material.AIR && confirmed == false ) {
                    if ( confirmed == false ) {
                        Message.print(player, "${ChatColor.AQUA}Port ${destinationName}'s owner ${owner.name} has set a fee: ${cost} ${destination.costMaterial}, type \"/port warp ${destinationName} confirm\" to confirm warp")
                        return
                    }
                    else if ( !player.getInventory().contains(destination.costMaterial, cost) ) {
                        Message.error(player, "You do not have enough to pay port ${destinationName}'s owner's ${owner.name} warp fee: ${cost} ${destination.costMaterial}")
                        return
                    }
                }

            }
        }

        // determine who is warping and time:
        val playersToWarp: ArrayList<Player> = arrayListOf()
        val entitiesToWarp: ArrayList<Entity> = arrayListOf()
        var warpTime = destination.warpTime

        // 1. player by itself, no vehicle
        val entityVehicle = player.getVehicle()
        if ( entityVehicle === null ) {
            if ( !Ports.allowWarpWithoutBoat ) {
                Message.error(player, "You must be in a boat or a ship vehicle to warp to ports")
                return
            }

            playersToWarp.add(player)
        }
        else {
            // 2. player in a boat
            if ( entityVehicle.type == EntityType.BOAT ) {
                entitiesToWarp.add(entityVehicle)
            }
            // // 3. player in a custom plugin vehicle (ArmorStand only)
            // else if ( entityVehicle.type == EntityType.ARMOR_STAND ) {
            //     // TODO
            // }
        }

        // do warp
        val task = PortWarpTask(
            player,
            destination,
            playersToWarp.toList(),
            entitiesToWarp.toList(),
            player.location.clone(),
            costMaterial,
            cost,
            warpTime,
            2.0
        )
        
        // run asynchronous warp timer
        Ports.playerWarpTasks.put(
            player.getUniqueId(),
            task.runTaskTimerAsynchronously(Ports.plugin!!, 2, 2)
        )
    }

    /**
     * Task for running warp
     */
    public class PortWarpTask(
        val player: Player,
        val destination: Port,
        val playersToWarp: List<Player>,
        val entitiesToWarp: List<Entity>,
        val initialLoc: Location,
        val costMaterial: Material,
        val costAmount: Int,
        val timeWarp: Double,
        val tick: Double
    ): BukkitRunnable() {
        private val locX = initialLoc.getBlockX()
        private val locY = initialLoc.getBlockY()
        private val locZ = initialLoc.getBlockZ()
    
        // remaining time counter
        private var time = timeWarp
    
        override fun run() {
            // check if player moved
            val location = player.location
            if ( locX != location.getBlockX() || locY != location.getBlockY() || locZ != location.getBlockZ() ) {
                Message.announcement(player, "${ChatColor.RED}Moved! Stopped warping...")
                this.cancel()

                // schedule main thread to remove cancelled task
                Bukkit.getScheduler().runTask(Ports.plugin!!, object: Runnable {
                    override public fun run() {
                        Ports.playerWarpTasks.remove(player.getUniqueId())
                    }
                })

                return
            }
            
            time -= tick
            
            if ( time <= 0.0 ) {
                this.cancel()

                // schedule main thread to finish warp
                Bukkit.getScheduler().runTask(Ports.plugin!!, object: Runnable {
                    override public fun run() {
                        Ports.playerWarpTasks.remove(player.getUniqueId())
                
                        // remove cost from player
                        val playerInventory = player.getInventory()
                        if ( playerInventory.contains(costMaterial, costAmount) ) {
                            playerInventory.removeItem(ItemStack(costMaterial, costAmount))
                        } else {
                            Message.announcement(player, "${ChatColor.RED}You do not have enough to pay warp fee (${costAmount} ${costMaterial})...")
                            return
                        }

                        // do warp
                        Ports.warp(
                            destination,
                            playersToWarp,
                            entitiesToWarp,
                        )

                        Message.announcement(player, "${ChatColor.GREEN}Warped to ${destination.name}")
                    }
                })
            }
            else {
                val progress = 1.0 - (time / timeWarp)
                Message.announcement(player, "Warping ${ChatColor.GREEN}${progressBar(progress)}")
            }
        }
    }

    /**
     * Actually runs warp on player/vehicle
     */
    public fun warp(
        destination: Port,
        playersToWarp: List<Player>,
        entitiesToWarp: List<Entity>,
        // vehiclesToWarp: List<Vehicle> // TODO: custom vehicles
    ) {
        // calculate destination location
        val portX = destination.locX
        val portZ = destination.locZ

        // calculate random offset from port center pillar
        val rand = ThreadLocalRandom.current()
        val xoffset0 = rand.nextDouble() * Ports.maxDistanceFromPortToWarp
        val zoffset0 = rand.nextDouble() * Ports.maxDistanceFromPortToWarp

        // require |x|, |z| > 1.5 so don't get stuck in pillar
        val xoff = if ( xoffset0 > 0.0 && xoffset0 < 1.5 ) {
            1.5
        } else if ( xoffset0 < 0.0 && xoffset0 > -1.5 ) {
            -1.5
        } else {
            xoffset0
        }

        val zoff = if ( zoffset0 > 0.0 && zoffset0 < 1.5 ) {
            1.5
        } else if ( zoffset0 < 0.0 && zoffset0 > -1.5 ) {
            -1.5
        } else {
            zoffset0
        }

        val x = portX + xoff
        val z = portZ + zoff

        for ( player in playersToWarp ) {
            val loc = Location(
                Ports.defaultWorld,
                x,
                Ports.seaLevel,
                z
            )

            player.teleport(loc)
        }

        for ( entity in entitiesToWarp ) {
            val loc = Location(
                Ports.defaultWorld,
                x,
                entity.location.y,
                z
            )

            Ports.teleportEntity(entity, loc)
        }

        // for ( vehicle in vehiclesToWarp ) {
        //     val loc = Location(
        //         Ports.defaultWorld,
        //         x,
        //         vehicle.location.y,
        //         z
        //     )

        //     val chunk = loc.getChunk()
        //     chunk.load()

        //     vehicle.teleport(loc)
        // }
    }

    /**
     * Handle warping entity which may have passenger
     */
    public fun teleportEntity(entity: Entity, destination: Location) {
        val passengers = entity.getPassengers()

        // remove players from boats and teleport to destination
        for ( p in passengers ) {
            p.eject()
            entity.removePassenger(p)
            p.teleport(destination)
        }

        // schedule entity teleport (after players already teleported)
        Bukkit.getScheduler().runTaskLater(Ports.plugin!!, object: Runnable {
            override public fun run() {
                entity.teleport(destination)

                // re-send entity to players to make sure it exists on client
                for ( p in passengers ) {
                    try {
                        val nmsPlayer = (p as CraftPlayer).getHandle()
                        val nmsEntity = (entity as CraftEntity).getHandle()
                        nmsPlayer.connection.send(ClientboundAddEntityPacket(nmsEntity))
                    } catch ( err: Exception ) {
                        if ( Ports.debug ) {
                            err.printStackTrace()
                        }
                    }
                }
            }
        }, 1L)

        // schedule re-attaching player to boat
        Bukkit.getScheduler().runTaskLater(Ports.plugin!!, object: Runnable {
            override public fun run() {
                for ( p in passengers ) {
                    entity.addPassenger(p)
                }
            }
        }, 2L)
    }
}
