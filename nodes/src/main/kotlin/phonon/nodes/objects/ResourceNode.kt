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
            newOres.put(mat, ore.copy())
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
// MULTIPLIER ATTRIBUTES
// ============================================================================

public data class ResourceAttributeTotalIncomeMultiplier(
    private val multiplier: Double,
): ResourceAttribute {
    override val priority: Int = 50
    val description: String = "Income Multiplier: ${this.multiplier}"

    /**
     * Apply multiplier to resource's income.
     */
    override fun apply(resources: TerritoryResources): TerritoryResources {
        val newIncome = resources.income.clone()
        val newIncomeSpawnEgg = resources.incomeSpawnEgg.clone()

        newIncome.forEach { (type, amount) ->
            newIncome[type] = amount * multiplier
        }
        newIncomeSpawnEgg.forEach { (type, amount) ->
            newIncomeSpawnEgg[type] = amount * multiplier
        }

        return resources.copy(
            income = newIncome,
            incomeSpawnEgg = newIncomeSpawnEgg,
        )
    }

    override fun describe(): String = this.description
}


public data class ResourceAttributeTotalOreMultiplier(
    private val multiplier: Double,
): ResourceAttribute {
    override val priority: Int = 50
    val description: String = "Ore Multiplier: ${this.multiplier}"

    /**
     * Apply multiplier to resource's ores.
     */
    override fun apply(resources: TerritoryResources): TerritoryResources {
        val newOres = resources.ores.clone()

        newOres.forEach { (type, ore) ->
            newOres[type] = ore.copy(dropChance = ore.dropChance * multiplier)
        }

        return resources.copy(
            ores = newOres,
        )
    }

    override fun describe(): String = this.description
}


public data class ResourceAttributeTotalCropsMultiplier(
    private val multiplier: Double,
): ResourceAttribute {
    override val priority: Int = 50
    val description: String = "Crops Multiplier: ${this.multiplier}"

    /**
     * Apply multiplier to resource's crops.
     */
    override fun apply(resources: TerritoryResources): TerritoryResources {
        val newCrops = resources.crops.clone()

        newCrops.forEach { (type, amount) ->
            newCrops[type] = amount * multiplier
        }

        return resources.copy(
            crops = newCrops,
        )
    }

    override fun describe(): String = this.description
}


public data class ResourceAttributeTotalAnimalsMultiplier(
    private val multiplier: Double,
): ResourceAttribute {
    override val priority: Int = 50
    val description: String = "Animal Breeding Multiplier: ${this.multiplier}"

    /**
     * Apply multiplier to resource's crops.
     */
    override fun apply(resources: TerritoryResources): TerritoryResources {
        val newAnimals = resources.animals.clone()

        newAnimals.forEach { (type, amount) ->
            newAnimals[type] = amount * multiplier
        }

        return resources.copy(
            animals = newAnimals,
        )
    }

    override fun describe(): String = this.description
}


// ============================================================================
// NEIGHBOR ATTRIBUTES
// ============================================================================

public data class ResourceAttributeNeighborTotalIncomeMultiplier(
    private val neighborTotalIncomeMultiplier: Double,
): ResourceAttribute {
    override val priority: Int = 50
    val description: String = "Neighbor Income Multipliers: ${this.neighborTotalIncomeMultiplier}"

    /**
     * Add together neighbor multipliers.
     */
    override fun apply(resources: TerritoryResources): TerritoryResources {
        val neighborTotalIncomeMultiplier = resources.neighborTotalIncomeMultiplier ?: 0.0
        return resources.copy(
            neighborTotalIncomeMultiplier = neighborTotalIncomeMultiplier + this.neighborTotalIncomeMultiplier,
        )
    }

    override fun describe(): String = this.description
}


