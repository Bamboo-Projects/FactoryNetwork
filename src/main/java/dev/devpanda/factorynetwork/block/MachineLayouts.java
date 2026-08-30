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

    /** Höhe der Boden- und der Deckplatte der Presse. */
    private static final int BASE = 2;

    /** Tiefe des Rahmens, der vor ihrem Gehäuse steht. */
    private static final int PORTAL = 2;

    /** Breite seiner Pfeiler. */
    private static final int PILLAR = 4;

    /** Höhe seines Querträgers. */
    private static final int HEAD = 3;

    /**
     * Wie tief der Deckel unter der Blockoberkante liegt.
     *
     * <p>Damit die Zylinderköpfe darauf Platz haben, ohne aus dem Block zu
     * ragen. Geometrie außerhalb von 0 bis 16 beleuchtet Minecraft falsch —
     * die Mulde ist der Weg, trotzdem etwas oben stehen zu haben.
     */
    private static final int CAP = 1;

    /** Höhe des Stempels. */
    private static final int RAM = 4;

    /**
     * Wie weit der Gehäusekern hinter den Platten zurückbleibt.
     *
     * <p><b>Das ist die Schattenfuge</b>, und sie ist der billigste Griff der
     * ganzen Form: Ein Kern, der bündig mit Sockel und Deckel abschließt,
     * liest sich als eine Fläche. Einen Blockpixel zurückgesetzt, liest er
     * sich als Gehäuse zwischen zwei Platten.
     */
    private static final int INSET = 1;

    /** Stärke der Seitenwände der Presse. */
    private static final int WALL = 3;

    /** Stärke ihrer Rückwand. */
    private static final int BACK = 4;

    /** Höhe des Ambosses über der Bodenplatte. */
    private static final int ANVIL = 2;

    /**
     * Die Presse: ein Portalrahmen mit einem Gehäuse dahinter.
     *
     * <p>Vorn stehen zwei Pfeiler und ein Querträger, dahinter sitzt das
     * Gehäuse zwischen Sockel und Deckel. Unten bleibt der Rahmen offen: Dort
     * schaut das Pressbett heraus, und dort greift die Maschine.
     *
     * <p><b>Die Kleinteile stehen mit in dieser Liste</b> — Kühlrippen,
     * Zylinderköpfe, Klemmkasten. Sie kosten ein paar Kästen mehr in der
     * Trefferfläche und ersparen dem Spieler, auf eine Rippe zu zielen und
     * Luft zu treffen. Was man sieht, greift man auch.
     *
     * <p><b>Der Stempel steht nicht darin.</b> Er bewegt sich und ist deshalb
     * ein eigenes Modell, das der {@code PressRenderer} zeichnet. Für die
     * Trefferfläche zählt er nicht: Man greift nach dem Gehäuse, nicht nach
     * einem Teil, das gerade woanders steht.
     */
    public static List<int[]> press() {
        int lid = 16 - CAP;
        int deck = lid - BASE;
        return List.of(
                // Sockel und Deckel, über die volle Breite.
                new int[] {0, 0, PORTAL, 16, BASE, 16},
                new int[] {0, deck, PORTAL, 16, lid, 16},
                // Der Rahmen davor.
                new int[] {0, 0, 0, PILLAR, lid, PORTAL},
                new int[] {16 - PILLAR, 0, 0, 16, lid, PORTAL},
                new int[] {PILLAR, lid - HEAD, 0, 16 - PILLAR, lid, PORTAL},
                // Der Gehäusekern, zurückgesetzt hinter Sockel und Deckel.
                new int[] {INSET, BASE, PORTAL, INSET + WALL, deck, 16},
                new int[] {16 - INSET - WALL, BASE, PORTAL, 16 - INSET, deck, 16},
                new int[] {INSET + WALL, BASE, 16 - BACK, 16 - INSET - WALL, deck, 16},
                // Das Pressbett zieht bis unter den Rahmen vor.
                new int[] {PILLAR, BASE, 1, 16 - PILLAR, BASE + ANVIL, 16 - BACK},
                // Kühlrippen in der Fuge, je zwei links und rechts.
                new int[] {0, 4, 4, INSET, 7, 14},
                new int[] {0, 8, 4, INSET, 11, 14},
                new int[] {16 - INSET, 4, 4, 16, 7, 14},
                new int[] {16 - INSET, 8, 4, 16, 11, 14},
                // Der Klemmkasten hinten oben.
                new int[] {5, 8, 15, 11, 11, 16},
                // Zwei Zylinderköpfe in der Mulde, dazwischen die Leitung.
                new int[] {5, lid, 6, 7, 16, 9},
                new int[] {9, lid, 6, 11, 16, 9},
                new int[] {7, lid, 7, 9, 16, 8},
                // Eine schmale Leiste unter dem Querträger.
                new int[] {PILLAR, lid - HEAD - 1, 0, 16 - PILLAR, lid - HEAD, PORTAL});
    }

    /**
     * Wie weit der Stempel fährt, in Blockpixeln.
     *
     * <p>Von seiner Ruhelage unter der Decke bis auf den Amboss. Der
     * Renderer rechnet daraus die Bewegung; steht die Zahl hier, ändert sich
     * beides gemeinsam.
     */
    public static int pressStroke() {
        return (16 - CAP - BASE - RAM) - (BASE + ANVIL);
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
