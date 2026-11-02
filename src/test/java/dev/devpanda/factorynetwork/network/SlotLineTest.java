package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.lang.Diagnostic;
import dev.devpanda.factorynetwork.lang.Project;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The line a Ctrl+click on a slot copies: it has to compile once pasted,
 * otherwise copying it saves no one any typing.
 */
class SlotLineTest {

    /** Pasted into a function, the way a move stands in the examples. */
    private static void assertCompiles(String line) {
        String program = "fn copied() {\n    " + line + "\n}\n";
        List<Diagnostic> problems = new Project(Map.of("main.mf", program)).parse().diagnostics();
        assertTrue(problems.isEmpty(), () -> line + "\n" + problems);
    }

    @Test
    @DisplayName("A vanilla item is written without its namespace")
    void vanillaItem() {
        String line = SlotLine.of("ofen_1", "minecraft:iron_ore", 0);
        assertEquals("move 64 item:iron_ore from ofen_1.slots(0) to storage", line);
        assertCompiles(line);
    }

    @Test
    @DisplayName("A modded item is written with a slash")
    void moddedItem() {
        String line = SlotLine.of("brecher", "mekanism:dust_iron", 3);
        assertEquals("move 64 item:mekanism/dust_iron from brecher.slots(3) to storage", line);
        assertCompiles(line);
    }

    @Test
    @DisplayName("An empty slot moves whatever lies there")
    void emptySlot() {
        String line = SlotLine.of("ofen_1", null, 2);
        assertEquals("move 64 item:* from ofen_1.slots(2) to storage", line);
        assertCompiles(line);
    }

    @Test
    @DisplayName("A keyword label goes into backticks")
    void keywordLabel() {
        assertEquals("`for`", SlotLine.deviceName("for"));
        assertCompiles(SlotLine.of(SlotLine.deviceName("for"), "minecraft:coal", 1));
    }

    @Test
    @DisplayName("Umlauts need no backticks")
    void umlautLabel() {
        assertEquals("ofen_süd", SlotLine.deviceName("ofen_süd"));
        assertCompiles(SlotLine.of(SlotLine.deviceName("ofen_süd"), "minecraft:coal", 1));
    }

    @Test
    @DisplayName("An assembly label is written as its role")
    void assemblyLabel() {
        assertEquals("eingang", SlotLine.deviceName("werk_1/eingang"));
    }

    @Test
    @DisplayName("A label with a space goes into backticks")
    void labelWithSpace() {
        assertEquals("`ofen links`", SlotLine.deviceName("ofen links"));
        assertCompiles(SlotLine.of(SlotLine.deviceName("ofen links"), "minecraft:coal", 1));
    }
}
