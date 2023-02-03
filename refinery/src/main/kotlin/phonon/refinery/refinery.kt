/**
 * Refinery main logic.
 */

package phonon.refinery

import java.io.File
import java.io.FileReader
import java.util.UUID
import java.util.EnumMap
import java.util.EnumSet
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
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.entity.Player

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

import org.bukkit.scheduler.BukkitTask
import org.bukkit.scheduler.BukkitRunnable
import phonon.nodes.Nodes
import phonon.nodes.objects.Town
import phonon.nodes.objects.TerritoryId

/**
 * Available recipe in refinery for converting raw material to refined
 * material output.
 */
data class RefineryRecipe(
    val name: String,
    val inputs: List<ItemStack>,
    val outputs: List<ItemStack>,
    val timeRequiredMillis: Long, // in milliseconds
)

/**
 * Configuration for a refinery type.
 */
data class RefineryConfig(
    // Name of refinery type.
    val name: String,
    // Recipes available in refinery. During refinery run tick, ALL recipes
    // are checked and run if inputs are available.
    val recipes: List<RefineryRecipe>,
    // Refinery inventory size.
    val invSize: Int,
)

/**
 * Refinery object. Stores list of valid refinery conversion recipes
 * and internal inventory storage that players interact with to add input
 * materials. This does not hold outputs, instead those go into town's 
 * income chest.
 */
class Refinery(
    // Refinery's territory id
    val territoryId: TerritoryId,
    // Refinery config name
    val config: String,
    // Recipes available in refinery, reference to same List as in a
    // RefineryConfig.
    val recipes: List<RefineryRecipe>,
    // Current progress of each recipe. Index of this array corresponds to
    // index of recipe in recipes array. Value is an accumulated time in
    // milliseconds of how long this recipe has been running. Accumulates
    // when all inputs are available. Resets to zero if inputs are no 
    // longer available.
    val recipeProgressMillis: LongArray,
    // Refinery inventory
    val inv: Inventory,
): InventoryHolder {
    // Tracks allowed item inputs in all recipes, use to enforce players
    // only able to insert valid inputs into refinery.
    val validInputMaterials: EnumSet<Material> = EnumSet.noneOf(Material::class.java)

    // Tracks current count of each material in refinery inventory.
    // Use to check if inputs are available for each recipe.
    val inputMaterialCount: EnumMap<Material, Int> = EnumMap<Material, Int>(Material::class.java)

    init {
        // populate valid input materials
        for ( recipe in recipes ) {
            for ( input in recipe.inputs ) {
                validInputMaterials.add(input.type)
            }
        }
    }

    override public fun getInventory(): Inventory {
        return this.inv
    }

    /**
     * Convert into serialized object state for async thread JSON
     * serialization and file saving.
     */
    public fun toSaveState(): RefinerySaveState {
        return RefinerySaveState(
            territoryId = this.territoryId,
            inventory = this.inv.getStorageContents().map{ RefineryItemCount(it.type, it.amount) },
            recipeProgress = this.recipes.mapIndexed{ index, recipe -> RefineryProgress(recipe.name, this.recipeProgressMillis[index]) },
        )
    }
}

/**
 * Simpler data class implementation of ItemStack, for serializing
 * refinery inventory contents.
 */
data class RefineryItemCount(
    val material: Material,
    val amount: Int,
)

/**
 * Refinery progress for a single recipe. Used for serializing
 * progress for each specific recipe. Store as this pair
 * (recipe, progress) so that if a refinery is updated and more
 * recipes are added, we can still match this progress to the
 * old recipe using the String name.
 */
data class RefineryProgress(
    val recipe: String,
    val progress: Long,
)

/**
 * Refinery serialized save state. Contains references to config and
 * contain stored items and current recipe progress.
 */
data class RefinerySaveState(
    val territoryId: TerritoryId,
    val inventory: List<RefineryItemCount>,
    val recipeProgress: List<RefineryProgress>,
)

/*
 * Refinery global state and implement bukkit plugin interface
 */
public class RefineryPlugin: JavaPlugin() {
    // config settings
    public var saveTickPeriod: Long = 20 * 60 * 5 // 5 minutes
    public var updateTickPeriod: Long = 20 * 10 // 10 seconds

    // refinery configs, loaded on enable
    public var configs: Map<String, RefineryConfig> = HashMap<String, RefineryConfig>()

    // list view of all refineries
    public var refineries: List<Refinery> = ArrayList<Refinery>()

    // storage mapping territory id to its refinery (same object as in refineries)
    public var territoryToRefinery: Map<TerritoryId, Refinery> = LinkedHashMap<TerritoryId, Refinery>()

