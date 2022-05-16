/**
 * TerritoryChunk
 * -----------------------------
 * Wrapper around Minecraft chunk, basic unit in a territory
 * with custom flags
 */

package phonon.nodes.objects

data class TerritoryChunk(
    val coord: Coord,
    val territory: Territory,

    // flags for war
    var attacker: Town? = null,         // town currently attacking chunk
    var occupier: Town? = null          // town occupying chunk
)