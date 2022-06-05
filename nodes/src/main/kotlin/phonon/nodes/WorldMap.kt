/**
 * WorldMap Renderer
 * 
 * Manager to render territories from the world into
 * strings to display in chat or other objects.
 * 
 * Renders world chunk coords into ascii characters.
 */

package phonon.nodes

import org.bukkit.ChatColor
import phonon.nodes.objects.Coord
import phonon.nodes.objects.Resident

// minimap display primitive glyphs MUST BE SAME ASCII SIZE
private val SOLID = "\u2588"      // full solid block
private val SHADE0 = "\u2592"     // medium shade
private val SHADE1 = "\u2593"     // dark shade
private val SHADE2 = "\u2588"     // full solid block
private val HOME = SHADE2         // home territory
private val CORE = "\u256B"       // core chunk H
private val CONQUERED0 = "\u255E" // captured chunk flag symbol
private val CONQUERED1 = "\u255F" // other chunk flag symbol
private val PLAYER_TOKEN = "\u253C"     // player token
private val PLAYER_IN_OCCUPIED_TOKEN = "\u256C" // player token in occupied chunk

// minimap display tokens
private val EMPTY = "${ChatColor.BLACK}${SHADE0}"

// default player token
private val PLAYER_IN_EMPTY = "${ChatColor.WHITE}${PLAYER_TOKEN}"

// territory cores
private val CORE_UNCLAIMED = "${ChatColor.GRAY}${CORE}"
private val CORE_TOWN = "${ChatColor.GREEN}${CORE}"
private val CORE_NEUTRAL = "${ChatColor.YELLOW}${CORE}"
private val CORE_ENEMY = "${ChatColor.RED}${CORE}"
private val CORE_ALLY = "${ChatColor.AQUA}${CORE}"
private val CORE_CAPTURED = "${ChatColor.GREEN}${CORE}" // captured by your town
private val CORE_OCCUPIED = "${ChatColor.RED}${CORE}"   // occupied by another town

// home territory
private val HOME_TOWN = "${ChatColor.GREEN}${HOME}" // captured by your town
private val HOME_NEUTRAL = "${ChatColor.YELLOW}${HOME}"  // captured by neutral town
private val HOME_ALLY = "${ChatColor.AQUA}${HOME}"  // captured by ally town
private val HOME_ENEMY = "${ChatColor.RED}${HOME}"  // captured by enemy

// occupied chunk
private val OCCUPIED_TOWN = "${ChatColor.GREEN}${CONQUERED0}"      // captured by your town
private val OCCUPIED_NEUTRAL = "${ChatColor.YELLOW}${CONQUERED0}"  // captured by neutral town
private val OCCUPIED_ALLY = "${ChatColor.AQUA}${CONQUERED0}"       // captured by ally town
private val OCCUPIED_ENEMY = "${ChatColor.RED}${CONQUERED0}"       // captured by enemy

// territory colors: maps territory color id -> color
// territories have color assigned to them during
// map generation from nodes editor

// unclaimed territory colors
private val COLOR_UNCLAIMED: Array<String> = arrayOf(
    "${ChatColor.GRAY}${SHADE0}",
    "${ChatColor.GRAY}${SHADE1}",
    "${ChatColor.DARK_GRAY}${SHADE0}",
    "${ChatColor.DARK_GRAY}${SHADE1}",
    "${ChatColor.GRAY}${SHADE2}",
    "${ChatColor.DARK_GRAY}${SHADE2}"
)

// player town chunk
private val COLOR_TOWN: Array<String> = arrayOf(
    "${ChatColor.GREEN}${SHADE0}",
    "${ChatColor.GREEN}${SHADE1}",
    "${ChatColor.DARK_GREEN}${SHADE0}",
    "${ChatColor.DARK_GREEN}${SHADE1}",
    "${ChatColor.GREEN}${SHADE2}",
    "${ChatColor.DARK_GREEN}${SHADE2}"
)

// neutral town chunk
private val COLOR_NEUTRAL: Array<String> = arrayOf(
    "${ChatColor.YELLOW}${SHADE0}",
    "${ChatColor.YELLOW}${SHADE1}",
    "${ChatColor.GOLD}${SHADE0}",
    "${ChatColor.GOLD}${SHADE1}",
    "${ChatColor.GOLD}${SHADE2}",
    "${ChatColor.GOLD}${SHADE2}"
)

