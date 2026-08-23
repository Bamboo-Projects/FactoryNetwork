package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.FactoryNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Kleinteile der Oberfläche auf beliebige Breite.
 *
 * <p>Reiter und Suchfeld sind so breit, wie das Fenster es zulässt, ihre
 * Vorlagen im Atlas aber nicht. <b>Zweimal ist genau daran etwas
 * kaputtgegangen</b>, und beide Male sah es aus wie ein Zeichenfehler:
 *
 * <ul>
 *   <li>Die Vorlage einfach breiter zeichnen holt die fehlenden Pixel aus dem
 *       Nachbarbild im Atlas — mitten durch „Storage" lief ein Strich.</li>
 *   <li>Die ganze Vorlage kacheln bringt bei jeder Kachel ihren Rand mit —
 *       alle sechsundneunzig Pixel eine Naht durch die Eingabezeile.</li>
 * </ul>
 *
 * <p>Richtig ist beides zusammen: die Kappen einmal an die Enden, und
 * dazwischen so oft die <b>randlose</b> Mitte, wie hineinpasst. Deshalb steht
 * das hier an einer Stelle und nicht in jedem Bildschirm neu.
 */
public final class Widgets {

    public static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/widgets.png");

    /** Das Blatt, auf dem die Kleinteile liegen. */
    private static final int SHEET = 512;

    private Widgets() {
    }

    /**
     * Zeichnet ein Kleinteil in beliebiger Breite.
     *
     * @param u      linke Kante der Vorlage im Atlas
     * @param v      obere Kante der Vorlage im Atlas
     * @param source wie breit die Vorlage ist
     * @param cap    wie breit die beiden Kappen sind
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