public data class ResourceAttributeNeighborIncomeMultiplier(
    private val neighborIncomeMultiplier: EnumMap<Material, Double>,
    private val neighborIncomeSpawnEggMultiplier: EnumMap<EntityType, Double>,
): ResourceAttribute {
    override val priority: Int = 50
    val description: String

    init {
        val s = StringBuilder("Neighbor Income Multipliers:\n")
        for ( entry in this.neighborIncomeMultiplier.entries ) {
            s.append("- ${entry.key}: ${entry.value}\n")
        }
        for ( entry in this.neighborIncomeSpawnEggMultiplier.entries ) {
            s.append("- ${entry.key}: ${entry.value}\n")
        }

        description = s.toString()
    }

    /**
     * Add together neighbor multipliers.
     */
    override fun apply(resources: TerritoryResources): TerritoryResources {
        val newNeighborIncomeMultiplier = resources.neighborIncomeMultiplier?.clone() ?: EnumMap<Material, Double>(Material::class.java)
        val newNeighborIncomeSpawnEggMultiplier = resources.neighborIncomeSpawnEggMultiplier?.clone() ?: EnumMap<EntityType, Double>(EntityType::class.java)

        this.neighborIncomeMultiplier.forEach { k, v -> 
            newNeighborIncomeMultiplier.get(k)?.let { currentVal -> 
                newNeighborIncomeMultiplier.put(k, currentVal + v)
            } ?: run {
                newNeighborIncomeMultiplier.put(k, v)
            }
        }
        this.neighborIncomeSpawnEggMultiplier.forEach { k, v -> 
            newNeighborIncomeSpawnEggMultiplier.get(k)?.let { currentVal -> 
                newNeighborIncomeSpawnEggMultiplier.put(k, currentVal + v)
            } ?: run {
                newNeighborIncomeSpawnEggMultiplier.put(k, v)
            }
        }

        return resources.copy(
            neighborIncomeMultiplier = newNeighborIncomeMultiplier,
            neighborIncomeSpawnEggMultiplier = newNeighborIncomeSpawnEggMultiplier,
        )
    }

    override fun describe(): String = this.description
}


public data class ResourceAttributeNeighborTotalOreMultiplier(
    private val neighborTotalOresMultiplier: Double,
): ResourceAttribute {
    override val priority: Int = 50
    val description: String = "Neighbor Total Ore Multiplier: ${this.neighborTotalOresMultiplier}"

    /**
     * Add together neighbor multipliers.
     */
    override fun apply(resources: TerritoryResources): TerritoryResources {
        val neighborTotalOresMultiplier = resources.neighborTotalOresMultiplier ?: 0.0
        return resources.copy(
            neighborTotalOresMultiplier = neighborTotalOresMultiplier + this.neighborTotalOresMultiplier,
        )
    }

    override fun describe(): String = this.description
}


public data class ResourceAttributeNeighborOreMultiplier(
    private val neighborOresMultiplier: EnumMap<Material, Double>,
): ResourceAttribute {
    override val priority: Int = 50
    val description: String

    init {
        val s = StringBuilder("Neighbor Ores Multipliers:\n")
        for ( entry in this.neighborOresMultiplier.entries ) {
            s.append("- ${entry.key}: ${entry.value}\n")
        }

        description = s.toString()
    }

    /**
     * Add together neighbor multipliers.
     */
    override fun apply(resources: TerritoryResources): TerritoryResources {
        val newNeighborOresMultiplier = resources.neighborOresMultiplier?.clone() ?: EnumMap<Material, Double>(Material::class.java)

        this.neighborOresMultiplier.forEach { k, v -> 
            newNeighborOresMultiplier.get(k)?.let { currentVal -> 
                newNeighborOresMultiplier.put(k, currentVal + v)
            } ?: run {
                newNeighborOresMultiplier.put(k, v)
            }
        }

        return resources.copy(
            neighborOresMultiplier = newNeighborOresMultiplier,
        )
    }

    override fun describe(): String = this.description
}


public data class ResourceAttributeNeighborTotalCropsMultiplier(
    private val neighborTotalCropsMultiplier: Double,
): ResourceAttribute {
    override val priority: Int = 50
    val description: String = "Neighbor Total Crops Multiplier: ${this.neighborTotalCropsMultiplier}"

    /**
     * Add together neighbor multipliers.
     */
    override fun apply(resources: TerritoryResources): TerritoryResources {
        val neighborTotalCropsMultiplier = resources.neighborTotalCropsMultiplier ?: 0.0
        return resources.copy(
            neighborTotalCropsMultiplier = neighborTotalCropsMultiplier + this.neighborTotalCropsMultiplier,
        )
    }

    override fun describe(): String = this.description
}