// ally town chunk
private val COLOR_ALLY: Array<String> = arrayOf(
    "${ChatColor.AQUA}${SHADE0}",
    "${ChatColor.AQUA}${SHADE1}",
    "${ChatColor.DARK_AQUA}${SHADE0}",
    "${ChatColor.DARK_AQUA}${SHADE1}",
    "${ChatColor.AQUA}${SHADE2}",
    "${ChatColor.DARK_AQUA}${SHADE2}"
)

// enemy town chunk
private val COLOR_ENEMY: Array<String> = arrayOf(
    "${ChatColor.RED}${SHADE0}",
    "${ChatColor.RED}${SHADE1}",
    "${ChatColor.DARK_RED}${SHADE0}",
    "${ChatColor.DARK_RED}${SHADE1}",
    "${ChatColor.DARK_RED}${SHADE2}",
    "${ChatColor.DARK_RED}${SHADE2}"
)

// town occupied territory
private val COLOR_OCCUPIED_TOWN: Array<String> = arrayOf(
    "${ChatColor.GREEN}${CONQUERED0}",
    "${ChatColor.GREEN}${CONQUERED1}",
    "${ChatColor.DARK_GREEN}${CONQUERED0}",
    "${ChatColor.DARK_GREEN}${CONQUERED1}",
    "${ChatColor.GREEN}${CONQUERED1}",
    "${ChatColor.DARK_GREEN}${CONQUERED1}"
)

// territory occupied by neutral
private val COLOR_OCCUPIED_NEUTRAL: Array<String> = arrayOf(
    "${ChatColor.YELLOW}${CONQUERED0}",
    "${ChatColor.YELLOW}${CONQUERED1}",
    "${ChatColor.GOLD}${CONQUERED0}",
    "${ChatColor.GOLD}${CONQUERED1}",
    "${ChatColor.GOLD}${CONQUERED1}",
    "${ChatColor.GOLD}${CONQUERED1}"
)

// territory occupied by ally
private val COLOR_OCCUPIED_ALLY: Array<String> = arrayOf(
    "${ChatColor.AQUA}${CONQUERED0}",
    "${ChatColor.AQUA}${CONQUERED1}",
    "${ChatColor.DARK_AQUA}${CONQUERED0}",
    "${ChatColor.DARK_AQUA}${CONQUERED1}",
    "${ChatColor.AQUA}${CONQUERED1}",
    "${ChatColor.DARK_AQUA}${CONQUERED1}"
)

// territory occupied by enemy
private val COLOR_OCCUPIED_ENEMY: Array<String> = arrayOf(
    "${ChatColor.RED}${CONQUERED0}",
    "${ChatColor.RED}${CONQUERED1}",
    "${ChatColor.DARK_RED}${CONQUERED0}",
    "${ChatColor.DARK_RED}${CONQUERED1}",
    "${ChatColor.DARK_RED}${CONQUERED1}",
    "${ChatColor.DARK_RED}${CONQUERED1}"
)

/**
 * Map from a color token to alternate color token.
 */
private fun getAlternativeColor(c: String): String {
    when ( c ) {
        "${ChatColor.GRAY}" -> return "${ChatColor.WHITE}"
        "${ChatColor.DARK_GRAY}" -> return "${ChatColor.GRAY}"

        "${ChatColor.GREEN}" -> return "${ChatColor.DARK_GREEN}"
        "${ChatColor.DARK_GREEN}" -> return "${ChatColor.GREEN}"

        "${ChatColor.YELLOW}" -> return "${ChatColor.GOLD}"
        "${ChatColor.GOLD}" -> return "${ChatColor.YELLOW}"

        "${ChatColor.AQUA}" -> return "${ChatColor.DARK_AQUA}"
        "${ChatColor.DARK_AQUA}" -> return "${ChatColor.AQUA}"

        "${ChatColor.RED}" -> return "${ChatColor.DARK_RED}"
        "${ChatColor.DARK_RED}" -> return "${ChatColor.RED}"
        
        else -> return "${ChatColor.WHITE}"
    }
}

public object WorldMap {

