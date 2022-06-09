/**
 * Resource Node
 * 
 * Composable resource modifier attached to territory:
 *    income: [(blockId, count), ...]
 *    ore: [(blockId, rate, count), ...]
 *    crops: [(name, rate), ...]
 *    animals: [(name, rate), ...]
 * Territory calculates net resources from array of 
 * nodes during initialization
 */

package phonon.nodes.objects

import java.util.EnumMap
import kotlin.math.min
import org.bukkit.Material
import org.bukkit.ChatColor
import org.bukkit.entity.EntityType
import org.bukkit.command.CommandSender
import com.google.gson.JsonObject
import phonon.nodes.Nodes
import phonon.nodes.Message

/**
 * Interface for resource attributes. These are modifiers
 * that are applied to territories to modify their resource
 * properties.
 */
interface ResourceAttribute {
    /**
     * Sort priority within a ResourceNode object's attributes.
     * Lower priority attributes are applied first.
     */
    public val priority: Int
    
    /**
     * Apply this attribute to a TerritoryResources to generate a
     * new TerritoryResources with modified properties.
     */
    public fun apply(resources: TerritoryResources): TerritoryResources

    /**
     * Return string description of this attribute
     */
    public fun describe(): String
}


/**
 * Interface for resource attribute loading system.
 */
interface ResourceAttributeLoader {
    /**
     * Load resource attribute definitions from a parsed JsonObject.
     * This takes the current resource nodes state and outputs a new
     * state with new attributes loaded from the JsonObject.
     * The load implementation determines which fields correspond to
     * different resource attributes.
     */
    public fun load(resources: HashMap<String, ResourceNode>, json: JsonObject): HashMap<String, ResourceNode>
}


/**
 * Resources are composed of a list of ResourceAttribute
 * objects. ResourceAttribute objects are not unique and can be
 * duplicated in a Resource.
 */
data class ResourceNode(
    val name: String,
    val icon: String?,
    val costConstant: Int,
    val costScale: Double,
    val priority: Int, // sort priority vs. other resource nodes, lower = applied first
    val attributes: List<ResourceAttribute>,
) {
    // keep internal sorted attributes list to protect against
    // client passing a non-sorted attributes list 
    val attributesSorted = attributes.sortedBy { it.priority }

    /**
     * Apply resource node attributes to a TerritoryResources.
     */
    public fun apply(terr: TerritoryResources): TerritoryResources {
        return this.attributesSorted.fold(terr, { t, attribute -> attribute.apply(t) })
    }

    /**
     * Print resource node attributes info to sender.
     */
    public fun printInfo(sender: CommandSender) {
        Message.print(sender, "${ChatColor.BOLD}Resource node \"${this.name}\":")
        for ( attribute in this.attributesSorted ) {
            Message.print(sender, "${attribute.describe()}")
        }
    }
}

// ============================================================================
// RESOURCE ATTRIBUTE IMPLEMENTATIONS
// ============================================================================

public data class ResourceAttributeIncome(
    private val income: EnumMap<Material, Double>,
    private val incomeSpawnEgg: EnumMap<EntityType, Double>,
): ResourceAttribute {
    override val priority: Int = 1
    val description: String

    init {
        val s = StringBuilder("Income:\n")
        for ( (item, value) in this.income.entries ) {
            s.append("- ${item}: ${value}\n")
        }
        for ( (item, value) in this.incomeSpawnEgg.entries ) {
            s.append("- SPAWN_EGG_${item}: ${value}\n")
        }

        description = s.toString()
    }
    /**
     * Add income values from this attribute into territory income.
     */
    override fun apply(resources: TerritoryResources): TerritoryResources {
        val newIncome = resources.income.clone()
        val newIncomeSpawnEgg = resources.incomeSpawnEgg.clone()

        this.income.forEach { k, v ->
            newIncome.get(k)?.let { currentVal -> 
                newIncome.put(k, currentVal + v)
            } ?: run {
                newIncome.put(k, v)
            }
        }

        this.incomeSpawnEgg.forEach { k, v ->
            newIncomeSpawnEgg.get(k)?.let { currentVal -> 
                newIncomeSpawnEgg.put(k, currentVal + v)
            } ?: run {
                newIncomeSpawnEgg.put(k, v)
            }
        }

        return resources.copy(
            income = newIncome,
            incomeSpawnEgg = newIncomeSpawnEgg,
        )
    }

    override fun describe(): String = this.description
}


