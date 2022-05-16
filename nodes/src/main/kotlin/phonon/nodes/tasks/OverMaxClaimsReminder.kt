/**
 * Income background scheduler to periodically run
 * message that towns are over max claims
 */

package phonon.nodes.tasks

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import phonon.nodes.Nodes
import phonon.nodes.Config


public object OverMaxClaimsReminder {

    private var task: BukkitTask? = null

    // run scheduler for saving backups
    public fun start(plugin: Plugin, period: Long) {
        if ( this.task !== null ) {
            return
        }

        // scheduler for writing backups
        val task = object: Runnable {

            override public fun run() {
                // schedule main thread to run income tick
                Bukkit.getScheduler().runTask(plugin, object: Runnable {
                    override fun run() {
                        Nodes.overMaxClaimsReminder()
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