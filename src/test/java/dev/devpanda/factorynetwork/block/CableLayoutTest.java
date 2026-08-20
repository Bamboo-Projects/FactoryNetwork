package dev.devpanda.factorynetwork.block;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wacht darüber, dass Modelle und Trefferflächen dieselben Zahlen tragen.
 *
 * <p>Minecraft hält beides getrennt: Das Modell steht in JSON, die
 * Trefferfläche in Java. Laufen sie auseinander, greift der Spieler neben
 * das, was er sieht — ein Fehler, den man im Spiel spürt, aber schwer
 * benennen kann. Deshalb liest dieser Test die erzeugten Modelldateien.
 */
class CableLayoutTest {

    private static final Path MODELS = Path.of(
            "src/main/resources/assets/factorynetwork/models/block");

    @Test
    @DisplayName("Ein Strang allein ist sechs Pixel dick, im Bündel vier")
    void strandSizes() {
        assertEquals(6, CableLayout.size(1));
        assertEquals(4, CableLayout.size(2));
        assertEquals(4, CableLayout.size(4));
    }

    @Test
    @DisplayName("In der Tiefe sitzt jeder Strang mittig")
    void depthIsCentred() {
        assertEquals(5, CableLayout.depth(1));
        assertEquals(6, CableLayout.depth(4));
        for (int count = 1; count <= 4; count++) {
            assertEquals(16, CableLayout.depth(count) * 2 + CableLayout.size(count),
                    "Vorn und hinten muss gleich viel Platz bleiben");
        }
    }

    @Test
    @DisplayName("Für jede Anzahl gibt es genau so viele Plätze")
    void positionCounts() {
        for (int count = 1; count <= 4; count++) {
            assertEquals(count, CableLayout.count(count));
        }
    }

    @Test
    @DisplayName("Kein Strang ragt aus dem Block")
    void strandsStayInside() {
        for (int count = 1; count <= 4; count++) {
            int size = CableLayout.size(count);
            for (int[] position : CableLayout.positions(count)) {
                assertTrue(position[0] >= 0 && position[0] + size <= 16,
                        "x bei " + count + " Strängen");
                assertTrue(position[1] >= 0 && position[1] + size <= 16,
                        "y bei " + count + " Strängen");
            }
        }
    }

    @Test
    @DisplayName("Stränge überlappen sich nicht")
    void strandsDoNotOverlap() {
        for (int count = 2; count <= 4; count++) {
            int size = CableLayout.size(count);
            int[][] positions = CableLayout.positions(count);
            for (int a = 0; a < positions.length; a++) {
                for (int b = a + 1; b < positions.length; b++) {
                    boolean apart =
                            positions[a][0] + size <= positions[b][0]
                            || positions[b][0] + size <= positions[a][0]
                            || positions[a][1] + size <= positions[b][1]
                            || positions[b][1] + size <= positions[a][1];
                    assertTrue(apart, "Strang " + a + " und " + b + " bei "
                            + count + " Strängen liegen ineinander");
                }
            }
        }
    }

    @Test
    @DisplayName("Die Modelldateien tragen dieselben Zahlen")
    void modelsMatchLayout() throws IOException {
        int checked = 0;
        for (int count = 1; count <= 4; count++) {
            int size = CableLayout.size(count);
            int depth = CableLayout.depth(count);
            int[][] positions = CableLayout.positions(count);

            for (int index = 0; index < positions.length; index++) {
                Path path = MODELS.resolve("cable_core_" + count + "_" + index + ".json");
                assertTrue(Files.exists(path), "Modell fehlt: " + path);
                String json = Files.readString(path, StandardCharsets.UTF_8);

                double[] from = triple(json, "from");
                double[] to = triple(json, "to");

                assertEquals(positions[index][0], from[0], 0.001, "x in " + path.getFileName());
                assertEquals(positions[index][1], from[1], 0.001, "y in " + path.getFileName());
                assertEquals(depth, from[2], 0.001, "Tiefe in " + path.getFileName());
                assertEquals(size, to[0] - from[0], 0.001, "Breite in " + path.getFileName());
                assertEquals(size, to[1] - from[1], 0.001, "Höhe in " + path.getFileName());
                checked++;
            }
        }
        assertEquals(10, checked, "Anzahl geprüfter Kerne");
    }

    /** Liest die drei Zahlen hinter einem Schlüssel, ohne JSON zu deuten. */
    private static double[] triple(String json, String key) {
        int start = json.indexOf(quoted(key));
        assertTrue(start >= 0, "Schlüssel fehlt: " + key);
        int open = json.indexOf('[', start);
        int close = json.indexOf(']', open);
        String[] parts = json.substring(open + 1, close).split(",");
        assertEquals(3, parts.length, "Drei Zahlen erwartet bei " + key);
        double[] values = new double[3];
        for (int i = 0; i < 3; i++) {
            values[i] = Double.parseDouble(parts[i].trim());
        }
        return values;
    }

    private static String quoted(String key) {
        return '"' + key + '"';
    }
}
