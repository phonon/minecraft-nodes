/**
 * Territory
 * 
 * Data container around group of chunks,
 * contains settings for ore drop rates, crop growth, animal breeding, ...
 */

package phonon.nodes.objects

import java.util.logging.Logger
import java.util.EnumMap
import kotlin.math.max
import com.google.gson.JsonObject
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.EntityType
import phonon.nodes.Message


/**
 * Wrapper type for territory id int.
 */
@JvmInline
value class TerritoryId(private val id: Int) {
    override fun toString(): String = id.toString()
    fun toInt(): Int = id
}

/**
 * Wrapper type for list of territory ids as IntArray backing.
 * Used to describe territory neighbor id sets.
 * However, does not 
 */
@JvmInline
value class TerritoryIdArray(private val ids: IntArray) {
    override fun toString(): String = ids.toString()
    fun toIntArray(): IntArray = ids.copyOf()

    /**
     * Return true if this territory id array contains given territory id.
     */
    fun contains(id: TerritoryId): Boolean {
        for ( i in ids ) {
            if ( id == TerritoryId(i) ) {
                return true
            }
        }
        return false
    }

    /**
     * Return iterator over territory ids.
     */
    operator fun iterator(): TerritoryIdIterator {
        return TerritoryIdIterator(ids.iterator())
    }

    /**
     * Wrapper iterator to emit TerritoryId instead of int.
     */
    public class TerritoryIdIterator(val intIter: IntIterator): Iterator<TerritoryId> {
        override fun hasNext(): Boolean = intIter.hasNext()
        override fun next(): TerritoryId = TerritoryId(intIter.nextInt())
    }
}


/**
 * Intermediary struct for building a territory, contains the fixed
 * structural properties of a territory in the world:
 * name, chunks, neighbors, etc.
 * These are all non-resource properties. These are loaded from the
 * json, then combined with a TerritoryResources object (calculated 
 * from resource node names attached to the territory) to build the
 * final "compiled" Territory.
 */
data class TerritoryPreprocessing(
    val id: TerritoryId,
    val name: String,
    val color: Int,
    val core: Coord,
    val chunks: List<Coord>,
    val bordersWilderness: Boolean,
    val neighbors: TerritoryIdArray,
    val resourceNodes: List<String>,
) {
    companion object {
        /**
         * Load list of territory structures from world json object.
         * If `ids` list is specified, only load those ids (if they exist).
         * Otherwise, load all ids.
         */
        public fun loadFromJson(json: JsonObject, ids: List<TerritoryId>? = null): List<TerritoryPreprocessing> {
            val idStrings = if ( ids != null ) {
                ids.asSequence().map{ id -> id.toInt().toString() }
            } else {
                json.keySet().asSequence()
            }

            val territories = idStrings
                .map { id -> TerritoryPreprocessing.fromJson(id.toInt(), json[id].getAsJsonObject()) }
                .toList()
            
            return territories
        }

        /**
         * Load a single territory structure from world json object.
         * Note this will cause error if json is invalid.
         */
        public fun fromJson(id: Int, json: JsonObject): TerritoryPreprocessing {
            // territory name
            val name: String = json.get("name")?.getAsString() ?: "";

            // territory color, 6 possible colors -> integer in range [0, 5]
            // if null (editor error?), assign 5, the least likely color
            val color: Int = json.get("color")?.getAsInt() ?: 5;

            // core chunk (required)
            val coreChunkArray = json.get("coreChunk")!!.getAsJsonArray()!!
            val coreChunk = Coord(coreChunkArray[0].getAsInt(), coreChunkArray[1].getAsInt())

            // chunks:
            // parse interleaved coordinate buffer
            // [x1, y1, x2, y2, ... , xN, yN]
            val chunks: MutableList<Coord> = mutableListOf()
            val jsonChunkArray = json.get("chunks")?.getAsJsonArray()
            if ( jsonChunkArray !== null ) {
                for ( i in 0 until jsonChunkArray.size() step 2 ) {
                    val c = Coord(jsonChunkArray[i].getAsInt(), jsonChunkArray[i+1].getAsInt())
                    chunks.add(c)
                }
            }

            // resource nodes
            val resourceNodes: MutableList<String> = mutableListOf()
            val jsonNodesArray = json.get("nodes")?.getAsJsonArray()
            if ( jsonNodesArray !== null ) {
                jsonNodesArray.forEach { nodeJson ->
                    val s = nodeJson.getAsString()
                    resourceNodes.add(s)
                }
            }
            
            // neighbor territory ids
            val neighbors: MutableList<Int> = mutableListOf()
            val jsonNeighborsArray = json.get("neighbors")?.getAsJsonArray()
            if ( jsonNeighborsArray !== null ) {
                jsonNeighborsArray.forEach { neighborId ->
                    neighbors.add(neighborId.getAsInt())
                }
            }

            // flag that territory borders wilderness (regions without any territories)
            val bordersWilderness: Boolean = json.get("isEdge")?.getAsBoolean() ?: false

            return TerritoryPreprocessing(
                TerritoryId(id),
                name,
                color,
                coreChunk,
                chunks as List<Coord>,
                bordersWilderness,
                TerritoryIdArray(neighbors.toIntArray()),
                resourceNodes as List<String>
            )
        }
    }
}

