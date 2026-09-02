package dev.devpanda.factorynetwork.block;

import java.util.ArrayList;
import java.util.List;

/**
 * The dimensions of the remaining converted blocks — as plain numbers.
 *
 * <p><b>Why five in one class</b> while gateway, drive, controller and
 * terminal each get their own: those four carry numbers that something hinges
 * on — an opening that has to stay clear, a face that has to stand proud —
 * plus their own tests along with them. The five here are shape and nothing
 * else. Five files for a dozen lines each would be an order that orders
 * nothing.
 *
 * <p>The same numbers live in the model script {@code tools/assets.py};
 * {@code MachineLayoutTest} watches that both say the same thing.
 */
public final class MachineLayouts {

    /** Width of the burner's frame. */
    private static final int FRAME = 3;

    /**
     * How deep the burner's frame is.
     *
     * <p>Three, because three layers sit one behind another here: the frame,
     * the hatch behind it and the window within. At two there would be no
     * block-pixel left for the hatch.
     */
    private static final int BURNER_DEPTH = 3;

    /** Height of the press's floor and top plates. */
    private static final int BASE = 2;

    /** Depth of the frame that stands in front of its housing. */
    private static final int PORTAL = 2;

    /** Width of its pillars. */
    private static final int PILLAR = 4;

    /** Height of its crossbeam. */
    private static final int HEAD = 3;

    /**
     * How far the lid sits below the top edge of the block.
     *
     * <p>So the cylinder heads have room on top without poking out of the
     * block. Minecraft lights geometry outside 0 to 16 wrongly — the recess
     * is the way to still have something standing on top.
     */
    private static final int CAP = 1;

    /** Height of the ram. */
    private static final int RAM = 4;

    /**
     * How far the housing core stays back behind the plates.
     *
     * <p><b>This is the shadow gap</b>, and it is the cheapest trick in the
     * whole shape: a core that finishes flush with base and lid reads as one
     * surface. Set back by one block-pixel, it reads as a housing between two
     * plates.
     */
    private static final int INSET = 1;

    /** Thickness of the press's side walls. */
    private static final int WALL = 3;

    /** Thickness of its back wall. */
    private static final int BACK = 4;

    /** Height of the anvil above the floor plate. */
    private static final int ANVIL = 2;

    /**
     * The press: a portal frame with a housing behind it.
     *
     * <p>Two pillars and a crossbeam stand at the front, behind them the
     * housing sits between base and lid. The frame stays open at the bottom:
     * that is where the press bed looks out, and where the machine bites.
     *
     * <p><b>The small parts are in this list too</b> — cooling fins, cylinder
     * heads, terminal box. They cost a few more boxes in the hitbox and spare
     * the player aiming at a fin and hitting air. What you see, you can grab.
     *
     * <p><b>The ram is not in it.</b> It moves and is therefore its own model,
     * drawn by {@code PressRenderer}. It does not count towards the hitbox:
     * you grab the housing, not a part that happens to be somewhere else right
     * now.
     */
    public static List<int[]> press() {
        int lid = 16 - CAP;
        int deck = lid - BASE;
        return List.of(
                // Base and lid, across the full width.
                new int[] {0, 0, PORTAL, 16, BASE, 16},
                new int[] {0, deck, PORTAL, 16, lid, 16},
                // The frame in front.
                new int[] {0, 0, 0, PILLAR, lid, PORTAL},
                new int[] {16 - PILLAR, 0, 0, 16, lid, PORTAL},
                new int[] {PILLAR, lid - HEAD, 0, 16 - PILLAR, lid, PORTAL},
                // The housing core, set back behind base and lid.
                new int[] {INSET, BASE, PORTAL, INSET + WALL, deck, 16},
                new int[] {16 - INSET - WALL, BASE, PORTAL, 16 - INSET, deck, 16},
                new int[] {INSET + WALL, BASE, 16 - BACK, 16 - INSET - WALL, deck, 16},
                // The press bed draws forward to under the frame.
                new int[] {PILLAR, BASE, 1, 16 - PILLAR, BASE + ANVIL, 16 - BACK},
                // Cooling fins in the gap, two each on the left and right.
                new int[] {0, 4, 4, INSET, 7, 14},
                new int[] {0, 8, 4, INSET, 11, 14},
                new int[] {16 - INSET, 4, 4, 16, 7, 14},
                new int[] {16 - INSET, 8, 4, 16, 11, 14},
                // The terminal box at the top rear.
                new int[] {5, 8, 15, 11, 11, 16},
                // Two cylinder heads in the recess, the line between them.
                new int[] {5, lid, 6, 7, 16, 9},
                new int[] {9, lid, 6, 11, 16, 9},
                new int[] {7, lid, 7, 9, 16, 8},
                // A narrow strip beneath the crossbeam.
                new int[] {PILLAR, lid - HEAD - 1, 0, 16 - PILLAR, lid - HEAD, PORTAL});
    }

