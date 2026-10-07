package dev.devpanda.factorynetwork.client;

import com.google.gson.JsonObject;
import dev.devpanda.factorynetwork.lang.Project;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EditorProtocolTest {
    private static JsonObject files(Map<String, String> files) {
        JsonObject json = new JsonObject();
        files.forEach(json::addProperty);
        return json;
    }

    @Test void preservesNestedPathsAndUnicodeText() {
        String path = "a".repeat(30) + "/" + "b".repeat(30) + "/worker.mf";
        String source = "fn grüße() { log(\"Bambus 🌿\") }\n";
        Project next = EditorProtocol.readProject(files(Map.of(path, source)), Project.of(""), Map.of());
        assertEquals(source, next.source(path));
        assertTrue(path.length() > 64);
    }

    @Test void rejectsInvalidNamesInsteadOfSilentlyDroppingFiles() {
        for (String name : new String[]{"../main.mf", "C:\\main.mf", "main.js", "UPPER.mf"}) {
            assertThrows(IllegalArgumentException.class, () -> EditorProtocol.readProject(
                    files(Map.of(name, "valuable work")), Project.of(""), Map.of()));
        }
    }

    @Test void lockedFilesCannotBeChangedDeletedOrRenamed() {
        Project current = new Project(Map.of("main.mf", "", "shared.mf", "original"));
        Map<String, String> locks = Map.of("shared.mf", "Alex");
        assertThrows(IllegalArgumentException.class, () -> EditorProtocol.readProject(
                files(Map.of("main.mf", "", "shared.mf", "overwritten")), current, locks));
        assertThrows(IllegalArgumentException.class, () -> EditorProtocol.readProject(
                files(Map.of("main.mf", "")), current, locks));
        assertThrows(IllegalArgumentException.class, () -> EditorProtocol.readProject(
                files(Map.of("main.mf", "", "renamed.mf", "original")), current, locks));
        assertEquals("new text", EditorProtocol.readProject(files(Map.of(
                "main.mf", "new text", "shared.mf", "original")), current, locks).source("main.mf"));
    }

    @Test void limitsFileAndTotalWireSize() {
        assertThrows(IllegalArgumentException.class, () -> EditorProtocol.readProject(
                files(Map.of("main.mf", "x".repeat(EditorProtocol.MAX_FILE + 1))), Project.of(""), Map.of()));
        String largeUnicode = "界".repeat(50_000);
        assertThrows(IllegalArgumentException.class, () -> EditorProtocol.readProject(
                files(Map.of("a.mf", largeUnicode, "b.mf", largeUnicode)), Project.of(""), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> EditorProtocol.readProject(
                new JsonObject(), Project.of(""), Map.of()));
    }
}
