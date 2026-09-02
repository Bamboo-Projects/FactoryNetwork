package dev.devpanda.factorynetwork.client.menu;

/**
 * The dimensions of the terminal window — the same numbers as in {@code gui.py}.
 *
 * <p><b>In one place, because two sides need them.</b> The menu positions the
 * slots of the player inventory, the screen draws the wells beneath them. If
 * the numbers lived in both places, the slots would sit twenty pixels above
 * their wells after the first rebuild — and you only see that in the game,
 * because no test checks the drawing.
 *
 * <p>Here and not in the screen: the screen is client code, the menu also
 * runs on a server without graphics.
 *
 * <p><b>The height is computed, not set.</b> Each row knows its own height;
 * what the window measures is the sum. When designing it I had guessed the
 * height, and the status row ended up in the grid.
 */
public final class TerminalLayout {

    /**
     * Wider than vanilla's 176.
     *
     * <p>The network tab carries two columns, and the code editor lives on
     * every line it can show in full. At GUI scale 3, 352 stays under 1100
     * pixels — that fits every screen on which one plays Minecraft, and
     * Minecraft scales down on its own if need be.
     *
     * <p>The same number is in {@code tools/gui.py}: the drawing script does
     * not run at runtime, and if the two drift apart, the frame is drawn
     * somewhere other than the contents.
     */
    public static final int WIDTH = 352;

    /** The pane in the sheet metal. */
    public static final int SCREEN_X0 = 6;
    public static final int SCREEN_X1 = WIDTH - 7;
    public static final int SCREEN_TOP = 6;
    public static final int SCREEN_PAD = 3;

    /** The row with the tab labels. */
    public static final int TAB_ROW = 14;

    /** The search row of the storage tab. */
    public static final int SEARCH_ROW = 13;

    /** The stored-items grid. */
    /**
     * How many columns the grid of the storage view has.
     *
     * <p>As many as fit between the edge and the scrollbar — seventeen at a
     * window width of 352 pixels. Fourteen before, and the three missing ones
     * left a gap on the right.
     *
     * <p><b>The same number is in {@code tools/gui.py}</b>: the drawing script
     * draws the tiles, and if the two drift apart, the image ends before or
     * behind the slots.
     */
    public static final int GRID_COLUMNS = 17;
    /**
     * How many rows tall the work area is.
     *
     * <p>Three more than before: the network tab shows two columns, and the
     * code editor lives on every line it shows in full.
     */
    public static final int GRID_ROWS = 9;

    public static final int SLOT = 18;

    /** The status row at the bottom edge of the pane. */
    public static final int STATUS_ROW = 11;

    /** The area into which the tabs draw. */
    public static final int WORK_X = SCREEN_X0 + 3;
    public static final int WORK_Y = SCREEN_TOP + SCREEN_PAD + TAB_ROW + 1;
    public static final int WORK_W = (SCREEN_X1 - 2) - WORK_X;
    public static final int WORK_H = SEARCH_ROW + 2 + GRID_ROWS * SLOT;

    public static final int SCREEN_BOTTOM = WORK_Y + WORK_H + 2 + STATUS_ROW;
    public static final int HEIGHT = SCREEN_BOTTOM + 14 + 82;

    /** The player inventory sits where it sits in every Minecraft window. */
    public static final int INV_X = (WIDTH - 9 * SLOT) / 2;
    public static final int INV_Y = HEIGHT - 82;
    public static final int HOTBAR_Y = HEIGHT - 24;

    private TerminalLayout() {
    }
}
