package dev.devpanda.factorynetwork.block;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the model and hitbox of every block that is no longer a cube
 * together.
 *
 * <p>Minecraft keeps the two apart: the model lives in JSON, the hitbox in
 * Java. If they drift apart, the player grabs next to what he sees — an error
 * you feel in the game but can hardly name.
 *
 * <p><b>One class for all</b>, because the checks are the same for every
 * block. What is peculiar to a single block stands in its own test class:
 * {@link GatewayLayoutTest} and {@link DriveLayoutTest}. Whoever reworks the
 * next block adds it to the list below and gets the four basic checks for
 * free.
 */
class MachineLayoutTest {

    /** A reworked block: name of the model file, boxes, old ancestor. */
    private record Machine(String model, Supplier<List<int[]>> boxes, String cube) {
    }

    private static final List<Machine> MACHINES = List.of(
            new Machine("gateway", GatewayLayout::boxes, "cube_all"),
            new Machine("drive", DriveLayout::boxes, "orientable"),
            new Machine("controller", ControllerLayout::boxes, "cube_bottom_top"),
            new Machine("terminal", TerminalLayout::boxes, "orientable"),
            new Machine("press", MachineLayouts::press, "orientable"),
            new Machine("burner", MachineLayouts::burner, "orientable"),
            new Machine("burner_on", MachineLayouts::burner, "orientable"),
            new Machine("fabricator", MachineLayouts::fabricator, "cube_bottom_top"),
            new Machine("controller_extension", MachineLayouts::extension, "cube_all"),
            new Machine("creative_source", MachineLayouts::source, "cube_all"),
            new Machine("router", MachineLayouts::router, "cube_all"),
            new Machine("mast", MastLayout::boxes, "cube_all"));

    private static String model(String name) throws IOException {
        Path file = Path.of("src/main/resources/assets/factorynetwork/models/block",
                name + ".json");
        assertTrue(Files.exists(file), name + ".json fehlt — tools/assets.py laufen lassen");
        return Files.readString(file, StandardCharsets.UTF_8).replaceAll("[ \t\r\n]+", "");
    }

    @Test
    @DisplayName("Every box of the hitbox stands the same way in the model too")
    void everyBoxIsInTheModel() throws IOException {
        for (Machine machine : MACHINES) {
            String json = model(machine.model());
            for (int[] box : machine.boxes().get()) {
                String pair = "\"from\":[%d,%d,%d],\"to\":[%d,%d,%d]"
                        .formatted(box[0], box[1], box[2], box[3], box[4], box[5]);
                assertTrue(json.contains(pair),
                        machine.model() + ".json kennt diesen Kasten nicht: " + pair);
            }
        }
    }

    @Test
    @DisplayName("And no model has a box beyond that")
    void noModelHasAnExtraBox() throws IOException {
        for (Machine machine : MACHINES) {
            String json = model(machine.model());
            int found = json.split("\"from\":", -1).length - 1;
            assertEquals(machine.boxes().get().size(), found,
                    machine.model() + ": Modell und Layout zählen verschieden viele Kästen");
        }
    }

    @Test
    @DisplayName("No model inherits a cube any more")
    void noModelIsACubeAnyMore() throws IOException {
        for (Machine machine : MACHINES) {
            String json = model(machine.model());
            assertTrue(json.contains("\"elements\":["),
                    machine.model() + ".json hat keine Kästen");
            assertFalse(json.contains(machine.cube()),
                    machine.model() + ".json hängt wieder an " + machine.cube());
        }
    }

    @Test
    @DisplayName("No box sticks out of its block")
    void everyBoxStaysInsideTheBlock() {
        for (Machine machine : MACHINES) {
            for (int[] box : machine.boxes().get()) {
                for (int axis = 0; axis < 3; axis++) {
                    assertTrue(box[axis] >= 0 && box[axis] < box[axis + 3]
                                    && box[axis + 3] <= 16,
                            machine.model() + ": Kasten außerhalb von 0 bis 16");
                }
            }
        }
    }

    @Test
    @DisplayName("The ram of the press travels exactly down onto the anvil")
    void theRamMeetsTheAnvil() throws IOException {
        // The ram stands in its own model because it moves. How far it travels
        // is computed by MachineLayouts, and the PressRenderer takes the number
        // from there. If one of the two runs off, it strikes either into the
        // air or through the anvil.
        String json = model("press_ram");
        assertTrue(json.contains("\"elements\":["), "press_ram.json hat keine Kästen");

        // The numbers come from MachineLayouts: lid one block pixel below the
        // top edge, beneath it the cover plate, beneath that the ram hangs.
        // The anvil stands on the base.
        int rest = 16 - 1 - 2 - 4;      // Rest position: below the ceiling of the maw
        int anvil = 2 + 2;              // Top edge of the anvil
        assertEquals(rest - anvil, MachineLayouts.pressStroke(),
                "der Weg des Stempels passt nicht zu Decke und Amboss");
        assertTrue(json.contains("\"from\":[4," + rest + ",2]"),
                "der Stempel hängt nicht unter der Decke");

        String body = model("press");
        assertTrue(body.contains("\"to\":[12," + anvil + ",12]"),
                "der Amboss steht nicht dort, wo der Stempel ihn trifft");
        assertFalse(body.contains("\"from\":[4," + rest + ",2]"),
                "der Stempel steht auch im Gehäusemodell — dann steht er still"
                        + " und bewegt sich gleichzeitig");
    }

    @Test
    @DisplayName("Every reworked block has more than one box")
    void everyMachineHasShape() {
        // A layout with a single box would be a cube by a detour.
        for (Machine machine : MACHINES) {
            assertTrue(machine.boxes().get().size() > 1,
                    machine.model() + " hat nur einen Kasten");
        }
    }
}