public data class ResourceAttributeNeighborCropsMultiplier(
    private val neighborCropsMultiplier: EnumMap<Material, Double>,
): ResourceAttribute {
    override val priority: Int = 50
    val description: String

    init {
        val s = StringBuilder("Neighbor Crops Multipliers:\n")
        for ( entry in this.neighborCropsMultiplier.entries ) {
            s.append("- ${entry.key}: ${entry.value}\n")
        }

        description = s.toString()
    }

    /**
     * Add together neighbor multipliers.
     */
    override fun apply(resources: TerritoryResources): TerritoryResources {
        val newNeighborCropsMultiplier = resources.neighborCropsMultiplier?.clone() ?: EnumMap<Material, Double>(Material::class.java)

        this.neighborCropsMultiplier.forEach { k, v -> 
            newNeighborCropsMultiplier.get(k)?.let { currentVal -> 
                newNeighborCropsMultiplier.put(k, currentVal + v)
            } ?: run {
                newNeighborCropsMultiplier.put(k, v)
            }
        }

        return resources.copy(
            neighborCropsMultiplier = newNeighborCropsMultiplier,
        )
    }

    override fun describe(): String = this.description
}


public data class ResourceAttributeNeighborTotalAnimalsMultiplier(
    private val neighborTotalAnimalsMultiplier: Double,
): ResourceAttribute {
    override val priority: Int = 50
    val description: String = "Neighbor Total Animals Multiplier: ${this.neighborTotalAnimalsMultiplier}"

    /**
     * Add together neighbor multipliers.
     */
    override fun apply(resources: TerritoryResources): TerritoryResources {
        val neighborTotalAnimalsMultiplier = resources.neighborTotalAnimalsMultiplier ?: 0.0
        return resources.copy(
            neighborTotalAnimalsMultiplier = neighborTotalAnimalsMultiplier + this.neighborTotalAnimalsMultiplier,
        )
    }

    override fun describe(): String = this.description
}

public data class ResourceAttributeNeighborAnimalsMultiplier(
    private val neighborAnimalsMultiplier: EnumMap<EntityType, Double>,
): ResourceAttribute {
    override val priority: Int = 50
    val description: String

    init {
        val s = StringBuilder("Neighbor Animals Multipliers:\n")
        for ( entry in this.neighborAnimalsMultiplier.entries ) {
            s.append("- ${entry.key}: ${entry.value}\n")
        }

        description = s.toString()
    }

    /**
     * Add together neighbor multipliers.
     */
    override fun apply(resources: TerritoryResources): TerritoryResources {
        val newNeighborAnimalsMultiplier = resources.neighborAnimalsMultiplier?.clone() ?: EnumMap<EntityType, Double>(EntityType::class.java)

        this.neighborAnimalsMultiplier.forEach { k, v -> 
            newNeighborAnimalsMultiplier.get(k)?.let { currentVal -> 
                newNeighborAnimalsMultiplier.put(k, currentVal + v)
            } ?: run {
                newNeighborAnimalsMultiplier.put(k, v)
            }
        }

        return resources.copy(
            neighborAnimalsMultiplier = newNeighborAnimalsMultiplier,
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

                // territory total modifiers
                node.get("income_total_multiplier")?.getAsDouble()?.let { multipler ->
                    attributes.add(ResourceAttributeTotalIncomeMultiplier(multipler))
                }
                node.get("ore_total_multiplier")?.getAsDouble()?.let { multipler ->
                    attributes.add(ResourceAttributeTotalOreMultiplier(multipler))
                }
                node.get("crops_total_multiplier")?.getAsDouble()?.let { multipler ->
                    attributes.add(ResourceAttributeTotalCropsMultiplier(multipler))
                }
                node.get("animals_total_multiplier")?.getAsDouble()?.let { multipler ->
                    attributes.add(ResourceAttributeTotalAnimalsMultiplier(multipler))
                }

                // TODO: territory specific type multipliers

                // neighbor direct properties
                // TODO: neighbor specific type modifiers

                // neighbor modifiers
                node.get("neighbor_income_total_multiplier")?.getAsDouble()?.let { multipler ->
                    attributes.add(ResourceAttributeNeighborTotalIncomeMultiplier(multipler))
                }
                node.get("neighbor_ore_total_multiplier")?.getAsDouble()?.let { multipler ->
                    attributes.add(ResourceAttributeNeighborTotalOreMultiplier(multipler))
                }
                node.get("neighbor_crops_total_multiplier")?.getAsDouble()?.let { multipler ->
                    attributes.add(ResourceAttributeNeighborTotalCropsMultiplier(multipler))
                }
                node.get("neighbor_animals_total_multiplier")?.getAsDouble()?.let { multipler ->
                    attributes.add(ResourceAttributeNeighborTotalAnimalsMultiplier(multipler))
                }

                // TODO: neighbor specific multipliers

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