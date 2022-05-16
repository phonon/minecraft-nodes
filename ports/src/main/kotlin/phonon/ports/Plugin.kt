/**
 * Ports system
 * 
 * Note: theres multithreaded read to save ports state with
 * non concurrent/synchronized HashMap. Other thread only reads
 * to save state, no structural change to HashMap, so mostly ok
 * but may potentially cause mismatched save
 */

package phonon.ports

import org.bukkit.plugin.java.JavaPlugin

/*
 * Implement bukkit plugin interface
 */
public class PortPlugin : JavaPlugin() {

    override fun onEnable() {
        // measure load time
        val timeStart = System.currentTimeMillis()

        val logger = this.getLogger()
        val pluginManager = this.getServer().getPluginManager()

        // load everything
        Ports.initialize(this)
        Ports.reload()

        // register listener
        pluginManager.registerEvents(PortsProtectionListener(), this)

        // register commands
        this.getCommand("port")?.setExecutor(PortCommand)
        this.getCommand("portadmin")?.setExecutor(PortAdminCommand)

        // print load time
        val timeEnd = System.currentTimeMillis()
        val timeLoad = timeEnd - timeStart
        logger.info("Enabled in ${timeLoad}ms")

        // print success message
        logger.info("now this is epic")
    }

    override fun onDisable() {
        Ports.onDisable()
        logger.info("wtf i hate xeth now")
    }
}
