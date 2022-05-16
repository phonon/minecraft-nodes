/**
 * IncomeInventory
 * 
 * Inventory container for adding income to town,
 * Uses multiple containers for storing items:
 * - EnumMap<Material, Int> main internal container (maps item -> amount)
 * - Special maps included for items with special cases:
 *   - spawn eggs
 * 
 * Reason is that in >=1.13, materials are flattened so only the normal
 * EnumMap will be required. In 1.8-1.12, we have to handle special cases
 * (e.g. spawn egg + spawn egg meta), and it's easier to add in special cases
 * rather than polluting the normal EnumMap structure to handle 1 item type.
 * 
 * TODO: potentially special case storage for colored items (wool, concrete)
 * if requested by users
 */

package phonon.nodes.objects

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SpawnEggMeta
import phonon.nodes.utils.entity.entityTypeFromOrdinal
import java.util.*

public class IncomeInventory: InventoryHolder {

    // normal items:
    // map material -> current amount of it in storage
    val storage: EnumMap<Material, Int> = EnumMap<Material, Int>(Material::class.java)
    
    // storage for spawn eggs
    val storageSpawnEgg: EnumMap<EntityType, Int> = EnumMap<EntityType, Int>(EntityType::class.java)

    // inventory gui object, only populate when open
    val _inventory: Inventory = Bukkit.createInventory(this, 54, "Town Income")

    // internal, add items to storage
    private fun _add(mat: Material, amount: Int) {
        this.storage.get(mat)?.let { current -> 
            storage.put(mat, current + amount)
        } ?: run {
            storage.put(mat, amount)
        }
    }

    // internal, add items to spawn egg storage
    private fun _addSpawnEgg(type: EntityType, amount: Int) {
        this.storageSpawnEgg.get(type)?.let { current -> 
            storageSpawnEgg.put(type, current + amount)
        } ?: run {
            storageSpawnEgg.put(type, amount)
        }
    }

    // public interface to add new items to storage
    // meta: integer metadata depends on material, either EntityType or damage value
    public fun add(mat: Material, amount: Int, meta: Int = 0) {
        if ( amount <= 0 ) {
            return
        }

        this._add(mat, amount)

        // 1.12 handler, special path for monster egg
        // if ( mat == Material.MONSTER_EGG ) {
        //     val entityType = entityTypeFromOrdinal(meta)
        //     this._addSpawnEgg(entityType, amount)
        // }
        // else {
        //     this._add(mat, amount)
        // }
    }

    // checks if any items in inventory or storage
    public fun empty(): Boolean {
        return ( storage.size == 0 ) && ( storageSpawnEgg.size == 0 )
    }

    // implement getInventory for InventoryHolder
    override public fun getInventory(): Inventory {
        // populate inventory
        while ( this.storage.size > 0 ) {
            val item = this.storage.iterator().next()
            val material = item.key
            val amount = item.value
            this.storage.remove(material)

            val itemsFailedToAdd = this._inventory.addItem(ItemStack(material, amount))
            if ( itemsFailedToAdd.size > 0 ) {
                val leftoverItems = itemsFailedToAdd.get(0)!!
                this.storage.put(material, leftoverItems.amount)
                return this._inventory
            }
        }
        
        // 1.12 spawn egg add
        // while ( this.storageSpawnEgg.size > 0 ) {
        //     val item = this.storageSpawnEgg.iterator().next()
        //     val type = item.key
        //     val amount = item.value
        //     this.storageSpawnEgg.remove(type)

        //     // create spawn egg with specific type
        //     val itemStack = ItemStack(Material.MONSTER_EGG, amount)
        //     val itemMeta: SpawnEggMeta = itemStack.getItemMeta() as SpawnEggMeta
        //     itemMeta.setSpawnedType(type)
        //     itemStack.setItemMeta(itemMeta)

        //     val itemsFailedToAdd = this._inventory.addItem(itemStack)
        //     if ( itemsFailedToAdd.size > 0 ) {
        //         val leftoverItems = itemsFailedToAdd.get(0)!!
        //         this.storageSpawnEgg.put(type, leftoverItems.amount)
        //         return this._inventory
        //     }
        // }

        return this._inventory
    }

    // moves items into storage and clear inventory
    // use this before saving game state
    // (still potential to dupe items if storage cleared and
    // game crashes before next save finishes)
    //
    // By default, only pushes to backend if no players are viewing
    // the income (so items don't seem like they're disappearing)
    // "force" option force-pushes items to backend (e.g. on server close)
    //
    // return if items moved (needed to determine if town needsUpdate()):
    // - true: if any items moved
    // - false: if no items moved
    public fun pushToStorage(force: Boolean): Boolean {
        var hasMovedItems = false

        val viewers = this._inventory.viewers
        if ( viewers.size == 0 || force ) {
            for ( itemStack in this._inventory.iterator() ) {
                if ( itemStack != null ) {
                    // 1.12 special path spawn eggs
                    // if ( itemStack.type == Material.MONSTER_EGG ) {
                    //     val meta: SpawnEggMeta = itemStack.getItemMeta() as SpawnEggMeta
                    //     val entityType = meta.getSpawnedType()
                    //     this._addSpawnEgg(entityType, itemStack.amount)
                    // }
                    // else {
                    //     this._add(itemStack.type, itemStack.amount)
                    // }

                    this._add(itemStack.type, itemStack.amount)
    
                    hasMovedItems = true
                }
            }
            this._inventory.clear()
        }

        return hasMovedItems
    }

}
