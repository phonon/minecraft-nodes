/**
 * Wrapper around 2D point (x,y) for inventory gui
 */

package phonon.nodes.gui

public data class GuiSlot(val x: Int, val y: Int)

// pre-calculated gui slots for 54 sized chest
// for iterating items alongside chest slots
public val GUI_SLOTS: Array<GuiSlot> = arrayOf(
    GuiSlot(0, 0), GuiSlot(1, 0), GuiSlot(2, 0), GuiSlot(3, 0), GuiSlot(4, 0), GuiSlot(5, 0), GuiSlot(6, 0), GuiSlot(7, 0), GuiSlot(8, 0),
    GuiSlot(0, 1), GuiSlot(1, 1), GuiSlot(2, 1), GuiSlot(3, 1), GuiSlot(4, 1), GuiSlot(5, 1), GuiSlot(6, 1), GuiSlot(7, 1), GuiSlot(8, 1),
    GuiSlot(0, 2), GuiSlot(1, 2), GuiSlot(2, 2), GuiSlot(3, 2), GuiSlot(4, 2), GuiSlot(5, 2), GuiSlot(6, 2), GuiSlot(7, 2), GuiSlot(8, 2),
    GuiSlot(0, 3), GuiSlot(1, 3), GuiSlot(2, 3), GuiSlot(3, 3), GuiSlot(4, 3), GuiSlot(5, 3), GuiSlot(6, 3), GuiSlot(7, 3), GuiSlot(8, 3),
    GuiSlot(0, 4), GuiSlot(1, 4), GuiSlot(2, 4), GuiSlot(3, 4), GuiSlot(4, 4), GuiSlot(5, 4), GuiSlot(6, 4), GuiSlot(7, 4), GuiSlot(8, 4),
    GuiSlot(0, 5), GuiSlot(1, 5), GuiSlot(2, 5), GuiSlot(3, 5), GuiSlot(4, 5), GuiSlot(5, 5), GuiSlot(6, 5), GuiSlot(7, 5), GuiSlot(8, 5)
)
