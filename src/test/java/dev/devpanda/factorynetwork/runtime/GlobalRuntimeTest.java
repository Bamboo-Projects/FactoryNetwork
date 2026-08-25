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
 * Lesen und Schreiben globaler Werte.
 *
 * <p><b>Sie liegen nicht im Stapel der Geltungsbereiche.</b> Der lebt für
 * einen Aufruf; ein globaler Wert für die Fabrik. Er liegt deshalb dort, wo
 * auch Bestände und Redstone liegen — beim Host, den die Laufzeit mitbringt.
 */
class GlobalRuntimeTest {

    /** Eine Welt aus Papier mit einer Kiste voller globaler Werte. */
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
     * Baut eine Laufzeit und füllt die globalen Werte so, wie es der Server
     * beim Übernehmen eines Programms täte.
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
    @DisplayName("Ein globaler Wert lässt sich lesen")
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
    @DisplayName("Was eine Funktion schreibt, sieht die nächste")
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
    @DisplayName("Ein Name, den keine global-Zeile erklärt, bleibt ein Fehler")
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
    @DisplayName("Ein gleichnamiges let verdeckt den globalen Wert")
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
    @DisplayName("Eine Zahl lässt sich hochzählen")
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

    // ---- Listen als globale Werte ------------------------------------------

    @Test
    @DisplayName("Eine globale Liste fängt leer an")
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
    @DisplayName("Angehängt wird über eine Zuweisung")
    void appendingGoesThroughAnassignment() {
        // Die Entscheidung in einer Zeile: `liste = liste.plus(x)` und kein
        // änderndes `add`. Damit läuft jede Änderung über denselben Pfad wie
        // bei einer Zahl — und damit durch die Wache für const und den
        // Schutz im Mehrspielerbetrieb.
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
    @DisplayName("Eine Liste, die zu groß wird, hält das Programm an")
    void alistThatGrowsTooBigStopsTheProgram() {
        // Ohne Deckel wäre ein globaler Listenwert der einzige Weg, mit einer
        // Schleife unbegrenzt Speicher zu belegen, der den Neustart übersteht.
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
