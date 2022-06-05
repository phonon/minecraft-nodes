/**
 * Town outpost structure
 * Wrapper around territory with a name + spawnpoint
 */

package phonon.nodes.objects

import org.bukkit.Location

public data class TownOutpost(
    var name: String,
    val territory: TerritoryId,
    var spawn: Location
) {

    // serialize territory id + spawn location to json string
    public fun toJsonString(): String {
        return "[${territory.toInt()}, ${spawn.x}, ${spawn.y}, ${spawn.z}]"
    }
}