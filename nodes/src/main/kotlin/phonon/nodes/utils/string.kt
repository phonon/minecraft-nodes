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
public fun filterByStart(list: List<String>, start: String): List<String> {
    val startLowerCase = start.lowercase()
    return list.filter { s -> s.lowercase().startsWith(startLowerCase) }
}

// match player name from online players
public fun filterPlayer(start: String): List<String> {
    val startLowerCase = start.lowercase()
    val players = Bukkit.getOnlinePlayers()
    val filtered = players
        .asSequence()
        .map{ p -> p.name }
        .filter{ s -> s.lowercase().startsWith(startLowerCase) }
        .toList()
    
    return filtered
}

public fun filterResident(start: String): List<String> {
    val startLowerCase = start.lowercase()
    val filtered = Nodes.residents.values
        .asSequence()
        .map{ v -> v.name }
        .filter{ s -> s.lowercase().startsWith(startLowerCase) }
        .toList()
    
    return filtered
}

public fun filterTown(start: String): List<String> {
    val startLowerCase = start.lowercase()
    val filtered = Nodes.towns.values
        .asSequence()
        .map{ v -> v.name }
        .filter{ s -> s.lowercase().startsWith(startLowerCase) }
        .toList()
    
    return filtered
}

public fun filterNation(start: String): List<String> {
    val startLowerCase = start.lowercase()
    val filtered = Nodes.nations.values
        .asSequence()
        .map{ v -> v.name }
        .filter{ s -> s.lowercase().startsWith(startLowerCase) }
        .toList()
    
    return filtered
}

// filter both Towns and Nations by starting phrase
public fun filterTownOrNation(start: String): List<String> {
    val filteredTowns = filterTown(start)
    val filteredNations = filterNation(start)
    
    return filteredTowns.plus(filteredNations)
}

// filter town residents names
public fun filterTownResident(town: Town, start: String): List<String> {
    val startLowerCase = start.lowercase()
    val filtered = town.residents
        .asSequence()
        .map{ v -> v.name }
        .filter{ s -> s.lowercase().startsWith(startLowerCase) }
        .toList()
    
    return filtered
}

// filter nation residents names
public fun filterNationResident(nation: Nation, start: String): List<String> {
    val startLowerCase = start.lowercase()
    val filtered = nation.residents
        .asSequence()
        .map{ v -> v.name }
        .filter{ s -> s.lowercase().startsWith(startLowerCase) }
        .toList()
    
    return filtered
}

// filter nation town names
public fun filterNationTown(nation: Nation, start: String): List<String> {
    val startLowerCase = start.lowercase()
    val filtered = nation.towns
        .asSequence()
        .map{ v -> v.name }
        .filter{ s -> s.lowercase().startsWith(startLowerCase) }
        .toList()
    
    return filtered
}