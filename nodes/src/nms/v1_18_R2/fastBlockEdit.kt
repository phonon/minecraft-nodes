/**
 * Fast set blocks
 * Because default world.setBlock or block.setType is very slow (lighting + packets)
 * 
 * See: https://www.spigotmc.org/threads/how-to-set-blocks-incredibly-fast.476097/
 * 
 * ```
 * This is code I actually use.
 * The code itself is very clear.
 * What I do here is just simple 3 steps:
 * 1. modify block data in memory
 * 2. update lighting
 * 3. unload & load chunk data
 * 
 * Minecraft server contains data as of what chunks are loaded for players. However, it's not visible and we have to check whether a modified chunk is in view distance or not.
 * 
 * There are several cons that you have to take care of.
 * 1. You can modify blocks in unloaded chunks. However, it's slower than usual.
 * 2. Block update does not happen.
 * 3. Light update is not perfect when you edit unloaded chunks
 * 4. Light update doesn't work well when you have Sodium or some other lighting mod installed, because lighting is cached and ignore lighting update packet
 * ```
 * - Toshimichi
 * 
 * This is sufficiently different between 1.16.5 and 1.18.2: 
 * the chunk + lighting packet format changed. (Along with nms mapping
 * changes with de-obfuscated 1.17+ mappings.)
 * So just keep this separately implemented per version.
 */
package phonon.nms.blockedit

import java.util.HashMap
import java.util.HashSet
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import phonon.nodes.nms.NMSBlockPos
import phonon.nodes.nms.NMSBlockState
import phonon.nodes.nms.NMSChunk
import phonon.nodes.nms.NMSPlayer
import phonon.nodes.nms.NMSPacketLevelChunkWithLightPacket
import phonon.nodes.nms.CraftWorld
import phonon.nodes.nms.CraftPlayer
import phonon.nodes.nms.CraftMagicNumbers

public class FastBlockEditSession(
    val bukkitWorld: org.bukkit.World
) {
    // nms world
    private val world = (bukkitWorld as CraftWorld).getHandle()
    
    // blocks that were modified in this session
    private val modified: HashMap<NMSBlockPos, NMSBlockState> = hashMapOf()

    /**
     * Mark a block as updated to a new material. This is lazy
     * and does not actually change world until `execute` is called.
     */
    public fun setBlock(x: Int, y: Int, z: Int, material: Material) {
        modified.put(NMSBlockPos(x, y, z), CraftMagicNumbers.getBlock(material).defaultBlockState())
    }

    /**
     * Get material of block at location (x, y, z) in this session's
     * world. This will also check the modified material stored in this
     * session object. 
     */
    public fun getBlock(x: Int, y: Int, z: Int): Material {
        val bData = modified.get(NMSBlockPos(x, y, z))
        if ( bData != null ) {
            return CraftMagicNumbers.getMaterial(bData).getItemType()
        }
        return Location(bukkitWorld, x.toDouble(), y.toDouble(), z.toDouble()).getBlock().getType()
    }

    /**
     * Run the block changes and send updates to players in view distance.
     * 
     * Chunk source api:
     * https://nms.screamingsandals.org/1.18.2/net/minecraft/server/level/ServerChunkCache.html
     */
    public fun execute(updateLighting: Boolean) {
        //modify blocks
        val chunks: HashSet<NMSChunk> = hashSetOf()
        for ( (bPos, bState) in modified ) {
            val chunk = world.getChunkSource().getChunk(bPos.getX() shr 4, bPos.getZ() shr 4, true)
            if ( chunk !== null ) {
                chunks.add(chunk)
                chunk.setBlockState(bPos, bState, false)
            }
        }

        // update lighting
        val lightingEngine = world.getChunkSource().getLightEngine()
        if ( updateLighting ) {
            for ( pos in modified.keys ) {
                lightingEngine.checkBlock(pos)
            }
        }

        // send chunk updates to players
        for ( chunk in chunks ) {
            val packetChunk = NMSPacketLevelChunkWithLightPacket(chunk, chunk.getLevel().getLightEngine(), null, null, true)

            for ( p in Bukkit.getOnlinePlayers() ) {
                val nmsPlayer = (p as CraftPlayer).getHandle()
                val dist = Bukkit.getViewDistance() + 1
                val playerChunk = nmsPlayer.chunkPosition()
                if ( chunk.getPos().x < playerChunk.x - dist ||
                     chunk.getPos().x > playerChunk.x + dist ||
                     chunk.getPos().z < playerChunk.z - dist ||
                     chunk.getPos().z > playerChunk.z + dist ) {
                    continue
                }
                nmsPlayer.connection.send(packetChunk)
            }
        }
    }
}