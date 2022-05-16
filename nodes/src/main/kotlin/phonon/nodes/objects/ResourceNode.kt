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
import org.bukkit.Material
import org.bukkit.ChatColor
import org.bukkit.entity.EntityType
import org.bukkit.command.CommandSender
import phonon.nodes.Message


data class ResourceNode(
    val name: String,
    val icon: String?,
    val income: EnumMap<Material, Double>,
    val incomeSpawnEgg: EnumMap<EntityType, Double>,
    val ores: EnumMap<Material, OreDeposit>,
    val crops: EnumMap<Material, Double>,
    val animals: EnumMap<EntityType, Double>,
    val costConstant: Int,
    val costScale: Double
) {
    // print info about object
    public fun printInfo(sender: CommandSender) {
        Message.print(sender, "${ChatColor.BOLD}Resource node \"${this.name}\":")
                
        Message.print(sender, "Income:")
        for ( entry in this.income.entries ) {
            Message.print(sender, "- ${entry.key} ${entry.value}")
        }

        Message.print(sender, "Crops: ")
        for ( entry in this.crops.entries ) {
            Message.print(sender, "- ${entry.key} ${entry.value}")
        }

        Message.print(sender, "Animals: ")
        for ( entry in this.animals.entries ) {
            Message.print(sender, "- ${entry.key} ${entry.value}")
        }

        Message.print(sender, "Ore: ")
        for ( entry in this.ores.entries ) {
            val ore = entry.value
            Message.print(sender, "- ${entry.key} ${ore.dropChance} ${ore.minAmount}-${ore.maxAmount}")
        }
    }

    // clone of this object
    public fun clone(): ResourceNode {
        // deep clone ores
        val oresClone: EnumMap<Material, OreDeposit> = EnumMap<Material, OreDeposit>(Material::class.java)
        for ( (mat, ore) in this.ores ) {
            oresClone.put(mat, ore.clone())
        }

        return ResourceNode(
            this.name,
            this.icon,
            this.income.clone(),
            this.incomeSpawnEgg.clone(),
            oresClone,
            this.crops.clone(),
            this.animals.clone(),
            this.costConstant,
            this.costScale
        )
    }

    // merge another resource node into this resource node:
    // 1. income: add together income from other node
    // 2. ore: add together probabilities
    //         take MAX of both minAmount and maxAmount
    // 3. crops: add together probabilities for each type
    // 4. animals: add together probabilities for each type
    //
    // Probabilities going >1.0 is okay, crop/animal rates are
    // limited to 1.0 by actual handler.
    // OreSampler will handle non-normalized probabilities
    public fun merge(other: ResourceNode): ResourceNode {
        // sum together income
        other.income.forEach { k, v ->
            this.income.get(k)?.let { currentVal -> 
                this.income.put(k, currentVal + v)
            } ?: run {
                this.income.put(k, v)
            }
        }

        // sum together spawn egg income
        other.incomeSpawnEgg.forEach { k, v ->
            this.incomeSpawnEgg.get(k)?.let { currentVal -> 
                this.incomeSpawnEgg.put(k, currentVal + v)
            } ?: run {
                this.incomeSpawnEgg.put(k, v)
            }
        }

        // merge ore deposits
        other.ores.forEach { k, v ->
            this.ores.get(k)?.let { oreDeposit -> 
                this.ores.put(k, oreDeposit.merge(v))
            } ?: run {
                this.ores.put(k, v)
            }
        }

        // sum together crop probabilities
        other.crops.forEach { k, v -> 
            this.crops.get(k)?.let { currentVal -> 
                this.crops.put(k, currentVal + v)
            } ?: run {
                this.crops.put(k, v)
            }
        }

        // sum together animal probabilities
        other.animals.forEach { k, v -> 
            this.animals.get(k)?.let { currentVal -> 
                this.animals.put(k, currentVal + v)
            } ?: run {
                this.animals.put(k, v)
            }
        }

        return this
    }
}
