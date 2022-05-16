/**
 * Entity utilities
 */

package phonon.nodes.utils.entity

import org.bukkit.Material
import org.bukkit.entity.EntityType

private val ENTITY_TYPES = EntityType.values()

/**
 * get entity type from string ordinal
 * client assumes responsibility for i within array bounds
 */
public fun entityTypeFromOrdinal(i: Int): EntityType {
    return ENTITY_TYPES[i]
}

/**
 * Get spawn egg material from entity type
 */
public fun spawnEggFromEntity(entityType: EntityType): Material? {
    return when ( entityType ) {
        EntityType.BEE -> Material.BEE_SPAWN_EGG
        EntityType.BLAZE -> Material.BLAZE_SPAWN_EGG
        EntityType.CAT -> Material.CAT_SPAWN_EGG
        EntityType.CAVE_SPIDER -> Material.CAVE_SPIDER_SPAWN_EGG
        EntityType.CHICKEN -> Material.CHICKEN_SPAWN_EGG
        EntityType.COD -> Material.COD_SPAWN_EGG
        EntityType.COW -> Material.COW_SPAWN_EGG
        EntityType.CREEPER -> Material.CREEPER_SPAWN_EGG
        EntityType.DOLPHIN -> Material.DOLPHIN_SPAWN_EGG
        EntityType.DONKEY -> Material.DONKEY_SPAWN_EGG
        EntityType.DROWNED -> Material.DROWNED_SPAWN_EGG
        EntityType.ELDER_GUARDIAN -> Material.ELDER_GUARDIAN_SPAWN_EGG
        EntityType.ENDERMAN -> Material.ENDERMAN_SPAWN_EGG
        EntityType.ENDERMITE -> Material.ENDERMITE_SPAWN_EGG
        EntityType.EVOKER -> Material.EVOKER_SPAWN_EGG
        EntityType.FOX -> Material.FOX_SPAWN_EGG
        EntityType.GHAST -> Material.GHAST_SPAWN_EGG
        EntityType.GUARDIAN -> Material.GUARDIAN_SPAWN_EGG
        EntityType.HOGLIN -> Material.HOGLIN_SPAWN_EGG
        EntityType.HORSE -> Material.HORSE_SPAWN_EGG
        EntityType.HUSK -> Material.HUSK_SPAWN_EGG
        EntityType.LLAMA -> Material.LLAMA_SPAWN_EGG
        EntityType.MAGMA_CUBE -> Material.MAGMA_CUBE_SPAWN_EGG
        EntityType.MUSHROOM_COW -> Material.MOOSHROOM_SPAWN_EGG
        EntityType.MULE -> Material.MULE_SPAWN_EGG
        EntityType.OCELOT -> Material.OCELOT_SPAWN_EGG
        EntityType.PANDA -> Material.PANDA_SPAWN_EGG
        EntityType.PARROT -> Material.PARROT_SPAWN_EGG
        EntityType.PHANTOM -> Material.PHANTOM_SPAWN_EGG
        EntityType.PIG -> Material.PIG_SPAWN_EGG
        EntityType.PIGLIN_BRUTE -> Material.PIGLIN_BRUTE_SPAWN_EGG
        EntityType.PIGLIN -> Material.PIGLIN_SPAWN_EGG
        EntityType.PILLAGER -> Material.PILLAGER_SPAWN_EGG
        EntityType.POLAR_BEAR -> Material.POLAR_BEAR_SPAWN_EGG
        EntityType.PUFFERFISH -> Material.PUFFERFISH_SPAWN_EGG
        EntityType.RABBIT -> Material.RABBIT_SPAWN_EGG
        EntityType.RAVAGER -> Material.RAVAGER_SPAWN_EGG
        EntityType.SALMON -> Material.SALMON_SPAWN_EGG
        EntityType.SHEEP -> Material.SHEEP_SPAWN_EGG
        EntityType.SHULKER -> Material.SHULKER_SPAWN_EGG
        EntityType.SILVERFISH -> Material.SILVERFISH_SPAWN_EGG
        EntityType.SKELETON_HORSE -> Material.SKELETON_HORSE_SPAWN_EGG
        EntityType.SKELETON -> Material.SKELETON_SPAWN_EGG
        EntityType.SLIME -> Material.SLIME_SPAWN_EGG
        EntityType.SPIDER -> Material.SPIDER_SPAWN_EGG
        EntityType.SQUID -> Material.SQUID_SPAWN_EGG
        EntityType.STRAY -> Material.STRAY_SPAWN_EGG
        EntityType.STRIDER -> Material.STRIDER_SPAWN_EGG
        EntityType.TRADER_LLAMA -> Material.TRADER_LLAMA_SPAWN_EGG
        EntityType.TROPICAL_FISH -> Material.TROPICAL_FISH_SPAWN_EGG
        EntityType.TURTLE -> Material.TURTLE_SPAWN_EGG
        EntityType.VEX -> Material.VEX_SPAWN_EGG
        EntityType.VILLAGER -> Material.VILLAGER_SPAWN_EGG
        EntityType.VINDICATOR -> Material.VINDICATOR_SPAWN_EGG
        EntityType.WANDERING_TRADER -> Material.WANDERING_TRADER_SPAWN_EGG
        EntityType.WITCH -> Material.WITCH_SPAWN_EGG
        EntityType.WITHER_SKELETON -> Material.WITHER_SKELETON_SPAWN_EGG
        EntityType.WOLF -> Material.WOLF_SPAWN_EGG
        EntityType.ZOGLIN -> Material.ZOGLIN_SPAWN_EGG
        EntityType.ZOMBIE_HORSE -> Material.ZOMBIE_HORSE_SPAWN_EGG
        EntityType.ZOMBIE -> Material.ZOMBIE_SPAWN_EGG
        EntityType.ZOMBIE_VILLAGER -> Material.ZOMBIE_VILLAGER_SPAWN_EGG
        EntityType.ZOMBIFIED_PIGLIN -> Material.ZOMBIFIED_PIGLIN_SPAWN_EGG
        else -> null
    }
}