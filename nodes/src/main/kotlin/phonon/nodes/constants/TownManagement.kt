/**
 * Enum statuses for town management functions
 * 
 */

package phonon.nodes.constants

// town create errors
val ErrorTownExists = Exception("Town name already exists")
val ErrorPlayerHasTown = Exception("Player already in a town")
val ErrorTerritoryOwned = Exception("Territory already has town") // doubles as territory claim error

// town territory claim/unclaim errors
// ErrorTerritoryOwned doubles as error