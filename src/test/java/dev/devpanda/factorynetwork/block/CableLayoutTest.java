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
 * Watches that models and hitboxes carry the same numbers.
 *
 * <p>Minecraft keeps the two apart: the model lives in JSON, the hitbox in
 * Java. If they drift apart, the player grabs next to what he sees — an error
 * you feel in the game but can hardly name. That is why this test reads the
 * generated model files.
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
    @DisplayName("The thicknesses are those of AE2: six and ten block pixels")
    void sizesMatchAppliedEnergistics() {
        assertEquals(6, CableLayout.THIN);
        assertEquals(10, CableLayout.DENSE);
    }

    @Test
    @DisplayName("The sheath sits centred in the block")
    void mantleIsCentred() {
        assertEquals(5, CableLayout.offset(CableLayout.THIN));
        assertEquals(11, CableLayout.far(CableLayout.THIN));
        assertEquals(3, CableLayout.offset(CableLayout.DENSE));
        assertEquals(13, CableLayout.far(CableLayout.DENSE));
    }

    @Test
    @DisplayName("The core in the model has the dimensions from CableLayout")
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
    @DisplayName("The arm reaches from the block edge up to the core")
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
     * The orientation of the texture is the error that showed up in the game
     * as corrugated iron: all faces had the same cutout.
     */
    @Test
    @DisplayName("Lengthwise faces take the edge region of the texture, end faces the middle")
    void uvsRunAlongTheCable() throws IOException {
        int lo = CableLayout.offset(CableLayout.THIN);
        int hi = CableLayout.far(CableLayout.THIN);
        String json = read("cable_arm.json");

        // End face: the cross section in both axes.
        assertTrue(json.contains("\"uv\":[" + lo + "," + lo + "," + hi + "," + hi + "]"),
                "Die Stirnfläche muss den Querschnitt zeigen");
        // Sides: lengthwise over the depth, crosswise over the height.
        assertTrue(json.contains("\"uv\":[0," + lo + "," + lo + "," + hi + "]"),
                "Die Seitenflächen müssen längs laufen");
        // Top and bottom: crosswise over the width.
        assertTrue(json.contains("\"uv\":[" + lo + ",0," + hi + "," + lo + "]"),
                "Ober- und Unterseite müssen längs laufen");
    }

    @Test
    @DisplayName("The blockstate no longer knows any strands")
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
     * The connectors on the faces: twelve models, the same numbers.
     *
     * <p>Here the double bookkeeping pays off. {@code CableShapes} computes the
     * hitbox, {@code tools/assets.py} the model — both with the same formula,
     * but in two languages. If one of the two runs off, you grab next to what
     * you see.
     */
    @Test
    @DisplayName("Plate and stem in the model have the dimensions from CableLayout")
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

                // Plate and lamp ring — nothing more. The stretch to the core
                // is covered by the arm of the cable, and a stem there would be
                // a second box at the same spot.
                assertEquals(2, elementCount(json),
                        name + ": ein Kasten zu viel oder zu wenig");
            }
        }
    }

    /** A corner as it stands in the model: {@code "from":[2,2,-0.05]}. */
    private static String corner(String key, double[] box, int at) {
        return "\"" + key + "\":[" + number(box[at]) + "," + number(box[at + 1])
                + "," + number(box[at + 2]) + "]";
    }

    /**
     * A number the way the model script writes it.
     *
     * <p>Whole numbers without a decimal point, fractional ones with — exactly
     * what Python's {@code json.dumps} does with an {@code int} and a
     * {@code float}.
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
