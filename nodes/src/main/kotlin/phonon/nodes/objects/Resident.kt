/**
 * Resident
 * -----------------------------
 * Wrapper around Minecraft player, member
 * of a town.
 * Identified by name which equals unique Minecraft username.
 */

package phonon.nodes.objects

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.scheduler.BukkitTask
import phonon.nodes.Config
import phonon.nodes.Message
import phonon.nodes.chat.ChatMode
import phonon.nodes.serdes.JsonSaveState
import java.util.*

public class Resident(val uuid: UUID, val name: String) {
    var town: Town? = null
    var nation: Nation? = null
    
    // claims power and current elapsed time until power added
    var claims: Int = 0
    var claimsTime: Long = 0L

    // town create cooldown listener
    var townCreateCooldown: Long = 0L

    // flag that player trusted by town
    var trusted: Boolean = false
    
    // player is protecting chest with right click
    var isProtectingChests: Boolean = false
    var chestProtectListener: Listener? = null

    // chat mode config
    var chatMode: ChatMode = ChatMode.GLOBAL
    var prefix: String = ""
    var suffix: String = ""
    var isOfficer: Boolean = false // purely for chat, NOT PERMISSIONS
    var isLeader: Boolean = false  // purely for chat, NOT PERMISSIONS
    var isNationOfficer: Boolean = false // purely for chat, NOT PERMISSIONS
    var isNationLeader: Boolean = false  // purely for chat, NOT PERMISSIONS

    // town teleport thread and flag for outpost
    var teleportThread: BukkitTask? = null
    var isTeleportingToOutpost: Boolean = false    // if true, gives outpost teleport cost refund if teleport fails
    var isTeleportingToNationTown: Boolean = false // if true, gives nation town teleport cost refund if teleport fails

    // town invite
    var invitingNation: Nation? = null
    var invitingTown: Town? = null
    var invitingPlayer: Player? = null
    var inviteThread: BukkitTask? = null

    // minimap
    var minimap: Minimap? = null

    // save state needs update flag
    private var saveState: ResidentSaveState
    private var _needsUpdate = false

    init {
        this.saveState = ResidentSaveState(this)
    }
    
    override public fun hashCode(): Int {
        return this.uuid.hashCode()
    }
    
    // returns player associated with resident
    // returns null when player is offline
    public fun player(): Player? {
        return Bukkit.getPlayer(this.uuid)
    }

    // ===================================
    // Minimap functions
    // each minimap attached to a resident
    // and only viewable by player
    // ===================================

    public fun createMinimap(player: Player, size: Int) {
        // remove any existing minimap
        this.destroyMinimap()

        // create new minimap
        this.minimap = Minimap(this, player, size)
    }

    public fun destroyMinimap() {
        val minimap = this.minimap
        if ( minimap != null ) {
            minimap.destroy()
            this.minimap = null
        }
    }

    // update player minimap if it exists
    public fun updateMinimap(coord: Coord) {
        this.minimap?.render(coord)
    }

    // ===================================

    // print resident info
    public fun printInfo(sender: CommandSender) {
        val town = this.town?.name ?: "${ChatColor.GRAY}None"
        val nation = this.nation?.name ?: "${ChatColor.GRAY}None"

        Message.print(sender, "${ChatColor.BOLD}Player ${this.name}:")
        Message.print(sender, "- Claim Power${ChatColor.WHITE}: ${this.claims}/${Config.playerClaimsMax}")
        Message.print(sender, "- Town${ChatColor.WHITE}: ${town}")
        Message.print(sender, "- Nation${ChatColor.WHITE}: ${nation}")
    }

    /**
     * Immutable save snapshot, must be composed of immutable primitives.
     * Used to generate json string serialization.
     */
    public class ResidentSaveState(r: Resident): JsonSaveState {
        public val uuid = r.uuid
        public val name = r.name
        public val town = r.town?.name
        public val nation = r.nation?.name
        public val claims = r.claims
        public val claimsTime = r.claimsTime
        public val prefix = r.prefix
        public val suffix = r.suffix
        public val trusted = r.trusted
        public val townCreateCooldown = r.townCreateCooldown

        override public var jsonString: String? = null

        override public fun createJsonString(): String {
            val jsonString = ("{"
            + "\"name\":\"${this.name}\","
            + "\"town\":${ if ( this.town !== null ) "\"${this.town}\"" else null },"
            + "\"nation\":${ if ( this.nation !== null ) "\"${this.nation}\"" else null },"
            + "\"claims\":[${this.claims},${this.claimsTime}],"
            + "\"prefix\":\"${this.prefix}\","
            + "\"suffix\":\"${this.suffix}\","
            + "\"trust\":${this.trusted},"
            + "\"townCool\":${this.townCreateCooldown}"
            + "}")
            return jsonString
        }
    }

    // function to let client flag this object as dirty
    public fun needsUpdate() {
        this._needsUpdate = true
    }

    // wrapper to return self as state
    // - returns memoized copy if needsUpdate false
    // - otherwise, parses self
    public fun getSaveState(): ResidentSaveState {
        if ( this._needsUpdate ) {
            this.saveState = ResidentSaveState(this)
            this._needsUpdate = false
        }
        return this.saveState
    }
}