/**
 * Enum statuses for town management functions
 * 
 */

package phonon.nodes.constants

// town create errors
public val ErrorTownExists = Exception("Town name already exists")
public val ErrorPlayerHasTown = Exception("Player already in a town")
public val ErrorTerritoryOwned = Exception("Territory already has town") // doubles as territory claim error

// town territory claim/unclaim errors
// ErrorTerritoryOwned doubles as error