package dev.devpanda.factorynetwork.block;

import java.util.List;

/**
 * Die Kästen der Quantum-Brücke.
 *
 * <p><b>Massiv, nicht hohl.</b> Ein Sockel, ein gedrungener Körper und eine
 * Fassung obendrauf, in der die Hälfte sitzt. Was durch die Brücke geht,
 * sieht man nicht — also zeigt sie es nicht als Loch, sondern als Fassung mit
 * etwas darin.
 *
 * <p>Dieselben Zahlen stehen in {@code tools/assets.py}. Sie hier zu haben
 * kostet eine Doppelung; sie <i>nicht</i> zu haben kostet eine Trefferfläche,
 * die neben dem Bild liegt.
 */
public final class BridgeLayout {

    /** Der Sockel steht auf dem Boden auf und trägt alles. */
    public static final int BASE = 5;

    /** Der Körper darüber, ein Stück schmaler. */
    public static final int BODY_INSET = 2;
    public static final int BODY_TOP = 12;

    /** Die Fassung, in der die Hälfte sitzt. */
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
