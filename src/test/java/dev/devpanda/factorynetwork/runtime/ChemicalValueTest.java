package dev.devpanda.factorynetwork.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.devpanda.factorynetwork.lang.parse.Parser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A chemical as a value.
 *
 * <p>It was missing from the value model even though `chemical:` could long
 * since be moved and counted: an entry from an inventory listing could say
 * how much it is, but not what — {@code it.item} and {@code it.fluid} existed,
 * {@code it.chemical} did not. This is the part of 1.8 whose precondition has
 * been met ever since Mekanism was hooked up.
 *
 * <p><b>The core speaks in identifiers.</b> A chemical stands here as text,
 * just as everywhere outside of {@code compat/mekanism}: a signature with a
 * Mekanism type would resolve the class on load, and without the mod
 * everything that so much as touches it would collapse.
 *
 * <p>What is tested here is therefore the <b>mechanics over text</b> — the
 * resolution against the registry needs the running game and lives in the
 * game tests.
 */
class ChemicalValueTest {

    /** A paper world with an inventory it makes up. */
    private static final class TestHost implements Interpreter.Host {

        final List<String> logs = new ArrayList<>();

        /** What an inventory listing should return. */
        final List<Value> stored = new ArrayList<>();

        @Override
        public long move(Value amount, Value from, Value to) {
            return 0;
        }

        @Override
        public long count(Value what) {
            return 0;
        }

        @Override
        public int redstone(String device) {
            return 0;
        }

        @Override
        public void setRedstone(String device, int strength) {
        }

        @Override
        public void log(String message) {
            logs.add(message);
        }

        @Override
        public boolean hasDevice(String name) {
            return true;
        }

        @Override
        public String suggestDevice(String name) {
            return null;
        }

        @Override
        public List<Value> storedItems() {
            return stored;
        }
    }

    private static Interpreter interpreterFor(String source, TestHost host) {
        Parser.ParseResult result = Parser.parse(source);
        assertTrue(result.diagnostics().stream().noneMatch(d -> d.isError()),
                () -> "Übersetzungsfehler: " + result.diagnostics());
        return new Interpreter(result.program(), host);
    }

    @Test
    @DisplayName("it.chemical names the kind of an entry")
    void itChemicalNamesTheKind() {
        TestHost host = new TestHost();
        host.stored.add(Value.Selection.ofChemicals(List.of("mekanism:hydrogen"), 500));
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    for posten in storage.items() {
                        log(posten.amount)
                        log(posten.chemical)
                    }
                }""", host);

        interpreter.call("zeigen", List.of());

        assertEquals(2, host.logs.size(), () -> host.logs.toString());
        assertEquals("500", host.logs.get(0));
        assertTrue(host.logs.get(1).contains("hydrogen"),
                () -> "die Sorte muss ablesbar sein: " + host.logs.get(1));
    }

    @Test
    @DisplayName("An entry over several kinds has no single one")
    void apostOverSeveralKindsHasNosingleOne() {
        // The same rule as for it.item: to pick the first would be guessing.
        TestHost host = new TestHost();
        host.stored.add(Value.Selection.ofChemicals(
                List.of("mekanism:hydrogen", "mekanism:oxygen"), 500));
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    for posten in storage.items() {
                        log(posten.chemical)
                    }
                }""", host);

        assertThrows(ScriptError.class, () -> interpreter.call("zeigen", List.of()));
    }

    @Test
    @DisplayName("A chemical survives the saving of a flow")
    void achemicalSurvivesSavingAflow() {
        // A flow waiting for an event gets saved. Without an entry in the
        // codec a waiting flow would silently lose its value.
        Value single = Value.Resource.ofChemical("mekanism:hydrogen");
        Value selection = Value.Selection.ofChemicals(
                List.of("mekanism:hydrogen", "mekanism:oxygen"), 500);

        assertEquals(single, dev.devpanda.factorynetwork.runtime.flow.ValueCodec.read(
                dev.devpanda.factorynetwork.runtime.flow.ValueCodec.write(single)));
        assertEquals(selection, dev.devpanda.factorynetwork.runtime.flow.ValueCodec.read(
                dev.devpanda.factorynetwork.runtime.flow.ValueCodec.write(selection)));
    }

    @Test
    @DisplayName("Without Mekanism the identifier stands, not an invented name")
    void withoutMekanismTheIdIsShown() {
        // describe() goes through Chemicals.nameOf, and without the mod that
        // returns the identifier. Nothing is invented.
        assertEquals("mekanism:hydrogen",
                Value.Resource.ofChemical("mekanism:hydrogen").describe());
    }
}
