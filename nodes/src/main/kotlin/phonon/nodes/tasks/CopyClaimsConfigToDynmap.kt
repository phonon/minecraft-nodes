/**
 * Copy global claims config into dynmap folder as "config.json":
 * {
 *   "meta":{"type":"config"},
 *   "territoryCost":{
 *       "constant": 10,
 *       "scale": 0.25
 *   }
 * }
 */

package phonon.nodes.tasks

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import phonon.nodes.Config
import phonon.nodes.Nodes

public object CopyClaimsConfigToDynmap {
    
    public fun run(plugin: JavaPlugin) {
        if ( Nodes.dynmap == false ) {
            return
        }

        val configJson = (
            "{\"meta\":{\"type\":\"config\"},"
            + "\"territoryCost\":{\"constant\":${Config.territoryCostBase},\"scale\":${Config.territoryCostScale}}}"
        )

        Bukkit.getScheduler().runTaskAsynchronously(plugin, FileWriteTask(configJson, Nodes.DYNMAP_PATH_NODES_CONFIG))
    }

}