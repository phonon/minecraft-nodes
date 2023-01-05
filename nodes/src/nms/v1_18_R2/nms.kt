/**
 * NMS 1.18.2 type aliases
 * https://nms.screamingsandals.org/1.18.2/
 * 
 * NOTE: for 1.18.2, use the "Mojang" names for classes and methods.
 */

package phonon.nodes.nms

import java.util.Optional
import net.minecraft.core.BlockPos as NMSBlockPos
import net.minecraft.world.entity.player.Player as NMSPlayer
import net.minecraft.world.level.block.state.BlockState as NMSBlockState
import net.minecraft.world.level.chunk.LevelChunk as NMSChunk
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextComponent
import net.minecraft.network.PacketListener as NMSPacketListener
import net.minecraft.network.protocol.Packet as NMSPacket
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket as NMSPacketLevelChunkWithLightPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket as NMSPacketSetEntityData
import net.minecraft.network.syncher.EntityDataSerializers as NMSEntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData as NMSSynchedEntityData
import org.bukkit.craftbukkit.v1_18_R2.CraftWorld
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftEntity
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftPlayer
import org.bukkit.craftbukkit.v1_18_R2.util.CraftMagicNumbers
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player

// re-exported type aliases
internal typealias NMSBlockPos = NMSBlockPos
internal typealias NMSBlockState = NMSBlockState
internal typealias NMSChunk = NMSChunk
internal typealias NMSPlayer = NMSPlayer
internal typealias NMSPacketLevelChunkWithLightPacket = NMSPacketLevelChunkWithLightPacket
internal typealias NMSPacketSetEntityData = NMSPacketSetEntityData
internal typealias CraftWorld = CraftWorld
internal typealias CraftPlayer = CraftPlayer
internal typealias CraftMagicNumbers = CraftMagicNumbers

/**
 * Wrapper for getting Bukkit player connection and sending packet.
 * Player connection field and sendPacket name differ in 1.18.2 vs 1.16.5.
 */
internal fun <T: NMSPacketListener> Player.sendPacket(p: NMSPacket<T>) {
    return (this as CraftPlayer).handle.connection.send(p)
}

/**
 * Create custom name packet for armor stand entity.
 * https://github.com/Rosewood-Development/RoseDisplays/tree/master/NMS/v1_18_R1/src/main/java/dev/rosewood/rosedisplays/nms/v1_18_R1
 */
public fun ArmorStand.createArmorStandNamePacket(name: String): NMSPacketSetEntityData {
    val entityId = (this as CraftEntity).handle.id
    val nameComponent = Optional.of(TextComponent(name) as Component)
    
    val dataItems = ArrayList<NMSSynchedEntityData.DataItem<Any>>()
    dataItems.add(NMSSynchedEntityData.DataItem(NMSEntityDataSerializers.BYTE.createAccessor(0), 0x20.toByte()) as NMSSynchedEntityData.DataItem<Any>) // invisible
    dataItems.add(NMSSynchedEntityData.DataItem(NMSEntityDataSerializers.OPTIONAL_COMPONENT.createAccessor(2), nameComponent) as NMSSynchedEntityData.DataItem<Any>)
    dataItems.add(NMSSynchedEntityData.DataItem(NMSEntityDataSerializers.BOOLEAN.createAccessor(3), true) as NMSSynchedEntityData.DataItem<Any>)
    dataItems.add(NMSSynchedEntityData.DataItem(NMSEntityDataSerializers.BOOLEAN.createAccessor(5), true) as NMSSynchedEntityData.DataItem<Any>)

    return NMSPacketSetEntityData(entityId, SynchedEntityDataWrapper(dataItems), false)
}

public class SynchedEntityDataWrapper(
    private val dataItems: List<NMSSynchedEntityData.DataItem<Any>>,
): NMSSynchedEntityData(null) {
    override fun packDirty(): List<NMSSynchedEntityData.DataItem<Any>> {
        return this.dataItems
    }
}