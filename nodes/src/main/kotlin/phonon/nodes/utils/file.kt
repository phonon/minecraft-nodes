/**
 * Utils for file io.
 */
package phonon.nodes.utils

import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.Path
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.StandardOpenOption
import java.util.concurrent.Future

/**
 * Synchronously save string to file from given path.
 */
public fun saveStringToFile(str: String, path: Path) {
    val buffer = ByteBuffer.wrap(str.toByteArray())
    val fileChannel: AsynchronousFileChannel = AsynchronousFileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    val operation: Future<Int> = fileChannel.write(buffer, 0)
    operation.get()
}

/**
 * Load long number from file
 */
public fun loadLongFromFile(path: Path): Long? {
    if ( Files.exists(path) ) {
        try {
            val numString = String(Files.readAllBytes(path))
            try {
                val num = numString.toLong()
                return num
            }
            catch ( e: Exception ) {
                e.printStackTrace()
            }
        }
        catch ( e: Exception ) {
            e.printStackTrace()
        }
    }

    return null
}

/**
 * Runnable task for writing string to a file, with optional callback
 * to run after writing is complete.
 */
public class FileWriteTask (
    str: String,
    val path: Path,
    val callback: (()->Unit)? = null
): Runnable {
    val buffer = ByteBuffer.wrap(str.toByteArray())

    override public fun run() {
        val fileChannel: AsynchronousFileChannel = AsynchronousFileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        
        val operation: Future<Int> = fileChannel.write(buffer, 0);
        
        operation.get()

        if ( callback != null ) {
            callback.invoke()
        }
    }
}