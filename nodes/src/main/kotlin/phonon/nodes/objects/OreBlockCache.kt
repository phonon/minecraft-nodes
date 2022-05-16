/**
 * Cache broken block locations used for hidden ore
 * Used to avoid exploits of placing and re-breaking blocks
 * to get ore.
 * 
 * Object is wrapper around hashset, client should only add
 * blocks into cache (removing handled internally), and use
 * contains to check if block exists
 * 
 * Size input: number of blocks to cache before overwriting
 */

package phonon.nodes.objects

import java.util.Collections
import org.bukkit.block.Block

public class OreBlockCache(val maxSize: Int) {
    private val cache: MutableSet<Block> = Collections.newSetFromMap(object: LinkedHashMap<Block, Boolean>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Block, Boolean>): Boolean {
            return this.size > maxSize
        }
    })

    public fun add(block: Block) {
        this.cache.add(block)
    }
    
    public fun contains(block: Block): Boolean {
        return cache.contains(block)
    }
}