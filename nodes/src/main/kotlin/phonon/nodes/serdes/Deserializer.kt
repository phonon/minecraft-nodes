/**
 * Deserializer
 * 
 */

package phonon.nodes.serdes

import java.io.FileReader
import java.nio.file.Path
import com.google.gson.JsonParser
import com.google.gson.JsonObject
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.EntityType
import phonon.nodes.Nodes
import phonon.nodes.constants.*
import phonon.nodes.objects.Coord
import phonon.nodes.objects.OreDeposit
import phonon.nodes.objects.Town
import phonon.nodes.objects.Nation
import phonon.nodes.utils.Color
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

/**
 * Utility class to hold resources and territories json
 * object sections.
 */
public data class WorldJsonState(
    val resources: JsonObject?,
    val territories: JsonObject?,
)

public object Deserializer {

    // parse the world.json definition file:
    // contains resource nodes and territories
    public fun worldFromJson(path: Path): WorldJsonState {
        val json = JsonParser.parseReader(FileReader(path.toString())) // newer gson
        // val json = JsonParser().parse(FileReader(path.toString()))        // gson bundled in mineman
        val jsonObj = json.asJsonObject

        val jsonNodes = jsonObj.get("nodes")?.getAsJsonObject()
        val jsonTerritories = jsonObj.get("territories")?.getAsJsonObject()
        
        return WorldJsonState(jsonNodes, jsonTerritories)

    }

