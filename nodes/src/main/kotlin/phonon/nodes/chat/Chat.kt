/**
 * Basic chat pub/sub channels manager.
 */

package phonon.nodes.chat

import phonon.nodes.Nodes
import phonon.nodes.objects.Resident
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.event.player.AsyncPlayerChatEvent

enum class ChatMode {
    GLOBAL,
    TOWN,
    NATION,
    ALLY,
}

object Chat {
    
    val playersMuteGlobal: HashSet<Player> = hashSetOf()

    var colorDefault = ChatColor.WHITE
    val colorGreen = ChatColor.GREEN
    
    var colorTown = ChatColor.DARK_AQUA
    var colorNation = ChatColor.GOLD
    var colorAlly = ChatColor.GREEN

    var colorPlayerTownless = ChatColor.GRAY
    var colorPlayerOp = ChatColor.DARK_RED
    var colorPlayerTownOfficer = ChatColor.WHITE
    var colorPlayerTownLeader = ChatColor.BOLD
    var colorPlayerNationLeader = "${ChatColor.GOLD}${ChatColor.BOLD}"

    fun process(event: AsyncPlayerChatEvent) {
        // FIRST MOST IMPORTANT: APPLY GREENTEXT
        val msg = event.message
        if ( msg.get(0) == '>' ) {
            event.message = "${colorGreen}${msg}"
        }

        // get player chat mode
        val player = event.player
        val fetchResident = Nodes.getResident(player)
        val resident: Resident = if ( fetchResident != null ) {
            fetchResident
        } else { // print normal message...
            return
        }

        val chatMode = resident.chatMode

        when ( chatMode ) {
            ChatMode.GLOBAL -> {
                // remove players who muted global
                val recipients = event.recipients
                for ( p in playersMuteGlobal ) {
                    recipients.remove(p)
                }
                event.format = formatMsgGlobal(resident)
            }
            ChatMode.TOWN -> {
                val town = resident.town
                if ( town == null ) {
                    event.isCancelled = true
                    return
                }
                event.recipients.clear()
                event.recipients.addAll(town.playersOnline)
                event.format = formatMsgTown(resident)
            }
            ChatMode.NATION -> {
                val nation = resident.nation
                if ( nation == null ) {
                    event.isCancelled = true
                    return
                }
                event.recipients.clear()
                event.recipients.addAll(nation.playersOnline)
                event.format = formatMsgNation(resident)
            }
            ChatMode.ALLY -> {
                val town = resident.town
                if ( town == null ) {
                    event.isCancelled = true
                    return
                }
                event.recipients.clear()
                event.recipients.addAll(town.playersOnline)
                for ( allyTown in town.allies ) {
                    event.recipients.addAll(allyTown.playersOnline)
                }
                event.format = formatMsgAlly(resident)
            }
        }
    }

    // unmute global chat for player
    fun enableGlobalChat(player: Player) {
        playersMuteGlobal.remove(player)
    }

    // mute global chat for player
    fun disableGlobalChat(player: Player) {
        playersMuteGlobal.add(player)
    }

//    fun isMuted(player: Player): Boolean {
//        // TODO: hook into essentials, check muted player
//        return false
//    }

    fun formatResidentName(resident: Resident): String {
        
        val town = resident.town
        val nation = resident.nation

        // get player name color
        val color = if ( resident.player()?.isOp == true ) {
            colorPlayerOp
        } else if ( town == null ) {
            colorPlayerTownless
        } else { // town != null
            if ( resident === nation?.capital?.leader ) {
                colorPlayerNationLeader
            }
            else if ( resident.uuid == town.leader?.uuid ) {
                colorPlayerTownLeader
            }
            else if ( town.officers.contains(resident) ) {
                colorPlayerTownOfficer
            }
            else {
                colorDefault
            }
        }

        if ( resident.prefix != "" && resident.suffix != "" ) {
            return "${color}${resident.prefix} ${color}%1\$s ${color}${resident.suffix}"
        } else if ( resident.prefix != "" ) {
            return "${color}${resident.prefix} ${color}%1\$s"
        } else if ( resident.suffix != "" ) {
            return "${color}%1\$s ${resident.suffix}"
        } else {
            return "${color}%1\$s"
        }

    }

    fun formatMsgGlobal(resident: Resident): String {
        // format player name
        val formattedResidentName = formatResidentName(resident)

        // format town, nation
        val formattedResidentAllegience = if ( resident.town != null && resident.nation != null ) {
            "[${colorNation}${resident.nation?.name}${colorDefault}|${colorTown}${resident.town?.name}${colorDefault}] "
        } else if ( resident.town != null ) {
            "[${colorTown}${resident.town?.name}${colorDefault}] "
        } else {
            ""
        }

        return "${formattedResidentAllegience}${formattedResidentName}${colorDefault}: %2\$s"
    }

    fun formatMsgTown(resident: Resident): String {
        // format player name
        val formattedResidentName = formatResidentName(resident)

        return "${colorTown}[Town] ${formattedResidentName}${colorTown}: %2\$s"
    }

    fun formatMsgNation(resident: Resident): String {
        // format player name
        val formattedResidentName = formatResidentName(resident)

        return "${colorNation}[Nation] ${formattedResidentName}${colorNation}: %2\$s"
    }
    
    fun formatMsgAlly(resident: Resident): String {
        // format player name
        val formattedResidentName = formatResidentName(resident)

        return "${colorAlly}[Ally] ${formattedResidentName}${colorAlly}: %2\$s"
    }

}