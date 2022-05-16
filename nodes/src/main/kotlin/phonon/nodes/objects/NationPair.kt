/**
 * Unordered pair of two nations
 * 
 * If either nation1 or nation2 are equal, two NationPairs
 * must be equals.
 */

package phonon.nodes.objects

public data class NationPair(
    val nation1: Nation,
    val nation2: Nation
) {
    override fun equals(other: Any?): Boolean {
        if ( this === other ) {
            return true
        }
        if ( other?.javaClass != javaClass ) {
            return false
        }

        other as NationPair

        return ( this.nation1 === other.nation1 || this.nation1 === other.nation2 ) && ( this.nation2 === other.nation1 || this.nation2 === other.nation2 )
    }

    override public fun hashCode(): Int {
        return this.nation1.hashCode() + this.nation2.hashCode()
    }
}