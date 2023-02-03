package phonon.refinery

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.Path
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.StandardOpenOption
import java.util.concurrent.Future
import org.bukkit.Location
import org.bukkit.entity.Entity

// matches any string in a list of strings that 
// begins with start
internal fun filterByStart(list: List<String>, start: String): List<String> {
    val startLowerCase = start.lowercase()
    return list.filter { s -> s.lowercase().startsWith(startLowerCase) }
}

internal fun saveStringToFile(str: String, path: Path) {
    val buffer = ByteBuffer.wrap(str.toByteArray())
    val fileChannel: AsynchronousFileChannel = AsynchronousFileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    val operation: Future<Int> = fileChannel.write(buffer, 0)
    operation.get()
}