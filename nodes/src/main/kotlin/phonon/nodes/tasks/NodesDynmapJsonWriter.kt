/**
 * Async writer to copy saved towns.json
 * to dynmap web/nodes/towns.json
 * Async task to avoid blocking main thread
 */

package phonon.nodes.tasks

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

public class NodesDynmapJsonWriter(
    val pathTownsJson: Path,
    val pathDynmapDir: Path,
    val pathDynmapJson: Path
): Runnable {
    
    init {
        Files.createDirectories(pathDynmapDir)
    }

    override public fun run() {
        Files.copy(pathTownsJson, pathDynmapJson, StandardCopyOption.REPLACE_EXISTING) 
    }
}