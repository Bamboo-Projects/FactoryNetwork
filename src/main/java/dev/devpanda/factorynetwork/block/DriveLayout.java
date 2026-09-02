package dev.devpanda.factorynetwork.block;

import java.util.ArrayList;
import java.util.List;

/**
 * The dimensions of the drive — as pure numbers, front facing north.
 *
 * <p><b>Without any Minecraft dependency</b>, for the same reason as
 * {@link CableLayout} and {@link GatewayLayout}: only this way can the
 * geometry be checked against the generated model file in an ordinary test.
 *
 * <p>The numbers describe only the one direction. {@link FacingShapes}
 * computes the other three from them — the same rotation that the block state
 * applies to the model.
 *
 * <p>The same numbers live in the model script {@code tools/assets.py};
 * {@code DriveLayoutTest} watches over both saying the same thing.
 */
public final class DriveLayout {

    /** Height of the feet. */
    public static final int FOOT = 2;

    /** Footprint of a foot. */
    public static final int FOOT_WIDE = 3;

    /** How far the faceplate stands out in front of the housing. */
    public static final int FRONT = 2;

    /** How far the housing sets back behind the faceplate. */
    public static final int INSET = 1;

    /**
     * Width of the bezel around the bay field.
     *
     * <p>Two and not one, because the texture's four rivets sit around texture
     * pixels 4 and 59 and are drawn with radius 2 — so in block pixels from 0.5
     * to 1.5 and from 14.25 to 15.25. A bezel of one block pixel cut each of
     * them through the middle.
     */
    public static final int BEZEL = 2;

    /** How deep the field sits within the faceplate. */
    public static final int RECESS = 1;

    /** All boxes of the model, each as {@code x0 y0 z0 x1 y1 z1}. */
    public static List<int[]> boxes() {
        List<int[]> boxes = new ArrayList<>();

        // The housing.
        boxes.add(new int[] {INSET, FOOT, FRONT, 16 - INSET, 16, 16});

        // The bezel of the faceplate, over the full height: the two lower
        // rivets of the texture sit below two block pixels.
        boxes.add(new int[] {0, 16 - BEZEL, 0, 16, 16, FRONT});
        boxes.add(new int[] {0, 0, 0, 16, BEZEL, FRONT});
        boxes.add(new int[] {0, BEZEL, 0, BEZEL, 16 - BEZEL, FRONT});
        boxes.add(new int[] {16 - BEZEL, BEZEL, 0, 16, 16 - BEZEL, FRONT});

        // The recessed bay field.
        boxes.add(new int[] {BEZEL, BEZEL, RECESS,
                16 - BEZEL, 16 - BEZEL, FRONT});

        // Four feet, under the housing and not at the block corners: there
        // each one stuck out by exactly the block pixel by which the housing
        // is narrower than the faceplate.
        for (int x : new int[] {INSET, 16 - INSET - FOOT_WIDE}) {
            for (int z : new int[] {FRONT, 16 - FOOT_WIDE}) {
                boxes.add(new int[] {x, 0, z, x + FOOT_WIDE, FOOT, z + FOOT_WIDE});
            }
        }

        return boxes;
    }

    private DriveLayout() {
    }
}
