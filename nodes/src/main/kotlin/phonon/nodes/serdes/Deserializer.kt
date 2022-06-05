/**
 * Deserializer
 * 
 */

package phonon.nodes.serdes

import java.io.FileReader
import java.util.EnumMap
import java.util.EnumSet
import java.util.UUID
import java.nio.file.Path
import com.google.gson.JsonParser
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

public object Deserializer {

    // parse the world.json definition file:
    // contains resource nodes and territories
    public fun worldFromJson(path: Path) {
        val json = JsonParser.parseReader(FileReader(path.toString())) // newer gson
        // val json = JsonParser().parse(FileReader(path.toString()))        // gson bundled in mineman
        val jsonObj = json.getAsJsonObject()

        // parse resource nodes
        val jsonNodes = jsonObj.get("nodes")?.getAsJsonObject()
        if ( jsonNodes !== null ) {
            jsonNodes.keySet().forEach { name -> 
                val node = jsonNodes[name].getAsJsonObject()
                
                // icon
                val icon = node.get("icon")?.getAsString()

                // cost
                val costJson = node.get("cost")?.getAsJsonObject()
                val costConstantJson = costJson?.get("constant")
                val costScaleJson = costJson?.get("scale")

                val costConstant: Int = if ( costConstantJson !== null ) {
                    costConstantJson.getAsInt()
                } else {
                    0
                }

                val costScale: Double = if ( costScaleJson !== null ) {
                    costScaleJson.getAsDouble()
                } else {
                    1.0
                }

                // income
                val income: EnumMap<Material, Double> = EnumMap<Material, Double>(Material::class.java)
                val incomeSpawnEgg: EnumMap<EntityType, Double> = EnumMap<EntityType, Double>(EntityType::class.java)
                val nodeIncomeJson = node.get("income")?.getAsJsonObject()
                if ( nodeIncomeJson !== null ) {
                    nodeIncomeJson.keySet().forEach { type ->
                        val itemName = type.uppercase()

                        // spawn egg
                        if ( itemName.startsWith("SPAWN_EGG_")) {
                            val entityType = EntityType.valueOf(itemName.replace("SPAWN_EGG_", ""))
                            if ( entityType !== null ) {
                                incomeSpawnEgg.put(entityType, nodeIncomeJson.get(type).getAsDouble())
                            }
                        }
                        // regular material
                        else {
                            val material = Material.matchMaterial(type)
                            if ( material !== null ) {
                                income.put(material, nodeIncomeJson.get(type).getAsDouble())
                            }
                        }
                    }
                }

                // ore
                val ores: EnumMap<Material, OreDeposit> = EnumMap<Material, OreDeposit>(Material::class.java)
                val nodeOreJson = node.get("ore")?.getAsJsonObject()
                if ( nodeOreJson !== null ) {
                    nodeOreJson.keySet().forEach { type ->
                        val material = Material.matchMaterial(type)
                        if ( material !== null ) {
                            val oreData = nodeOreJson.get(type)

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
                }

                // crops
                val crops: EnumMap<Material, Double> = EnumMap<Material, Double>(Material::class.java)
                val nodeCropsJson = node.get("crops")?.getAsJsonObject()
                if ( nodeCropsJson !== null ) {
                    nodeCropsJson.keySet().forEach { type ->
                        val material = Material.matchMaterial(type)
                        if ( material !== null ) {
                            crops.put(material, nodeCropsJson.get(type).getAsDouble())
                        }
                    }
                }

                // animals
                val animals = EnumMap<EntityType, Double>(EntityType::class.java)
                val nodeAnimalsJson = node.get("animals")?.getAsJsonObject()
                if ( nodeAnimalsJson !== null ) {
                    nodeAnimalsJson.keySet().forEach { type ->
                        try {
                            val entityType = EntityType.valueOf(type.uppercase())
                            animals.put(entityType, nodeAnimalsJson.get(type).getAsDouble())
                        }
                        catch ( err: Exception ) {
                            err.printStackTrace()
                        }
                    }
                }

                //TODO: MAKE "ore" AN OREDEPOSIT
                Nodes.createResourceNode(name, icon, income, incomeSpawnEgg, ores, crops, animals, costConstant, costScale)
            }
        }
        
        // parse territories
        val jsonTerritories = jsonObj.get("territories")?.getAsJsonObject()
        if ( jsonTerritories !== null ) {

            jsonTerritories.keySet().forEach { idString -> 
                val territory = jsonTerritories[idString].getAsJsonObject()

                // territory id
                val id: Int = idString.toInt()

                // territory name
                val name: String = territory.get("name")?.getAsString() ?: "";

                // territory color, 6 possible colors -> integer in range [0, 5]
                // if null (editor error?), assign 5, the least likely color
                val color: Int = territory.get("color")?.getAsInt() ?: 5;

                // core chunk
                val coreChunkArray = territory.get("coreChunk")?.getAsJsonArray()
                val coreChunk = if ( coreChunkArray?.size() == 2 ) {
                    Coord(coreChunkArray[0].getAsInt(), coreChunkArray[1].getAsInt())
                } else {
                    // TODO: console warn
                    return@forEach // ignore territory, invalid core
                }

                // chunks:
                // parse interleaved coordinate buffer
                // [x1, y1, x2, y2, ... , xN, yN]
                val chunks: MutableList<Coord> = mutableListOf()
                val jsonChunkArray = territory.get("chunks")?.getAsJsonArray()
                if ( jsonChunkArray !== null ) {
                    for ( i in 0 until jsonChunkArray.size() step 2 ) {
                        val c = Coord(jsonChunkArray[i].getAsInt(), jsonChunkArray[i+1].getAsInt())
                        chunks.add(c)
                    }
                }

                // resource nodes
                val resourceNodes: MutableList<String> = mutableListOf()
                val jsonNodesArray = territory.get("nodes")?.getAsJsonArray()
                if ( jsonNodesArray !== null ) {
                    jsonNodesArray.forEach { nodeJson ->
                        val s = nodeJson.getAsString()
                        resourceNodes.add(s)
                    }
                }
                
                // neighbor territory ids
                val neighbors: MutableList<Int> = mutableListOf()
                val jsonNeighborsArray = territory.get("neighbors")?.getAsJsonArray()
                if ( jsonNeighborsArray !== null ) {
                    jsonNeighborsArray.forEach { neighborId ->
                        neighbors.add(neighborId.getAsInt())
                    }
                }

                // flag that territory borders wilderness (regions without any territories)
                val bordersWilderness: Boolean = territory.get("isEdge")?.getAsBoolean() ?: false

                Nodes.createTerritory(
                    id,
                    name,
                    color,
                    coreChunk,
                    chunks as ArrayList<Coord>,
                    bordersWilderness,
                    neighbors.toIntArray(),
                    resourceNodes as ArrayList<String>
                )
            }
        }

    }

    // import towns.json definition file
    // contains
    public fun townsFromJson(path: Path) {

        // list of towns, nations and relations, for post-process adding diplomacy
        val towns: ArrayList<Town> = ArrayList()
        val townAllies: ArrayList<ArrayList<String>> = ArrayList()
        val townEnemies: ArrayList<ArrayList<String>> = ArrayList()
        val nations: ArrayList<Nation> = ArrayList()
        val nationAllies: ArrayList<ArrayList<String>> = ArrayList()
        val nationEnemies: ArrayList<ArrayList<String>> = ArrayList()

        val json = JsonParser.parseReader(FileReader(path.toString()))
        val jsonObj = json.getAsJsonObject()
        
        // ===============================
        // Residents
        // ===============================
        val jsonResidents = jsonObj.get("residents")?.getAsJsonObject()
        if ( jsonResidents !== null ) {
            jsonResidents.keySet().forEach { uuid -> 
                val resident = jsonResidents[uuid].getAsJsonObject()

                // claims
                var claims: Int = 0
                var claimsTime: Long = 0
                val claimsJson = resident.get("claims")?.getAsJsonArray()
                if ( claimsJson !== null && claimsJson.size() == 2 ) {
                    claims = claimsJson[0].getAsInt()
                    claimsTime = claimsJson[1].getAsLong()
                }

                // prefix, suffix
                val prefix = resident.get("prefix")?.getAsString() ?: ""
                val suffix = resident.get("suffix")?.getAsString() ?: ""

                // trusted
                val trusted = resident.get("trust")?.getAsBoolean() ?: false

                // town create cooldown
                val townCreateCooldown: Long = resident.get("townCool")?.getAsLong() ?: 0L

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
        val jsonTowns = jsonObj.get("towns")?.getAsJsonObject()
        if ( jsonTowns !== null ) {
            jsonTowns.keySet().forEach { name -> 
                val town = jsonTowns[name].getAsJsonObject()

                // parse uuid
                val uuidJson = town.get("uuid")
                val uuid: UUID = if ( uuidJson !== null ) {
                    UUID.fromString(uuidJson.getAsString())
                }
                else {
                    UUID.randomUUID()
                }
                
                // get home territory id, if missing skip town
                val homeId = town.get("home")?.getAsInt()
                if ( homeId == null ) {
                    System.err.println("Cannot create ${name}: no home")
                    return@forEach
                }

                // parse leader uuid (may be null)
                val leaderJson = town.get("leader")
                val leader: UUID? = if ( leaderJson == null || leaderJson.isJsonNull() ) {
                    null
                } else {
                    UUID.fromString(leaderJson.getAsString())
                }

                // parse spawn location
                val spawnLocArray = town.get("spawn")?.getAsJsonArray()
                var spawn = if ( spawnLocArray !== null && spawnLocArray.size() == 3 ) {
                    Location(Bukkit.getWorlds().get(0), spawnLocArray[0].getAsDouble(), spawnLocArray[1].getAsDouble(), spawnLocArray[2].getAsDouble())
                } else {
                    null
                }

                // parse color
                val colorArray = town.get("color")?.getAsJsonArray()
                var color = if ( colorArray !== null && colorArray.size() == 3 ) {
                    Color(colorArray[0].getAsInt(), colorArray[1].getAsInt(), colorArray[2].getAsInt())
                } else {
                    null
                }

                // parse claimsBonus
                var claimsBonus = town.get("claimsBonus")?.getAsInt() ?: 0

                // parse claimsAnnexed penalty
                var claimsAnnexed = town.get("claimsAnnex")?.getAsInt() ?: 0

                // parse claimsPenalty
                var claimsPenalty: Int = 0
                var claimsPenaltyTime: Long = 0
                val claimsPenaltyJson = town.get("claimsPenalty")?.getAsJsonArray()
                if ( claimsPenaltyJson !== null && claimsPenaltyJson.size() == 2 ) {
                    claimsPenalty = claimsPenaltyJson[0].getAsInt()
                    claimsPenaltyTime = claimsPenaltyJson[1].getAsLong()
                }

                // parse residents
                val residentsUUID: ArrayList<UUID> = ArrayList()
                val residentsArray = town.get("residents")?.getAsJsonArray()
                if ( residentsArray !== null ) {
                    residentsArray.forEach { uuid ->
                        residentsUUID.add(UUID.fromString(uuid.getAsString()))
                    }
                }

                // parse officers
                val officersUUID: ArrayList<UUID> = ArrayList()
                val officersArray = town.get("officers")?.getAsJsonArray()
                if ( officersArray !== null ) {
                    officersArray.forEach { uuid ->
                        officersUUID.add(UUID.fromString(uuid.getAsString()))
                    }
                }

                // parse territories
                val territoryIds: ArrayList<Int> = ArrayList()
                val territoryArray = town.get("territories")?.getAsJsonArray()
                if ( territoryArray !== null ) {
                    territoryArray.forEach { id ->
                        territoryIds.add(id.getAsInt())
                    }
                }

                // parse captured territories
                val capturedIds: ArrayList<Int> = ArrayList()
                val capturedTerrArray = town.get("captured")?.getAsJsonArray()
                if ( capturedTerrArray !== null ) {
                    capturedTerrArray.forEach { id ->
                        capturedIds.add(id.getAsInt())
                    }
                }

                // parse annexed territories
                val annexedIds: ArrayList<Int> = ArrayList()
                val annexedTerrArray = town.get("annexed")?.getAsJsonArray()
                if ( annexedTerrArray !== null ) {
                    annexedTerrArray.forEach { id ->
                        annexedIds.add(id.getAsInt())
                    }
                }

                // parse stored income
                val income: EnumMap<Material, Int> = EnumMap<Material, Int>(Material::class.java)
                val townIncomeJson = town.get("income")?.getAsJsonObject()
                if ( townIncomeJson !== null ) {
                    townIncomeJson.keySet().forEach { type ->
                        val material = Material.matchMaterial(type)
                        if ( material !== null ) {
                            income.put(material, townIncomeJson.get(type).getAsInt())
                        }
                    }
                }

                // parse stored spawn egg income
                val incomeSpawnEgg: EnumMap<EntityType, Int> = EnumMap<EntityType, Int>(EntityType::class.java)
                val townIncomeSpawnEggJson = town.get("incomeEgg")?.getAsJsonObject()
                if ( townIncomeSpawnEggJson !== null ) {
                    townIncomeSpawnEggJson.keySet().forEach { type ->
                        val entityType = EntityType.valueOf(type)
                        if ( entityType !== null ) {
                            incomeSpawnEgg.put(entityType, townIncomeSpawnEggJson.get(type).getAsInt())
                        }
                    }
                }

                // parse ally names
                val allies: ArrayList<String> = ArrayList()
                val alliesArray = town.get("allies")?.getAsJsonArray()
                if ( alliesArray !== null ) {
                    alliesArray.forEach { name ->
                        allies.add(name.getAsString())
                    }
                }
                
                // parse enemy names
                val enemies: ArrayList<String> = ArrayList()
                val enemiesArray = town.get("enemies")?.getAsJsonArray()
                if ( enemiesArray !== null ) {
                    enemiesArray.forEach { name ->
                        enemies.add(name.getAsString())
                    }
                }

                // parse permissions
                val permissions: EnumMap<TownPermissions, EnumSet<PermissionsGroup>> = enumValues<TownPermissions>().toList().associateWithTo(
                    EnumMap<TownPermissions, EnumSet<PermissionsGroup>>(TownPermissions::class.java),
                    {_ -> EnumSet.noneOf(PermissionsGroup::class.java)}
                )
                val permissionsJson = town.get("perms")?.getAsJsonObject()
                if ( permissionsJson !== null ) {
                    permissionsJson.keySet().forEach { type ->
                        // get enum type
                        try {
                            val permType = TownPermissions.valueOf(type)
                            val permGroupList = permissionsJson.get(type)?.getAsJsonArray()
                            if ( permGroupList !== null ) {
                                for ( group in permGroupList ) {
                                    permissions.get(permType)!!.add(PermissionsGroup.values[group.getAsInt()]) 
                                }
                            }
                        }
                        catch ( err: IllegalArgumentException ) {
                            System.err.println("Invalid town permission: ${type}")
                        }
                    }
                }

                // parse town isOpen
                val isOpen: Boolean = town.get("open")?.getAsBoolean() ?: false
                
                // parse town protected blocks
                val protectedBlocks: HashSet<Block> = hashSetOf()
                val protectedBlocksJsonArray = town.get("protect")?.getAsJsonArray()
                if ( protectedBlocksJsonArray !== null ) {
                    val world = Bukkit.getWorlds().get(0)
                    for ( item in protectedBlocksJsonArray ) {
                        val blockArray = item.getAsJsonArray()
                        if ( blockArray !== null && blockArray.size() == 3 ) {
                            val x = blockArray[0].getAsInt()
                            val y = blockArray[1].getAsInt()
                            val z = blockArray[2].getAsInt()
                            val block = world.getBlockAt(x, y, z)
                            protectedBlocks.add(block)
                        }
                    }
                }

                // town move home cooldown
                val moveHomeCooldown: Long = town.get("homeCool")?.getAsLong() ?: 0L

                // parse town outposts "[terr.id, spawn.x, spawn.y, spawn.z]"
                val outposts: HashMap<String, Pair<Int, Location>> = hashMapOf()
                val outpostsJson = town.get("outpost")?.getAsJsonObject()
                if ( outpostsJson !== null ) {
                    outpostsJson.keySet().forEach { name ->
                        val dataArray = outpostsJson.get(name)?.getAsJsonArray()
                        if ( dataArray !== null && dataArray.size() == 4 ) {
                            val terrId = dataArray[0].getAsInt()
                            val spawnX = dataArray[1].getAsDouble()
                            val spawnY = dataArray[2].getAsDouble()
                            val spawnZ = dataArray[3].getAsDouble()
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
        val jsonNations = jsonObj.get("nations")?.getAsJsonObject()
        if ( jsonNations !== null ) {
            jsonNations.keySet().forEach { name -> 
                val nation = jsonNations[name].getAsJsonObject()

                // parse uuid
                val uuidJson = nation.get("uuid")
                val uuid: UUID = if ( uuidJson !== null ) {
                    UUID.fromString(uuidJson.getAsString())
                }
                else {
                    UUID.randomUUID()
                }

                // parse capital town name
                val capitalName = nation.get("capital").getAsString()

                // parse color
                val colorArray = nation.get("color")?.getAsJsonArray()
                var color = if ( colorArray !== null && colorArray.size() == 3 ) {
                    Color(colorArray[0].getAsInt(), colorArray[1].getAsInt(), colorArray[2].getAsInt())
                } else {
                    null
                }

                // parse towns
                val towns: ArrayList<String> = arrayListOf()
                val townsArray = nation.get("towns")?.getAsJsonArray()
                if ( townsArray !== null ) {
                    townsArray.forEach { townName ->
                        towns.add(townName.getAsString())
                    }
                }
                
                // parse ally names
                val allies: ArrayList<String> = ArrayList()
                val alliesArray = nation.get("allies")?.getAsJsonArray()
                if ( alliesArray !== null ) {
                    alliesArray.forEach { name ->
                        allies.add(name.getAsString())
                    }
                }
                
                // parse enemy names
                val enemies: ArrayList<String> = ArrayList()
                val enemiesArray = nation.get("enemies")?.getAsJsonArray()
                if ( enemiesArray !== null ) {
                    enemiesArray.forEach { name ->
                        enemies.add(name.getAsString())
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
            nations,
            nationAllies,
            nationEnemies
        )
    }
}
