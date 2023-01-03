/**
 * NMS 1.18.2 type aliases
 * https://nms.screamingsandals.org/1.18.2/
 * 
 * NOTE: for 1.18.2, use the "Mojang" names for classes and methods.
 */

package phonon.xc.nms

import net.minecraft.core.BlockPos as NMSBlockPos
import net.minecraft.world.entity.player.Player as NMSPlayer
import net.minecraft.world.level.block.state.BlockState as NMSBlockState
import net.minecraft.world.level.chunk.LevelChunk as NMSChunk
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket as NMSPacketLevelChunkWithLightPacket
import org.bukkit.craftbukkit.v1_18_R2.CraftWorld
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftPlayer
import org.bukkit.craftbukkit.v1_18_R2.util.CraftMagicNumbers

// re-exported type aliases
internal typealias NMSBlockPos = NMSBlockPos
internal typealias NMSBlockState = NMSBlockState
internal typealias NMSChunk = NMSChunk
internal typealias NMSPlayer = NMSPlayer
internal typealias NMSPacketLevelChunkWithLightPacket = NMSPacketLevelChunkWithLightPacket
internal typealias CraftWorld = CraftWorld
internal typealias CraftPlayer = CraftPlayer
internal typealias CraftMagicNumbers = CraftMagicNumbers