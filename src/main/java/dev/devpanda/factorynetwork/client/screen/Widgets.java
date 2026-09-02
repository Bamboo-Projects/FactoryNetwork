package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.FactoryNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Small UI parts at an arbitrary width.
 *
 * <p>Tabs and the search field are as wide as the window allows, but their
 * templates in the atlas are not. <b>Twice something broke on exactly
 * this</b>, and both times it looked like a drawing glitch:
 *
 * <ul>
 *   <li>Simply drawing the template wider pulls the missing pixels from the
 *       neighbouring image in the atlas — a stripe ran straight through
 *       "Storage".</li>
 *   <li>Tiling the whole template brings each tile's border along with it —
 *       a seam through the input line every ninety-six pixels.</li>
 * </ul>
 *
 * <p>The right answer is both together: the caps once at the ends, and between
 * them the <b>borderless</b> middle as often as it fits. That is why this
 * lives in one place and not anew in every screen.
 */
public final class Widgets {

    public static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/widgets.png");

    /** The sheet the small parts sit on. */
    private static final int SHEET = 512;

    /**
     * Lettering on the casing.
     *
     * <p>The same colour Minecraft uses to write "Inventory" on every chest.
     * It lives here and not three times over as a number: the casing is light,
     * ever since the slot grid inside it was meant to be visible, and light
     * text on top would have been unreadable. Whoever turns the palette in
     * {@code tools/gui.py} back turns this one line with it.
     *
     * <p>What sits <b>inside</b> the screen does not belong here — the pane
     * stays dark, and its text stays light.
     */
    public static final int CASE_TEXT = 0x3F3F3F;

    /** The same for a secondary detail that should not be read first. */
    public static final int CASE_TEXT_DIM = 0x7F7F7F;

    private Widgets() {
    }

    /**
     * Draws a small part at an arbitrary width.
     *
     * @param u      left edge of the template in the atlas
     * @param v      top edge of the template in the atlas
     * @param source how wide the template is
     * @param cap    how wide the two caps are
     */
    public static void stretched(GuiGraphics graphics, int x, int y, int width, int height,
                                 int u, int v, int source, int cap) {
        if (width <= 2 * cap) {
            graphics.blit(ATLAS, x, y, u, v, width, height, SHEET, SHEET);
            return;
        }
        graphics.blit(ATLAS, x, y, u, v, cap, height, SHEET, SHEET);
        graphics.blit(ATLAS, x + width - cap, y, u + source - cap, v, cap, height,
                SHEET, SHEET);

        int middle = source - 2 * cap;
        int remaining = width - 2 * cap;
        int drawn = 0;
        while (drawn < remaining) {
            int piece = Math.min(middle, remaining - drawn);
            graphics.blit(ATLAS, x + cap + drawn, y, u + cap, v, piece, height, SHEET, SHEET);
            drawn += piece;
        }
    }
}
