package dev.devpanda.factorynetwork.client.menu;

/**
 * Die Maße des Terminalfensters — dieselben Zahlen wie in {@code gui.py}.
 *
 * <p><b>An einer Stelle, weil zwei Seiten sie brauchen.</b> Das Menü setzt
 * die Slots des Spielerinventars, der Bildschirm zeichnet die Mulden darunter.
 * Standen die Zahlen an beiden Orten, lagen die Slots nach dem ersten Umbau
 * zwanzig Pixel über ihren Mulden — und das sieht man erst im Spiel, weil
 * kein Test das Zeichnen prüft.
 *
 * <p>Hier und nicht im Bildschirm: Der Bildschirm ist Client-Code, das Menü
 * läuft auch auf einem Server ohne Grafik.
 *
 * <p><b>Die Höhe wird gerechnet, nicht gesetzt.</b> Jede Zeile kennt ihre
 * eigene Höhe; was das Fenster misst, ist die Summe. Beim Entwurf hatte ich
 * sie geraten, und die Statuszeile lag im Raster.
 */
public final class TerminalLayout {

    public static final int WIDTH = 288;

    /** Die Scheibe im Blech. */
    public static final int SCREEN_X0 = 6;
    public static final int SCREEN_X1 = WIDTH - 7;
    public static final int SCREEN_TOP = 6;
    public static final int SCREEN_PAD = 3;

    /** Die Zeile mit den Reiterbeschriftungen. */
    public static final int TAB_ROW = 14;

    /** Die Suchzeile des Speicher-Reiters. */
    public static final int SEARCH_ROW = 13;

    /** Das Bestandsraster. */
    public static final int GRID_COLUMNS = 14;
    public static final int GRID_ROWS = 6;
    public static final int SLOT = 18;

    /** Die Statuszeile am unteren Rand der Scheibe. */
    public static final int STATUS_ROW = 11;

    /** Der Bereich, in den die Reiter zeichnen. */
    public static final int WORK_X = SCREEN_X0 + 3;
    public static final int WORK_Y = SCREEN_TOP + SCREEN_PAD + TAB_ROW + 1;
    public static final int WORK_W = (SCREEN_X1 - 2) - WORK_X;
    public static final int WORK_H = SEARCH_ROW + 2 + GRID_ROWS * SLOT;

    public static final int SCREEN_BOTTOM = WORK_Y + WORK_H + 2 + STATUS_ROW;
    public static final int HEIGHT = SCREEN_BOTTOM + 14 + 82;

    /** Das Spielerinventar sitzt, wo es in jedem Minecraft-Fenster sitzt. */
    public static final int INV_X = (WIDTH - 9 * SLOT) / 2;
    public static final int INV_Y = HEIGHT - 82;
    public static final int HOTBAR_Y = HEIGHT - 24;

    private TerminalLayout() {
    }
}
