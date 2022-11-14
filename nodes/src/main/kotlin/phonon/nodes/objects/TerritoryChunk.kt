package phonon.nodes.objects

/**
 * TerritoryChunk
 * 
 * Wrapper around Minecraft chunk, basic unit in a territory.
 * This allows easier mapping from chunk to Territory object.
 * And it handles references to attack and occupier during war.
 * This uses direct Territory reference because it's a common
 * operation to map from chunk to territory. Note since this
 * contains reference to Territory, this must be kept in sync
 * with Territory objects when they are swapped in and out of
 * the world during runtime.
 */
data class TerritoryChunk(
    val coord: Coord,
    val territory: Territory,

    // flags for war
    var attacker: Town? = null,         // town currently attacking chunk
    var occupier: Town? = null          // town occupying chunk
)