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

public object Truce {
    // truce map of (town1, town2) -> time ticked in current truce
    public val truces: HashMap<TownPair, Long> = hashMapOf()

    // all truces for a given town, map Town -> TownPair that contains it
    public val trucesByTown: HashMap<Town, HashSet<TownPair>> = hashMapOf()

    // indicator that truces changed and requires save
    public var needsUpdate: Boolean = false

    // creates new truce between two towns
    public fun create(town1: Town, town2: Town, startTime: Long) {
        
        val towns = TownPair(town1, town2)
        Truce.truces.put(towns, startTime)

        // put references from each town -> TownPair
        val town1Truces = Truce.trucesByTown.get(town1)
        if ( town1Truces !== null ) {
            town1Truces.add(towns)
        } else {
            Truce.trucesByTown.put(town1, hashSetOf(towns))
        }

        val town2Truces = Truce.trucesByTown.get(town2)
        if ( town2Truces !== null ) {
            town2Truces.add(towns)
        } else {
            Truce.trucesByTown.put(town2, hashSetOf(towns))
        }

        // mark save update needed
        Truce.needsUpdate = true
    }

    // removes truce between two towns
    public fun remove(town1: Town, town2: Town) {
        val towns = TownPair(town1, town2)
        Truce.truces.remove(towns)

        // remove references from each town -> TownPair
        val town1Truces = Truce.trucesByTown.get(town1)
        if ( town1Truces !== null ) {
            town1Truces.remove(towns)
        }

        val town2Truces = Truce.trucesByTown.get(town2)
        if ( town2Truces !== null ) {
            town2Truces.remove(towns)
        }

        // mark save update needed
        Truce.needsUpdate = true
    }

    // returns true if truce exists between (town1, town2)
    public fun contains(town1: Town, town2: Town): Boolean {
        return Truce.truces.contains(TownPair(town1, town2))
    }

    // get list of truces involving input town
    public fun get(town: Town): List<TownPair> {
        val townTruces = Truce.trucesByTown.get(town)
        if ( townTruces !== null ) {
            return townTruces.toList()
        }

        return listOf()
    }

    // convert truces into string
    public fun toJsonString(): String {
        val size = truces.size
        var i = 0
        
        val s = StringBuilder()
        s.append("{\"truce\":[")
        for ( (towns, count) in Truce.truces ) {
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
    public fun fromJsonString(jsonString: String) {
        // clear current data
        Truce.truces.clear()
        Truce.trucesByTown.clear()

        val json = JsonParser().parse(jsonString).getAsJsonObject()

        val truceList = json.get("truce")?.getAsJsonArray()
        if ( truceList !== null ) {
            for ( truceJson in truceList ) {
                val truce = truceJson.getAsJsonArray()
                val town1Name: String = truce[0].getAsString()
                val town2Name: String = truce[1].getAsString()
                val startTime: Long = truce[2].getAsLong()

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
                Truce.create(town1, town2, startTime)
            }
        }
    }
}