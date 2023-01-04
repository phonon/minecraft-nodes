/**
 * Tasks for copying nodes world save state files to the dynmap plugin
 * folder.
 */

package phonon.nodes.tasks

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import phonon.nodes.utils.saveStringToFile

/**
 * Task for copying world.json or towns.json to dynmap folder.
 */
public class TaskCopyToDynmap(
    val pathTownsJson: Path,
    val pathDynmapDir: Path,
    val pathDynmapJson: Path,
): Runnable {
    override fun run() {
        Files.createDirectories(pathDynmapDir)
        Files.copy(pathTownsJson, pathDynmapJson, StandardCopyOption.REPLACE_EXISTING) 
    }
}

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
public class TaskSaveDynmapClaimsConfig(
    val territoryCostBase: Int,
    val territoryCostScale: Double,
    val pathDynmapConfig: Path,
): Runnable {
    
    override fun run() {
        val configJson = (
            "{\"meta\":{\"type\":\"config\"},"
            + "\"territoryCost\":{\"constant\":${territoryCostBase},\"scale\":${territoryCostScale}}}"
        )

        saveStringToFile(configJson, pathDynmapConfig)
    }

}