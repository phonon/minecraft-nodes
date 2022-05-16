/**
 * DEPRECATED
 * Simple peace request manager
 * 
 * 
 * Handles "/peace [town/nation]" commands.
 * Three types of relations:
 * - town-town
 * - town-nation
 * - nation-nation
 * 
 * When /peace [town/nation] run by both sides, peace is
 * activated
 */

package phonon.nodes.war

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.scheduler.BukkitTask
import phonon.nodes.Nodes
import phonon.nodes.Message
import phonon.nodes.Config
import phonon.nodes.objects.Town
import phonon.nodes.objects.Nation
import phonon.nodes.constants.*

// peace request status results
public enum class PeaceRequest {
    NEW,       // new offer created
    ACCEPTED   // offer accepted
}

// errors
public val ErrorPeaceRequestNotEnemies = Exception("Not enemies")
public val ErrorPeaceRequestAlreadyCreated = Exception("Already sent a peace request")

// timeout for peace request to cancel (default 1200 ticks ~ 1 minute)
private const val PEACE_REQUEST_TIMEOUT: Long = 1200L

public data class PeaceRequestInstance(
    val town1: Town,
    val town2: Town,
    val creator: Town // who initiated/responded
) {
    override fun equals(other: Any?): Boolean {
        if ( this === other ) {
            return true
        }
        if ( other?.javaClass != javaClass ) {
            return false
        }

        other as PeaceRequestInstance

        return ( this.town1 === other.town1 || this.town1 === other.town2 ) && ( this.town2 === other.town1 || this.town2 === other.town2 )
    }

    override public fun hashCode(): Int {
        return this.town1.hashCode() + this.town2.hashCode()
    }
}


public object Peace {

    // peace offer lists
    public val requests: ArrayList<PeaceRequestInstance> = arrayListOf()

    // threads to delete requests after timeout
    public val requestTimers: HashMap<PeaceRequestInstance, BukkitTask> = hashMapOf()

    // offer/accept peace request between two towns
    // if no peace offer exists, create new peace request
    // else create a new peace offer
    //
    // input requirements:
    // - if town1, town2 have nations, town1, town2 should be === nation.capital
    // - town1 should always be the town that initiates peace offer
    public fun request(town1: Town, town2: Town): Result<PeaceRequest> {
        // check towns are enemies
        if ( !town1.enemies.contains(town2) && !town2.enemies.contains(town1) ) {
            return Result.failure(ErrorPeaceRequestNotEnemies)
        }

        val peaceRequest = PeaceRequestInstance(town1, town2, town1)
        
        // if request exists, accept peace request
        val index = Peace.requests.indexOf(peaceRequest)
        if ( index != -1 ) {
            // check peace request responder is not same town
            val existingPeaceRequest = Peace.requests.get(index)
            
            if ( peaceRequest.creator !== existingPeaceRequest.creator ) {
                // remove peace request
                Peace.requests.removeAt(index)

                // cancel timeout thread
                val timeoutThread = Peace.requestTimers.remove(peaceRequest)
                if ( timeoutThread !== null ) {
                    timeoutThread.cancel()
                }

                // accept peace
                Nodes.removeEnemy(town1, town2)

                return Result.success(PeaceRequest.ACCEPTED)
            }
            else {
                return Result.failure(ErrorPeaceRequestAlreadyCreated)
            }
        }
        // add peace request
        else {
            Peace.requests.add(peaceRequest)

            // create timeout thread
            val timeoutThread = Bukkit.getScheduler().runTaskLaterAsynchronously(Nodes.plugin!!, object: Runnable {
                override fun run() {
                    Peace.cancelRequest(peaceRequest)
                }
            }, PEACE_REQUEST_TIMEOUT)
            
            Peace.requestTimers.put(peaceRequest, timeoutThread)

            return Result.success(PeaceRequest.NEW)
        }
    }

    // remove and cancel peace request
    public fun cancelRequest(peaceRequest: PeaceRequestInstance) {
        val index = Peace.requests.indexOf(peaceRequest)
        if ( index != -1 ) {
            // remove peace request
            Peace.requests.removeAt(index)

            // cancel timeout thread
            val timeoutThread = Peace.requestTimers.remove(peaceRequest)
            if ( timeoutThread !== null ) {
                timeoutThread.cancel()
            }

            // message creator that request was not accepted
            val creator = peaceRequest.creator
            val target = if ( creator === peaceRequest.town1 ) {
                peaceRequest.town2
            }
            else {
                peaceRequest.town1
            }

            for ( r in creator.residents ) {
                val p = r.player()
                if ( p !== null ) {
                    Message.print(p, "${ChatColor.DARK_RED}Your peace offer to ${target.name} was ignored...")
                }
            }
        }
    }

}