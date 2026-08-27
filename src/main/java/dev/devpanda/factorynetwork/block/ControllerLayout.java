package dev.devpanda.factorynetwork.block;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Maße des Controllers — als reine Zahlen.
 *
 * <p><b>Ohne jeden Minecraft-Bezug</b>, aus demselben Grund wie
 * {@link CableLayout}: Nur so lässt sich die Geometrie in einem gewöhnlichen
 * Test gegen die erzeugte Modelldatei prüfen.
 *
 * <p>Dieselben Zahlen stehen im Modellskript {@code tools/assets.py};
 * {@code MachineLayoutTest} wacht darüber, dass beide dasselbe sagen.
 */
public final class ControllerLayout {

    /** Höhe der Deckplatten oben und unten. */
    public static final int PLATE = 1;

    /** Wie weit der Körper dazwischen zurückspringt. */
    public static final int INSET = 1;

    /** Breite einer Kantensäule. */
    public static final int EDGE = 3;

    /** Alle Kästen des Modells, jeder als {@code x0 y0 z0 x1 y1 z1}. */
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
