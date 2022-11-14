/**
 * Constants for diplomatic relations
 * (town, nation ally/enemy functions)
 */

package phonon.nodes.constants

/**
 * Simple relationship groups:
 * Town - contains town residents
 * Ally - contains nation towns and other allies
 * Neutral - neutral towns, or players with no town
 * Enemy - enemy towns
 */
enum class DiplomaticRelationship {
    TOWN,
    NATION,
    ALLY,
    NEUTRAL,
    ENEMY
}

// constants for setting enemy
val ErrorWarAllyOrTruce = Exception("Cannot declare war against ally or truced")
val ErrorAlreadyEnemies = Exception("Already enemies")
val ErrorAlreadyAllies = Exception("Already allies")

// constants for adding/removing ally
val ErrorNotAllies = Exception("Not allies")

// constants for adding/remove truce
val ErrorAlreadyTruce = Exception("Already truce")