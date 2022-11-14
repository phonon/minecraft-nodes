/**
 * Truce storage
 * 
 * truce.json storage format:
 * Array of truce array objects: [town1, town2, count]
 * {
 *   "truce": [
 *     ["town1", "town2", 420],
 *     ["town3", "town4", 500]
 *   ]
 * }
 */

package phonon.nodes.war

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.scheduler.BukkitTask
import phonon.nodes.Nodes
import phonon.nodes.Message
import phonon.nodes.Config
import phonon.nodes.objects.Resident
import phonon.nodes.objects.Town
import phonon.nodes.objects.TownPair

object Truce {
    // truce map of (town1, town2) -> time ticked in current truce
    val truces: HashMap<TownPair, Long> = hashMapOf()

    // all truces for a given town, map Town -> TownPair that contains it
    val trucesByTown: HashMap<Town, HashSet<TownPair>> = hashMapOf()

    // indicator that truces changed and requires save
    var needsUpdate: Boolean = false

    // creates new truce between two towns
    fun create(town1: Town, town2: Town, startTime: Long) {
        
        val towns = TownPair(town1, town2)
        truces.put(towns, startTime)

        // put references from each town -> TownPair
        val town1Truces = trucesByTown.get(town1)
        if ( town1Truces !== null ) {
            town1Truces.add(towns)
        } else {
            trucesByTown.put(town1, hashSetOf(towns))
        }

        val town2Truces = trucesByTown.get(town2)
        if ( town2Truces !== null ) {
            town2Truces.add(towns)
        } else {
            trucesByTown.put(town2, hashSetOf(towns))
        }

        // mark save update needed
        needsUpdate = true
    }

    // removes truce between two towns
    fun remove(town1: Town, town2: Town) {
        val towns = TownPair(town1, town2)
        truces.remove(towns)

        // remove references from each town -> TownPair
        val town1Truces = trucesByTown.get(town1)
        if ( town1Truces !== null ) {
            town1Truces.remove(towns)
        }

        val town2Truces = trucesByTown.get(town2)
        if ( town2Truces !== null ) {
            town2Truces.remove(towns)
        }

        // mark save update needed
        needsUpdate = true
    }

    // returns true if truce exists between (town1, town2)
    fun contains(town1: Town, town2: Town): Boolean {
        return truces.contains(TownPair(town1, town2))
    }

    // get list of truces involving input town
    fun get(town: Town): List<TownPair> {
        val townTruces = trucesByTown.get(town)
        if ( townTruces !== null ) {
            return townTruces.toList()
        }

        return listOf()
    }

    // convert truces into string
    fun toJsonString(): String {
        val size = truces.size
        var i = 0
        
        val s = StringBuilder()
        s.append("{\"truce\":[")
        for ( (towns, count) in truces ) {
            s.append("[\"${towns.town1.name}\", \"${towns.town2.name}\", ${count}]")

            // add comma
            if ( i < size-1 ) {
                s.append(",")
                i += 1
            }
        }
        s.append("]}")

        return s.toString()
    }

    // parse truces from Json string
    fun fromJsonString(jsonString: String) {
        // clear current data
        truces.clear()
        trucesByTown.clear()

        val json = JsonParser.parseString(jsonString).asJsonObject

        val truceList = json.get("truce")?.asJsonArray
        if ( truceList !== null ) {
            for ( truceJson in truceList ) {
                val truce = truceJson.asJsonArray
                val town1Name: String = truce[0].asString
                val town2Name: String = truce[1].asString
                val startTime: Long = truce[2].asLong

                // get towns
                val town1 = Nodes.getTownFromName(town1Name)
                if ( town1 === null ) {
                    continue
                }

                val town2 = Nodes.getTownFromName(town2Name)
                if ( town2 === null ) {
                    continue
                }

                // set truce
                create(town1, town2, startTime)
            }
        }
    }
}