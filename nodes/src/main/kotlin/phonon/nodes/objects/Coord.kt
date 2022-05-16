/*
 * Coordinate system for Nodes, follow game chunks
 */

package phonon.nodes.objects;

const val CHUNK_SIZE: Int = 16;

fun toChunk(v: Int): Int {
    return Math.floorDiv(v, CHUNK_SIZE);
}

data class Coord(val x: Int, val z: Int) {

    // bernstein djb2 hash using magic number 33:
    // hash = 33 * x + z
    override public fun hashCode(): Int {
        return ((this.x shl 5) + this.x) + z
    }

    companion object {
        fun fromBlockCoords(x: Int, z: Int): Coord = Coord(toChunk(x), toChunk(z))

        // return coord from string in format "x,z"
        // two numbers separated by comma (with no spaces)
        fun fromString(s: String): Coord? {
            val splitIndex = s.indexOf(",")
            if ( splitIndex != -1 && splitIndex < s.length ) {
                try {
                    val x = s.substring(0, splitIndex).toInt()
                    val z = s.substring(splitIndex+1).toInt()
                    return Coord(x, z)
                } catch ( e: NumberFormatException ) {
                    System.err.println("Invalid Coord string: ${s}")
                }
            }

            return null
        }
    }
}