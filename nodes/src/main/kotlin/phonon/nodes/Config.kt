/**
 * Config
 * 
 * Contains global config state variables read in from 
 * plugin config.yml file
 */

package phonon.nodes

import java.nio.file.Paths
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.Material
import org.bukkit.entity.EntityType
import phonon.nodes.objects.TerritoryResources
import phonon.nodes.objects.OreDeposit
import java.util.*
import kotlin.collections.HashSet

object Config {

    // ===================================
    // engine configs
    // ===================================
    // main plugin path for config and saves 
    var pathPlugin = "plugins/nodes"
    
    // folder for backups of json state files
    var pathBackup = Paths.get("plugins/nodes/backup").normalize()
    
    // file names for world, towns, war json files
    var pathWorld = Paths.get(pathPlugin, "world.json").normalize()
    var pathTowns = Paths.get(pathPlugin, "towns.json").normalize()
    var pathWar = Paths.get(pathPlugin, "war.json").normalize()
    var pathTruce = Paths.get(pathPlugin, "truce.json").normalize()
    var pathLastBackupTime = Paths.get(pathPlugin, "lastBackupTime.txt").normalize()
    var pathLastIncomeTime = Paths.get(pathPlugin, "lastIncomeTime.txt").normalize()
    
    // disable world when nodes world.json or towns.json fails due to errors
    public var disableWorldWhenLoadFails = true

    // period for running world save
    var savePeriod: Long = 600L
    
    // all long tick cycle values
    // 1 hour = 3600000 ms
    // 2 hour = 7200000 ms
    var backupPeriod: Long = 3600000L // 1 hour

    // main tick period check for backup, income, town + resident cooldown counters
    var mainPeriodicTick: Long = 1200L

    // period to send reminder to players that town over max claims
    var overMaxClaimsReminderPeriod: Long = 24000L
    
    // nametag update period
    var nametagUpdatePeriod: Long = 80L
    var nametagPipelineTicks: Int = 16

    // force copy to dynmap folder
    var dynmapCopyTowns: Boolean = false

    // ===================================
    // nametag configs
    // ===================================
    var useNametags: Boolean = true

    // ===================================
    // resource configs
    // ===================================
    // territory income time
    var incomePeriod: Long = 3600000L

    // global resource node in all territories
    public var globalResources = TerritoryResources()

    // hidden ore blocks, stone only
    var oreBlocks = EnumSet.of(
        Material.STONE, // note: granite, diorite, and andesite are variants of stone in 1.12.2
        Material.ANDESITE,
        Material.GRANITE,
        Material.DIORITE
    )

    // harvestable crop types affected by occupied territory tax

    // version 1.12
    // public var cropTypes = EnumSet.of(
    //     Material.CROPS, // wheat
    //     Material.POTATO,
    //     Material.CARROT,
    //     Material.BEETROOT_BLOCK,
    //     Material.PUMPKIN,
    //     Material.MELON_BLOCK,
    //     Material.SUGAR_CANE_BLOCK,
    //     Material.COCOA,
    //     Material.CACTUS
    // )

    // version 1.16
    var cropTypes = EnumSet.of(
        Material.BEETROOTS,
        Material.CACTUS,
        Material.CARROTS,
        Material.KELP,
        Material.COCOA,
        Material.MELON,
        Material.NETHER_WART,
        Material.POTATOES,
        Material.PUMPKIN,
        Material.SUGAR_CANE,
        Material.SWEET_BERRY_BUSH,
        Material.WHEAT
    )

    // map block type -> alternative editor name
    var cropAlternativeNames: EnumMap<Material, Material> = {
        val alternativeNames: EnumMap<Material, Material> = EnumMap<Material, Material>(Material::class.java)
        
        alternativeNames.put(Material.MELON_STEM, Material.MELON)
        alternativeNames.put(Material.PUMPKIN_STEM, Material.PUMPKIN)
        alternativeNames.put(Material.SWEET_BERRY_BUSH, Material.SWEET_BERRIES)

        alternativeNames
    }()

    // allow mining/harvesting/breeding in unowned territories
    var allowOreInWilderness: Boolean = false
    var allowCropsInWilderness: Boolean = false
    var allowBreedingInWilderness: Boolean = false

