/**
 * Peace treaty system.
 * 
 * Peace treaties are created for (town1, town2) pairs.
 * If either town has a nation, the town capital is used for treaties.
 * Treaties can occur at any time (even without war).import
 * 
 * Treaty objects are stored in memory, until it is either
 * rejected, deleted, or executed. If either side does not view it,
 * the treaty will hang in limbo.
 */

package phonon.nodes.war

import java.util.EnumMap
import org.bukkit.ChatColor
import org.bukkit.inventory.ItemStack
import org.bukkit.entity.Player
import phonon.nodes.Nodes
import phonon.nodes.Message
import phonon.nodes.gui.Gui
import phonon.nodes.objects.Resident
import phonon.nodes.objects.Town
import phonon.nodes.objects.TownPair

// state machine for each player's treaty gui views
public enum class TreatyGuiView {
    MAIN,                     // main screen
    SELECT_TERRITORY_OCCUPY   // territory selection to be occupied
}

// peace treaty instance between two towns
public class Treaty(
    val town1: Town,
    val town2: Town
) {
    // two treaty term holders, just so item treaty items
    // can be displayed after all land items
    val termsLand: HashSet<TreatyTerm> = hashSetOf()           // must be unique
    val termsItems: ArrayList<TreatyTermItems> = arrayListOf() // can have duplicates

    // player view state machines
    // -> support any number of players in different views
    val viewers: EnumMap<TreatyGuiView, ArrayList<Player>> = enumValues<TreatyGuiView>().toList().associateWithTo(
        EnumMap<TreatyGuiView, ArrayList<Player>>(TreatyGuiView::class.java),
        {_ -> arrayListOf()}
    )

    // flag that treaty terms are locked for final confirmation
    var town1Locked: Boolean = false // locked for town1
    var town2Locked: Boolean = false // locked for town2

    // flags that side has confirmed treaty terms
    var town1Confirmed: Boolean = false // locked for town1
    var town2Confirmed: Boolean = false // locked for town2

    // flag that this treaty is finished and needs to be deleted
    // -> mark when treaty rejected or executing
    // -> used as lock so player closeInventory() events do not
    //    concurrently modify this.viewers 
    var finished: Boolean = false

    // destroy treaty and delete it
    // occur in response to event (e.g. town or nation involved gets deleted)
    public fun destroy() {
        // lock treaty
        this.finished = true

        // close inventory for all viewers
        for ( (view, players) in this.viewers ) {
            for ( p in players ) {
                p.closeInventory()
            }
        }

        // cancel/undo all treaty terms
        for ( term in this.termsLand ) {
            term.cancel()
        }
        for ( term in this.termsItems ) {
            term.cancel()
        }

        Treaty.delete(this)
    }

    // reject treaty and delete it (remove from active treaties)
    public fun reject(rejector: Town) {
        // lock treaty
        this.finished = true

        // close inventory for all viewers
        for ( (view, players) in this.viewers ) {
            for ( p in players ) {
                p.closeInventory()
            }
        }

        // cancel/undo all treaty terms
        for ( term in this.termsLand ) {
            term.cancel()
        }
        for ( term in this.termsItems ) {
            term.cancel()
        }

        Treaty.delete(this)
        
        // print reject message
        val other = if ( rejector === this.town1 ) {
            town2
        } else {
            town1
        }

        val msgRejector = "${ChatColor.DARK_RED}You have rejected peace terms with ${other.name}"
        val msgOther = "${ChatColor.DARK_RED}${rejector.name} has rejected your peace terms"
        for ( resident in rejector.residents ) {
            val player = resident.player()
            if ( player != null ) {
                Message.print(player, msgRejector)
            }
        }
        for ( resident in other.residents ) {
            val player = resident.player()
            if ( player != null ) {
                Message.print(player, msgOther)
            }
        }
    }

    // offer terms from town:
    // - if other side already offered, enter confirmation state
    // - else, lock side's terms
    public fun offer(side: Town) {
        if ( side === town1 ) {
            this.town1Locked = true
        }
        else if ( side === town2 ) {
            this.town2Locked = true
        }
        else {
            return
        }
        
        renderMainView()
    }

    // confirm terms
    public fun confirm(side: Town) {
        if ( side === town1 ) {
            this.town1Confirmed = true
        }
        else if ( side === town2 ) {
            this.town2Confirmed = true
        }
        else {
            return
        }
        
        // execute if both sides confirmed
        if ( this.town1Confirmed && this.town2Confirmed ) {
            this.execute()
        }
        else {
            renderMainView()
        }
    }

    // undo confirm + lock, revise treaty terms
    public fun revise(side: Town) {
        // undo confirmation
        this.town1Confirmed = false
        this.town2Confirmed = false

        // unlock
        if ( side === town1 ) {
            this.town1Locked = false
        }
        else if ( side === town2 ) {
            this.town2Locked = false
        }
        else {
            return
        }

        renderMainView()
    }

    // run treaty and all terms, then apply truce between towns
    public fun execute() {
        // lock treaty terms
        this.finished = true

        // close inventory for all viewers
        for ( (view, players) in this.viewers ) {
            for ( p in players ) {
                p.closeInventory()
            }
        }
        
        // execute treaty terms
        for ( term in this.termsLand ) {
            term.execute()
        }
        for ( term in this.termsItems ) {
            term.execute()
        }

        // set truce between towns
        Nodes.addTruce(this.town1, this.town2)
        
        // remove enemy status
        Nodes.removeEnemy(this.town1, this.town2)
        
        Treaty.delete(this)

        Message.broadcast("${ChatColor.BOLD}${this.town1.name} has signed a peace treaty with ${this.town2.name}")
    }

    // add term to treaty
    public fun add(term: TreatyTerm) {
        // check if side locked
        if ( ( term.provider === this.town1 && this.town1Locked == true ) || ( term.provider === this.town2 && this.town2Locked == true ) ) {
            return
        }

        val result = when ( term ) {
            is TreatyTermOccupation -> this.termsLand.add(term)
            is TreatyTermItems -> this.termsItems.add(term)
            else -> false
        }

        // if item added successfully
        if ( result == true ) {
            renderMainView()
        }
    }

    // remove term from treaty
    public fun remove(term: TreatyTerm) {
        // check if side locked
        if ( ( term.provider === this.town1 && this.town1Locked == true ) || ( term.provider === this.town2 && this.town2Locked == true ) ) {
            return
        }
        
        when ( term ) {
            is TreatyTermOccupation -> this.termsLand.remove(term)
            is TreatyTermItems -> {
                val result = this.termsItems.remove(term)
                if ( result == true ) {
                    term.cancel()
                }
            }
        }
        renderMainView()
    }

    // add player actively viewing treaty
    public fun addViewer(player: Player, view: TreatyGuiView) {
        if ( this.finished == true ) {
            return
        }

        this.viewers.get(view)!!.add(player)
    }

    // remove player actively viewing treaty
    public fun removeViewer(player: Player, view: TreatyGuiView) {
        if ( this.finished == true ) {
            return
        }

        this.viewers.get(view)!!.remove(player)
    }

    // re-render main view for players viewing
    public fun renderMainView() {
        val viewers: List<Player> = this.viewers.get(TreatyGuiView.MAIN)!!.toList()
        for ( p in viewers ) {
            // get town from player
            val resident = Nodes.getResident(p)
            if ( resident === null ) {
                return
            }

            var town = resident.town
            if ( town === null || ( resident.town !== this.town1 && resident.town !== this.town2 ) ) {
                // default town for admin or others viewing treaty
                town = this.town1
            }
            
            val guiTreaty = TreatyGui(
                TreatyGuiView.MAIN,
                p,
                this,
                town
            )
            Gui.render(guiTreaty, p, 54, "Peace Treaty")
        }
    }

    // set player gui view
    public fun setPlayerGuiView(view: TreatyGuiView, player: Player, treaty: Treaty, town: Town) {
        // player.closeInventory()
        
        val guiTreaty = TreatyGui(
            view,
            player,
            treaty,
            town
        )
        Gui.render(guiTreaty, player, 54, "Peace Treaty")
    }

    /**
     * Manager functions for treaties
     */
    companion object {

        // list of all treaties in progress
        public val treaties: HashMap<TownPair, Treaty> = hashMapOf()
        
        // get peace treaty object between two towns
        fun get(town1: Town, town2: Town): Treaty? {
            return treaties.get(TownPair(town1, town2))
        }
        
        // get active peace treaty for player and render gui
        // town1: town for player
        // town2: other town involved
        fun show(player: Player, town1: Town, town2: Town): Boolean {
            var treaty = treaties.get(TownPair(town1, town2))
            var result: Boolean = true
            if ( treaty == null ) {
                treaty = Treaty.create(town1, town2)
                result = false
            }

            val guiTreaty = TreatyGui(
                TreatyGuiView.MAIN,
                player,
                treaty,
                town1
            )
            Gui.render(guiTreaty, player, 54, "Peace Treaty")

            return result
        }

        // create new active peace treaty
        fun create(town1: Town, town2: Town): Treaty {
            val treaty = Treaty(town1, town2)
            Treaty.treaties.put(TownPair(town1, town2), treaty)
            return treaty
        }

        fun delete(treaty: Treaty) {
            Treaty.treaties.remove(TownPair(treaty.town1, treaty.town2))
        }
    }
}