    /**
     * How far the ram travels, in block-pixels.
     *
     * <p>From its rest position under the ceiling down onto the anvil. The
     * renderer derives the movement from this; with the number here, both
     * change together.
     */
    public static int pressStroke() {
        return (16 - CAP - BASE - RAM) - (BASE + ANVIL);
    }

    /**
     * The burner: frame, hatch, window — and the handle, the only thing that
     * stands out in front of the hatch.
     */
    public static List<int[]> burner() {
        List<int[]> boxes = new ArrayList<>(frontFrame(BURNER_DEPTH));
        boxes.add(new int[] {0, 0, BURNER_DEPTH, 16, 16, 16});
        boxes.add(new int[] {FRAME, FRAME, 1, 16 - FRAME, 16 - FRAME, BURNER_DEPTH - 1});
        boxes.add(new int[] {5, 5, BURNER_DEPTH - 1, 11, 11, BURNER_DEPTH});
        boxes.add(new int[] {FRAME, 7, 0, FRAME + 1, 9, 1});
        return boxes;
    }

    /** The frame all around the burner's hatch. */
    private static List<int[]> frontFrame(int deep) {
        return List.of(
                new int[] {0, 0, 0, 16, FRAME, deep},
                new int[] {0, 16 - FRAME, 0, 16, 16, deep},
                new int[] {0, FRAME, 0, FRAME, 16 - FRAME, deep},
                new int[] {16 - FRAME, FRAME, 0, 16, 16 - FRAME, deep});
    }

    /** The fabricator: base, recessed body, lid, superstructure. */
    public static List<int[]> fabricator() {
        int base = 2;
        int lid = 3;
        int inset = 1;
        int top = 2;
        return List.of(
                new int[] {0, 0, 0, 16, base, 16},
                new int[] {inset, base, inset, 16 - inset, 16 - lid, 16 - inset},
                new int[] {0, 16 - lid, 0, 16, 15, 16},
                new int[] {top, 15, top, 16 - top, 16, 16 - top});
    }

    /**
     * The controller extension: a cage of twelve edge strips around a core
     * that steps back one block-pixel all round.
     *
     * <p>The vertical strips stand in the corners, the horizontal ones begin
     * behind them — no two overlap. Two surfaces in the same plane flicker in
     * game, and only from certain angles.
     */
    public static List<int[]> extension() {
        return cage(1, 1);
    }

    /**
     * A cage: twelve strips on the block edges, a core between them.
     *
     * <p>The vertical strips stand in the corners, the horizontal ones begin
     * behind them — no two overlap. Two surfaces in the same plane flicker in
     * game, and only from certain angles.
     *
     * @param edge  thickness of one strip
     * @param inset how far the core steps back
     */
    private static List<int[]> cage(int edge, int inset) {
        int far = 16 - edge;
        List<int[]> boxes = new ArrayList<>();
        boxes.add(new int[] {inset, inset, inset,
                16 - inset, 16 - inset, 16 - inset});
        for (int x : new int[] {0, far}) {
            for (int z : new int[] {0, far}) {
                boxes.add(new int[] {x, 0, z, x + edge, 16, z + edge});
            }
        }
        for (int y : new int[] {0, far}) {
            for (int z : new int[] {0, far}) {
                boxes.add(new int[] {edge, y, z, far, y + edge, z + edge});
            }
            for (int x : new int[] {0, far}) {
                boxes.add(new int[] {x, y, edge, x + edge, y + edge, far});
            }
        }
        return boxes;
    }

    /**
     * The router: the same cage, only with thick strips and a deeper core.
     *
     * <p><b>The three block-pixels of strip thickness are measured.</b> The
     * {@code RouterRenderer} paints the lane marking across the full face of
     * each side; its ring runs from block-pixel 1 to 3 and from 12.75 to
     * 14.75, transparent in between. Three cover the inner edge exactly.
     * Thinner, and the ring floats; thicker, and from an oblique view the
     * strip hides the four contacts in the middle.
     */
    public static List<int[]> router() {
        // One block-pixel of recess. Two would leave a hole in each side
        // that looked like a hole; a plate in the middle to make up for it
        // showed the collar of the texture a second time.
        return cage(3, 1);
    }

    /** The creative source: a recessed core and eight corner blocks. */
    public static List<int[]> source() {
        int corner = 3;
        int inset = 1;
        List<int[]> boxes = new ArrayList<>();
        boxes.add(new int[] {inset, inset, inset,
                16 - inset, 16 - inset, 16 - inset});
        for (int x : new int[] {0, 16 - corner}) {
            for (int y : new int[] {0, 16 - corner}) {
                for (int z : new int[] {0, 16 - corner}) {
                    boxes.add(new int[] {x, y, z, x + corner, y + corner, z + corner});
                }
            }
        }
        return boxes;
    }

    private MachineLayouts() {
    }
}
