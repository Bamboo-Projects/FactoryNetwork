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
    public static final int BEZEL = 2;

    /** Alle Kästen des Modells, jeder als {@code x0 y0 z0 x1 y1 z1}. */
    public static List<int[]> boxes() {
        int frame = DESK - 1;
        int screen = DESK;
        int back = screen + 1;
        // Jedes Frontteil reicht bis ans Gehäuse. Zuerst endete jedes an
        // seiner eigenen Tiefe, und hinter dem oberen Rahmen und der Konsole
        // klaffte ein Schlitz über die volle Breite.
        return List.of(
                // Die Konsole, der einzige Teil, der bis an die Blockkante vorsteht.
                new int[] {0, 0, 0, 16, DESK_HIGH, back},
                // Der Rahmen um den Bildschirm — oben und an beiden Seiten.
                new int[] {0, 16 - BEZEL, frame, 16, 16, back},
                new int[] {0, DESK_HIGH, frame, BEZEL, 16 - BEZEL, back},
                new int[] {16 - BEZEL, DESK_HIGH, frame, 16, 16 - BEZEL, back},
                // Der Bildschirm dahinter.
                new int[] {BEZEL, DESK_HIGH, screen, 16 - BEZEL, 16 - BEZEL, back},
                // Und das Gehäuse, über die volle Breite: Seitlich schmaler
                // hing die Konsole über, und über dem Bildschirm klaffte ein
                // Schlitz.
                new int[] {0, 0, screen + 1, 16, 16, 16});
    }

    private TerminalLayout() {
    }
}
