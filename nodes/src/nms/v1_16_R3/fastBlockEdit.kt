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
 */
package phonon.nms.blockedit

import java.util.HashMap
import java.util.HashSet
import net.minecraft.server.v1_16_R3.*
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer
import org.bukkit.craftbukkit.v1_16_R3.util.CraftMagicNumbers
import org.bukkit.entity.Player

public class FastBlockEditSession(
    val bukkitWorld: org.bukkit.World
) {
    // nms world
    private val world: World = (bukkitWorld as CraftWorld).getHandle()

    // blocks that were modified in this session
    private val modified: HashMap<BlockPosition, IBlockData> = hashMapOf()

    /**
     * Mark a block as updated to a new material. This is lazy
     * and does not actually change world until `execute` is called.
     */
    public fun setBlock(x: Int, y: Int, z: Int, material: Material) {
        modified.put(BlockPosition(x, y, z), CraftMagicNumbers.getBlock(material).getBlockData())
    }

    /**
     * Get material of block at location (x, y, z) in this session's
     * world. This will also check the modified material stored in this
     * session object. 
     */
    public fun getBlock(x: Int, y: Int, z: Int): Material {
        val bData = modified.get(BlockPosition(x, y, z))
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
        val chunks: HashSet<Chunk> = hashSetOf()
        for ( (bPos, bData) in modified ) {
            val chunk = world.getChunkProvider().getChunkAt(bPos.getX() shr 4, bPos.getZ() shr 4, true)
            if ( chunk !== null ) {
                chunks.add(chunk)
                chunk.setType(bPos, bData, false)
            }
        }

        // update lights
        val lightingEngine: LightEngine = world.getChunkProvider().getLightEngine()
        if ( updateLighting ) {
            for ( pos in modified.keys ) {
                lightingEngine.a(pos)
            }
        }


        //unload & load chunk data
        for ( chunk in chunks ) {
            // val unload = PacketPlayOutUnloadChunk(chunk.getPos().x, chunk.getPos().z)
            val load: PacketPlayOutMapChunk = PacketPlayOutMapChunk(chunk, 65535)
            val light: PacketPlayOutLightUpdate = PacketPlayOutLightUpdate(chunk.getPos(), lightingEngine, true)

            for ( p in Bukkit.getOnlinePlayers() ) {
                val ep: EntityPlayer = (p as CraftPlayer).getHandle()
                val dist = Bukkit.getViewDistance() + 1
                val chunkX = ep.chunkX
                val chunkZ = ep.chunkZ
                if ( chunk.getPos().x < chunkX - dist ||
                     chunk.getPos().x > chunkX + dist ||
                     chunk.getPos().z < chunkZ - dist ||
                     chunk.getPos().z > chunkZ + dist ) {
                    continue
                }
                // ep.playerConnection.sendPacket(unload)
                ep.playerConnection.sendPacket(load)
                ep.playerConnection.sendPacket(light)
            }
        }

        //clear modified blocks
        modified.clear()
    }
}