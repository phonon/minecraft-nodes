/**
 * Nation
 * -----------------------------
 * 
 */

package phonon.nodes.objects

import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import phonon.nodes.Message
import phonon.nodes.utils.Color
import phonon.nodes.utils.string.stringArrayFromSet
import phonon.nodes.serdes.JsonSaveState
import java.util.*

// random number generator
private val random = Random()

class Nation(
    val uuid: UUID,
    var name: String,
    var capital: Town    // main town in nation, used for nation leadership
) {

    // must be Set to satisfy bukkit interface in Chat.kt
    val playersOnline: MutableSet<Player> = mutableSetOf()

    val towns: HashSet<Town> = hashSetOf()
    val residents: HashSet<Resident> = hashSetOf()

    // nation's diplomatic relations: allies, enemies, truce
    // determine who nation can attack during war
    val allies: HashSet<Nation> = hashSetOf()
    val enemies: HashSet<Nation> = hashSetOf()

    // color for displaying on map
    var color: Color

    // json string and memoization flag
    private var saveState: NationSaveState
    private var _needsUpdate = false

    init {
        // assign random color
        this.color = Color(
            random.nextInt(256),
            random.nextInt(256),
            random.nextInt(256)
        )

        this.saveState = NationSaveState(this)
    }

    override fun hashCode(): Int {
        return this.uuid.hashCode()
    }

    // prints out nation object info
    fun printInfo(sender: CommandSender) {
        val leader = this.capital.leader?.name ?: "${ChatColor.GRAY}None"

        // read info out of towns:
        // - get town names
        // - get total residents count
        var residents = 0
        val towns = if ( this.towns.size > 0 ) {
            val townNames: ArrayList<String> = arrayListOf()
            for ( t in this.towns ) {
                townNames.add(t.name)
                residents += t.residents.size
            }
            townNames.joinToString(", ")
        } else {
            "${ChatColor.GRAY}None"
        }

        val allies = if ( this.allies.size > 0 ) {
            this.allies.map {v -> v.name}.joinToString(", ")
        } else {
            "${ChatColor.GRAY}None"
        }

        val enemies = if ( this.enemies.size > 0 ) {
            this.enemies.map {v -> v.name}.joinToString(", ")
        } else {
            "${ChatColor.GRAY}None"
        }

        Message.print(sender, "${ChatColor.BOLD}Nation ${this.name}:")
        Message.print(sender, "- Capital${ChatColor.WHITE}: ${this.capital.name}")
        Message.print(sender, "- Leader${ChatColor.WHITE}: ${leader}")
        Message.print(sender, "- Towns[${this.towns.size}]${ChatColor.WHITE}: ${towns}")
        Message.print(sender, "- Residents${ChatColor.WHITE}: ${residents}")
        Message.print(sender, "- Allies${ChatColor.WHITE}: ${allies}")
        Message.print(sender, "- Enemies${ChatColor.WHITE}: ${enemies}")
    }

    /**
     * Immutable save snapshot, must be composed of immutable primitives.
     * Used to generate json string serialization.
     */
    class NationSaveState(n: Nation): JsonSaveState {
        val uuid = n.uuid
        val capital = n.capital.name
        val color = n.color
        val towns = n.towns.map{ x -> x.name }
        val allies = n.allies.map{ x -> x.name }
        val enemies = n.enemies.map{ x -> x.name }

        override var jsonString: String? = null

        override fun createJsonString(): String {
            val capitalName = "\"${capital}\""
            val towns = this.towns.asSequence().map{ x -> "\"${x}\""}.joinToString(",", "[", "]")
            val allies = this.allies.asSequence().map{ x -> "\"${x}\""}.joinToString(",", "[", "]")
            val enemies = this.enemies.asSequence().map{ x -> "\"${x}\""}.joinToString(",", "[", "]")

            val jsonString = ("{"
            + "\"uuid\":\"${this.uuid}\","
            + "\"capital\":${capitalName},"
            + "\"color\":[${this.color.r},${this.color.g},${this.color.b}],"
            + "\"towns\":${towns},"
            + "\"allies\":${allies},"
            + "\"enemies\":${enemies}"
            + "}")

            return jsonString
        }
    }

    // function to let client flag this object as dirty
    fun needsUpdate() {
        this._needsUpdate = true
    }

    // wrapper to return self as savestate
    // - returns memoized copy if needsUpdate false
    // - otherwise, parses self
    fun getSaveState(): NationSaveState {
        if ( this._needsUpdate ) {
            this.saveState = NationSaveState(this)
            this._needsUpdate = false
        }
        return this.saveState
    }
}