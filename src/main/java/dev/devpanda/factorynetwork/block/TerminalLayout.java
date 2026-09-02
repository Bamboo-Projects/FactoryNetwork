package dev.devpanda.factorynetwork.block;

import java.util.List;

/**
 * The dimensions of the terminal — as plain numbers, front facing north.
 *
 * <p><b>With no reference to Minecraft</b>, for the same reason as
 * {@link CableLayout}. {@link FacingShapes} computes the other three directions
 * from these.
 *
 * <p>The same numbers live in the model script {@code tools/assets.py};
 * {@code MachineLayoutTest} watches that both say the same thing.
 */
public final class TerminalLayout {

    /** How far the console protrudes. */
    public static final int DESK = 2;

    /**
     * And how high it is.
     *
     * <p>Five and not four: the texture's recessed frame runs from texture
     * pixel 6 to 44, that is from block pixel 5 to 14.5 above the ground. At
     * four, the edge of the console cut through one block pixel below that and
     * ran straight across the painted image.
     */
    public static final int DESK_HIGH = 5;

    /** Width of the frame around the screen. */
    public static final int BEZEL = 2;

    /** All boxes of the model, each as {@code x0 y0 z0 x1 y1 z1}. */
    public static List<int[]> boxes() {
        int frame = DESK - 1;
        int screen = DESK;
        int back = screen + 1;
        // Every front part reaches back to the housing. At first each ended at
        // its own depth, and behind the upper frame and the console a slit
        // gaped across the full width.
        return List.of(
                // The console, the only part that protrudes to the block edge.
                new int[] {0, 0, 0, 16, DESK_HIGH, back},
                // The frame around the screen — top and both sides.
                new int[] {0, 16 - BEZEL, frame, 16, 16, back},
                new int[] {0, DESK_HIGH, frame, BEZEL, 16 - BEZEL, back},
                new int[] {16 - BEZEL, DESK_HIGH, frame, 16, 16 - BEZEL, back},
                // The screen behind it.
                new int[] {BEZEL, DESK_HIGH, screen, 16 - BEZEL, 16 - BEZEL, back},
                // And the housing, across the full width: any narrower at the
                // sides and the console would overhang, and above the screen a
                // slit would gape.
                new int[] {0, 0, screen + 1, 16, 16, 16});
    }

    private TerminalLayout() {
    }
}
