/**
 * Async task for writing string to a file
 * For saving towns.json after serialization
 * without blocking main thread
 */

package phonon.nodes.tasks

import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.Future

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