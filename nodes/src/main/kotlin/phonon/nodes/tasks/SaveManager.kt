/**
 * Scheduler for saving Nodes world state to towns.json
 * 
 * Runs world save to towns.json on a fixed tick schedule.
 * If we save everytime world state updates, players can lag servers
 * by spamming commands. Running on fixed schedules avoids
 * this exploit.
 * 
 */

package phonon.nodes.tasks

import java.nio.file.Files
import java.nio.file.Paths
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import phonon.nodes.Nodes
import phonon.nodes.Config

public object SaveManager {

    private var task: BukkitTask? = null

    public fun start(plugin: Plugin, period: Long) {
        if ( this.task !== null ) {
            return
        }

        // scheduler for saving world
        val task = object: Runnable {

            init {
                // create save folder if it does not exist
                Files.createDirectories(Paths.get(Config.pathPlugin).normalize())
            }

            override public fun run() {
                // schedule main thread to run save
                Bukkit.getScheduler().runTask(plugin, object: Runnable {
                    override fun run() {
                        Nodes.saveWorldAsync()
                    }
                })
            }
        }

        this.task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, period, period)
    }

    public fun stop() {
        val task = this.task
        if ( task === null ) {
            return
        }

        task.cancel()
        this.task = null
    }
}