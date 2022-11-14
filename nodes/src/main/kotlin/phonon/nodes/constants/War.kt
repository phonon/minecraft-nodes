/**
 * Enum statuses for war functions
 */

package phonon.nodes.constants

// flag war attack errors
val ErrorNoTerritory = Exception("[War] There is no territory here")
val ErrorAlreadyUnderAttack = Exception("[War] Chunk already under attack")
val ErrorAlreadyCaptured = Exception("[War] Chunk already captured by town or allies")
val ErrorTownBlacklisted = Exception("[War] Cannot attack this town (blacklisted)")
val ErrorTownNotWhitelisted = Exception("[War] Cannot attack this town (not whitelisted)")
val ErrorNotEnemy = Exception("[War] Chunk does not belong to an enemy")
val ErrorNotBorderTerritory = Exception("[War] Can only attack border territories")
val ErrorChunkNotEdge = Exception("[War] Chunk is not at the edge")
val ErrorFlagTooHigh = Exception("[War] Flag placement too high, cannot create flag")
val ErrorSkyBlocked = Exception("[War] Flag must see the sky")
val ErrorTooManyAttacks = Exception("[War] You cannot attack any more chunks at the same time")
val ErrorAttackCustomCancel = Exception("[War] Custom event cancelled")