    // last periodic tick time
    private var lastUpdateTime = System.currentTimeMillis()

    // refinery update task
    private var taskUpdate: BukkitTask? = null
    
    // refinery save task
    private var taskSave: BukkitTask? = null

    override fun onEnable() {
        // measure load time
        val timeStart = System.currentTimeMillis()

        val logger = this.getLogger()
        val pluginManager = this.getServer().getPluginManager()

        // register listener

        // register commands
        this.getCommand("refinery")?.setExecutor(RefineryCommand)

        // load config and tasks
        this.reloadConfigAndTasks()

        // print load time
        val timeEnd = System.currentTimeMillis()
        val timeLoad = timeEnd - timeStart
        logger.info("Enabled in ${timeLoad}ms")

        // print success message
        logger.info("now this is epic")
    }

    override fun onDisable() {
        logger.info("wtf i hate xeth now")
    }

    /**
     * Reloads config and restarts tick tasks.
     */
    internal fun reloadConfigAndTasks() {
        this.taskUpdate?.cancel()
        this.taskSave?.cancel()

        // load yml config

        // start tasks
        val self = this
        this.taskUpdate = Bukkit.getScheduler().runTaskTimer(this, object: Runnable {
            override fun run() {
                self.update()
            }
        }, this.updateTickPeriod, this.updateTickPeriod)

        this.taskSave = Bukkit.getScheduler().runTaskTimer(this, object: Runnable {
            override fun run() {
                self.save()
            }
        }, this.saveTickPeriod, this.saveTickPeriod)
    }

    /**
     * Reloads refineries from Nodes world. Must be done separately from
     * config and after Nodes world is fully loaded. Runs in event handler
     * for Nodes world loaded event.
     */
    internal fun reloadWorld() {
        // save state to file first
        this.save()

        // first load refineries for each territory
        // TODO

        // load and apply refinery save state onto updated
        // refinery configs
        // TODO
    }
    
    /**
     * Periodic task for ticking all refineries: run recipes, update
     * progress, and remove inputs + create outputs for recipes whose
     * progress has reached the required time.
     */
    private fun update() {
        // time delta since last update
        val dt = System.currentTimeMillis() - this.lastUpdateTime

        for ( refinery in this.refineries ) {
            val terrId = refinery.territoryId
            val terr = Nodes.getTerritoryFromId(terrId)
            if ( terr === null ) {
                continue
            }

            // if territory is not owned by a town, skip
            val town = terr.town
            if ( town === null ) {
                continue
            }
            
            try { // try/catching to prevent one refinery from breaking all refineries
                // first pass: calculate number of input materials available
                refinery.validInputMaterials.forEach { material ->
                    refinery.inputMaterialCount[material] = 0
                }
                for ( itemstack in refinery.inventory.getStorageContents() ) {
                    if ( itemstack === null ) {
                        continue
                    }
                    val material = itemstack.type
                    if ( refinery.validInputMaterials.contains(material) ) {
                        refinery.inputMaterialCount[material] = refinery.inputMaterialCount[material]!! + itemstack.amount
                    }
                }

                // second pass: run recipes if enough inputs are available
                // if recipes share inputs and use up enough inputs that
                // the next recipe cannot run, then skip
                for ( (idx, recipe) in refinery.recipes.withIndex() ) {
                    var inputsAvailable = true
                    for ( input in recipe.inputs ) {
                        val material = input.type
                        val amount = input.amount
                        if ( refinery.inputMaterialCount[material]!! < amount ) {
                            inputsAvailable = false
                            break
                        }
                    }

                    // tick recipe, if past time required, remove inputs and 
                    // put output into town's income chest
                    if ( inputsAvailable ) {
                        refinery.recipeProgressMillis[idx] += dt

                        if ( refinery.recipeProgressMillis[idx] >= recipe.timeRequiredMillis ) {
                            for ( output in recipe.outputs ) {
                                Nodes.addToIncome(town, output.type, output.amount)
                            }
                            refinery.recipeProgressMillis[idx] = 0
                        }
                    }
                    else {
                        refinery.recipeProgressMillis[idx] = 0
                    }
                }
            }
            catch ( e: Exception ) {
                e.printStackTrace()
            }
        }

        // update last tick time (do here to ignore time for update)
        this.lastUpdateTime = System.currentTimeMillis()
    }

    /**
     * Periodic task for saving refineries state to file. Serialize all
     * refineries into list of RefinerySaveState, then send to async
     * thread for JSON serialization and file saving.
     */
    private fun save() {
        // TODO
    }
}
