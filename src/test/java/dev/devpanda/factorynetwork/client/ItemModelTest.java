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
 * Watches over the item models with their own boxes.
 *
 * <p><b>The trap this test sets.</b> Minecraft recognizes from the ancestry
 * {@code builtin/generated} that it should build the faces of an item itself
 * — from {@code layer0}. A model that brings its own boxes and still inherits
 * from {@code item/generated} has no faces at all afterwards: in the hand it
 * is invisible.
 *
 * <p>This would only show up in the game, and there only to whoever takes
 * this exact item in hand. That is why it stands here.
 */
class ItemModelTest {

    private static final Path MODELS = Path.of(
            "src/main/resources/assets/factorynetwork/models/item");

    /** All item models that bring their own boxes. */
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
    @DisplayName("No model with its own boxes inherits from item/generated")
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
    @DisplayName("And brings its own views itself")
    void ownBoxesMeanOwnDisplay() throws IOException {
        for (Path file : withElements()) {
            String json = read(file);
            // Whoever inherits from a block model gets them from there — from
            // its own as from minecraft:block/block. The connector does that:
            // it is held like a block, because it becomes one.
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
    @DisplayName("Every face of an item names its cutout")
    void everyFaceNamesItsUv() throws IOException {
        // Without an ancestor there is no default from which Minecraft could
        // derive the UV — it stands in the model or it is missing.
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
