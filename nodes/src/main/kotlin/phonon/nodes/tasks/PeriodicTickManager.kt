/**
 * Centralized handler for long periodic tasks:
 * - Income, backup, cooldowns
 */

package phonon.nodes.tasks

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import phonon.nodes.Config
import phonon.nodes.Nodes


object PeriodicTickManager {

    private var task: BukkitTask? = null

    // previous tick time
    private var previousTime: Long = 0L

    // run scheduler for saving backups
    fun start(plugin: Plugin, period: Long) {
        if ( task !== null ) {
            return
        }
        
        // initialize previous time
        previousTime = System.currentTimeMillis()

        // scheduler for writing backups
        val task = Runnable { // update time tick
            // capture previousTime locally so that functions that require dt = currTime - prevTime
            // have safe previous time value, in the rare/~impossible case that the main thread
            // lags so much that this runs and sets previousTime again before scheduled tasks run
            val currTime = System.currentTimeMillis()
            val capturedPreviousTime = previousTime
            previousTime = currTime

            // =================================
            // backup cycle
            // =================================
            if ( currTime > Nodes.lastBackupTime + Config.backupPeriod) {
                Nodes.lastBackupTime = currTime
                Nodes.doBackup()

                // save current time
                Bukkit.getScheduler().runTaskAsynchronously(Nodes.plugin!!, FileWriteTask(currTime.toString(),
                    Config.pathLastBackupTime, null))
            }

            // =================================
            // income cycle
            // =================================
            if ( currTime > Nodes.lastIncomeTime + Config.incomePeriod) {
                Nodes.lastIncomeTime = currTime

                // schedule main thread to run task
                Bukkit.getScheduler().runTask(plugin, Runnable { Nodes.runIncome() })

                // save current time
                Bukkit.getScheduler().runTaskAsynchronously(Nodes.plugin!!, FileWriteTask(currTime.toString(),
                    Config.pathLastIncomeTime, null))
            }

            // =================================
            // town, resident, truce cooldowns
            // pipeline by running on offset
            // =================================
            Bukkit.getScheduler().runTask(plugin, Runnable {
                val currTime = System.currentTimeMillis()
                val dt = currTime - capturedPreviousTime
                Nodes.townMoveHomeCooldownTick(dt)
            })
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                val currTime = System.currentTimeMillis()
                val dt = currTime - capturedPreviousTime
                Nodes.claimsPowerRamp(dt)
            }, 1L)
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                val currTime = System.currentTimeMillis()
                val dt = currTime - capturedPreviousTime
                Nodes.claimsPenaltyDecay(dt)
            }, 2L)
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                val currTime = System.currentTimeMillis()
                val dt = currTime - capturedPreviousTime
                Nodes.residentTownCreateCooldownTick(dt)
            }, 3L)
            Bukkit.getScheduler().runTaskLater(plugin, Runnable { Nodes.truceTick() }, 4L)
        }

        PeriodicTickManager.task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, period, period)
    }

    
    fun stop() {
        val task = task
        if ( task === null ) {
            return
        }

        task.cancel()
        PeriodicTickManager.task = null
    }
}