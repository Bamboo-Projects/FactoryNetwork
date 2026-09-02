package dev.devpanda.factorynetwork.block;

import java.util.ArrayList;
import java.util.List;

/**
 * The controller's dimensions — as plain numbers.
 *
 * <p><b>With no ties to Minecraft whatsoever</b>, for the same reason as
 * {@link CableLayout}: only this way can the geometry be checked against the
 * generated model file in an ordinary test.
 *
 * <p>The same numbers live in the model script {@code tools/assets.py};
 * {@code MachineLayoutTest} watches that both say the same thing.
 */
public final class ControllerLayout {

    /** Height of the cover plates, top and bottom. */
    public static final int PLATE = 1;

    /** How far the body between them is inset. */
    public static final int INSET = 1;

    /** Width of an edge column. */
    public static final int EDGE = 3;

    /** All boxes of the model, each as {@code x0 y0 z0 x1 y1 z1}. */
    public static List<int[]> boxes() {
        List<int[]> boxes = new ArrayList<>();

        boxes.add(new int[] {0, 0, 0, 16, PLATE, 16});
        boxes.add(new int[] {0, 16 - PLATE, 0, 16, 16, 16});
        boxes.add(new int[] {INSET, PLATE, INSET, 16 - INSET, 16 - PLATE, 16 - INSET});

        for (int x : new int[] {0, 16 - EDGE}) {
            for (int z : new int[] {0, 16 - EDGE}) {
                boxes.add(new int[] {x, PLATE, z, x + EDGE, 16 - PLATE, z + EDGE});
            }
        }

        return boxes;
    }

    private ControllerLayout() {
    }
}
