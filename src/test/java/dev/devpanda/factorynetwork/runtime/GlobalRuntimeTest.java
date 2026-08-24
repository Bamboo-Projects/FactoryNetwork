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
                host.globals.put(global.name(), literal(global.value()));
            }
        }
        return new Interpreter(result.program(), host);
    }

    /** Ein Literal aus dem Baum als Wert. */
    private static Value literal(dev.devpanda.factorynetwork.lang.ast.Expr expr) {
        return switch (expr) {
            case dev.devpanda.factorynetwork.lang.ast.Expr.IntLit number ->
                    new Value.Int(number.value());
            case dev.devpanda.factorynetwork.lang.ast.Expr.StringLit text ->
                    new Value.Text(text.value());
            case dev.devpanda.factorynetwork.lang.ast.Expr.BoolLit flag ->
                    new Value.Bool(flag.value());
            default -> Value.Nothing.get();
        };
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
}
