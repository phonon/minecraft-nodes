// CURRENTLY CANNOT RUN UNIT TESTS WITHOUT SERVER INSTANCE
// https://github.com/seeseemelk/MockBukkit

// Bukkit requires globals not instantiated till server runs
// i.e. Bukkit.getItemFactory() returns null pointer exception
// need to use mock server for actual unit testing

// package phonon.nodes.objects.OreSamplerTest

// import kotlin.test.Test
// import kotlin.test.assertNotNull

// import org.bukkit.Material
// import phonon.nodes.objects.OreDeposit
// import phonon.nodes.objects.OreSampler
// import org.bukkit.Bukkit

// public class OreSamplerTest {
//     @Test fun test() {
//         // create ores
//         val ore1 = OreDeposit(Material.DIAMOND, 0.1, 1, 1)
//         val ore2 = OreDeposit(Material.EMERALD, 0.1, 1, 1)
//         val ore3 = OreDeposit(Material.GOLD_ORE, 0.3, 1, 1)
//         val ore4 = OreDeposit(Material.IRON_ORE, 0.4, 1, 1)
//         println(ore1)
//         println(ore2)
//         println(ore3)
//         println(ore4)

//         // create ore sampler from ores
//         val arrOre: ArrayList<OreDeposit> = arrayListOf(ore1, ore2, ore3, ore4)
//         val oreSampler = OreSampler(arrOre)

//         // try sampling
//         println(oreSampler.sampleAll())
//         println(oreSampler.sampleAll())
//         println(oreSampler.sampleAll())
//     }
// }
