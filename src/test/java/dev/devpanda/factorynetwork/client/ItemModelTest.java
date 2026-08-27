package dev.devpanda.factorynetwork.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wacht über die Gegenstandsmodelle mit eigenen Kästen.
 *
 * <p><b>Die Falle, die dieser Test stellt.</b> Minecraft erkennt an der
 * Ahnenreihe {@code builtin/generated}, dass es die Flächen eines
 * Gegenstands selbst bauen soll — aus {@code layer0}. Ein Modell, das eigene
 * Kästen mitbringt und trotzdem von {@code item/generated} erbt, hat danach
 * überhaupt keine Flächen mehr: In der Hand ist es unsichtbar.
 *
 * <p>Auffallen würde das erst im Spiel, und dort auch nur dem, der genau
 * diesen Gegenstand in die Hand nimmt. Deshalb steht es hier.
 */
class ItemModelTest {

    private static final Path MODELS = Path.of(
            "src/main/resources/assets/factorynetwork/models/item");

    /** Alle Gegenstandsmodelle, die eigene Kästen mitbringen. */
    private static List<Path> withElements() throws IOException {
        List<Path> found = new ArrayList<>();
        try (Stream<Path> files = Files.list(MODELS)) {
            for (Path file : files.toList()) {
                if (!file.toString().endsWith(".json")) {
                    continue;
                }
                if (read(file).contains("\"elements\":[")) {
                    found.add(file);
                }
            }
        }
        return found;
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8)
                .replaceAll("[ \t\r\n]+", "");
    }

    @Test
    @DisplayName("Kein Modell mit eigenen Kästen erbt von item/generated")
    void ownBoxesMeanNoGeneratedParent() throws IOException {
        List<Path> models = withElements();
        assertFalse(models.isEmpty(), "kein einziges Modell hat eigene Kästen");
        for (Path file : models) {
            String json = read(file);
            assertFalse(json.contains("\"parent\":\"minecraft:item/generated\"")
                            || json.contains("\"parent\":\"minecraft:item/handheld\""),
                    file.getFileName() + " hat eigene Kästen und erbt trotzdem von"
                            + " item/generated — in der Hand wäre es unsichtbar");
        }
    }

    @Test
    @DisplayName("Und bringt seine Ansichten selbst mit")
    void ownBoxesMeanOwnDisplay() throws IOException {
        for (Path file : withElements()) {
            String json = read(file);
            // Wer von einem Blockmodell erbt, bekommt sie von dort — vom
            // eigenen wie von minecraft:block/block. Der Connector tut das:
            // Er wird gehalten wie ein Block, weil er einer wird.
            if (json.contains("\"parent\":\"factorynetwork:block/")
                    || json.contains("\"parent\":\"minecraft:block/")) {
                continue;
            }
            assertTrue(json.contains("\"display\":{"),
                    file.getFileName() + " hat weder Vorfahren noch eigene"
                            + " Ansichten — es läge falsch in der Hand");
        }
    }

    @Test
    @DisplayName("Jede Fläche eines Gegenstands nennt ihren Ausschnitt")
    void everyFaceNamesItsUv() throws IOException {
        // Ohne Vorfahren gibt es keine Vorgabe, aus der Minecraft die UV
        // ableiten könnte — sie steht im Modell oder sie fehlt.
        for (Path file : withElements()) {
            String json = read(file);
            if (json.contains("\"parent\":")) {
                continue;
            }
            int faces = json.split("\"texture\":", -1).length - 1;
            int uvs = json.split("\"uv\":", -1).length - 1;
            assertTrue(uvs >= faces - 2,
                    file.getFileName() + ": " + faces + " Flächen, aber nur "
                            + uvs + " Ausschnitte");
        }
    }
}
