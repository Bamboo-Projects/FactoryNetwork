package dev.devpanda.factorynetwork.block;

/**
 * The dimensions of a cable — as plain numbers.
 *
 * <p><b>With no reference to Minecraft</b>, so that the geometry can be checked
 * against the generated model files in ordinary tests. It is exactly these
 * numbers that otherwise drift apart without anyone noticing: Minecraft keeps
 * model and hitbox separate.
 *
 * <p>The values come from Applied Energistics — there a covered cable is six
 * block pixels thick and a dense one ten. The same numbers live in the model
 * script {@code tools/assets.py}; {@code CableLayoutTest} watches that both say
 * the same thing.
 */
public final class CableLayout {

    /** The ordinary cable: six block pixels, like AE2's covered one. */
    public static final int THIN = 6;

    /** The dense cable: ten block pixels, like AE2's dense one. */
    public static final int DENSE = 10;

    /** Where the sheath begins — it sits centred in the block. */
    public static int offset(int size) {
        return (16 - size) / 2;
    }

    /** Where the sheath ends. */
    public static int far(int size) {
        return offset(size) + size;
    }

    /**
     * The lanes of the channel lines, in block pixels from the block edge.
     *
     * <p>Eight lanes in the middle of the cable, each a quarter block pixel
     * wide and offset by an eighth — the first four for channels one to four,
     * the second four for five to eight. Exactly AE2's layout, except that with
     * us one lane counts more than one channel.
     */
    public static double[] channelLanes() {
        return new double[] {7.00, 7.25, 7.50, 7.75, 8.00, 8.25, 8.50, 8.75};
    }

    /** How wide a channel line is, in block pixels. */
    public static final double LANE_WIDTH = 0.25;

    /**
     * How deep a connector juts from the block face into the block.
     *
     * <p>Three block pixels — enough to see it as its own thing and to hit it,
     * and little enough that on the dense cable it just meets that cable's
     * sheath: there the core already begins at three.
     */
    public static final int PART_DEPTH = 3;

    /** How wide the plate of a connector is. */
    public static final int PART_WIDTH = 12;

    /**
     * How far the plate protrudes past the block edge.
     *
     * <p><b>Against Z-fighting.</b> Ever since the cable grows an arm to every
     * face with a connector, arm and plate both end exactly on the block
     * edge — two surfaces in the same plane. If a machine stands behind it,
     * Minecraft culls both away and you see nothing of it. If the connector
     * points into empty space, the cable's colour flickers across its face.
     *
     * <p>One twentieth of a block pixel is enough for the plate to win. That is
     * one three-thousandth of a block — invisible in the game, and it juts into
     * the machine behind only where that machine's own surface hides it anyway.
     */
    public static final double PART_OVERHANG = 0.05;

    /** Where the plate begins — it sits centred on the face. */
    public static int partOffset() {
        return (16 - PART_WIDTH) / 2;
    }

    /**
     * <b>The stalk no longer exists.</b>
     *
     * <p>Until 26 Aug a grey box sat between plate and cable core. Ever since
     * the face with a connector gets an ordinary arm, the arm carries that
     * stretch — in the cable's colour, and a visible crossing forms at the
     * cable instead of a foreign body.
     */

    private CableLayout() {
    }
}