/**
 * Intermediary struct for building a territory. This is passed through
 * resource loaders to merge data from all territory resource attributes
 * before compiling into a final immutable Territory.
 */
data class TerritoryResources(
    // core properties
    val income: EnumMap<Material, Double> = EnumMap<Material, Double>(Material::class.java),
    val incomeSpawnEgg: EnumMap<EntityType, Double> = EnumMap<EntityType, Double>(EntityType::class.java),
    val ores: EnumMap<Material, OreDeposit> = EnumMap<Material, OreDeposit>(Material::class.java),
    val crops: EnumMap<Material, Double> = EnumMap<Material, Double>(Material::class.java),
    val animals: EnumMap<EntityType, Double> = EnumMap<EntityType, Double>(EntityType::class.java),
    val customProperties: HashMap<String, Any> = HashMap(0),
    // claim time modifier
    val attackerTimeMultiplier: Double = 1.0,
    val defenderTimeMultiplier: Double = 1.0,
    // accumulated neighbor multipliers applied onto THIS TerritoryResources
    val accumulatedNeighborTotalIncomeMultiplier: Double = 1.0,
    val accumulatedNeighborTotalOresMultiplier: Double = 1.0,
    val accumulatedNeighborTotalCropsMultiplier: Double = 1.0,
    val accumulatedNeighborTotalAnimalsMultiplier: Double = 1.0,
    val accumulatedNeighborIncomeMultiplier: EnumMap<Material, Double> = EnumMap<Material, Double>(Material::class.java),
    val accumulatedNeighborIncomeSpawnEggMultiplier: EnumMap<EntityType, Double> = EnumMap<EntityType, Double>(EntityType::class.java),
    val accumulatedNeighborOresMultiplier: EnumMap<Material, Double> = EnumMap<Material, Double>(Material::class.java),
    val accumulatedNeighborCropsMultiplier: EnumMap<Material, Double> = EnumMap<Material, Double>(Material::class.java),
    val accumulatedNeighborAnimalsMultiplier: EnumMap<EntityType, Double> = EnumMap<EntityType, Double>(EntityType::class.java),
    // modifiers for neighbors of this territory
    val neighborIncome: EnumMap<Material, Double>? = null,
    val neighborIncomeSpawnEgg: EnumMap<EntityType, Double>? = null,
    val neighborOres: EnumMap<Material, OreDeposit>? = null,
    val neighborCrops: EnumMap<Material, Double>? = null,
    val neighborAnimals: EnumMap<EntityType, Double>? = null,
    val neighborTotalIncomeMultiplier: Double? = null,
    val neighborTotalOresMultiplier: Double? = null,
    val neighborTotalCropsMultiplier: Double? = null,
    val neighborTotalAnimalsMultiplier: Double? = null,
    val neighborIncomeMultiplier: EnumMap<Material, Double>? = null,
    val neighborIncomeSpawnEggMultiplier: EnumMap<EntityType, Double>? = null,
    val neighborOresMultiplier: EnumMap<Material, Double>? = null,
    val neighborCropsMultiplier: EnumMap<Material, Double>? = null,
    val neighborAnimalsMultiplier: EnumMap<EntityType, Double>? = null,
) {
    // flag that this resource contains a non-null neighbor modifier.
    // this is used to avoid unnecessarily running `applyNeighborModifiers`
    // on a TerritoryResources for all its neighbors, since most neighbors
    // will not contain any neighbor modifiers.
    val hasNeighborModifier: Boolean = (
        neighborIncome != null ||
        neighborIncomeSpawnEgg != null ||
        neighborOres != null ||
        neighborCrops != null ||
        neighborAnimals != null ||
        neighborTotalIncomeMultiplier != null ||
        neighborTotalOresMultiplier != null ||
        neighborTotalCropsMultiplier != null ||
        neighborTotalAnimalsMultiplier != null ||
        neighborIncomeMultiplier != null ||
        neighborIncomeSpawnEggMultiplier != null ||
        neighborOresMultiplier != null ||
        neighborCropsMultiplier != null ||
        neighborAnimalsMultiplier != null
    )

    /**
     * Returns a new TerritoryResources with neighbor
     * TerritoryResources object's neighbor modifiers applied
     * onto this object's resource properties. 
     */
    public fun accumulateNeighborModifiers(neighbor: TerritoryResources): TerritoryResources {
        val newIncome = this.income.clone()
        val newIncomeSpawnEgg = this.incomeSpawnEgg.clone()
        val newOres = this.ores.clone()
        val newCrops = this.crops.clone()
        val newAnimals = this.animals.clone()

        // neighbor total neighbor multipliers applied
        // for now: CLAMP TO MAX AMONG NEIGHBORS
        var maxAccumulatedNeighborTotalIncomeMultiplier = this.accumulatedNeighborTotalIncomeMultiplier
        var maxAccumulatedNeighborTotalOresMultiplier = this.accumulatedNeighborTotalOresMultiplier
        var maxAccumulatedNeighborTotalCropsMultiplier = this.accumulatedNeighborTotalCropsMultiplier
        var maxAccumulatedNeighborTotalAnimalsMultiplier = this.accumulatedNeighborTotalAnimalsMultiplier
        var maxAccumulatedNeighborIncomeMultiplier = this.accumulatedNeighborIncomeMultiplier.clone()
        var maxAccumulatedNeighborIncomeSpawnEggMultiplier = this.accumulatedNeighborIncomeSpawnEggMultiplier.clone()
        var maxAccumulatedNeighborOresMultiplier = this.accumulatedNeighborOresMultiplier.clone()
        var maxAccumulatedNeighborCropsMultiplier = this.accumulatedNeighborCropsMultiplier.clone()
        var maxAccumulatedNeighborAnimalsMultiplier = this.accumulatedNeighborAnimalsMultiplier.clone()
        
        // income direct addition
        neighbor.neighborIncome?.forEach { (type, amount) ->
            newIncome[type] = newIncome.getOrDefault(type, 0.0) + amount
        }
        neighbor.neighborIncomeSpawnEgg?.forEach { (type, amount) ->
            newIncomeSpawnEgg[type] = newIncomeSpawnEgg.getOrDefault(type, 0.0) + amount
        }
        // income multiplier
        neighbor.neighborTotalIncomeMultiplier?.let { multiplier ->
            maxAccumulatedNeighborTotalIncomeMultiplier = max(multiplier, maxAccumulatedNeighborTotalIncomeMultiplier)
        }
        neighbor.neighborIncomeMultiplier?.let { multipliers ->
            multipliers.forEach { (type, multiplier) ->
                maxAccumulatedNeighborIncomeMultiplier[type] = max(multiplier, maxAccumulatedNeighborIncomeMultiplier[type] ?: 1.0)
            }
        }
        neighbor.neighborIncomeSpawnEggMultiplier?.let { multipliers ->
            multipliers.forEach { (type, multiplier) ->
                maxAccumulatedNeighborIncomeSpawnEggMultiplier[type] = max(multiplier, maxAccumulatedNeighborIncomeSpawnEggMultiplier[type] ?: 1.0)
            }
        }

        // ore direct addition
        neighbor.neighborOres?.forEach { (type, ore) ->
            if ( newOres.containsKey(type) ) {
                val oreDeposit = newOres[type]!!
                newOres[type] = oreDeposit.copy(dropChance = oreDeposit.dropChance + ore.dropChance)
            } else {
                newOres[type] = ore.copy()
            }
        }
        // ores multiplier
        neighbor.neighborTotalOresMultiplier?.let { multiplier ->
            maxAccumulatedNeighborTotalOresMultiplier = max(multiplier, maxAccumulatedNeighborTotalOresMultiplier)
        }
        neighbor.neighborOresMultiplier?.let { multipliers ->
            multipliers.forEach { (type, multiplier) ->
                maxAccumulatedNeighborOresMultiplier[type] = max(multiplier, maxAccumulatedNeighborOresMultiplier[type] ?: 1.0)
            }
        }

        // crops direct addition
        neighbor.neighborCrops?.forEach { (type, amount) ->
            newCrops[type] = newCrops.getOrDefault(type, 0.0) + amount
        }
        // crops multiplier
        neighbor.neighborTotalCropsMultiplier?.let { multiplier ->
            maxAccumulatedNeighborTotalCropsMultiplier = max(multiplier, maxAccumulatedNeighborTotalCropsMultiplier)
        }
        neighbor.neighborCropsMultiplier?.let { multipliers ->
            multipliers.forEach { (type, multiplier) ->
                maxAccumulatedNeighborCropsMultiplier[type] = max(multiplier, maxAccumulatedNeighborCropsMultiplier[type] ?: 1.0)
            }
        }

        // animals direct addition
        neighbor.neighborAnimals?.forEach { (type, amount) ->
            newAnimals[type] = newAnimals.getOrDefault(type, 0.0) + amount
        }
        // animals multiplier
        neighbor.neighborTotalAnimalsMultiplier?.let { multiplier ->
            maxAccumulatedNeighborTotalAnimalsMultiplier = max(multiplier, maxAccumulatedNeighborTotalAnimalsMultiplier)
        }
        neighbor.neighborAnimalsMultiplier?.let { multipliers ->
            multipliers.forEach { (type, multiplier) ->
                maxAccumulatedNeighborAnimalsMultiplier[type] = max(multiplier, maxAccumulatedNeighborAnimalsMultiplier[type] ?: 1.0)
            }
        }

        return this.copy(
            income = newIncome,
            incomeSpawnEgg = newIncomeSpawnEgg,
            ores = newOres,
            crops = newCrops,
            animals = newAnimals,
            accumulatedNeighborTotalIncomeMultiplier = maxAccumulatedNeighborTotalIncomeMultiplier,
            accumulatedNeighborTotalOresMultiplier = maxAccumulatedNeighborTotalOresMultiplier,
            accumulatedNeighborTotalCropsMultiplier = maxAccumulatedNeighborTotalCropsMultiplier,
            accumulatedNeighborTotalAnimalsMultiplier = maxAccumulatedNeighborTotalAnimalsMultiplier,
            accumulatedNeighborIncomeMultiplier = maxAccumulatedNeighborIncomeMultiplier,
            accumulatedNeighborIncomeSpawnEggMultiplier = maxAccumulatedNeighborIncomeSpawnEggMultiplier,
            accumulatedNeighborOresMultiplier = maxAccumulatedNeighborOresMultiplier,
            accumulatedNeighborCropsMultiplier = maxAccumulatedNeighborCropsMultiplier,
            accumulatedNeighborAnimalsMultiplier = maxAccumulatedNeighborAnimalsMultiplier,
        )
    }

    /**
     * Applies all accumulated neighbor modifiers onto this territory.
     */
    public fun applyNeighborModifiers(): TerritoryResources {
        val newIncome = this.income.clone()
        val newIncomeSpawnEgg = this.incomeSpawnEgg.clone()
        val newOres = this.ores.clone()
        val newCrops = this.crops.clone()
        val newAnimals = this.animals.clone()
        
        // income multiplier
        newIncome.forEach { (type, value) ->
            newIncome[type] = value * accumulatedNeighborTotalIncomeMultiplier
        }
        newIncomeSpawnEgg.forEach { (type, value) ->
            newIncomeSpawnEgg[type] = value * accumulatedNeighborTotalIncomeMultiplier
        }
        accumulatedNeighborIncomeMultiplier.forEach { (type, multiplier) ->
            if ( newIncome.containsKey(type) ) {
                newIncome[type] = newIncome[type]!! * multiplier
            }
        }
        accumulatedNeighborIncomeSpawnEggMultiplier.forEach { (type, multiplier) ->
            if ( newIncomeSpawnEgg.containsKey(type) ) {
                newIncomeSpawnEgg[type] = newIncomeSpawnEgg[type]!! * multiplier
            }
        }

        // ores multiplier
        newOres.forEach { (type, oreDeposit) ->
            newOres[type] = oreDeposit.copy(dropChance = oreDeposit.dropChance * accumulatedNeighborTotalOresMultiplier)
        }
        accumulatedNeighborOresMultiplier.forEach { (type, multiplier) ->
            if ( newOres.containsKey(type) ) {
                val oreDeposit = newOres[type]!!
                newOres[type] = oreDeposit.copy(dropChance = oreDeposit.dropChance * multiplier)
            }
        }

        // crops multiplier
        newCrops.forEach { (type, value) ->
            newCrops[type] = value * accumulatedNeighborTotalCropsMultiplier
        }
        accumulatedNeighborCropsMultiplier.forEach { (type, multiplier) ->
            if ( newCrops.containsKey(type) ) {
                newCrops[type] = newCrops[type]!! * multiplier
            }
        }

        // animals multiplier
        newAnimals.forEach { (type, value) ->
            newAnimals[type] = value * accumulatedNeighborTotalAnimalsMultiplier
        }
        accumulatedNeighborAnimalsMultiplier.forEach { (type, multiplier) ->
            if ( newAnimals.containsKey(type) ) {
                newAnimals[type] = newAnimals[type]!! * multiplier
            }
        }

        return this.copy(
            income = newIncome,
            incomeSpawnEgg = newIncomeSpawnEgg,
            ores = newOres,
            crops = newCrops,
            animals = newAnimals,
        )
    }
}

