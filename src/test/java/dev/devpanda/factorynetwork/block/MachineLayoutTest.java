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
 * Hält Modell und Trefferfläche jedes Blocks zusammen, der kein Würfel mehr
 * ist.
 *
 * <p>Minecraft führt beides getrennt: Das Modell steht in JSON, die
 * Trefferfläche in Java. Laufen sie auseinander, greift der Spieler neben
 * das, was er sieht — ein Fehler, den man im Spiel spürt, aber schwer
 * benennen kann.
 *
 * <p><b>Eine Klasse für alle</b>, weil die Proben für jeden Block dieselben
 * sind. Was an einem Block eigen ist, steht in seiner eigenen Testklasse:
 * {@link GatewayLayoutTest} und {@link DriveLayoutTest}. Wer den nächsten
 * Block umbaut, trägt ihn unten in die Liste ein und hat die vier
 * Grundproben geschenkt.
 */
class MachineLayoutTest {

    /** Ein umgebauter Block: Name der Modelldatei, Kästen, alter Vorfahre. */
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
    @DisplayName("Jeder Kasten der Trefferfläche steht so auch im Modell")
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
    @DisplayName("Und kein Modell hat einen Kasten darüber hinaus")
    void noModelHasAnExtraBox() throws IOException {
        for (Machine machine : MACHINES) {
            String json = model(machine.model());
            int found = json.split("\"from\":", -1).length - 1;
            assertEquals(machine.boxes().get().size(), found,
                    machine.model() + ": Modell und Layout zählen verschieden viele Kästen");
        }
    }

    @Test
    @DisplayName("Kein Modell erbt mehr einen Würfel")
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
    @DisplayName("Kein Kasten ragt aus seinem Block heraus")
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
    @DisplayName("Der Stempel der Presse fährt genau bis auf den Amboss")
    void theRamMeetsTheAnvil() throws IOException {
        // Der Stempel steht in einem eigenen Modell, weil er sich bewegt.
        // Wie weit er fährt, rechnet MachineLayouts, und der PressRenderer
        // nimmt die Zahl von dort. Läuft eines der beiden davon weg, schlägt
        // er entweder in die Luft oder durch den Amboss hindurch.
        String json = model("press_ram");
        assertTrue(json.contains("\"elements\":["), "press_ram.json hat keine Kästen");

        // Die Zahlen stammen aus MachineLayouts: Deckel einen Blockpixel
        // unter der Oberkante, darunter die Deckplatte, darunter hängt der
        // Stempel. Der Amboss steht auf dem Sockel.
        int rest = 16 - 1 - 2 - 4;      // Ruhelage: unter der Decke des Maules
        int anvil = 2 + 2;              // Oberkante des Ambosses
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
    @DisplayName("Jeder umgebaute Block hat mehr als einen Kasten")
    void everyMachineHasShape() {
        // Ein Layout mit einem einzigen Kasten wäre ein Würfel mit Umweg.
        for (Machine machine : MACHINES) {
            assertTrue(machine.boxes().get().size() > 1,
                    machine.model() + " hat nur einen Kasten");
        }
    }
}
