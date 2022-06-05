/**
 * Instance for attacking a chunk
 * - holds state data of attack
 * - functions as runnable thread for attack tick
 */

package phonon.nodes.war

import java.util.UUID
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask
import org.bukkit.block.Block
import org.bukkit.boss.*
import phonon.nodes.Nodes
import phonon.nodes.Config
import phonon.nodes.objects.Coord
import phonon.nodes.objects.TerritoryChunk
import phonon.nodes.objects.Town

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
            this.flagBase
        )
        
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