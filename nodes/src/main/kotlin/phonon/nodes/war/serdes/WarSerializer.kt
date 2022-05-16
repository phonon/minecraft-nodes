/**
 * Handle saving war state
 * JSON Format:
 * {
 *   "war": true,            // flag for war enabled/disabled
 *   "occupied": {           // chunks occupied by a town
 *     "town1": [            // town occupying a chunk
 *        0, 1,              // interleaved chunk buffer [x0, y0, x1, y1, ...]
 *        2, 3 ],
 *     "town2": [
 *        4, 5,
 *        6, 7 ]
 *   },
 *   "atttacks": [           // ongoing attacks
 *     {attackJsonObject0},
 *     {attackJsonObject1},
 *     ...
 *   ]
 * }
 */

package phonon.nodes.war

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.charset.CharsetEncoder
import java.nio.charset.StandardCharsets
import java.nio.CharBuffer
import java.nio.ByteBuffer
import java.nio.channels.*
import java.util.concurrent.Future
import kotlin.system.measureNanoTime
import org.bukkit.Bukkit
import phonon.nodes.Nodes
import phonon.nodes.Config
import phonon.nodes.war.FlagWar
import phonon.nodes.utils.estimateNumDigits

public object WarSerializer {

    // pre-processed state

    // occupied chunks for each territory in format:
    // town.name -> [c0.x, c0.y, c1.x, c1.y , ... ] interleaved buffer
    internal var occupiedChunks: HashMap<String, ArrayList<Int>> = hashMapOf()

    // list of all serialized attacks as Json
    internal val attacksJsonList: ArrayList<StringBuilder> = arrayListOf()

    // pre-process war objects
    public fun save(async: Boolean) {
        // val timePreprocess = measureNanoTime {

        // convert occupiedChunks to json string
        WarSerializer.occupiedChunks.clear()

        for ( coord in FlagWar.occupiedChunks ) {
            val chunk = Nodes.getTerritoryChunkFromCoord(coord)
            if ( chunk === null ) {
                continue
            }
            val town = chunk.occupier?.name
            if ( town != null ) {
                val coord = chunk.coord
                val cx = coord.x
                val cz = coord.z

                WarSerializer.occupiedChunks.get(town)?.let { chunkList -> 
                    chunkList.add(cx)
                    chunkList.add(cz)
                } ?: run {
                    WarSerializer.occupiedChunks.put(town, arrayListOf(cx, cz))
                }
            }
        }

        // update json strings for each attack
        WarSerializer.attacksJsonList.clear()
        for ( attack in FlagWar.chunkToAttacker.values ) {
            WarSerializer.attacksJsonList.add(attack.toJson())
        }
        
        // }

        // println("[WAR] PRE-PROCESS TIME: ${timePreprocess.toString()}ns")

        if ( async == true ) {
            // write file in worker thread
            Bukkit.getScheduler().runTaskAsynchronously(Nodes.plugin!!, object: Runnable {
                override public fun run() {
                    WarSerializer.writeToJson(Config.pathWar)
                }
            })
        }
        else {
            WarSerializer.writeToJson(Config.pathWar)
        }

    }

    // save war json file synchronously on main thread
    public fun writeToJson(path: Path) {
        
        // =============================================
        // calculate string builder capacity

        // war status [13]: {"war":false,
        // occupied header + close bracket + comma [14]: "occupied":{},
        // attacks header + close bracket [13]: "attacks":[]}
        // -> 40 minimum
        // will add arbitrary extra margin and up size to 60
        var bufferSize = 60

        // captured chunks format:
        // "town": [0, 1, 2, 3, ...]
        // -> get each integer size, then include brackets [] and commas ,
        for ( (townName, coordList) in WarSerializer.occupiedChunks ) {
            // size of "townName":[]
            bufferSize += (5 + townName.length + coordList.size)
            
            // size of each integer
            for ( c in coordList ) {
                val intLength = 2 + estimateNumDigits(c)
                bufferSize += intLength
            }
        }

        // list of attack json objects
        // add 1 to length to account for comma
        for ( s in WarSerializer.attacksJsonList) {
            bufferSize += (1 + s.length)
        }
        // =============================================

        // json string builder
        val jsonString = StringBuilder(bufferSize)

        var bytes: ByteBuffer = ByteBuffer.allocate(0)

        // val timeBuffers = measureNanoTime {
        
        // ===============================
        // War status and flags
        // ===============================
        jsonString.append("{\"war\":${FlagWar.enabled},")
        jsonString.append("\"flagAnnex\":${FlagWar.canAnnexTerritories},")
        jsonString.append("\"flagBordersOnly\":${FlagWar.canOnlyAttackBorders},")
        jsonString.append("\"flagDestruction\":${FlagWar.destructionEnabled},")

        // ===============================
        // Occupied chunks
        // ===============================
        jsonString.append("\"occupied\":{")

        var index = 1
        for ( (townName, coordList) in WarSerializer.occupiedChunks ) {
            jsonString.append("\"${townName}\":[")
            for ( (i, c) in coordList.withIndex() ) {
                jsonString.append(c)
                if ( i < coordList.size - 1 ) {
                    jsonString.append(",")
                }
            }

            // add comma
            if ( index < WarSerializer.occupiedChunks.size ) {
                jsonString.append("],")
                index += 1
            }
            // no comma for last, close with "},"
            else {
                jsonString.append("]")
            }
        }

        jsonString.append("},")

        // ===============================
        // Attacks
        // ===============================
        jsonString.append("\"attacks\":[")

        for ( (i, attack) in WarSerializer.attacksJsonList.iterator().withIndex() ) {
            jsonString.append(attack)

            // add comma
            if ( i < WarSerializer.attacksJsonList.size - 1 ) {
                jsonString.append(",")
            }
        }

        jsonString.append("]}")

        // ===============================

        // get byte buffer
        val encoder: CharsetEncoder = StandardCharsets.UTF_8.newEncoder()
        val charBuffer: CharBuffer = CharBuffer.wrap(jsonString)
        bytes = encoder.encode(charBuffer)

        // }

        // println("[WAR] BUFFER WRITE TIME: ${timeBuffers.toString()}ns")

        // ===============================
        // WRITE FILE
        // ===============================
        // val timeWrite = measureNanoTime {

        val fileChannel: AsynchronousFileChannel = AsynchronousFileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        
        val operation: Future<Int> = fileChannel.write(bytes, 0);
        
        operation.get()
        // }

        // println("[WAR] FILE SAVE TIME: ${timeWrite.toString()}ns")
    }
}