/**
 * OreDeposit
 * 
 * Represents single material hidden ore 
 * drop properties (chance, amount, etc...)
 */

package phonon.nodes.objects

import org.bukkit.Material

data class OreDeposit(
    val material: Material,
    val dropChance: Double,
    val minAmount: Int,
    val maxAmount: Int,
    val ymin: Int = 0,
    val ymax: Int = 255,
) {
    // return new ore deposit from merging two
    // merge rules:
    // - sum together dropChance (going >1.0 is okay)
    // - take MAX of both minAmount and maxAmount
    public fun merge(other: OreDeposit): OreDeposit {
        return OreDeposit(
            this.material,
            this.dropChance + other.dropChance,
            Math.max(this.minAmount, other.minAmount),
            Math.max(this.maxAmount, other.maxAmount),
            this.ymin,
            this.ymax
        )
    }
}