public data class ResourceAttributeOre(
    private val ores: EnumMap<Material, OreDeposit>,
): ResourceAttribute {
    override val priority: Int = 2
    val description: String

    init {
        val s = StringBuilder("Ore:\n")
        for ( entry in this.ores.entries ) {
            val ore = entry.value
            s.append("- ${entry.key}: ${ore.dropChance} ${ore.minAmount}-${ore.maxAmount}\n")
        }

        description = s.toString()
    }

    /**
     * Add ore rate probability from this attribute into territory.
     */
    override fun apply(resources: TerritoryResources): TerritoryResources {
        // deep clone ores
        val newOres: EnumMap<Material, OreDeposit> = EnumMap<Material, OreDeposit>(Material::class.java)
        for ( (mat, ore) in resources.ores ) {
            newOres.put(mat, ore.clone())
        }
        
        // merge ore deposits
        this.ores.forEach { k, v ->
            newOres.get(k)?.let { oreDeposit -> 
                newOres.put(k, oreDeposit.merge(v))
            } ?: run {
                newOres.put(k, v)
            }
        }

        return resources.copy(
            ores = newOres,
        )
    }

    override fun describe(): String = this.description
}


public data class ResourceAttributeCrops(
    private val crops: EnumMap<Material, Double>,
): ResourceAttribute {
    override val priority: Int = 3
    val description: String

    init {
        val s = StringBuilder("Crops:\n")
        for ( entry in this.crops.entries ) {
            s.append("- ${entry.key}: ${entry.value}\n")
        }

        description = s.toString()
    }

    /**
     * Add crop growth rate from this attribute into territory.
     * Saturate rate at 1.0.
     */
    override fun apply(resources: TerritoryResources): TerritoryResources {
        val newCrops = resources.crops.clone()

        this.crops.forEach { k, v -> 
            newCrops.get(k)?.let { currentVal -> 
                newCrops.put(k, min(1.0, currentVal + v))
            } ?: run {
                newCrops.put(k, v)
            }
        }

        return resources.copy(
            crops = newCrops,
        )
    }

    override fun describe(): String = this.description
}


public data class ResourceAttributeAnimals(
    private val animals: EnumMap<EntityType, Double>,
): ResourceAttribute {
    override val priority: Int = 4
    val description: String

    init {
        val s = StringBuilder("Animals:\n")
        for ( entry in this.animals.entries ) {
            s.append("- ${entry.key}: ${entry.value}\n")
        }

        description = s.toString()
    }

    /**
     * Add animal breed success probability from this attribute into territory.
     * Saturate probability at 1.0.
     */
    override fun apply(resources: TerritoryResources): TerritoryResources {
        val newAnimals = resources.animals.clone()

        this.animals.forEach { k, v -> 
            newAnimals.get(k)?.let { currentVal -> 
                newAnimals.put(k, min(1.0, currentVal + v))
            } ?: run {
                newAnimals.put(k, v)
            }
        }

        return resources.copy(
            animals = newAnimals,
        )
    }

    override fun describe(): String = this.description
}

// ============================================================================
// RESOURCE ATTRIBUTE LOADER IMPLEMENTATION
// ============================================================================

