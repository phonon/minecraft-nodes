/**
 * Enum statuses for nation management functions
 * 
 */

package phonon.nodes.constants

// exceptions during nation creation
val ErrorNationExists = Exception("Nation name already exists")
val ErrorTownHasNation = Exception("Town already has a nation")
val ErrorPlayerHasNation = Exception("Player already has a nation")
val ErrorPlayerNotInTown = Exception("Player not in this town")
val ErrorNationDoesNotHaveTown = Exception("Nation does not have town")

// exception during loading nation
val ErrorTownDoesNotExist = Exception("Town does not exist")