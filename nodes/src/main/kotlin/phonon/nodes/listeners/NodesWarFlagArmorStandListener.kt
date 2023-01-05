package phonon.nodes.listeners

import org.bukkit.NamespacedKey
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.world.EntitiesLoadEvent
import org.bukkit.persistence.PersistentDataType
import phonon.nodes.war.NODES_ARMORSTAND_KEY

public class NodesWarFlagArmorStandListener: Listener {

    /**
     * If an war flag label armor stand is loaded from a chunk, it is most
     * likely a stale entity. E.g. normally when server closed, armor stands
     * removed, but if server crashes there will be dangling armor stands.
     * This automatically deletes any armorstands with associated nodes plugin key.
     * 
     * If we accidentally delete a valid armorstand when a chunk becomes loaded,
     * that's okay because the war flag system will automatically respawn it.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public fun onLoad(event: EntitiesLoadEvent) {
        for ( entity in event.entities ) {
            if ( entity.type == EntityType.ARMOR_STAND && entity.persistentDataContainer.has(NODES_ARMORSTAND_KEY, PersistentDataType.INTEGER) ) {
                entity.remove()
            }
        }
    }
}