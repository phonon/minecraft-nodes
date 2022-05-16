package phonon.nodes.listeners

import java.util.concurrent.ThreadLocalRandom
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason
import phonon.nodes.Config
import phonon.nodes.Nodes
import phonon.nodes.objects.Territory
import phonon.nodes.utils.entity.spawnEggFromEntity

public class NodesEntityBreedListener: Listener {

    // Cancelling breed event causes entities to keep breeding until timeout
    // Cannot stop breeding, instead delete spawned child with % chance
    // from territory resource nodes
    @EventHandler
    public fun onEntitySpawn(event: CreatureSpawnEvent) {
        
        val spawnReason = event.getSpawnReason()
        if ( spawnReason != SpawnReason.BREEDING && spawnReason != SpawnReason.DISPENSE_EGG && spawnReason != SpawnReason.EGG ) {
            return
        }

        val location = event.location
        val block = location.block
        val blockY = location.blockY

        // checks that breed event meets sky light level requirements
        if ( block.lightFromSky < Config.breedingMinSkyLight ) {
            event.setCancelled(true)
            return
        }

        // check breed event within height range allowed
        if ( blockY < Config.breedingMinYHeight || blockY > Config.breedingMaxYHeight ) {
            event.setCancelled(true)
            return
        }
        
        val entity: Entity = event.getEntity() //father and mother are the same entity type
        val territory: Territory? = Nodes.getTerritoryFromChunk(entity.location.chunk)        

        // check if territory is wilderness (either no territory or unowned land)
        if ( !Config.allowBreedingInWilderness && ( territory === null || territory.town === null ) ) {
            event.setCancelled(true)
            return
        }
        
        if ( territory !== null && territory.animalsCanBreed ) {
            val entityType = entity.type
            val breedRate = territory.animals.get(entityType)

            // successful breed
            val randNum: Double = ThreadLocalRandom.current().nextDouble()
            if ( breedRate !== null && randNum <= breedRate ) {
                // do tax event check
                val territoryOccupier = territory.occupier
                if ( territoryOccupier != null && Math.random() <= Config.taxAnimalRate ) {
                    // convert breeded animal to spawn egg given to occupier
                    // 1.12
                    // Nodes.addToIncome(territoryOccupier, Material.MONSTER_EGG, 1, entity.type.ordinal)
                    // 1.13+
                    val spawnEgg = spawnEggFromEntity(entityType)
                    if ( spawnEgg !== null ) {
                        Nodes.addToIncome(territoryOccupier, spawnEgg, 1)
                    }

                    event.entity.remove()
                }
                // handle town over max claims penalty
                else if ( territory?.town?.isOverClaimsMax == true ) {
                    if ( Math.random() < Config.overClaimsMaxPenalty ) {
                        event.entity.remove()
                    }
                }

                return
            }
        }

        event.entity.remove()
    }

}