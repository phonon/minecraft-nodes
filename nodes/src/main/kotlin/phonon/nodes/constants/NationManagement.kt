/**
 * Enum statuses for nation management functions
 * 
 */

package phonon.nodes.constants

// exceptions during nation creation
public val ErrorNationExists = Exception("Nation name already exists")
public val ErrorTownHasNation = Exception("Town already has a nation")
public val ErrorPlayerHasNation = Exception("Player already has a nation")
public val ErrorPlayerNotInTown = Exception("Player not in this town")
public val ErrorNationDoesNotHaveTown = Exception("Nation does not have town")

// exception during loading nation
public val ErrorTownDoesNotExist = Exception("Town does not exist")