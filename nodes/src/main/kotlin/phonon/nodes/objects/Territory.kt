/**
 * Territory
 * 
 * Data container around group of chunks,
 * contains settings for ore drop rates, crop growth, animal breeding, ...
 */

package phonon.nodes.objects

import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.EntityType
import phonon.nodes.Message
import java.util.*

data class Territory(
    val id: Int,
    val name: String,
    val color: Int,
    val core: Coord,
    val chunks: ArrayList<Coord>,
    val bordersWilderness: Boolean, //  if territory is next to wilderness (region without any territories)
    val resourceNodes: ArrayList<String>,
    var income: EnumMap<Material, Double>,
    var incomeSpawnEgg: EnumMap<EntityType, Double>,
    val ores: OreSampler,
    var crops: EnumMap<Material, Double>,
    var animals: EnumMap<EntityType, Double>,
    val cost: Int
) {
    // neighboring territories (touching chunks)
    val neighbors: HashSet<Territory> = hashSetOf()
    
    var containsIncome: Boolean
    var containsOre: Boolean
    var cropsCanGrow: Boolean
    var animalsCanBreed: Boolean

    // town owner
    var town: Town? = null
    
    // town occupier (after being captured in war)
    var occupier: Town? = null

    init {
        this.containsIncome = income.size > 0
        this.containsOre = ores.containsOre
        this.cropsCanGrow = crops.size > 0
        this.animalsCanBreed = animals.size > 0
    }
    
    // id is forced to be unique by system
    override public fun hashCode(): Int {
        return this.id
    }

    // print territory info
    public fun printInfo(sender: CommandSender) {
        val town: String = this.town?.name ?: "${ChatColor.GRAY}None"
        val occupier: String = if ( this.occupier != null ) {
            "${ChatColor.RED}${this.occupier!!.name}"
        } else {
            "${ChatColor.GRAY}None"
        }
        val core = this.core

        Message.print(sender, "${ChatColor.BOLD}Territory (id = ${this.id}):")
        if ( this.name != "" ) {
            Message.print(sender, "- Name${ChatColor.WHITE}: ${name}")
        }
        
        Message.print(sender, "- Town${ChatColor.WHITE}: ${town}")
        Message.print(sender, "- Occupier${ChatColor.WHITE}: ${occupier}")
        Message.print(sender, "- Chunks${ChatColor.WHITE}: ${this.chunks.size}")
        Message.print(sender, "- Core chunk (x,z)${ChatColor.WHITE}: (${core.x}, ${core.z})")
        Message.print(sender, "- Cost${ChatColor.WHITE}: ${this.cost}")
        Message.print(sender, "- Resources:")
        for ( name in this.resourceNodes ) {
            Message.print(sender, "   - ${name}")
        }
    }

    // print territory net resources
    public fun printResources(sender: CommandSender) {

        // print income
        Message.print(sender, "- Income:")
        for ( (k, v) in this.income ) {
            Message.print(sender, "   - ${k}: ${v}")
        }
        for ( (k, v) in this.incomeSpawnEgg ) {
            Message.print(sender, "   - ${k}: ${v}")
        }

        // print ore deposits
        Message.print(sender, "- Ore:")
        for ( ore in this.ores.ores ) {
            Message.print(sender, "   - ${ore.material}: ${String.format("%.5f", ore.dropChance)}, ${ore.minAmount} - ${ore.maxAmount}")
        }

        // print crops
        Message.print(sender, "- Crops:")
        for ( (k, v) in this.crops ) {
            Message.print(sender, "   - ${k}: ${v}")
        }

        // print animals
        Message.print(sender, "- Animals:")
        for ( (k, v) in this.animals ) {
            Message.print(sender, "   - ${k}: ${v}")
        }
    }
}