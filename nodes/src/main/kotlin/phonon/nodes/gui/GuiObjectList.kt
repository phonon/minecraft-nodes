/**
 * Wrapper around list of Gui objects, renders all items in list
 */

package phonon.nodes.gui

public class GuiElementList(val list: List<GuiElement>): GuiElement {
    override public fun render(screen: GuiWindow) {
        for ( obj in this.list ) {
            obj.render(screen)
        }
    }
}