    // allow getting ore in captured territory
    var allowOreInCaptured: Boolean = true

    // allow mining in other towns in nation
    var allowOreInNationTowns: Boolean = true

    // required sky light for crops and animal breeding (set = 0 to disable)
    var cropsMinSkyLight: Int = 14
    var breedingMinSkyLight: Int = 14

    // min/max y levels allowed for crop growth and animal breeding
    var cropsMinYHeight: Int = 10
    var cropsMaxYHeight: Int = 255
    var breedingMinYHeight: Int = 10
    var breedingMaxYHeight: Int = 255
    
    // ===================================
    // afk kick time
    // ===================================
    // time in milliseconds for afk time before player kicked
    // adjusts claims progress as penalty
    // 10 min = 600000 ms
    // 20 min = 1200000 ms
    var afkKickTime: Long = 900000L

    // ===================================
    // permissions
    // ===================================
    // interact in area with NO TERRITORIES (build, destroy, etc...)
    var canInteractInEmpty: Boolean = false

    // interact in territory without town (build, destroy, etc...)
    var canInteractInUnclaimed: Boolean = true

    // only allow shearing in sheep node
    var requireSheepNodeToShear: Boolean = true

    // ===================================
    // town cooldowns
    // ===================================
    // 24 hour = 86400000 ms
    // 48 hour = 172800000 ms
    // 72 hour = 259200000 ms
    var townCreateCooldown: Long = 172800000L

    var townMoveHomeCooldown: Long = 172800000L

    // ===================================
    // town claim configs
    // ===================================
    // territory cost = base + scale * chunks
    var territoryCostBase: Int = 10
    var territoryCostScale: Double = 0.25

    // initial player given on town creation
    var townInitialClaims: Int = 25

    // penalty scale factor when over initial claim allowed:
    // penalty = scale * (territory.cost - initialAllowed) if cost > allowed
    var initialOverClaimsAmountScale: Int = 2

    // base number of claim power per town
    var townClaimsBase: Int = 20

    // max claim power per town (-1 for unlimited)
    var townClaimsMax: Int = -1

    // town penalty decay amount
    var townPenaltyDecay: Int = 2

    // claim power per player
    var playerClaimsInitial: Int = 0     // initial player claims on town join
    var playerClaimsMax: Int = 20        // max player claims for town
    var playerClaimsIncrease: Int = 1    // claims increase per tick period
    
    // time periods for town claims penalty decay and player power gain, milliseconds
    // 1 hour = 3600000 ms
    // 2 hour = 7200000 ms
    var townClaimsPenaltyDecayPeriod: Long = 3600000L
    var playerClaimsIncreasePeriod: Long = 3600000L

    // reduced resource rate when over max claim (runs Math.random() < rate)
    var overClaimsMaxPenalty: Double = 0.5

    // annexation settings
    // only allow annexing during war time
    var canOnlyAnnexDuringWar: Boolean = true

    // ===================================
    // town settings
    // ===================================
    // town spawn timer in seconds (converted to ticks by num * 20)
    var townSpawnTime: Int = 10

    // outpost configs
    val outpostTeleportCost: EnumMap<Material, Int> = EnumMap<Material, Int>(Material::class.java)

    // inline string list of outpost teleport cost
    var outpostTeleportCostString: String = ""

    // ===================================
    // nation settings
    // ===================================
    // allow spawning in nation towns
    var allowNationTownSpawn: Boolean = false

    // cost for nation town spawn
    val nationTownTeleportCost: EnumMap<Material, Int> = EnumMap<Material, Int>(Material::class.java)

    // inline string list of outpost teleport cost
    var nationTownTeleportCostString: String = ""

    // ===================================
    // captured territory tax rates:
    // (taxation is theft)
    // ===================================
    // fraction of territory income that goes to occupier
    var taxIncomeRate: Double = 0.2

    // probability that a hidden ore event resources go to occupier
    var taxMineRate: Double = 0.2

    // probability that all of a crop's harvest items go to occupier
    var taxFarmRate: Double = 0.2

    // probability that a new animal goes to occupier (as a spawn egg item)
    var taxAnimalRate: Double = 0.2

    // ===================================
    // war configs
    // ===================================
    // Nodes internal explosion block damage restrictions
    var restrictExplosions: Boolean = true
    var onlyAllowExplosionsDuringWar: Boolean = true

