package dev.devpanda.factorynetwork.block;

import java.util.List;

/**
 * The boxes of the quantum bridge.
 *
 * <p><b>Solid, not hollow.</b> A base, a squat body, and a socket on top that
 * holds the half. What passes through the bridge cannot be seen — so it shows
 * this not as a hole but as a socket with something in it.
 *
 * <p>The same numbers live in {@code tools/assets.py}. Having them here
 * costs a duplication; <i>not</i> having them costs a hitbox that sits
 * beside the picture.
 */
public final class BridgeLayout {

    /** The base rests on the ground and carries everything. */
    public static final int BASE = 5;

    /** The body above it, a bit narrower. */
    public static final int BODY_INSET = 2;
    public static final int BODY_TOP = 12;

    /** The socket that holds the half. */
    public static final int SOCKET_INSET = 5;

    public static List<int[]> boxes() {
        return List.of(
                new int[] {0, 0, 0, 16, BASE, 16},
                new int[] {BODY_INSET, BASE, BODY_INSET,
                        16 - BODY_INSET, BODY_TOP, 16 - BODY_INSET},
                new int[] {SOCKET_INSET, BODY_TOP, SOCKET_INSET,
                        16 - SOCKET_INSET, 16, 16 - SOCKET_INSET});
    }

    private BridgeLayout() {
    }
}
