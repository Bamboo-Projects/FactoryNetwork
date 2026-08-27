package dev.devpanda.factorynetwork.block;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wacht darüber, dass das Laufwerk im Modell und in der Trefferfläche
 * dasselbe ist — und dass die Drehung stimmt.
 *
 * <p>Beim Laufwerk kommt zu der Falle, die schon das Gateway hatte, eine
 * zweite: Das Modell dreht der Blockzustand, die Trefferfläche muss jemand
 * mitdrehen. Wer die Formel dafür verdreht, merkt es an drei von vier
 * Wänden — und an der vierten nicht.
 */
class DriveLayoutTest {

    private static final Path MODEL = Path.of(
            "src/main/resources/assets/factorynetwork/models/block/drive.json");

    private static String model() throws IOException {
        assertTrue(Files.exists(MODEL), "drive.json fehlt — tools/assets.py laufen lassen");
        return Files.readString(MODEL, StandardCharsets.UTF_8).replaceAll("[ \t\r\n]+", "");
    }

    @Test
    @DisplayName("Jeder Kasten der Trefferfläche steht so auch im Modell")
    void everyBoxIsInTheModel() throws IOException {
        String json = model();
        for (int[] box : DriveLayout.boxes()) {
            String pair = "\"from\":[%d,%d,%d],\"to\":[%d,%d,%d]"
                    .formatted(box[0], box[1], box[2], box[3], box[4], box[5]);
            assertTrue(json.contains(pair),
                    "drive.json kennt diesen Kasten nicht: " + pair);
        }
    }

    @Test
    @DisplayName("Und das Modell hat keinen Kasten darüber hinaus")
    void theModelHasNoExtraBox() throws IOException {
        String json = model();
        int found = json.split("\"from\":", -1).length - 1;
        assertEquals(DriveLayout.boxes().size(), found,
                "Modell und DriveLayout zählen verschieden viele Kästen");
    }

    @Test
    @DisplayName("Das Modell hat eigene Kästen und erbt keinen Würfel mehr")
    void theModelIsNoLongerACube() throws IOException {
        String json = model();
        assertTrue(json.contains("\"elements\":["), "drive.json hat keine Kästen");
        assertFalse(json.contains("orientable"),
                "drive.json hängt wieder an einem Würfel");
    }

    @Test
    @DisplayName("Kein Kasten ragt aus dem Block heraus")
    void everyBoxStaysInsideTheBlock() {
        for (int[] box : DriveLayout.boxes()) {
            for (int i = 0; i < 3; i++) {
                assertTrue(box[i] >= 0 && box[i] < box[i + 3] && box[i + 3] <= 16,
                        "Kasten außerhalb von 0 bis 16: Achse " + i);
            }
        }
    }

    @Test
    @DisplayName("Die Blende steht vor dem Gehäuse, das Feld liegt darin")
    void theBezelStandsProud() {
        assertTrue(DriveLayout.FRONT > 0, "die Blende hat keine Tiefe");
        assertTrue(DriveLayout.INSET > 0,
                "das Gehäuse ist so breit wie die Blende — dann steht sie nicht vor");
        assertTrue(DriveLayout.RECESS > 0 && DriveLayout.RECESS < DriveLayout.FRONT,
                "das Schachtfeld liegt nicht in der Blende");
        assertTrue(DriveLayout.FOOT > 0, "das Gerät steht nicht auf Füßen");
        assertTrue(2 * DriveLayout.FOOT_WIDE < 16,
                "die Füße stoßen in der Mitte aneinander");
    }

    @Test
    @DisplayName("Nach Osten gedreht liegt die Vorderseite im Osten")
    void turningEastPutsTheFrontEast() {
        // Die Mitte der Vorderseite bei Norden ist (8, 0). Nach Osten gedreht
        // muss sie auf (16, 8) liegen — sonst greift man an drei von vier
        // Wänden daneben.
        assertArrayEquals(new double[] {16, 8}, FacingShapes.spin(8, 0, Direction.EAST));
        assertArrayEquals(new double[] {8, 16}, FacingShapes.spin(8, 0, Direction.SOUTH));
        assertArrayEquals(new double[] {0, 8}, FacingShapes.spin(8, 0, Direction.WEST));
        assertArrayEquals(new double[] {8, 0}, FacingShapes.spin(8, 0, Direction.NORTH));
    }

    @Test
    @DisplayName("Viermal gedreht steht jeder Punkt wieder am Anfang")
    void fourTurnsComeBack() {
        // Eine Drehung, die sich nicht schließt, ist keine Drehung. Die Probe
        // kostet nichts und fängt jedes falsche Vorzeichen.
        for (int x = 0; x <= 16; x += 4) {
            for (int z = 0; z <= 16; z += 4) {
                double[] point = {x, z};
                for (int turn = 0; turn < 4; turn++) {
                    point = FacingShapes.spin((int) point[0], (int) point[1],
                            Direction.EAST);
                }
                assertArrayEquals(new double[] {x, z}, point,
                        "der Punkt " + x + "/" + z + " kommt nicht zurück");
            }
        }
    }

    private static void assertArrayEquals(double[] expected, double[] actual) {
        assertArrayEquals(expected, actual, "");
    }

    private static void assertArrayEquals(double[] expected, double[] actual, String hint) {
        assertEquals(expected.length, actual.length, hint);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], hint);
        }
    }
}
