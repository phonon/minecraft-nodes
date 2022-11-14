/**
 * Manager for inventory gui objects
 */

package phonon.nodes.gui

import org.bukkit.entity.Player

object Gui {
    
    // create/render gui for player
    fun render(guiObj: GuiElement, player: Player, size: Int, title: String) {
        val window = GuiWindow(size, title)
        guiObj.render(window)
        player.openInventory(window.inventory)
    }
}
