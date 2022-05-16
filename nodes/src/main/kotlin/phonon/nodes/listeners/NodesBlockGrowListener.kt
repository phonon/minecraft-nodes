package phonon.nodes.listeners

import org.bukkit.Chunk
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockGrowEvent
import phonon.nodes.Nodes
import phonon.nodes.Config
import phonon.nodes.objects.Territory

public class NodesBlockGrowListener: Listener {

    @EventHandler
    public fun onBlockGrow(event: BlockGrowEvent) {
        val block = event.block
        val blockY = block.location.blockY

        // checks that crop meets sky light level requirements
        if ( block.lightFromSky < Config.cropsMinSkyLight ) {
            event.setCancelled(true)
            return
        }

        // check block within height range allowed for farming
        if ( blockY < Config.cropsMinYHeight || blockY > Config.cropsMaxYHeight ) {
            event.setCancelled(true)
            return
        }

        val chunk: Chunk = event.block.chunk
        val territory: Territory? = Nodes.getTerritoryFromChunk(chunk)

        // check if territory is wilderness (either no territory or unowned land)
        if ( !Config.allowCropsInWilderness && ( territory === null || territory.town === null ) ) {
            event.setCancelled(true)
            return
        }
        
        if ( territory !== null && territory.cropsCanGrow ) {
            
            // get block state that plant will grow into
            // -> for plants that grow like sugarcane or vines,
            //    using event.block = AIR because block has not spread there
            val blockState = event.getNewState()
            var plant: Material = blockState.type

            if ( !territory.crops.containsKey(plant) ) {
                val altName = Config.cropAlternativeNames.get(plant)
                if ( altName === null || !territory.crops.containsKey(altName) ) {
                    event.setCancelled(true)
                    return
                }
                
                // else, change plant type
                plant = altName
            }

            // do roll
            val randNum = Math.random()
            val growRate = territory.crops.get(plant) ?: 0.0
            if ( randNum <= growRate ) {
                return
            }
            
            // old 1.12.2 handler
            // if ( plant == Material.CROPS ) {
            //     if ( territory.crops.containsKey(Material.WHEAT) ) {
            //         if (randNum <= territory.crops[Material.WHEAT]!! ) {
            //             return
            //         }
            //     }
            // }
            // else if ( plant == Material.MELON_STEM ) {
            //     if ( territory.crops.containsKey(Material.MELON) ) {
            //         if (randNum <= territory.crops[Material.MELON]!! ) {
            //             return
            //         }
            //     }
            // }
            // else if ( plant == Material.PUMPKIN_STEM ) {
            //     if ( territory.crops.containsKey(Material.PUMPKIN) ) {
            //         if (randNum <= territory.crops[Material.PUMPKIN]!! ) {
            //             return
            //         }
            //     }
            // }
            // else if ( plant == Material.BEETROOT_BLOCK ) {
            //     if ( territory.crops.containsKey(Material.BEETROOT) ) {
            //         if (randNum <= territory.crops[Material.BEETROOT]!! ) {
            //             return
            //         }
            //     }
            // }
            // else if ( territory.crops.containsKey(plant) ) {
            //     if ( randNum <= territory.crops[plant]!! ) {
            //         return
            //     }
            // }
        }

        // else, cancel event
        event.setCancelled(true)
    }

}