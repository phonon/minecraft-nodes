/**
 * Alliance request manager and storage structure
 * 
 * Alliances stored as town pairs (town1, town2)
 * Simple ally request manager
 * 
 * Handles "/ally [town/nation]" commands.
 * Three types of relations:
 * - town-town
 * - town-nation
 * - nation-nation
 */

package phonon.nodes.war

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.scheduler.BukkitTask
import phonon.nodes.Nodes
import phonon.nodes.Message
import phonon.nodes.Config
import phonon.nodes.objects.Town
import phonon.nodes.objects.TownPair
import phonon.nodes.objects.Nation
import phonon.nodes.constants.*

// peace request status results
enum class AllianceRequest {
    NEW,       // new offer created
    ACCEPTED   // offer accepted
}

// errors
val ErrorAllyRequestEnemies = Exception("Not enemies")
val ErrorAllyRequestAlreadyAllies = Exception("Already allies")
val ErrorAllyRequestAlreadyCreated = Exception("Already sent an ally request")

// timeout for ally request to cancel (default 1200 ticks ~ 1 minute)
private const val ALLY_REQUEST_TIMEOUT: Long = 1200L

/**
 * Alliance request manager
 */
object Alliance {

    // offers lists: maps TownPair involved -> Initiating Town
    val requests: HashMap<TownPair, Town> = hashMapOf()

    // threads to delete requests after timeout
    val requestTimers: HashMap<TownPair, BukkitTask> = hashMapOf()

    /**
     * Offer/accept request between two towns. Inputs:
     * - town1: initiator
     * - town2: other town involved
     * - if nations exist for either town, town must be nation's capital
     * 
     * if request exists and town1 is not == TownPair -> Town, accept request
     * else, create new request
     * 
     */
    fun request(town1: Town, town2: Town): Result<AllianceRequest> {
        // check towns are not enemies
        if ( town1.enemies.contains(town2) || town2.enemies.contains(town1) ) {
            return Result.failure(ErrorAllyRequestEnemies)
        }
        // check towns not already allied
        if ( town1.allies.contains(town2) && town2.allies.contains(town1) ) {
            return Result.failure(ErrorAllyRequestAlreadyAllies)
        }

        val towns = TownPair(town1, town2)
        val initiator = requests.get(towns)
        
        // no request, create new request
        if ( initiator === null ) {
            requests.put(towns, town1)

            // create timeout thread
            val timeoutThread = Bukkit.getScheduler().runTaskLaterAsynchronously(Nodes.plugin!!, object: Runnable {
                override fun run() {
                    cancelRequest(towns)
                }
            }, ALLY_REQUEST_TIMEOUT)
            
            requestTimers.put(towns, timeoutThread)

            return Result.success(AllianceRequest.NEW)
        }
        // else, check request initiator
        else {
            // initiator is same town
            if ( initiator === town1 ) {
                return Result.failure(ErrorPeaceRequestAlreadyCreated)
            }
            else { // accept request
                // remove request
                requests.remove(towns)
                
                // cancel timeout thread
                val timeoutThread = requestTimers.remove(towns)
                if ( timeoutThread !== null ) {
                    timeoutThread.cancel()
                }

                Nodes.addAlly(town1, town2)
                return Result.success(AllianceRequest.ACCEPTED)
            }
        }
    }

    // remove and cancel peace request
    fun cancelRequest(towns: TownPair) {
        val initiator = requests.remove(towns)
        if ( initiator !== null ) {
            // cancel timeout thread
            val timeoutThread = requestTimers.remove(towns)
            if ( timeoutThread !== null ) {
                timeoutThread.cancel()
            }

            // message creator that request was not accepted
            val target = if ( initiator === towns.town1 ) {
                towns.town2
            }
            else {
                towns.town1
            }

            val msg = "${ChatColor.DARK_RED}Your alliance offer to ${target.name} was ignored..."
            for ( r in initiator.residents ) {
                val p = r.player()
                if ( p !== null ) {
                    Message.print(p, msg)
                }
            }
        }
    }

}