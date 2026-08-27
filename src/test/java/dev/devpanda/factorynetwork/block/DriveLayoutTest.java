package dev.devpanda.factorynetwork.block;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Was am Laufwerk eigen ist — und die Drehung, die alle ausgerichteten
 * Blöcke betrifft.
 *
 * <p>Dass Modell und Trefferfläche dieselben Kästen haben, prüft
 * {@link MachineLayoutTest}. Hier steht die zweite Falle: Das Modell dreht
 * der Blockzustand, die Trefferfläche muss jemand mitdrehen. Wer die Formel
 * dafür verdreht, merkt es an drei von vier Wänden — und an der vierten
 * nicht.
 */
class DriveLayoutTest {

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
        assertPoint(16, 8, FacingShapes.spin(8, 0, Direction.EAST));
        assertPoint(8, 16, FacingShapes.spin(8, 0, Direction.SOUTH));
        assertPoint(0, 8, FacingShapes.spin(8, 0, Direction.WEST));
        assertPoint(8, 0, FacingShapes.spin(8, 0, Direction.NORTH));
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
                assertPoint(x, z, point);
            }
        }
    }

    private static void assertPoint(double x, double z, double[] actual) {
        assertEquals(2, actual.length);
        assertEquals(x, actual[0], "die x-Achse stimmt nicht");
        assertEquals(z, actual[1], "die z-Achse stimmt nicht");
    }
}