    // import towns.json definition file
    // contains
    fun townsFromJson(path: Path) {

        // list of towns, nations and relations, for post-process adding diplomacy
        val towns: ArrayList<Town> = ArrayList()
        val townAllies: ArrayList<ArrayList<String>> = ArrayList()
        val townEnemies: ArrayList<ArrayList<String>> = ArrayList()
        val nations: ArrayList<Nation> = ArrayList()
        val nationAllies: ArrayList<ArrayList<String>> = ArrayList()
        val nationEnemies: ArrayList<ArrayList<String>> = ArrayList()

        val json = JsonParser.parseReader(FileReader(path.toString()))
        val jsonObj = json.asJsonObject
        
        // ===============================
        // Residents
        // ===============================
        val jsonResidents = jsonObj.get("residents")?.asJsonObject
        if ( jsonResidents !== null ) {
            jsonResidents.keySet().forEach { uuid -> 
                val resident = jsonResidents[uuid].asJsonObject

                // claims
                var claims: Int = 0
                var claimsTime: Long = 0
                val claimsJson = resident.get("claims")?.asJsonArray
                if ( claimsJson !== null && claimsJson.size() == 2 ) {
                    claims = claimsJson[0].asInt
                    claimsTime = claimsJson[1].asLong
                }

                // prefix, suffix
                val prefix = resident.get("prefix")?.asString ?: ""
                val suffix = resident.get("suffix")?.asString ?: ""

                // trusted
                val trusted = resident.get("trust")?.asBoolean ?: false

                // town create cooldown
                val townCreateCooldown: Long = resident.get("townCool")?.asLong ?: 0L

                Nodes.loadResident(
                    UUID.fromString(uuid),
                    claims,
                    claimsTime,
                    prefix,
                    suffix,
                    trusted,
                    townCreateCooldown
                )
            }
        }

        // ===============================
        // Towns
        // ===============================
        val jsonTowns = jsonObj.get("towns")?.asJsonObject
        if ( jsonTowns !== null ) {
            jsonTowns.keySet().forEach { name -> 
                val town = jsonTowns[name].asJsonObject

                // parse uuid
                val uuidJson = town.get("uuid")
                val uuid: UUID = if ( uuidJson !== null ) {
                    UUID.fromString(uuidJson.asString)
                }
                else {
                    UUID.randomUUID()
                }
                
                // get home territory id, if missing skip town
                val homeId = town.get("home")?.asInt
                if ( homeId == null ) {
                    System.err.println("Cannot create ${name}: no home")
                    return@forEach
                }

                // parse leader uuid (may be null)
                val leaderJson = town.get("leader")
                val leader: UUID? = if ( leaderJson == null || leaderJson.isJsonNull) {
                    null
                } else {
                    UUID.fromString(leaderJson.asString)
                }

                // parse spawn location
                val spawnLocArray = town.get("spawn")?.asJsonArray
                var spawn = if ( spawnLocArray !== null && spawnLocArray.size() == 3 ) {
                    Location(Bukkit.getWorlds().get(0), spawnLocArray[0].asDouble, spawnLocArray[1].asDouble, spawnLocArray[2].asDouble)
                } else {
                    null
                }

                // parse color
                val colorArray = town.get("color")?.asJsonArray
                var color = if ( colorArray !== null && colorArray.size() == 3 ) {
                    Color(colorArray[0].asInt, colorArray[1].asInt, colorArray[2].asInt)
                } else {
                    null
                }

                // parse claimsBonus
                var claimsBonus = town.get("claimsBonus")?.asInt ?: 0

                // parse claimsAnnexed penalty
                var claimsAnnexed = town.get("claimsAnnex")?.asInt ?: 0

                // parse claimsPenalty
                var claimsPenalty: Int = 0
                var claimsPenaltyTime: Long = 0
                val claimsPenaltyJson = town.get("claimsPenalty")?.asJsonArray
                if ( claimsPenaltyJson !== null && claimsPenaltyJson.size() == 2 ) {
                    claimsPenalty = claimsPenaltyJson[0].asInt
                    claimsPenaltyTime = claimsPenaltyJson[1].asLong
                }

                // parse residents
                val residentsUUID: ArrayList<UUID> = ArrayList()
                val residentsArray = town.get("residents")?.asJsonArray
                if ( residentsArray !== null ) {
                    residentsArray.forEach { uuid ->
                        residentsUUID.add(UUID.fromString(uuid.asString))
                    }
                }

                // parse officers
                val officersUUID: ArrayList<UUID> = ArrayList()
                val officersArray = town.get("officers")?.asJsonArray
                if ( officersArray !== null ) {
                    officersArray.forEach { uuid ->
                        officersUUID.add(UUID.fromString(uuid.asString))
                    }
                }

                // parse territories
                val territoryIds: ArrayList<Int> = ArrayList()
                val territoryArray = town.get("territories")?.asJsonArray
                if ( territoryArray !== null ) {
                    territoryArray.forEach { id ->
                        territoryIds.add(id.asInt)
                    }
                }

                // parse captured territories
                val capturedIds: ArrayList<Int> = ArrayList()
                val capturedTerrArray = town.get("captured")?.asJsonArray
                if ( capturedTerrArray !== null ) {
                    capturedTerrArray.forEach { id ->
                        capturedIds.add(id.asInt)
                    }
                }

                // parse annexed territories
                val annexedIds: ArrayList<Int> = ArrayList()
                val annexedTerrArray = town.get("annexed")?.asJsonArray
                if ( annexedTerrArray !== null ) {
                    annexedTerrArray.forEach { id ->
                        annexedIds.add(id.asInt)
                    }
                }

                // parse stored income
                val income: EnumMap<Material, Int> = EnumMap<Material, Int>(Material::class.java)
                val townIncomeJson = town.get("income")?.asJsonObject
                if ( townIncomeJson !== null ) {
                    townIncomeJson.keySet().forEach { type ->
                        val material = Material.matchMaterial(type)
                        if ( material !== null ) {
                            income.put(material, townIncomeJson.get(type).asInt)
                        }
                    }
                }

                // parse stored spawn egg income
                val incomeSpawnEgg: EnumMap<EntityType, Int> = EnumMap<EntityType, Int>(EntityType::class.java)
                val townIncomeSpawnEggJson = town.get("incomeEgg")?.asJsonObject
                if ( townIncomeSpawnEggJson !== null ) {
                    townIncomeSpawnEggJson.keySet().forEach { type ->
                        val entityType = EntityType.valueOf(type)
                        incomeSpawnEgg.put(entityType, townIncomeSpawnEggJson.get(type).asInt)
                    }
                }

                // parse ally names
                val allies: ArrayList<String> = ArrayList()
                val alliesArray = town.get("allies")?.asJsonArray
                if ( alliesArray !== null ) {
                    alliesArray.forEach { name ->
                        allies.add(name.asString)
                    }
                }
                
                // parse enemy names
                val enemies: ArrayList<String> = ArrayList()
                val enemiesArray = town.get("enemies")?.asJsonArray
                if ( enemiesArray !== null ) {
                    enemiesArray.forEach { name ->
                        enemies.add(name.asString)
                    }
                }

                // parse permissions
                val permissions: EnumMap<TownPermissions, EnumSet<PermissionsGroup>> = enumValues<TownPermissions>().toList().associateWithTo(
                    EnumMap<TownPermissions, EnumSet<PermissionsGroup>>(TownPermissions::class.java),
                    {_ -> EnumSet.noneOf(PermissionsGroup::class.java)}
                )
                val permissionsJson = town.get("perms")?.asJsonObject
                if ( permissionsJson !== null ) {
                    permissionsJson.keySet().forEach { type ->
                        // get enum type
                        try {
                            val permType = TownPermissions.valueOf(type)
                            val permGroupList = permissionsJson.get(type)?.asJsonArray
                            if ( permGroupList !== null ) {
                                for ( group in permGroupList ) {
                                    permissions.get(permType)!!.add(PermissionsGroup.values[group.asInt])
                                }
                            }
                        }
                        catch ( err: IllegalArgumentException ) {
                            System.err.println("Invalid town permission: ${type}")
                        }
                    }
                }

                // parse town isOpen
                val isOpen: Boolean = town.get("open")?.asBoolean ?: false
                
                // parse town protected blocks
                val protectedBlocks: HashSet<Block> = hashSetOf()
                val protectedBlocksJsonArray = town.get("protect")?.asJsonArray
                if ( protectedBlocksJsonArray !== null ) {
                    val world = Bukkit.getWorlds().get(0)
                    for ( item in protectedBlocksJsonArray ) {
                        val blockArray = item.asJsonArray
                        if ( blockArray !== null && blockArray.size() == 3 ) {
                            val x = blockArray[0].asInt
                            val y = blockArray[1].asInt
                            val z = blockArray[2].asInt
                            val block = world.getBlockAt(x, y, z)
                            protectedBlocks.add(block)
                        }
                    }
                }

                // town move home cooldown
                val moveHomeCooldown: Long = town.get("homeCool")?.asLong ?: 0L

                // parse town outposts "[terr.id, spawn.x, spawn.y, spawn.z]"
                val outposts: HashMap<String, Pair<Int, Location>> = hashMapOf()
                val outpostsJson = town.get("outpost")?.asJsonObject
                if ( outpostsJson !== null ) {
                    outpostsJson.keySet().forEach { name ->
                        val dataArray = outpostsJson.get(name)?.asJsonArray
                        if ( dataArray !== null && dataArray.size() == 4 ) {
                            val terrId = dataArray[0].asInt
                            val spawnX = dataArray[1].asDouble
                            val spawnY = dataArray[2].asDouble
                            val spawnZ = dataArray[3].asDouble
                            val location = Location(Bukkit.getWorlds().get(0), spawnX, spawnY, spawnZ)
                            outposts.put(name, Pair(terrId, location))
                        }
                    }
                }

                val townObject: Town? = Nodes.loadTown(
                    uuid,
                    name,
                    leader,
                    homeId,
                    spawn,
                    color,
                    residentsUUID,
                    officersUUID,
                    territoryIds,
                    capturedIds,
                    annexedIds,
                    claimsBonus,
                    claimsAnnexed,
                    claimsPenalty,
                    claimsPenaltyTime,
                    income,
                    incomeSpawnEgg,
                    permissions,
                    isOpen,
                    protectedBlocks,
                    moveHomeCooldown,
                    outposts
                )

                if ( townObject !== null ) {
                    towns.add(townObject)
                    townAllies.add(allies)
                    townEnemies.add(enemies)
                }
            }
        }

        // ===============================
        // Nations
        // ===============================
        val jsonNations = jsonObj.get("nations")?.asJsonObject
        if ( jsonNations !== null ) {
            jsonNations.keySet().forEach { name -> 
                val nation = jsonNations[name].asJsonObject

                // parse uuid
                val uuidJson = nation.get("uuid")
                val uuid: UUID = if ( uuidJson !== null ) {
                    UUID.fromString(uuidJson.asString)
                }
                else {
                    UUID.randomUUID()
                }

                // parse capital town name
                val capitalName = nation.get("capital").asString

                // parse color
                val colorArray = nation.get("color")?.asJsonArray
                var color = if ( colorArray !== null && colorArray.size() == 3 ) {
                    Color(colorArray[0].asInt, colorArray[1].asInt, colorArray[2].asInt)
                } else {
                    null
                }

                // parse towns
                val towns: ArrayList<String> = arrayListOf()
                val townsArray = nation.get("towns")?.asJsonArray
                if ( townsArray !== null ) {
                    townsArray.forEach { townName ->
                        towns.add(townName.asString)
                    }
                }
                
                // parse ally names
                val allies: ArrayList<String> = ArrayList()
                val alliesArray = nation.get("allies")?.asJsonArray
                if ( alliesArray !== null ) {
                    alliesArray.forEach { name ->
                        allies.add(name.asString)
                    }
                }
                
                // parse enemy names
                val enemies: ArrayList<String> = ArrayList()
                val enemiesArray = nation.get("enemies")?.asJsonArray
                if ( enemiesArray !== null ) {
                    enemiesArray.forEach { name ->
                        enemies.add(name.asString)
                    }
                }

                val nationObject = Nodes.loadNation(
                    uuid,
                    name,
                    capitalName,
                    color,
                    towns
                )

                nations.add(nationObject)
                nationAllies.add(allies)
                nationEnemies.add(enemies)
            }
        }
        
        // post process finish load:
        // handle diplomacy
        Nodes.loadDiplomacy(
            towns,
            townAllies,
            townEnemies,
//            nations,
//            nationAllies,
//            nationEnemies
        )
    }
}
