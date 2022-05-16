/**
 * Unordered pair of two towns
 * 
 * If either town1 or town2 are equal, two TownPairs
 * must be equal.
 * 
 * Used for diplomacy functions as a map key for treaties,
 * e.g. HashMap<TownPair, Treaty>
 */

package phonon.nodes.objects

public data class TownPair(
    val town1: Town,
    val town2: Town
) {
    override fun equals(other: Any?): Boolean {
        if ( this === other ) {
            return true
        }
        if ( other?.javaClass != javaClass ) {
            return false
        }

        other as TownPair

        return ( this.town1 === other.town1 || this.town1 === other.town2 ) && ( this.town2 === other.town1 || this.town2 === other.town2 )
    }

    override public fun hashCode(): Int {
        return this.town1.hashCode() + this.town2.hashCode()
    }
}