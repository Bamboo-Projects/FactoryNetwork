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

    /** Breite des Rahmens der Brennkammer. */
    private static final int FRAME = 3;

    /**
     * Wie tief der Rahmen der Brennkammer ist.
     *
     * <p>Drei, weil hier drei Ebenen übereinander liegen: der Rahmen, die
     * Klappe dahinter und das Sichtfenster darin. Bei zwei bliebe für die
     * Klappe kein Blockpixel übrig.
     */
    private static final int BURNER_DEPTH = 3;

    /** Stärke der Wände der Presse. */
    private static final int WALL = 3;

    /** Stärke ihrer Rückwand. */
    private static final int BACK = 4;

    /** Höhe des Ambosses. */
    private static final int ANVIL = 2;

    /** Wie weit Amboss und Stempel hinter der Blockkante liegen. */
    private static final int TOOL_IN = 1;

    /** Und wie weit sie schmaler sind als der Hohlraum. */
    private static final int TOOL_SIDE = 1;

    /**
     * Die Presse: ein Gehäuse mit einem Loch darin.
     *
     * <p>Zwei Seitenwände, Boden, Decke und eine Rückwand — dazwischen ist
     * nichts. Vorn ist der Block offen, und was man durch die Öffnung sieht,
     * ist der Amboss unten und der Stempel darüber.
     *
     * <p><b>Der Stempel steht nicht in dieser Liste.</b> Er bewegt sich und
     * ist deshalb ein eigenes Modell, das der {@code PressRenderer}
     * zeichnet. Für die Trefferfläche zählt er nicht: Man greift nach dem
     * Gehäuse, nicht nach einem Teil, das gerade woanders steht.
     */
    public static List<int[]> press() {
        return List.of(
                new int[] {0, 0, 0, WALL, 16, 16},
                new int[] {16 - WALL, 0, 0, 16, 16, 16},
                new int[] {WALL, 0, 0, 16 - WALL, WALL, 16},
                new int[] {WALL, 16 - WALL, 0, 16 - WALL, 16, 16},
                // Die Rückwand schließt den Hohlraum. Ohne sie sähe man durch
                // den Block hindurch und griffe beim Zielen daneben.
                new int[] {WALL, WALL, 16 - BACK, 16 - WALL, 16 - WALL, 16},
                new int[] {WALL + TOOL_SIDE, WALL, TOOL_IN,
                        16 - WALL - TOOL_SIDE, WALL + ANVIL, 16 - BACK});
    }

    /**
     * Wie weit der Stempel fährt, in Blockpixeln.
     *
     * <p>Von seiner Ruhelage unter der Decke bis auf den Amboss. Der
     * Renderer rechnet daraus die Bewegung; steht die Zahl hier, ändert sich
     * beides gemeinsam.
     */
    public static int pressStroke() {
        return (16 - WALL - ANVIL) - (WALL + ANVIL);
    }

    /**
     * Die Brennkammer: Rahmen, Klappe, Sichtfenster — und der Griff, der als
     * einziger vor der Klappe steht.
     */
    public static List<int[]> burner() {
        List<int[]> boxes = new ArrayList<>(frontFrame(BURNER_DEPTH));
        boxes.add(new int[] {0, 0, BURNER_DEPTH, 16, 16, 16});
        boxes.add(new int[] {FRAME, FRAME, 1, 16 - FRAME, 16 - FRAME, BURNER_DEPTH - 1});
        boxes.add(new int[] {5, 5, BURNER_DEPTH - 1, 11, 11, BURNER_DEPTH});
        boxes.add(new int[] {FRAME, 7, 0, FRAME + 1, 9, 1});
        return boxes;
    }

    /** Der Rahmen ringsum die Klappe der Brennkammer. */
    private static List<int[]> frontFrame(int deep) {
        return List.of(
                new int[] {0, 0, 0, 16, FRAME, deep},
                new int[] {0, 16 - FRAME, 0, 16, 16, deep},
                new int[] {0, FRAME, 0, FRAME, 16 - FRAME, deep},
                new int[] {16 - FRAME, FRAME, 0, 16, 16 - FRAME, deep});
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
        return cage(1, 1);
    }

    /**
     * Ein Käfig: zwölf Leisten auf den Blockkanten, dazwischen ein Kern.
     *
     * <p>Die senkrechten Leisten stehen in den Ecken, die waagerechten fangen
     * dahinter an — keine zwei überlappen. Zwei Flächen in derselben Ebene
     * flimmern im Spiel, und zwar nur aus manchen Winkeln.
     *
     * @param edge  Stärke einer Leiste
     * @param inset wie weit der Kern zurückspringt
     */
    private static List<int[]> cage(int edge, int inset) {
        int far = 16 - edge;
        List<int[]> boxes = new ArrayList<>();
        boxes.add(new int[] {inset, inset, inset,
                16 - inset, 16 - inset, 16 - inset});
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

    /**
     * Der Router: derselbe Käfig, nur mit dicken Leisten und tieferem Kern.
     *
     * <p><b>Die drei Blockpixel Leistenstärke sind gemessen.</b> Der
     * {@code RouterRenderer} malt die Bahnkennung über die volle Fläche jeder
     * Seite; ihr Ring läuft von Blockpixel 1 bis 3 und von 12,75 bis 14,75,
     * dazwischen ist er durchsichtig. Drei decken den inneren Rand genau ab.
     * Dünner, und der Ring schwebt; dicker, und die Leiste verdeckt aus
     * schrägem Blick die vier Kontakte in der Mitte.
     */
    public static List<int[]> router() {
        // Ein Blockpixel Vertiefung. Zwei ließen in jeder Seite ein Loch,
        // das aussah wie ein Loch; eine Platte in der Mitte, die das
        // ausglich, zeigte den Kragen der Textur ein zweites Mal.
        return cage(3, 1);
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
