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
    public static final int FOOT = 3;

    /** Ab hier der Sturz. */
    public static final int HEAD = 13;

    /** Kantenlänge einer Ecksäule. */
    public static final int POST = 4;

    /** Ab dieser Höhe verengen die Schultern den Durchgang. */
    public static final int SHOULDER = 10;

    /** Bis hierhin reicht eine Schulter in den Durchgang. */
    public static final int REACH = 6;

    /** Wo die Leuchtstreifen anfangen; sie enden spiegelbildlich. */
    public static final int GLOW = 4;

    /** Wie stark ein Leuchtstreifen ist. */
    public static final int GLOW_HIGH = 1;

    /**
     * Alle Kästen des Modells, jeder als {@code x0 y0 z0 x1 y1 z1}.
     *
     * <p>Auch die beiden Leuchtstreifen stehen darin. Sie ragen einen
     * Blockpixel in den Durchgang, und was man sieht, soll man auch treffen.
     */
    public static List<int[]> boxes() {
        List<int[]> boxes = new ArrayList<>();

        // Sockel und Sturz über die volle Grundfläche.
        boxes.add(new int[] {0, 0, 0, 16, FOOT, 16});
        boxes.add(new int[] {0, HEAD, 0, 16, 16, 16});

        // Die beiden Streifen, die das Tor fassen.
        boxes.add(new int[] {GLOW, FOOT, GLOW, 16 - GLOW, FOOT + GLOW_HIGH, 16 - GLOW});
        boxes.add(new int[] {GLOW, HEAD - GLOW_HIGH, GLOW, 16 - GLOW, HEAD, 16 - GLOW});

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
