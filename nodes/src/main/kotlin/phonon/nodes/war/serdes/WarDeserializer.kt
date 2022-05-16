/**
 * Load war state from war.json format
 * See WarSerializer.kt for format
 */

package phonon.nodes.war


import java.io.FileReader
import java.util.EnumMap
import java.util.UUID
import java.nio.file.Path
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.bukkit.Bukkit
import org.bukkit.block.Block
import phonon.nodes.Nodes
import phonon.nodes.objects.Coord
import phonon.nodes.objects.Town
import phonon.nodes.objects.Nation

public object WarDeserializer {

    // parse war.json data file
    public fun fromJson(path: Path) {
        // val json = JsonParser.parseReader(FileReader(path.toString())) // newer gson
        val json = JsonParser().parse(FileReader(path.toString()))        // gson bundled in bukkit/spigot
        val jsonObj = json.getAsJsonObject()

        // parse war state and flags
        val warStatus = jsonObj.get("war")?.getAsBoolean() ?: false
        if ( warStatus == null || warStatus == false ) {
            return
        }
        
        // parse war flags
        val canAnnexTerritories = jsonObj.get("flagAnnex")?.getAsBoolean() ?: true
        val canOnlyAttackBorders = jsonObj.get("flagBordersOnly")?.getAsBoolean() ?: false
        val destructionEnabled = jsonObj.get("flagDestruction")?.getAsBoolean() ?: true

        // war enabled, parse full state
        Nodes.enableWar(canAnnexTerritories, canOnlyAttackBorders, destructionEnabled)

        // ===============================
        // Occupied chunks
        // ===============================
        val jsonOccupiedChunks = jsonObj.get("occupied")?.getAsJsonObject()
        if ( jsonOccupiedChunks !== null ) {
            for ( townName in jsonOccupiedChunks.keySet() ) {

                val chunkList = jsonOccupiedChunks[townName].getAsJsonArray()
                for ( i in 0 until chunkList.size() step 2) {
                    val cx = chunkList[i].getAsInt()
                    val cz = chunkList[i+1].getAsInt()
                    val coord = Coord(cx, cz)

                    FlagWar.loadOccupiedChunk(townName, coord)
                }

            }
        }

        // ===============================
        // Attacks
        // ===============================
        val jsonAttackList = jsonObj.get("attacks")?.getAsJsonArray()
        if ( jsonAttackList !== null ) {
            for ( jsonAttack in jsonAttackList ) {
                val attack = jsonAttack.getAsJsonObject()

                // parse attacker player uuid
                val uuidJson = attack.get("id")
                if ( uuidJson == null ) {
                    break
                }
                val uuid: UUID = UUID.fromString(uuidJson.getAsString())

                // parse attack coord
                val coordJson = attack.get("c")?.getAsJsonArray()
                if ( coordJson == null ) {
                    break
                }
                val coord: Coord = Coord(coordJson[0].getAsInt(), coordJson[1].getAsInt())

                // parse attack flagBase block
                val blockJson = attack.get("b")?.getAsJsonArray()
                if ( blockJson == null ) {
                    break
                }
                val world = Bukkit.getWorlds().get(0)
                val flagBase: Block = world.getBlockAt(
                    blockJson[0].getAsInt(),
                    blockJson[1].getAsInt(),
                    blockJson[2].getAsInt()
                )
                
                // parse progress
                val progress = attack.get("p")?.getAsLong()
                if ( progress == null ) {
                    break
                }
                
                FlagWar.loadAttack(
                    uuid,
                    coord,
                    flagBase,
                    progress
                )
            }
        }

    }

}
