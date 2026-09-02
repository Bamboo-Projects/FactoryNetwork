package dev.devpanda.factorynetwork.block;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What is peculiar to the drive — and the rotation that concerns all
 * oriented blocks.
 *
 * <p>That model and hitbox have the same boxes is checked by
 * {@link MachineLayoutTest}. Here stands the second trap: the block state
 * rotates the model, someone has to rotate the hitbox along with it. Whoever
 * garbles the formula for that notices it on three of four walls — and not on
 * the fourth.
 */
class DriveLayoutTest {

    @Test
    @DisplayName("The bezel stands proud of the housing, the field lies within it")
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
    @DisplayName("Rotated east the front lies in the east")
    void turningEastPutsTheFrontEast() {
        // The centre of the front at north is (8, 0). Rotated east it must
        // lie at (16, 8) — otherwise you grab next to it on three of four
        // walls.
        assertPoint(16, 8, FacingShapes.spin(8, 0, Direction.EAST));
        assertPoint(8, 16, FacingShapes.spin(8, 0, Direction.SOUTH));
        assertPoint(0, 8, FacingShapes.spin(8, 0, Direction.WEST));
        assertPoint(8, 0, FacingShapes.spin(8, 0, Direction.NORTH));
    }

    @Test
    @DisplayName("Rotated four times every point stands at the start again")
    void fourTurnsComeBack() {
        // A rotation that does not close is no rotation. The check costs
        // nothing and catches every wrong sign.
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
