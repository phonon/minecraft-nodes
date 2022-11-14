/**
 * Utils for parsing collections to strings
 * 
 * Used by serializer
 * TODO: benchmark if string array funcs are necessary
 */

package phonon.nodes.utils.string

import org.bukkit.Bukkit
import phonon.nodes.Nodes
import phonon.nodes.objects.Town
import phonon.nodes.objects.Nation
import java.util.*

// serializer for a list type container
inline fun <T> stringArrayFromList(iter: List<T>, itemName: (T) -> String): String {
    var s = "["
    for ( (i, v) in iter.withIndex() ) {
        s += itemName(v)
        if ( i < iter.size - 1 ) {
            s += ","
        }
    }
    s += "]"

    return s
}

inline fun <T> stringArrayFromSet(iter: Set<T>, itemName: (T) -> String): String {
    var s = "["
    for ( (i, v) in iter.withIndex() ) {
        s += itemName(v)
        if ( i < iter.size - 1 ) {
            s += ","
        }
    }
    s += "]"

    return s
}

inline fun <K,V> stringMapFromMap(iter: Map<K,V>, keyString: (K) -> String, valString: (V) -> String ): String {
    var s = "{"
    for ( (i, entry) in iter.entries.withIndex() ) {
        val key = keyString(entry.key)
        val value = valString(entry.value)
        s += "${key}:${value}"
        if ( i < iter.size - 1 ) {
            s += ","
        }
    }
    s += "}"
    return s
}

// matches any string in a list of strings that 
// begins with start
fun filterByStart(list: List<String>, start: String): List<String> {
    val startLowerCase = start.lowercase(Locale.getDefault())
    return list.filter { s -> s.lowercase(Locale.getDefault()).startsWith(startLowerCase) }
}

// match player name from online players
fun filterPlayer(start: String): List<String> {
    val startLowerCase = start.lowercase(Locale.getDefault())
    val players = Bukkit.getOnlinePlayers()
    val filtered = players
        .asSequence()
        .map{ p -> p.name }
        .filter{ s -> s.lowercase(Locale.getDefault()).startsWith(startLowerCase) }
        .toList()
    
    return filtered
}

fun filterResident(start: String): List<String> {
    val startLowerCase = start.lowercase(Locale.getDefault())
    val filtered = Nodes.residents.values
        .asSequence()
        .map{ v -> v.name }
        .filter{ s -> s.lowercase(Locale.getDefault()).startsWith(startLowerCase) }
        .toList()
    
    return filtered
}

fun filterTown(start: String): List<String> {
    val startLowerCase = start.lowercase(Locale.getDefault())
    val filtered = Nodes.towns.values
        .asSequence()
        .map{ v -> v.name }
        .filter{ s -> s.lowercase(Locale.getDefault()).startsWith(startLowerCase) }
        .toList()
    
    return filtered
}

fun filterNation(start: String): List<String> {
    val startLowerCase = start.lowercase(Locale.getDefault())
    val filtered = Nodes.nations.values
        .asSequence()
        .map{ v -> v.name }
        .filter{ s -> s.lowercase(Locale.getDefault()).startsWith(startLowerCase) }
        .toList()
    
    return filtered
}

// filter both Towns and Nations by starting phrase
fun filterTownOrNation(start: String): List<String> {
    val startLowerCase = start.lowercase(Locale.getDefault())
    val filteredTowns = filterTown(start)
    val filteredNations = filterNation(start)
    
    return filteredTowns.plus(filteredNations)
}

// filter town residents names
fun filterTownResident(town: Town, start: String): List<String> {
    val startLowerCase = start.lowercase(Locale.getDefault())
    val filtered = town.residents
        .asSequence()
        .map{ v -> v.name }
        .filter{ s -> s.lowercase(Locale.getDefault()).startsWith(startLowerCase) }
        .toList()
    
    return filtered
}

// filter nation residents names
fun filterNationResident(nation: Nation, start: String): List<String> {
    val startLowerCase = start.lowercase(Locale.getDefault())
    val filtered = nation.residents
        .asSequence()
        .map{ v -> v.name }
        .filter{ s -> s.lowercase(Locale.getDefault()).startsWith(startLowerCase) }
        .toList()
    
    return filtered
}

// filter nation town names
fun filterNationTown(nation: Nation, start: String): List<String> {
    val startLowerCase = start.lowercase(Locale.getDefault())
    val filtered = nation.towns
        .asSequence()
        .map{ v -> v.name }
        .filter{ s -> s.lowercase(Locale.getDefault()).startsWith(startLowerCase) }
        .toList()
    
    return filtered
}