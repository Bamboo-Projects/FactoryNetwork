package dev.devpanda.factorynetwork.block;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Maße des Sendemasts — als reine Zahlen.
 *
 * <p>Ein Sockel, ein Schaft darauf, oben vier Ausleger. <b>Er sieht
 * absichtlich nicht aus wie ein Gehäuse:</b> Wer eine Basis abgeht, soll den
 * Block finden, der funkt, ohne jeden anzuklicken.
 *
 * <p>Dieselben Zahlen stehen im Modellskript {@code tools/assets.py};
 * {@code MachineLayoutTest} wacht darüber, dass beide dasselbe sagen.
 */
public final class MastLayout {

    /** Höhe des Sockels. */
    public static final int BASE = 3;

    /** Halbe Breite des Schafts, von der Blockmitte aus. */
    public static final int SHAFT = 3;

    /** Ab hier sitzen die Ausleger. */
    public static final int ARMS = 11;

    /** Und so weit stehen sie ab. */
    public static final int ARM_OUT = 2;

    public static List<int[]> boxes() {
        List<int[]> boxes = new ArrayList<>();
        int near = 8 - SHAFT;
        int far = 8 + SHAFT;

        // Der Sockel, über die volle Grundfläche: Er trägt, und er zeigt,
        // dass der Block am Boden steht.
        boxes.add(new int[] {0, 0, 0, 16, BASE, 16});

        // Der Schaft bis unter die Ausleger.
        boxes.add(new int[] {near, BASE, near, far, ARMS, far});

        // Vier Ausleger, je einer nach Norden, Süden, Westen, Osten.
        boxes.add(new int[] {near, ARMS, near - ARM_OUT, far, ARMS + 2, near});
        boxes.add(new int[] {near, ARMS, far, far, ARMS + 2, far + ARM_OUT});
        boxes.add(new int[] {near - ARM_OUT, ARMS, near, near, ARMS + 2, far});
        boxes.add(new int[] {far, ARMS, near, far + ARM_OUT, ARMS + 2, far});

        // Die Spitze.
        boxes.add(new int[] {7, ARMS + 2, 7, 9, 16, 9});

        return boxes;
    }

    private MastLayout() {
    }
}
