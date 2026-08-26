package dev.devpanda.factorynetwork.block;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wacht darüber, dass Modelle und Trefferflächen dieselben Zahlen tragen.
 *
 * <p>Minecraft hält beides getrennt: Das Modell steht in JSON, die
 * Trefferfläche in Java. Laufen sie auseinander, greift der Spieler neben das,
 * was er sieht — ein Fehler, den man im Spiel spürt, aber schwer benennen
 * kann. Deshalb liest dieser Test die erzeugten Modelldateien.
 */
class CableLayoutTest {

    private static final Path MODELS = Path.of(
            "src/main/resources/assets/factorynetwork/models/block");

    private static String read(String name) throws IOException {
        Path file = MODELS.resolve(name);
        assertTrue(Files.exists(file), name + " fehlt — tools/assets.py laufen lassen");
        return Files.readString(file, StandardCharsets.UTF_8).replaceAll("[ \t\r\n]+", "");
    }

    @Test
    @DisplayName("Die Stärken sind die von AE2: sechs und zehn Blockpixel")
    void sizesMatchAppliedEnergistics() {
        assertEquals(6, CableLayout.THIN);
        assertEquals(10, CableLayout.DENSE);
    }

    @Test
    @DisplayName("Der Mantel sitzt mittig im Block")
    void mantleIsCentred() {
        assertEquals(5, CableLayout.offset(CableLayout.THIN));
        assertEquals(11, CableLayout.far(CableLayout.THIN));
        assertEquals(3, CableLayout.offset(CableLayout.DENSE));
        assertEquals(13, CableLayout.far(CableLayout.DENSE));
    }

    @Test
    @DisplayName("Der Kern im Modell hat die Maße aus CableLayout")
    void coreModelMatchesLayout() throws IOException {
        for (int size : new int[] {CableLayout.THIN, CableLayout.DENSE}) {
            String name = size == CableLayout.THIN ? "cable_core.json" : "dense_cable_core.json";
            int lo = CableLayout.offset(size);
            int hi = CableLayout.far(size);
            String json = read(name);
            assertTrue(json.contains("\"from\":[" + lo + "," + lo + "," + lo + "]"),
                    name + ": Anfang stimmt nicht mit CableLayout überein");
            assertTrue(json.contains("\"to\":[" + hi + "," + hi + "," + hi + "]"),
                    name + ": Ende stimmt nicht mit CableLayout überein");
        }
    }

    @Test
    @DisplayName("Der Arm reicht von der Blockkante bis an den Kern")
    void armReachesFromEdgeToCore() throws IOException {
        for (int size : new int[] {CableLayout.THIN, CableLayout.DENSE}) {
            String name = size == CableLayout.THIN ? "cable_arm.json" : "dense_cable_arm.json";
            int lo = CableLayout.offset(size);
            int hi = CableLayout.far(size);
            String json = read(name);
            assertTrue(json.contains("\"from\":[" + lo + "," + lo + ",0]"), name + ": Anfang");
            assertTrue(json.contains("\"to\":[" + hi + "," + hi + "," + lo + "]"), name + ": Ende");
        }
    }

    /**
     * Die Ausrichtung der Textur ist der Fehler, der im Spiel als Wellblech
     * auffiel: Alle Flächen hatten denselben Ausschnitt.
     */
    @Test
    @DisplayName("Längsflächen nehmen den Randbereich der Textur, Stirnflächen die Mitte")
    void uvsRunAlongTheCable() throws IOException {
        int lo = CableLayout.offset(CableLayout.THIN);
        int hi = CableLayout.far(CableLayout.THIN);
        String json = read("cable_arm.json");

        // Stirnfläche: in beiden Achsen der Querschnitt.
        assertTrue(json.contains("\"uv\":[" + lo + "," + lo + "," + hi + "," + hi + "]"),
                "Die Stirnfläche muss den Querschnitt zeigen");
        // Seiten: längs über die Tiefe, quer über die Höhe.
        assertTrue(json.contains("\"uv\":[0," + lo + "," + lo + "," + hi + "]"),
                "Die Seitenflächen müssen längs laufen");
        // Oben und unten: quer über die Breite.
        assertTrue(json.contains("\"uv\":[" + lo + ",0," + hi + "," + lo + "]"),
                "Ober- und Unterseite müssen längs laufen");
    }

    @Test
    @DisplayName("Die Blockstate kennt keine Stränge mehr")
    void blockstateHasNoStrands() throws IOException {
        Path file = Path.of("src/main/resources/assets/factorynetwork/blockstates/cable.json");
        String json = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(!json.contains("strands"),
                "Das Bündeln ist ausgebaut — die Blockstate darf es nicht mehr nennen");
        assertTrue(json.contains("cable_core"), "Der Kern fehlt");
        for (String direction : List.of("north", "south", "east", "west", "up", "down")) {
            assertTrue(json.contains("\"" + direction + "\""), "Arm nach " + direction + " fehlt");
        }
    }

    /**
     * Die Anschlüsse an den Flächen: zwölf Modelle, dieselben Zahlen.
     *
     * <p>Hier zahlt sich die doppelte Buchführung aus. {@code CableShapes}
     * rechnet die Trefferfläche, {@code tools/assets.py} das Modell — beide
     * mit derselben Formel, aber in zwei Sprachen. Läuft eine der beiden
     * davon, greift man neben das, was man sieht.
     */
    @Test
    @DisplayName("Platte und Stiel im Modell haben die Maße aus CableLayout")
    void partModelsMatchLayout() throws IOException {
        int wide = CableLayout.partOffset();
        for (int size : new int[] {CableLayout.THIN, CableLayout.DENSE}) {
            String prefix = size == CableLayout.THIN ? "" : "dense_";
            for (Direction facing : Direction.values()) {
                String name = prefix + "connector_part_" + facing.getName() + ".json";
                String json = read(name);

                double[] plate = CableShapes.slabBox(facing, -CableLayout.PART_OVERHANG,
                        CableLayout.PART_DEPTH, wide, 16 - wide);
                assertTrue(json.contains(corner("from", plate, 0)),
                        name + ": die Platte beginnt woanders als in CableLayout");
                assertTrue(json.contains(corner("to", plate, 3)),
                        name + ": die Platte endet woanders als in CableLayout");

                // Platte und Lämpchenring — mehr nicht. Die Strecke zum Kern
                // deckt der Arm des Kabels ab, und ein Stiel dort wäre ein
                // zweiter Kasten an derselben Stelle.
                assertEquals(2, elementCount(json),
                        name + ": ein Kasten zu viel oder zu wenig");
            }
        }
    }

    /** Eine Ecke als das, was im Modell steht: {@code "from":[2,2,-0.05]}. */
    private static String corner(String key, double[] box, int at) {
        return "\"" + key + "\":[" + number(box[at]) + "," + number(box[at + 1])
                + "," + number(box[at + 2]) + "]";
    }

    /**
     * Eine Zahl so, wie das Modellskript sie schreibt.
     *
     * <p>Ganze Zahlen ohne Komma, gebrochene mit — genau das macht Pythons
     * {@code json.dumps} mit einem {@code int} und einem {@code float}.
     */
    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((int) value)
                : String.valueOf(value);
    }

    private static int elementCount(String json) {
        int found = 0;
        int at = json.indexOf("\"from\":");
        while (at >= 0) {
            found++;
            at = json.indexOf("\"from\":", at + 1);
        }
        return found;
    }
}
