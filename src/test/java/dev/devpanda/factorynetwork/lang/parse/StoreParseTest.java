package dev.devpanda.factorynetwork.lang.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code store kiste_1 { … }} — a foreign inventory counts as network storage.
 *
 * <p>The storage bus as AE2 has it, only without a block of its own: the
 * connector is attached to the chest anyway, and the line says that its
 * contents belong to the network. The same stance as with
 * {@code recipe … at …} — what the network does is in the program and not in
 * a window that nobody can version. The reasoning is in
 * {@code speicherbus.md}.
 */
class StoreParseTest {

    @Test
    @DisplayName("A store without entries carries only its device name")
    void astoreWithoutEntriesCarriesItsDevice() {
        var result = Parser.parse("store kiste_1 { }");

        assertFalse(result.diagnostics().stream().anyMatch(d -> d.isError()),
                () -> "Übersetzungsfehler: " + result.diagnostics());
        Decl.Store store = assertInstanceOf(Decl.Store.class,
                result.program().declarations().get(0));
        assertEquals("kiste_1", store.device());
        assertEquals(0, store.priority(), "ohne Angabe steht er gleichauf mit den Zellen");
        assertEquals(null, store.filter(), "und nimmt alles");
    }

    @Test
    @DisplayName("With priority and filter")
    void withPriorityAndFilter() {
        var result = Parser.parse("""
                store kiste_1 {
                    priority 5
                    filter tag:c/ores
                }""");

        assertFalse(result.diagnostics().stream().anyMatch(d -> d.isError()),
                () -> "Übersetzungsfehler: " + result.diagnostics());
        Decl.Store store = assertInstanceOf(Decl.Store.class,
                result.program().declarations().get(0));
        assertEquals(5, store.priority());
        assertTrue(store.filter() != null, "der Filter muss dastehen");
    }

    @Test
    @DisplayName("A store without a device is an error")
    void astoreWithoutAdeviceIsAnerror() {
        // Unlike with a recipe there is nothing to guess here: without the
        // device nobody would know whose contents are meant.
        var result = Parser.parse("store { }");

        assertTrue(result.diagnostics().stream().anyMatch(d -> d.isError()),
                "das muss auffallen");
    }
}
