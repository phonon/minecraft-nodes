/**
 * Town outpost structure
 * Wrapper around territory with a name + spawnpoint
 */

package phonon.nodes.objects

import org.bukkit.Location

data class TownOutpost(
    var name: String,
    val territory: Territory,
    var spawn: Location
) {

    // serialize territory id + spawn location to json string
    fun toJsonString(): String {
        return "[${territory.id}, ${spawn.x}, ${spawn.y}, ${spawn.z}]"
    }
}