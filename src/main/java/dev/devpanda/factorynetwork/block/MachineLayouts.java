package dev.devpanda.factorynetwork.block;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Maße der übrigen umgebauten Blöcke — als reine Zahlen.
 *
 * <p><b>Warum fünf in einer Klasse</b> und Gateway, Laufwerk, Controller und
 * Terminal je in einer eigenen: Die vier tragen Zahlen, an denen etwas hängt
 * — ein Durchgang, der offen bleiben muss, eine Blende, die vorstehen muss —
 * und dazu eigene Proben. Die fünf hier sind Form und sonst nichts. Fünf
 * Dateien für je ein Dutzend Zeilen wären eine Ordnung, die nichts ordnet.
 *
 * <p>Dieselben Zahlen stehen im Modellskript {@code tools/assets.py};
 * {@code MachineLayoutTest} wacht darüber, dass beide dasselbe sagen.
 */
public final class MachineLayouts {

    /** Breite des Rahmens an Presse und Brennkammer. */
    private static final int FRAME = 3;

    /** Und wie tief er ist. */
    private static final int DEPTH = 3;

    /**
     * Die Presse: Rahmen, versenkter Arbeitsraum, darin Führungssäulen,
     * Stempelkopf und Amboss.
     */
    public static List<int[]> press() {
        int inner = DEPTH - 1;
        List<int[]> boxes = new ArrayList<>(frontFrame());
        boxes.add(new int[] {1, 0, DEPTH, 15, 16, 16});
        boxes.add(new int[] {FRAME, FRAME, inner, 16 - FRAME, 16 - FRAME, DEPTH});
        boxes.add(new int[] {FRAME, FRAME, inner - 1, FRAME + 1, 16 - FRAME, inner});
        boxes.add(new int[] {16 - FRAME - 1, FRAME, inner - 1, 16 - FRAME, 16 - FRAME, inner});
        boxes.add(new int[] {5, 9, 0, 11, 13, inner});
        boxes.add(new int[] {5, FRAME, 0, 11, 6, inner});
        return boxes;
    }

    /**
     * Die Brennkammer: Rahmen, Klappe, Sichtfenster — und der Griff, der als
     * einziger vor der Klappe steht.
     */
    public static List<int[]> burner() {
        List<int[]> boxes = new ArrayList<>(frontFrame());
        boxes.add(new int[] {0, 0, DEPTH, 16, 16, 16});
        boxes.add(new int[] {FRAME, FRAME, 1, 16 - FRAME, 16 - FRAME, DEPTH - 1});
        boxes.add(new int[] {5, 5, DEPTH - 1, 11, 11, DEPTH});
        boxes.add(new int[] {FRAME, 7, 0, FRAME + 1, 9, 1});
        return boxes;
    }

    /** Der Rahmen ringsum, den sich Presse und Brennkammer teilen. */
    private static List<int[]> frontFrame() {
        return List.of(
                new int[] {0, 0, 0, 16, FRAME, DEPTH},
                new int[] {0, 16 - FRAME, 0, 16, 16, DEPTH},
                new int[] {0, FRAME, 0, FRAME, 16 - FRAME, DEPTH},
                new int[] {16 - FRAME, FRAME, 0, 16, 16 - FRAME, DEPTH});
    }

    /** Der Fabricator: Sockel, zurückspringender Körper, Deckel, Aufbau. */
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
     * Der Controller-Anbau: ein Käfig aus zwölf Kantenleisten um einen Kern,
     * der ringsum einen Blockpixel zurückspringt.
     *
     * <p>Die senkrechten Leisten stehen in den Ecken, die waagerechten fangen
     * dahinter an — keine zwei überlappen. Zwei Flächen in derselben Ebene
     * flimmern im Spiel, und zwar nur aus manchen Winkeln.
     */
    public static List<int[]> extension() {
        int edge = 1;
        int far = 16 - edge;
        List<int[]> boxes = new ArrayList<>();
        boxes.add(new int[] {edge, edge, edge, far, far, far});
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

    /** Die Kreativquelle: ein zurückspringender Kern und acht Eckklötze. */
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
