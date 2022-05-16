package phonon.ports

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
public fun filterByStart(list: List<String>, start: String): List<String> {
    val startLowerCase = start.lowercase()
    return list.filter { s -> s.lowercase().startsWith(startLowerCase) }
}

/**
 * Create progress bar string. Input should be double
 * in range [0.0, 1.0] marking progress.
 */
public fun progressBar(progress: Double): String {
    // available shades
    // https://en.wikipedia.org/wiki/Box-drawing_character
    // val SOLID = 2588     // full solid block
    // val SHADE0 = 2592    // medium shade
    // val SHADE1 = 2593    // dark shade

    return when ( Math.round(progress * 10.0).toInt() ) {
        0 ->  "\u2503\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2503"
        1 ->  "\u2503\u2588\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2503"
        2 ->  "\u2503\u2588\u2588\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2503"
        3 ->  "\u2503\u2588\u2588\u2588\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2503"
        4 ->  "\u2503\u2588\u2588\u2588\u2588\u2592\u2592\u2592\u2592\u2592\u2592\u2503"
        5 ->  "\u2503\u2588\u2588\u2588\u2588\u2588\u2592\u2592\u2592\u2592\u2592\u2503"
        6 ->  "\u2503\u2588\u2588\u2588\u2588\u2588\u2588\u2592\u2592\u2592\u2592\u2503"
        7 ->  "\u2503\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2592\u2592\u2592\u2503"
        8 ->  "\u2503\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2592\u2592\u2503"
        9 ->  "\u2503\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2592\u2503"
        10 -> "\u2503\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2503"
        else -> ""
    }
}

public fun saveStringToFile(str: String, path: Path) {
    val buffer = ByteBuffer.wrap(str.toByteArray())
    val fileChannel: AsynchronousFileChannel = AsynchronousFileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    val operation: Future<Int> = fileChannel.write(buffer, 0)
    operation.get()
}