/**
 * Instance for attacking a chunk
 * - holds state data of attack
 * - functions as runnable thread for attack tick
 */

package phonon.nodes.war

import java.util.UUID
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.scheduler.BukkitTask
import org.bukkit.block.Block
import org.bukkit.boss.*
import org.bukkit.entity.ArmorStand
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType
import phonon.nodes.Nodes
import phonon.nodes.Config
import phonon.nodes.objects.Coord
import phonon.nodes.objects.TerritoryChunk
import phonon.nodes.objects.Town
import phonon.nodes.objects.townNametagViewedByPlayer // in nametag
import phonon.nodes.nms.createArmorStandNamePacket
import phonon.nodes.nms.sendPacket


public class Attack(
    val attacker: UUID,        // attacker's UUID
    val town: Town,            // attacker's town
    val coord: Coord,          // chunk coord under attack
    val flagBase: Block,       // fence base of flag
    val flagBlock: Block,      // wool block for flag
    val flagTorch: Block,      // torch block of flag
    val skyBeaconColorBlocks: List<Block>,
    val skyBeaconWireframeBlocks: List<Block>,
    val progressBar: BossBar,  // progress bar
    val attackTime: Long,      // 
    var progress: Long         // initial progress, current tick count
): Runnable {
    // no build region
    val noBuildXMin: Int
    val noBuildXMax: Int
    val noBuildZMin: Int
    val noBuildZMax: Int
    val noBuildYMin: Int
    val noBuildYMax: Int = 255 // temporarily set to height

    var progressColor: Int // index in progress color array
    var thread: BukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(Nodes.plugin!!, this, FlagWar.ATTACK_TICK, FlagWar.ATTACK_TICK)

    // armor stand used to label town name on flag
    val armorstandTownLabel = AttackArmorStandLabel(town, flagBase.world, flagBase.location.clone().add(0.5, 1.75, 0.5))

    // re-used json serialization StringBuilders
    val jsonStringBase: StringBuilder
    val jsonString: StringBuilder

    init {
        val flagX = flagBase.x
        val flagY = flagBase.y
        val flagZ = flagBase.z

        // set no build ranges
        this.noBuildXMin = flagX - Config.flagNoBuildDistance
        this.noBuildXMax = flagX + Config.flagNoBuildDistance
        this.noBuildZMin = flagZ - Config.flagNoBuildDistance
        this.noBuildZMax = flagZ + Config.flagNoBuildDistance
        this.noBuildYMin = flagY + Config.flagNoBuildYOffset
        
        // set boss bar progress
        val progressNormalized: Double = this.progress.toDouble() / this.attackTime.toDouble()
        this.progressBar.setProgress(progressNormalized)
        this.progressColor = FlagWar.getProgressColor(progressNormalized)

        // pre-generate main part of the JSON serialization string
        this.jsonStringBase = generateFixedJsonBase(
            this.attacker,
            this.coord,
            this.flagBase,
        )

        // send name packets to players in range
        try {
            this.armorstandTownLabel.sendLabel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // full json StringBuilder, initialize capacity to be
        // base capacity + room for progress ticks length
        val jsonStringBufferSize = this.jsonStringBase.capacity() + 20
        this.jsonString = StringBuilder(jsonStringBufferSize)
    }

    override public fun run() {
        FlagWar.attackTick(this)
    }
    
    public fun cancel() {
        this.thread.cancel()
        
        val attack = this
        Bukkit.getScheduler().runTask(Nodes.plugin!!, object: Runnable {
            override fun run() {
                FlagWar.cancelAttack(attack)
            }
        })
    }

    // returns json format string as a StringBuilder
    // only used with WarSerializer objects
    public fun toJson(): StringBuilder {
        // reset json StringBuilder
        this.jsonString.setLength(0)

        // add base
        this.jsonString.append(this.jsonStringBase)

        // add progress in ticks
        this.jsonString.append("\"p\":${this.progress.toString()}") 
        this.jsonString.append("}")

        return this.jsonString
    }
}

// pre-generate main part of the JSON serialization string
// for the attack which does not change
// (only part that changes is progress)
// parts required for serialization:
// - attacker: player uuid
// - coord: chunk coord
// - block: flag base block (fence)
// - skyBeaconColorBlocks: track blocks in sky beacon
// - skyBeaconWireframeBlocks: track blocks in sky beacon
private fun generateFixedJsonBase(
    attacker: UUID,
    coord: Coord,
    block: Block
): StringBuilder {
    val s = StringBuilder()

    s.append("{")

    // attacker uuid
    s.append("\"id\":\"${attacker.toString()}\",")

    // chunk coord [c.x, c.z]
    s.append("\"c\":[${coord.x},${coord.z}],")

    // flag base block [b.x, b.y, b.z]
    s.append("\"b\":[${block.x},${block.y},${block.z}],")

    return s
}

public class AttackArmorStandLabel(
    val town: Town,
    val world: World,
    val loc: Location,
    val maxViewDistance: Int = 3,
) {
    var armorstand = createArmorStandLabel(world, loc)

    // min/max x/z chunk view distance from this label
    val minViewChunkX: Int
    val maxViewChunkX: Int
    val minViewChunkZ: Int
    val maxViewChunkZ: Int

    init {
        // calculate max chunk view distance from this label
        val chunk = this.loc.chunk
        val chunkX = chunk.x
        val chunkZ = chunk.z

        minViewChunkX = chunkX - this.maxViewDistance
        maxViewChunkX = chunkX + this.maxViewDistance
        minViewChunkZ = chunkZ - this.maxViewDistance
        maxViewChunkZ = chunkZ + this.maxViewDistance
    }

    /**
     * Remove armorstand, for cleanup.
     */
    public fun remove() {
        this.armorstand.remove()
    }

    /**
     * Check if armorstand is still valid.
     */
    public fun isValid(): Boolean {
        return this.armorstand.isValid
    }

    /**
     * Re-create new armorstand.
     */
    public fun respawn() {
        this.armorstand.remove()
        this.armorstand = createArmorStandLabel(this.world, this.loc)
    }

    /**
     * Send player-specific view label packets for players
     * within maxViewDistance chunks of this armorstand.
     */
    public fun sendLabel() {
        // if this chunk not loaded, skip
        if ( !this.world.isChunkLoaded(this.loc.chunk) ) {
            return
        }

        for ( player in world.players ) {
            val playerChunk = player.location.chunk
            val playerChunkX = playerChunk.x
            val playerChunkZ = playerChunk.z

            if ( playerChunkX < minViewChunkX || playerChunkX > maxViewChunkX ||
                 playerChunkZ < minViewChunkZ || playerChunkZ > maxViewChunkZ ) {
                continue
            }

            // send view label packet
            val label = townNametagViewedByPlayer(town, player)
            val packet = this.armorstand.createArmorStandNamePacket(label)
            player.sendPacket(packet)
        }
    }
}

/**
 * Namespaced key for marking armorstands as nodes plugin armorstand labels.
 */
internal val NODES_ARMORSTAND_KEY = NamespacedKey("nodes", "armorstand_label")

/**
 * Helper function to create a new armorstand label with associated label
 * metadata.
 */
private fun createArmorStandLabel(
    world: World,    
    loc: Location,
): ArmorStand {
    val armorstand = world.spawn(loc, ArmorStand::class.java)
    armorstand.setSmall(true)
    armorstand.setGravity(false)
    armorstand.persistentDataContainer.set(NODES_ARMORSTAND_KEY, PersistentDataType.INTEGER, 0)
    return armorstand
}