public object DefaultResourceAttributeLoader: ResourceAttributeLoader {
    public override fun load(_ignored: HashMap<String, ResourceNode>, json: JsonObject): HashMap<String, ResourceNode> {
        // this should always run first. ignore existing resources input
        val resources: HashMap<String, ResourceNode> = hashMapOf()

        for ( name in json.keySet() ) {
            try {
                val node = json[name].getAsJsonObject()
                
                val attributes: ArrayList<ResourceAttribute> = arrayListOf()
                
                // icon
                val icon = node.get("icon")?.getAsString()

                // cost
                val costJson = node.get("cost")?.getAsJsonObject()
                val costConstant = costJson?.get("constant")?.getAsInt() ?: 0
                val costScale = costJson?.get("scale")?.getAsDouble() ?: 1.0

                // priority
                val priority = node.get("priority")?.getAsInt() ?: 0

                // ATTRIBUTES

                // income attribute
                node.get("income")?.getAsJsonObject()?.let { jsonIncome ->
                    if ( jsonIncome.size() > 0 ) {
                        val income: EnumMap<Material, Double> = EnumMap<Material, Double>(Material::class.java)
                        val incomeSpawnEgg: EnumMap<EntityType, Double> = EnumMap<EntityType, Double>(EntityType::class.java)

                        jsonIncome.keySet().forEach { type ->
                            val itemName = type.uppercase()

                            // spawn egg
                            if ( itemName.startsWith("SPAWN_EGG_")) {
                                val entityType = EntityType.valueOf(itemName.replace("SPAWN_EGG_", ""))
                                if ( entityType !== null ) {
                                    incomeSpawnEgg.put(entityType, jsonIncome.get(type).getAsDouble())
                                }
                            }
                            // regular material
                            else {
                                val material = Material.matchMaterial(type)
                                if ( material !== null ) {
                                    income.put(material, jsonIncome.get(type).getAsDouble())
                                }
                            }
                        }

                        attributes.add(ResourceAttributeIncome(income, incomeSpawnEgg))
                    }
                }

                // ore attribute
                node.get("ore")?.getAsJsonObject()?.let { jsonOre ->
                    if ( jsonOre.size() > 0 ) {
                        val ores: EnumMap<Material, OreDeposit> = EnumMap<Material, OreDeposit>(Material::class.java)

                        jsonOre.keySet().forEach { type ->
                            val material = Material.matchMaterial(type)
                            if ( material !== null ) {
                                val oreData = jsonOre.get(type)

                                // parse array format: [rate, minDrop, maxDrop]
                                if ( oreData?.isJsonArray() ?: false ) {
                                    val oreDataAsArray = oreData.getAsJsonArray()
                                    if ( oreDataAsArray.size() == 3 ) {
                                        ores.put(material, OreDeposit(
                                            material,
                                            oreDataAsArray[0].getAsDouble(),
                                            oreDataAsArray[1].getAsInt(),
                                            oreDataAsArray[2].getAsInt()
                                        ))
                                    }
                                }
                                // parse number format: rate (default minDrop = maxDrop = 1)
                                else if ( oreData?.isJsonPrimitive() ?: false ) {
                                    val oreDataRate = oreData.getAsDouble()
                                    ores.put(material, OreDeposit(
                                        material,
                                        oreDataRate,
                                        1,
                                        1
                                    ))
                                }
                            }
                        }

                        attributes.add(ResourceAttributeOre(ores))
                    }
                }

                // crops
                node.get("crops")?.getAsJsonObject()?.let { jsonCrops ->
                    if ( jsonCrops.size() > 0 ) {
                        val crops: EnumMap<Material, Double> = EnumMap<Material, Double>(Material::class.java)

                        jsonCrops.keySet().forEach { type ->
                            val material = Material.matchMaterial(type)
                            if ( material !== null ) {
                                crops.put(material, jsonCrops.get(type).getAsDouble())
                            }
                        }

                        attributes.add(ResourceAttributeCrops(crops))
                    }
                }

                // animals
                node.get("animals")?.getAsJsonObject()?.let { jsonAnimals ->
                    if ( jsonAnimals.size() > 0 ) {
                        val animals = EnumMap<EntityType, Double>(EntityType::class.java)

                        jsonAnimals.keySet().forEach { type ->
                            try {
                                val entityType = EntityType.valueOf(type.uppercase())
                                animals.put(entityType, jsonAnimals.get(type).getAsDouble())
                            }
                            catch ( err: Exception ) {
                                err.printStackTrace()
                            }
                        }

                        attributes.add(ResourceAttributeAnimals(animals))
                    }
                }

                resources.put(name, ResourceNode(
                    name,
                    icon,
                    costConstant,
                    costScale,
                    priority,
                    attributes,
                ))

            } catch ( err: Exception ) {
                Nodes.logger?.warning("Failed to parse resource ${name}: ${err}")
            }
        }

        return resources
    }
}