    var flagMaterialDefault: Material = Material.OAK_FENCE
    
    var flagMaterials: EnumSet<Material> = EnumSet.of(
        Material.ACACIA_FENCE,
        Material.BIRCH_FENCE,
        Material.DARK_OAK_FENCE,
        Material.JUNGLE_FENCE,
        Material.OAK_FENCE,
        Material.SPRUCE_FENCE
        // Material.FENCE, // 1.12 only
    )

    // disable building within this distance of flag (square range)
    var flagNoBuildDistance: Int = 1

    // disable building for y > flag base block + flagNoBuildYOffset
    var flagNoBuildYOffset: Int = -1
    
    // ticks required to capture chunk
    var chunkAttackTime: Long = 200

    // multiplier for chunk attacks
    var chunkAttackFromWastelandMultiplier: Double = 2.0 // territory next to wilderness
    var chunkAttackHomeMultiplier: Double = 2.0       // in home territory

    // number of chunks a player can attack at same time
    var maxPlayerChunkAttacks: Int = 1

    // flag sky beacon config
    var flagBeaconSize: Int = 6 // be in range [2, 16]
    var flagBeaconMinSkyLevel: Int = 100 // minimum height level in sky
    var flagBeaconSkyLevel: Int = 50    // height level above blocks

    // allow war permissions during skirmish mode
    var allowDestructionDuringSkirmish: Boolean = false

    // bypass permissions and allow extended ally interactions in towns
    var warPermissions: Boolean = true

    // allow leaving towns/natiosn during war
    var canLeaveTownDuringWar: Boolean = true

    // allow creating towns, nation stuff during war
    var canCreateTownDuringWar: Boolean = false
    var canDestroyTownDuringWar: Boolean = false
    var canLeaveNationDuringWar: Boolean = false

    // global disable annexing
    var annexDisabled: Boolean = false

    // use whitelist/blacklist for war (derived from list.size > 0 for lists below)
    var warUseWhitelist: Boolean = false
    var warUseBlacklist: Boolean = false
    
    // war whitelist: only allow attacking these town UUIDs
    var warWhitelist: HashSet<UUID> = hashSetOf()

    // war blacklist: disable attacking these town UUIDs
    var warBlacklist: HashSet<UUID> = hashSetOf()

    // annex blacklist: cannot annex these towns, only occupy
    var annexBlacklist: HashSet<UUID> = hashSetOf()
    var useAnnexBlacklist: Boolean = false

    // whitelist settings

    // only towns in whitelist can annex territories (from other whitelist territories)
    var onlyWhitelistCanAnnex: Boolean = true
    var onlyWhitelistCanClaim: Boolean = true

    // multiplier for warping home when occupied
    var occupiedHomeTeleportMultiplier: Double = 12.0

    // List of town UUIDs to allow building in occupied territory.
    // War whitelist often used to create AI towns that can be attacked
    // by anyone. People want to build in occupied territory from these
    // towns during non-war time. This list allows building/interacting
    // in these occupied towns.
    public var allowControlInOccupiedTownList: HashSet<UUID> = hashSetOf()

    // ===================================
    // truce configs
    // ===================================

