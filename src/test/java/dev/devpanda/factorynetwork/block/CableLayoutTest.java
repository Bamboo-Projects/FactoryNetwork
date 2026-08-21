package dev.devpanda.factorynetwork.block;

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
}
