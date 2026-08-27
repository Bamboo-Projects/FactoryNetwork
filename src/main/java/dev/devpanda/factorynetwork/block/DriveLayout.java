package dev.devpanda.factorynetwork.block;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Maße des Laufwerks — als reine Zahlen, Vorderseite nach Norden.
 *
 * <p><b>Ohne jeden Minecraft-Bezug</b>, aus demselben Grund wie
 * {@link CableLayout} und {@link GatewayLayout}: Nur so lässt sich die
 * Geometrie in einem gewöhnlichen Test gegen die erzeugte Modelldatei
 * prüfen.
 *
 * <p>Die Zahlen beschreiben nur die eine Richtung. Die anderen drei rechnet
 * {@link FacingShapes} daraus — dieselbe Drehung, die auch der Blockzustand
 * am Modell vornimmt.
 *
 * <p>Dieselben Zahlen stehen im Modellskript {@code tools/assets.py};
 * {@code DriveLayoutTest} wacht darüber, dass beide dasselbe sagen.
 */
public final class DriveLayout {

    /** Höhe der Füße. */
    public static final int FOOT = 2;

    /** Grundfläche eines Fußes. */
    public static final int FOOT_WIDE = 3;

    /** Wie weit die Blende vor dem Gehäuse steht. */
    public static final int FRONT = 2;

    /** Wie weit das Gehäuse hinter der Blende zurückspringt. */
    public static final int INSET = 1;

    /** Breite der Fassung um das Schachtfeld. */
    public static final int BEZEL = 1;

    /** Wie tief das Feld in der Blende liegt. */
    public static final int RECESS = 1;

    /** Alle Kästen des Modells, jeder als {@code x0 y0 z0 x1 y1 z1}. */
    public static List<int[]> boxes() {
        List<int[]> boxes = new ArrayList<>();

        // Das Gehäuse.
        boxes.add(new int[] {INSET, FOOT, FRONT, 16 - INSET, 16, 16});

        // Die Fassung der Blende.
        boxes.add(new int[] {0, 16 - BEZEL, 0, 16, 16, FRONT});
        boxes.add(new int[] {0, FOOT, 0, 16, FOOT + BEZEL, FRONT});
        boxes.add(new int[] {0, FOOT + BEZEL, 0, BEZEL, 16 - BEZEL, FRONT});
        boxes.add(new int[] {16 - BEZEL, FOOT + BEZEL, 0, 16, 16 - BEZEL, FRONT});

        // Das versenkte Schachtfeld.
        boxes.add(new int[] {BEZEL, FOOT + BEZEL, RECESS,
                16 - BEZEL, 16 - BEZEL, FRONT});

        // Vier Füße an den Ecken.
        for (int x : new int[] {0, 16 - FOOT_WIDE}) {
            for (int z : new int[] {0, 16 - FOOT_WIDE}) {
                boxes.add(new int[] {x, 0, z, x + FOOT_WIDE, FOOT, z + FOOT_WIDE});
            }
        }

        return boxes;
    }

    private DriveLayout() {
    }
}