    // render a horizontal line (constant z)
    // units in chunk coords
    public fun renderLine(resident: Resident, playerCoord: Coord, z: Int, xMin: Int, xMax: Int): String {
        var renderedString = ""

        for ( x in xMin..xMax ) {
            val coord = Coord(x, z)
            val territoryChunk = Nodes.getTerritoryChunkFromCoord(coord)
            
            // get token for current coordinate
            val token = if ( territoryChunk == null ) {
                if ( coord == playerCoord ) {
                    PLAYER_IN_EMPTY
                } else {
                    EMPTY
                }
            }
            else {
                var playerToken = PLAYER_TOKEN

                val territory = territoryChunk.territory
                val color = territory.color

                // town, nation in current territory
                val town = territory.town
                val nation = town?.nation
                val residentTown = resident.town
                val residentNation = residentTown?.nation
                val chunkOccupier = Nodes.territoryChunks.get(coord)?.occupier
                
                // special tokens during war for captured chunks
                val coordToken = if ( Nodes.war.enabled && residentTown !== null && chunkOccupier !== null ) {
                    playerToken = PLAYER_IN_OCCUPIED_TOKEN // overwrite player token with different occupied chunk token

                    val chunkOccupierNation = chunkOccupier.nation
                    if ( chunkOccupier === residentTown ) {
                        OCCUPIED_TOWN
                    }
                    else if ( residentTown.allies.contains(chunkOccupier) || ( residentNation !== null && residentNation === chunkOccupierNation ) ) {
                        OCCUPIED_ALLY
                    }
                    else if ( residentTown.enemies.contains(chunkOccupier) ) {
                        OCCUPIED_ENEMY
                    }
                    else {
                        OCCUPIED_NEUTRAL
                    }
                }
                else {
                    if ( town != null ) {
                        if ( coord == territory.core ) {
                            // town
                            if ( town === residentTown ) {
                                CORE_TOWN
                            }
                            // enemy
                            else if ( residentTown?.enemies?.contains(town) == true ) {
                                CORE_ENEMY
                            }
                            // ally
                            else if ( residentTown?.allies?.contains(town) == true || ( residentNation !== null && residentNation === nation ) ) {
                                CORE_ALLY
                            }
                            // neutral
                            else {
                                CORE_NEUTRAL
                            }
                        }
                        else {
                            val occupier = territory.occupier
                            if ( occupier != null ) {
                                val occupierNation = occupier.nation

                                // town
                                if ( occupier === residentTown ) {
                                    COLOR_OCCUPIED_TOWN[color]
                                }
                                // enemy
                                else if ( residentTown?.enemies?.contains(occupier) == true ) {
                                    COLOR_OCCUPIED_ENEMY[color]
                                }
                                // ally
                                else if ( residentTown?.allies?.contains(occupier) == true || ( residentNation !== null && residentNation === occupierNation ) ) {
                                    COLOR_OCCUPIED_ALLY[color]
                                }
                                // neutral
                                else {
                                    COLOR_OCCUPIED_NEUTRAL[color]
                                }
                            }
                            else {
                                // town's home territory
                                if ( territory.id == town.home ) {
                                    // town
                                    if ( town === residentTown ) {
                                        HOME_TOWN
                                    }
                                    // enemy
                                    else if ( residentTown?.enemies?.contains(town) == true ) {
                                        HOME_ENEMY
                                    }
                                    // ally
                                    else if ( residentTown?.allies?.contains(town) == true || ( residentNation !== null && residentNation === nation ) ) {
                                        HOME_ALLY
                                    }
                                    // neutral
                                    else {
                                        HOME_NEUTRAL
                                    }
                                }
                                // normal town chunk
                                else {
                                    // town
                                    if ( town === residentTown ) {
                                        COLOR_TOWN[color]
                                    }
                                    // enemy
                                    else if ( residentTown?.enemies?.contains(town) == true ) {
                                        COLOR_ENEMY[color]
                                    }
                                    // ally
                                    else if ( residentTown?.allies?.contains(town) == true || ( residentNation !== null && residentNation === nation ) ) {
                                        COLOR_ALLY[color]
                                    }
                                    // neutral
                                    else {
                                        COLOR_NEUTRAL[color]
                                    }
                                }
                            }
                        }
                    }
                    else { // default unclaimed colors
                        if ( coord == territory.core ) {
                            if ( residentTown !== null && territory.occupier === resident.town ) {
                                CORE_CAPTURED
                            }
                            else {
                                CORE_UNCLAIMED
                            }
                        }
                        else {
                            COLOR_UNCLAIMED[color]
                        }
                    }
                }

                if ( coord == playerCoord ) {
                    // take coord token color and append player token
                    val color = coordToken.substring(0 until (coordToken.length - 1))
                    "${getAlternativeColor(color)}${playerToken}"
                } else {
                    coordToken
                }
            }

            renderedString += token
        }

        return renderedString
    }
}