    // truce period in milliseconds
    // (truce counter uses System.currentTimeMillis)
    // 24 hour = 86400000 ms
    // 48 hour = 172800000 ms
    // 72 hour = 259200000 ms
    var trucePeriod: Long = 172800000L

    
    // ===================================================
    // Load config
    // ===================================================
    fun load(config: FileConfiguration) {

        // engine settings
        Config.disableWorldWhenLoadFails = config.getBoolean("disableWorldWhenLoadFails", Config.disableWorldWhenLoadFails)
        Config.savePeriod = config.getLong("savePeriod", Config.savePeriod)
        Config.backupPeriod = config.getLong("backupPeriod", Config.backupPeriod)
        Config.mainPeriodicTick = config.getLong("mainPeriodicTick", Config.mainPeriodicTick)
        Config.overMaxClaimsReminderPeriod = config.getLong("overMaxClaimsReminderPeriod", Config.overMaxClaimsReminderPeriod)
        Config.nametagUpdatePeriod = config.getLong("nametagUpdatePeriod", Config.nametagUpdatePeriod)
        Config.nametagPipelineTicks = config.getInt("nametagPipelineTicks", Config.nametagPipelineTicks)
        Config.dynmapCopyTowns = config.getBoolean("dynmapCopyTowns", Config.dynmapCopyTowns)

        // use internal nametag system
        useNametags = config.getBoolean("useNametags", useNametags)

        // afk kick time
        afkKickTime = config.getLong("afkKickTime", afkKickTime)
        
        // generic permissions
        canInteractInEmpty = config.getBoolean("canInteractInEmpty", canInteractInEmpty)
        canInteractInUnclaimed = config.getBoolean("canInteractInUnclaimed", canInteractInUnclaimed)
        requireSheepNodeToShear = config.getBoolean("requireSheepNodeToShear", requireSheepNodeToShear)

        // town cooldown configs
        townCreateCooldown = config.getLong("townCreateCooldown", townCreateCooldown)
        townMoveHomeCooldown = config.getLong("townMoveHomeCooldown", townMoveHomeCooldown)

        // resource configs
        incomePeriod = config.getLong("incomePeriod", incomePeriod)
        allowOreInWilderness = config.getBoolean("allowOreInWilderness", allowOreInWilderness)
        allowCropsInWilderness = config.getBoolean("allowCropsInWilderness", allowCropsInWilderness)
        allowBreedingInWilderness = config.getBoolean("allowBreedingInWilderness", allowBreedingInWilderness)
        allowOreInCaptured = config.getBoolean("allowOreInCaptured", allowOreInCaptured)
        allowOreInNationTowns = config.getBoolean("allowOreInNationTowns", allowOreInNationTowns)
        cropsMinSkyLight = config.getInt("cropsMinSkyLight", cropsMinSkyLight)
        breedingMinSkyLight = config.getInt("breedingMinSkyLight", breedingMinSkyLight)
        cropsMinYHeight = config.getInt("cropsMinYHeight", cropsMinYHeight)
        cropsMaxYHeight = config.getInt("cropsMaxYHeight", cropsMaxYHeight)
        breedingMinYHeight = config.getInt("breedingMinYHeight", breedingMinYHeight)
        breedingMaxYHeight = config.getInt("breedingMaxYHeight", breedingMaxYHeight)

        // global resources in all territories
        val globalResourcesSection = config.getConfigurationSection("globalResources")
        if ( globalResourcesSection !== null ) {
            globalResources = parseGlobalResources(globalResourcesSection)
        }

        // town claims
        territoryCostBase = config.getInt("territoryCostBase", territoryCostBase)
        territoryCostScale = config.getDouble("territoryCostScale", territoryCostScale)
        townInitialClaims = config.getInt("townInitialClaims", townInitialClaims)
        initialOverClaimsAmountScale = config.getInt("initialOverClaimsAmountScale", initialOverClaimsAmountScale)
        townClaimsBase = config.getInt("townClaimsBase", townClaimsBase)
        townClaimsMax = config.getInt("townClaimsMax", townClaimsMax)
        
        playerClaimsInitial = config.getInt("playerClaimsInitial", playerClaimsInitial)
        playerClaimsMax = config.getInt("playerClaimsMax", playerClaimsMax)
        playerClaimsIncrease = config.getInt("playerClaimsIncrease", playerClaimsIncrease)
        
        townPenaltyDecay = config.getInt("townPenaltyDecay", townPenaltyDecay)
        townClaimsPenaltyDecayPeriod = config.getLong("townClaimsPenaltyDecayPeriod", townClaimsPenaltyDecayPeriod)
        playerClaimsIncreasePeriod = config.getLong("playerClaimsIncreasePeriod", playerClaimsIncreasePeriod)
        overClaimsMaxPenalty = config.getDouble("overClaimsMaxPenalty", overClaimsMaxPenalty)

        canOnlyAnnexDuringWar = config.getBoolean("canOnlyAnnexDuringWar", canOnlyAnnexDuringWar)

        // town settings
        townSpawnTime = config.getInt("townSpawnTime", townSpawnTime)
        val outpostTeleportCostSection = config.getConfigurationSection("outpostTeleportCost")
        if ( outpostTeleportCostSection !== null ) {
            outpostTeleportCost.putAll(parseTeleportCost(outpostTeleportCostSection))
            outpostTeleportCostString = teleportCostToString(outpostTeleportCost)
        }

        // nation settings
        allowNationTownSpawn = config.getBoolean("allowNationTownSpawn", allowNationTownSpawn)
        val nationTeleportCostSection = config.getConfigurationSection("nationTownTeleportCost")
        if ( nationTeleportCostSection !== null ) {
            nationTownTeleportCost.putAll(parseTeleportCost(nationTeleportCostSection))
            nationTownTeleportCostString = teleportCostToString(nationTownTeleportCost)
        }

        // tax
        taxIncomeRate = config.getDouble("taxIncomeRate", taxIncomeRate)
        taxMineRate = config.getDouble("taxMineRate", taxMineRate)
        taxFarmRate = config.getDouble("taxFarmRate", taxFarmRate)
        taxAnimalRate = config.getDouble("taxAnimalRate", taxAnimalRate)

        // ======================
        // war
        // ======================
        restrictExplosions = config.getBoolean("restrictExplosions", restrictExplosions)
        onlyAllowExplosionsDuringWar = config.getBoolean("onlyAllowExplosionsDuringWar", onlyAllowExplosionsDuringWar)
        flagNoBuildDistance = config.getInt("flagNoBuildDistance", flagNoBuildDistance)
        flagNoBuildYOffset = config.getInt("flagNoBuildYOffset", flagNoBuildYOffset)
        chunkAttackTime = config.getLong("chunkAttackTime", chunkAttackTime)
        chunkAttackFromWastelandMultiplier = config.getDouble("chunkAttackFromWastelandMultiplier", chunkAttackFromWastelandMultiplier)
        chunkAttackHomeMultiplier = config.getDouble("chunkAttackHomeMultiplier", chunkAttackHomeMultiplier)
        maxPlayerChunkAttacks = config.getInt("maxPlayerChunkAttacks", maxPlayerChunkAttacks)
        flagBeaconSize = config.getInt("flagBeaconSize", flagBeaconSize)
        flagBeaconMinSkyLevel = config.getInt("flagBeaconMinSkyLevel", flagBeaconMinSkyLevel)
        flagBeaconSkyLevel = config.getInt("flagBeaconSkyLevel", flagBeaconSkyLevel)
        onlyAllowExplosionsDuringWar = config.getBoolean("onlyAllowExplosionsDuringWar", onlyAllowExplosionsDuringWar)
        warPermissions = config.getBoolean("warPermissions", warPermissions)

        allowDestructionDuringSkirmish = config.getBoolean("allowDestructionDuringSkirmish", allowDestructionDuringSkirmish)
        canLeaveTownDuringWar = config.getBoolean("canLeaveTownDuringWar", canLeaveTownDuringWar)
        canCreateTownDuringWar = config.getBoolean("canCreateTownDuringWar", canCreateTownDuringWar)
        canDestroyTownDuringWar = config.getBoolean("canDestroyTownDuringWar", canDestroyTownDuringWar)
        canLeaveNationDuringWar = config.getBoolean("canLeaveNationDuringWar", canLeaveNationDuringWar)
        annexDisabled = config.getBoolean("annexDisabled", annexDisabled)

        Config.warWhitelist = parseUUIDSet(config, "warWhitelist")
        Config.warUseWhitelist = Config.warWhitelist.size > 0
        Config.warBlacklist = parseUUIDSet(config, "warBlacklist")
        Config.warUseBlacklist = Config.warBlacklist.size > 0
        Config.annexBlacklist = parseUUIDSet(config, "annexBlacklist")
        Config.useAnnexBlacklist = Config.annexBlacklist.size > 0
        
        Config.onlyWhitelistCanAnnex = config.getBoolean("onlyWhitelistCanAnnex", Config.onlyWhitelistCanAnnex)
        Config.onlyWhitelistCanClaim = config.getBoolean("onlyWhitelistCanClaim", Config.onlyWhitelistCanClaim)
        
        Config.occupiedHomeTeleportMultiplier = config.getDouble("occupiedHomeTeleportMultiplier", Config.occupiedHomeTeleportMultiplier)

        Config.allowControlInOccupiedTownList = parseUUIDSet(config, "allowControlInOccupiedTownList")
        // ======================

        // truce
        trucePeriod = config.getLong("trucePeriod", trucePeriod)
    }
}

