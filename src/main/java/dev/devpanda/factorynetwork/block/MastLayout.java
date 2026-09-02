package dev.devpanda.factorynetwork.block;

import java.util.ArrayList;
import java.util.List;

/**
 * The mast's dimensions — as plain numbers.
 *
 * <p>A base, a shaft on top of it, four arms up top. <b>It deliberately does
 * not look like a housing:</b> whoever paces out a base should spot the block
 * that transmits, without clicking every one.
 *
 * <p>The same numbers live in the model script {@code tools/assets.py};
 * {@code MachineLayoutTest} watches that both say the same thing.
 */
public final class MastLayout {

    /** Height of the base. */
    public static final int BASE = 3;

    /** Half-width of the shaft, from the block centre. */
    public static final int SHAFT = 3;

    /** From here up sit the arms. */
    public static final int ARMS = 11;

    /** And this far they stick out. */
    public static final int ARM_OUT = 2;

    public static List<int[]> boxes() {
        List<int[]> boxes = new ArrayList<>();
        int near = 8 - SHAFT;
        int far = 8 + SHAFT;

        // The base, over the full footprint: it bears the load, and it shows
        // that the block stands on the ground.
        boxes.add(new int[] {0, 0, 0, 16, BASE, 16});

        // The shaft up to just below the arms.
        boxes.add(new int[] {near, BASE, near, far, ARMS, far});

        // Four arms, one each to north, south, west, east.
        boxes.add(new int[] {near, ARMS, near - ARM_OUT, far, ARMS + 2, near});
        boxes.add(new int[] {near, ARMS, far, far, ARMS + 2, far + ARM_OUT});
        boxes.add(new int[] {near - ARM_OUT, ARMS, near, near, ARMS + 2, far});
        boxes.add(new int[] {far, ARMS, near, far + ARM_OUT, ARMS + 2, far});

        // The tip.
        boxes.add(new int[] {7, ARMS + 2, 7, 9, 16, 9});

        return boxes;
    }

    private MastLayout() {
    }
}
