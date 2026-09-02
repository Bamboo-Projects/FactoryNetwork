package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.parse.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading and writing global values.
 *
 * <p><b>They do not lie in the stack of scopes.</b> That stack lives for one
 * call; a global value lives for the factory. It therefore lies where
 * inventories and redstone lie too — with the host that the runtime brings
 * along.
 */
class GlobalRuntimeTest {

    /** A paper world with a chest full of global values. */
    private static final class TestHost implements Interpreter.Host {

        final List<String> logs = new ArrayList<>();
        final Map<String, Value> globals = new HashMap<>();

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
            return false;
        }

        @Override
        public String suggestDevice(String name) {
            return null;
        }

        @Override
        public Value global(String name) {
            return globals.get(name);
        }

        @Override
        public void setGlobal(String name, Value value) {
            globals.put(name, value);
        }
    }

    /**
     * Builds a runtime and fills the global values as the server would when
     * taking over a program.
     */
    private static Interpreter interpreterFor(String source, TestHost host) {
        Parser.ParseResult result = Parser.parse(source);
        assertTrue(result.diagnostics().stream().noneMatch(d -> d.isError()),
                () -> "Übersetzungsfehler: " + result.diagnostics());
        for (var declaration : result.program().declarations()) {
            if (declaration instanceof dev.devpanda.factorynetwork.lang.ast.Decl.Global global) {
                host.globals.put(global.name(), Value.ofLiteral(global.value()));
            }
        }
        return new Interpreter(result.program(), host);
    }


    @Test
    @DisplayName("A global value can be read")
    void aGlobalCanBeRead() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                global modus = "tag"

                fn zeigen() {
                    log(modus)
                }""", host);

        interpreter.call("zeigen", List.of());

        assertEquals(List.of("tag"), host.logs);
    }

    @Test
    @DisplayName("What one function writes, the next one sees")
    void whatOneFunctionWritesTheNextOneSees() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                global modus = "tag"

                fn schalten() {
                    modus = "nacht"
                }

                fn zeigen() {
                    log(modus)
                }""", host);

        interpreter.call("schalten", List.of());
        interpreter.call("zeigen", List.of());

        assertEquals(List.of("nacht"), host.logs,
                "der Wert überdauert den Aufruf, in dem er gesetzt wurde");
    }

    @Test
    @DisplayName("A name that no global line declares stays an error")
    void anUndeclaredNameIsStillAnError() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                fn schalten() {
                    gibtesnicht = 3
                }""", host);

        assertThrows(ScriptError.class,
                () -> interpreter.call("schalten", List.of()),
                "ohne Erklärung ist eine Zuweisung ein Vertipper");
    }

    @Test
    @DisplayName("A let with the same name shadows the global value")
    void aLocalLetShadowsTheGlobal() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                global modus = "tag"

                fn schalten() {
                    let modus = "eigen"
                    modus = "geändert"
                    log(modus)
                }""", host);

        interpreter.call("schalten", List.of());

        assertEquals(List.of("geändert"), host.logs);
        assertEquals("tag", host.globals.get("modus").describe(),
                "der globale Wert darf davon nichts mitbekommen haben");
    }

    @Test
    @DisplayName("A number can be counted up")
    void aNumberCanBeCountedUp() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                global zaehler = 0

                fn zaehlen() {
                    zaehler = zaehler + 1
                }""", host);

        interpreter.call("zaehlen", List.of());
        interpreter.call("zaehlen", List.of());
        interpreter.call("zaehlen", List.of());

        assertEquals("3", host.globals.get("zaehler").describe());
    }

    // ---- Lists as global values --------------------------------------------

    @Test
    @DisplayName("A global list starts empty")
    void aglobalListStartsEmpty() {
        TestHost host = new TestHost();
        interpreterFor("""
                global warteschlange = []

                fn nichts() {
                }""", host);

        assertEquals("[]", host.globals.get("warteschlange").describe(),
                "der Anfangswert muss eine leere Liste sein, nicht nichts");
    }

    @Test
    @DisplayName("Appending goes through an assignment")
    void appendingGoesThroughAnassignment() {
        // The decision in one line: `liste = liste.plus(x)` and no mutating
        // `add`. That way every change goes through the same path as for a
        // number — and thereby through the guard for const and the protection
        // in multiplayer.
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                global warteschlange = []

                fn anhaengen(was: Text) {
                    warteschlange = warteschlange.plus(was)
                }""", host);

        interpreter.call("anhaengen", List.of(new Value.Text("eisen")));
        interpreter.call("anhaengen", List.of(new Value.Text("gold")));

        assertEquals("[eisen, gold]", host.globals.get("warteschlange").describe());
    }

    @Test
    @DisplayName("A list that grows too big halts the program")
    void alistThatGrowsTooBigStopsTheProgram() {
        // Without a cap a global list value would be the only way to occupy
        // unbounded memory with a loop that survives a restart.
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                global warteschlange = []

                fn fuellen() {
                    let i = 0
                    while i < 100000 {
                        warteschlange = warteschlange.plus(i)
                        i = i + 1
                    }
                }""", host);

        ScriptError error = assertThrows(ScriptError.class,
                () -> interpreter.call("fuellen", List.of()));

        assertTrue(error.getMessage().contains("zu lang"), error.getMessage());
    }
}
