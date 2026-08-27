package dev.devpanda.factorynetwork.block;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Maße des Torbogens — als reine Zahlen.
 *
 * <p><b>Ohne jeden Minecraft-Bezug</b>, aus demselben Grund wie
 * {@link CableLayout}: Nur so lässt sich die Geometrie in einem gewöhnlichen
 * Test gegen die erzeugte Modelldatei prüfen. Minecraft hält Modell und
 * Trefferfläche getrennt, und ein Block, den man sieht, aber nicht trifft,
 * ist der Fehler, den man am längsten sucht.
 *
 * <p>Dieselben Zahlen stehen im Modellskript {@code tools/assets.py};
 * {@code GatewayLayoutTest} wacht darüber, dass beide dasselbe sagen.
 */
public final class GatewayLayout {

    /** Bis hierhin reicht der Sockel. */
    public static final int FOOT = 4;

    /** Ab hier der Sturz. */
    public static final int HEAD = 12;

    /** Kantenlänge einer Ecksäule. */
    public static final int POST = 5;

    /** Ab dieser Höhe verengen die Schultern den Durchgang. */
    public static final int SHOULDER = 9;

    /** Bis hierhin reicht eine Schulter in den Durchgang. */
    public static final int REACH = 6;

    /** Wie stark die beiden Leuchtbänder sind. */
    public static final int GLOW = 1;

    /**
     * Alle Kästen des Modells, jeder als {@code x0 y0 z0 x1 y1 z1}.
     *
     * <p>Die beiden Leuchtbänder liegen in der Blockhülle und teilen sich die
     * Kanten mit Sockel und Sturz — für die Trefferfläche sind sie deshalb
     * keine eigenen Kästen, sondern gehören zu ihnen.
     */
    public static List<int[]> boxes() {
        List<int[]> boxes = new ArrayList<>();

        // Sockel und Sturz über die volle Grundfläche, jeweils in zwei
        // Kästen: der Block selbst und das Leuchtband darauf. Im Modell sind
        // es zwei, weil sie verschiedene Texturen tragen.
        boxes.add(new int[] {0, 0, 0, 16, FOOT - GLOW, 16});
        boxes.add(new int[] {0, FOOT - GLOW, 0, 16, FOOT, 16});
        boxes.add(new int[] {0, HEAD, 0, 16, HEAD + GLOW, 16});
        boxes.add(new int[] {0, HEAD + GLOW, 0, 16, 16, 16});

        // Die vier Ecksäulen.
        for (int x : new int[] {0, 16 - POST}) {
            for (int z : new int[] {0, 16 - POST}) {
                boxes.add(new int[] {x, FOOT, z, x + POST, HEAD, z + POST});
            }
        }

        // Über jeder der vier Öffnungen zwei Schultern.
        for (int lo : new int[] {POST, 16 - REACH}) {
            int hi = lo + REACH - POST;
            boxes.add(new int[] {lo, SHOULDER, 0, hi, HEAD, POST});
            boxes.add(new int[] {lo, SHOULDER, 16 - POST, hi, HEAD, 16});
            boxes.add(new int[] {0, SHOULDER, lo, POST, HEAD, hi});
            boxes.add(new int[] {16 - POST, SHOULDER, lo, 16, HEAD, hi});
        }

        return boxes;
    }

    private GatewayLayout() {
    }
}
