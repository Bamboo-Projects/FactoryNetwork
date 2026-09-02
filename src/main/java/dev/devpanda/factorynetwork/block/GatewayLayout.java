package dev.devpanda.factorynetwork.block;

import java.util.ArrayList;
import java.util.List;

/**
 * The dimensions of the archway — as plain numbers.
 *
 * <p><b>With no reference to Minecraft</b>, for the same reason as
 * {@link CableLayout}: only this way can the geometry be checked against the
 * generated model file in an ordinary test. Minecraft keeps model and hitbox
 * separate, and a block you can see but cannot hit is the bug you spend the
 * longest hunting for.
 *
 * <p>The same numbers live in the model script {@code tools/assets.py};
 * {@code GatewayLayoutTest} watches that both say the same thing.
 */
public final class GatewayLayout {

    /** The base reaches up to here. */
    public static final int FOOT = 5;

    /** From here up, the lintel. */
    public static final int HEAD = 11;

    /** Edge length of a corner post. */
    public static final int POST = 6;

    /** From this height the shoulders narrow the passage. */
    public static final int SHOULDER = 9;

    /** This far a shoulder reaches into the passage. */
    public static final int REACH = 7;

    /** How thick the two glow strips are. */
    public static final int GLOW = 1;

    /**
     * All boxes of the model, each as {@code x0 y0 z0 x1 y1 z1}.
     *
     * <p>The two glow strips lie within the block shell and share their edges
     * with base and lintel — for the hitbox they are therefore not boxes of
     * their own, but belong to those.
     */
    public static List<int[]> boxes() {
        List<int[]> boxes = new ArrayList<>();

        // Base and lintel across the full footprint, each in two boxes: the
        // block itself and the glow strip on it. In the model they are two,
        // because they carry different textures.
        boxes.add(new int[] {0, 0, 0, 16, FOOT - GLOW, 16});
        boxes.add(new int[] {0, FOOT - GLOW, 0, 16, FOOT, 16});
        boxes.add(new int[] {0, HEAD, 0, 16, HEAD + GLOW, 16});
        boxes.add(new int[] {0, HEAD + GLOW, 0, 16, 16, 16});

        // The four corner posts.
        for (int x : new int[] {0, 16 - POST}) {
            for (int z : new int[] {0, 16 - POST}) {
                boxes.add(new int[] {x, FOOT, z, x + POST, HEAD, z + POST});
            }
        }

        // Two shoulders above each of the four openings.
        for (int lo : new int[] {POST, 16 - REACH}) {
            int hi = lo + REACH - POST;
            boxes.add(new int[] {lo, SHOULDER, 0, hi, HEAD, POST});
            boxes.add(new int[] {lo, SHOULDER, 16 - POST, hi, HEAD, 16});
            boxes.add(new int[] {0, SHOULDER, lo, POST, HEAD, hi});
            boxes.add(new int[] {16 - POST, SHOULDER, lo, 16, HEAD, hi});
        }

        return boxes;
    }

    private GatewayLayout() {
    }
}
