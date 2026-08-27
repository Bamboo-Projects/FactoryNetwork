package dev.devpanda.factorynetwork.block;

import java.util.List;

/**
 * Die Maße des Terminals — als reine Zahlen, Vorderseite nach Norden.
 *
 * <p><b>Ohne jeden Minecraft-Bezug</b>, aus demselben Grund wie
 * {@link CableLayout}. Die anderen drei Richtungen rechnet
 * {@link FacingShapes} daraus.
 *
 * <p>Dieselben Zahlen stehen im Modellskript {@code tools/assets.py};
 * {@code MachineLayoutTest} wacht darüber, dass beide dasselbe sagen.
 */
public final class TerminalLayout {

    /** Wie weit die Konsole vorsteht. */
    public static final int DESK = 2;

    /** Und wie hoch sie ist. */
    public static final int DESK_HIGH = 4;

    /** Breite des Rahmens um den Bildschirm. */
    public static final int BEZEL = 1;

    /** Wie weit das Gehäuse seitlich zurückspringt. */
    public static final int INSET = 1;

    /** Alle Kästen des Modells, jeder als {@code x0 y0 z0 x1 y1 z1}. */
    public static List<int[]> boxes() {
        int frame = DESK - 1;
        int screen = DESK;
        return List.of(
                // Die Konsole, der einzige Teil, der bis an die Blockkante vorsteht.
                new int[] {0, 0, 0, 16, DESK_HIGH, DESK},
                // Der Rahmen um den Bildschirm — oben und an beiden Seiten.
                new int[] {0, 16 - BEZEL, frame, 16, 16, DESK},
                new int[] {0, DESK_HIGH, frame, BEZEL, 16 - BEZEL, DESK},
                new int[] {16 - BEZEL, DESK_HIGH, frame, 16, 16 - BEZEL, DESK},
                // Der Bildschirm dahinter.
                new int[] {BEZEL, DESK_HIGH, screen, 16 - BEZEL, 16 - BEZEL, screen + 1},
                // Und das Gehäuse, seitlich schmaler als die Front.
                new int[] {INSET, 0, screen + 1, 16 - INSET, 16, 16});
    }

    private TerminalLayout() {
    }
}