/**
 * Final territory object. Territory fixed properties and resource 
 * properties should not change after this is built.
 */
data class Territory(
    val id: TerritoryId,
    val name: String,
    val color: Int,
    val core: Coord,
    val chunks: List<Coord>,
    val bordersWilderness: Boolean,  // if territory is next to wilderness (region without any territories)
    val neighbors: TerritoryIdArray, // neighboring territories (touching chunks/shares border)
    val resourceNodes: List<String>,
    // resource properties, should be derived from a TerritoryResources object
    val cost: Int,
    val income: EnumMap<Material, Double>,
    val incomeSpawnEgg: EnumMap<EntityType, Double>,
    val ores: OreSampler,
    val crops: EnumMap<Material, Double>,
    val animals: EnumMap<EntityType, Double>,
    val customProperties: HashMap<String, Any> = HashMap(0),
    // claim time modifier
    val attackerTimeMultiplier: Double,
    val defenderTimeMultiplier: Double,
    // mutable properties: TODO find way to get rid?
    var town: Town? = null, // town owner
    var occupier: Town? = null, // town occupier (after being captured in war)
) {
    val containsIncome: Boolean
    val containsOre: Boolean
    val cropsCanGrow: Boolean
    val animalsCanBreed: Boolean

    init {
        this.containsIncome = income.size > 0
        this.containsOre = ores.containsOre
        this.cropsCanGrow = crops.size > 0
        this.animalsCanBreed = animals.size > 0
    }
    
    // id is forced to be unique by system
    override public fun hashCode(): Int {
        return this.id.toInt()
    }

    // Returns territory structural properties (chunks, id, neighbors, etc.)
    // as a TerritoryPreprocessing object. Used when rebuilding territories
    // in territory hot reloading.
    public fun toPreprocessing(): TerritoryPreprocessing {
        return TerritoryPreprocessing(
            id = this.id,
            name = this.name,
            color = this.color,
            core = this.core,
            chunks = this.chunks,
            bordersWilderness = this.bordersWilderness,
            neighbors = this.neighbors,
            resourceNodes = this.resourceNodes,
        )
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