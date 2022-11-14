/**
 * Wrapper for Treaty GUI, handle different views
 */

package phonon.nodes.war

import org.bukkit.entity.Player
import phonon.nodes.gui.*
import phonon.nodes.objects.Town
import phonon.nodes.war.treaty.TreatyTermsGui

class TreatyGui(
    val view: TreatyGuiView,
    val player: Player,
    val treaty: Treaty,
    val town: Town
): GuiElement {
    
    override fun render(screen: GuiWindow) {

        val receiver = if ( town === treaty.town1 ) {
            treaty.town2
        } else {
            treaty.town1
        }

        treaty.addViewer(this.player, view)
        screen.onClose({ treaty.removeViewer(this.player, view) })
        
        // render view
        when ( view ) {
            TreatyGuiView.MAIN -> {
                TreatyTermsGui(this.player, this.treaty, this.town).render(screen)
            }

            TreatyGuiView.SELECT_TERRITORY_OCCUPY -> {
                TerritorySelectGui(this.player, this.treaty, this.town, hashSetOf(), { terr ->
                    treaty.add(TreatyTermOccupation(this.town, receiver, terr))
                    treaty.setPlayerGuiView(TreatyGuiView.MAIN, player, treaty, town)
                }).render(screen)
            }
        }
        
    }

}