// parse global resources section in config.yml
private fun parseGlobalResources(globalResourcesSection: ConfigurationSection): TerritoryResources {
    val income: EnumMap<Material, Double> = EnumMap<Material, Double>(Material::class.java)
    val incomeSpawnEgg: EnumMap<EntityType, Double> = EnumMap<EntityType, Double>(EntityType::class.java)
    val ores: EnumMap<Material, OreDeposit> = EnumMap<Material, OreDeposit>(Material::class.java)
    val crops: EnumMap<Material, Double> = EnumMap<Material, Double>(Material::class.java)
    val animals: EnumMap<EntityType, Double> = EnumMap<EntityType, Double>(EntityType::class.java)

    globalResourcesSection.getConfigurationSection("income")?.let { section ->
        for ( item in section.getKeys(false) ) {
            val itemName = item.uppercase()
            // spawn egg
            if ( itemName.startsWith("SPAWN_EGG_")) {
                val entityType = EntityType.valueOf(itemName.replace("SPAWN_EGG_", ""))
                incomeSpawnEgg[entityType] = section.getDouble(item);
            }
            // regular material
            else {
                val material = Material.matchMaterial(item)
                if ( material !== null ) {
                    income.put(material, section.getDouble(item))
                }
            }
        }
    }
    
    globalResourcesSection.getConfigurationSection("ore")?.let{ section ->
        for ( item in section.getKeys(false) ) {
            val material = Material.matchMaterial(item)
            if ( material !== null ) {
                // list format [chance, min, max]
                if ( section.isList(item) ) {
                    val list = section.getDoubleList(item)
                    if ( list.size == 3 ) {
                        val chance = list[0]
                        val min = list[1].toInt()
                        val max = list[2].toInt()
                        ores.put(material, OreDeposit(material, chance, min, max))
                    }
                }
                // single chance number (implicit min = max = 1)
                else {
                    val chance = section.getDouble(item)
                    ores.put(material, OreDeposit(material, chance, 1, 1))
                }
            }
        }
    }
    
    globalResourcesSection.getConfigurationSection("crops")?.let { section ->
        for ( item in section.getKeys(false) ) {
            val material = Material.matchMaterial(item)
            if ( material !== null ) {
                crops.put(material, section.getDouble(item))
            }
        }
    }
    
    globalResourcesSection.getConfigurationSection("animals")?.let { section -> 
        for ( item in section.getKeys(false) ) {
            try {
                val entityType = EntityType.valueOf(item.uppercase())
                animals.put(entityType, section.getDouble(item))
            }
            catch ( err: Exception ) {
                err.printStackTrace()
            }
        }
    }

    return TerritoryResources(
        income = income,
        incomeSpawnEgg = incomeSpawnEgg,
        ores = ores,
        crops = crops,
        animals = animals,
    )
}

