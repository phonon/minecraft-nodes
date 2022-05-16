/**
 * Enum statuses for war functions
 */

package phonon.nodes.constants

// flag war attack errors
public val ErrorNoTerritory = Exception("[War] There is no territory here")
public val ErrorAlreadyUnderAttack = Exception("[War] Chunk already under attack")
public val ErrorAlreadyCaptured = Exception("[War] Chunk already captured by town or allies")
public val ErrorTownBlacklisted = Exception("[War] Cannot attack this town (blacklisted)")
public val ErrorTownNotWhitelisted = Exception("[War] Cannot attack this town (not whitelisted)")
public val ErrorNotEnemy = Exception("[War] Chunk does not belong to an enemy")
public val ErrorNotBorderTerritory = Exception("[War] Can only attack border territories")
public val ErrorChunkNotEdge = Exception("[War] Chunk is not at the edge")
public val ErrorFlagTooHigh = Exception("[War] Flag placement too high, cannot create flag")
public val ErrorSkyBlocked = Exception("[War] Flag must see the sky")
public val ErrorTooManyAttacks = Exception("[War] You cannot attack any more chunks at the same time")
public val ErrorAttackCustomCancel = Exception("[War] Custom event cancelled")