// parse teleport material cost list:
// material: amount
private fun parseTeleportCost(section: ConfigurationSection): EnumMap<Material, Int> {
    val materials: EnumMap<Material, Int> = EnumMap<Material, Int>(Material::class.java)

    for ( item in section.getKeys(false) ) {
        val material = Material.matchMaterial(item)
        if ( material !== null ) {
            materials.put(material, section.getInt(item))
        }
    }

    return materials
}

// string format of teleport cost item list
private fun teleportCostToString(materials: EnumMap<Material, Int>): String {
    var s = ""

    var index = 0
    for ( (mat, amount) in materials ) {

        s += "${amount} ${mat}"

        if ( index < materials.size - 1 ) {
            s += ", "
        }

        index += 1
    }

    return s
}

/**
 * Load yaml uuid string list into a hashset of uuid
 */
private fun parseUUIDSet(config: ConfigurationSection, listName: String): HashSet<UUID> {
    val uuids: HashSet<UUID> = hashSetOf()

    if ( config.isList(listName) ) {
        val uuidList = config.getStringList(listName)
        for ( uuidString in uuidList ) {
            val uuid = try {
                UUID.fromString(uuidString)
            } catch ( err: Exception ) {
                System.err.println("[Config] Invalid UUID: $uuidString")
                continue
            }

            uuids.add(uuid)
        }
    }

    return uuids
}