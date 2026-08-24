package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.parse.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Was an einem Gerät steht — über {@code online} und {@code name} hinaus.
 *
 * <p>{@code sprache.md} §6 nennt vier Mitglieder. Zwei davon sind eindeutig
 * und stehen hier: {@code insert} legt etwas hinein, {@code items} sagt, was
 * drin ist. {@code output()} und {@code send()} brauchen erst eine
 * Entscheidung — siehe {@code offene-punkte.md}.
 */
class DeviceMemberTest {

    /** Eine Welt aus Papier, die sich merkt, was in die Geräte gewandert ist. */
    private static final class TestHost implements Interpreter.Host {

        final List<String> logs = new ArrayList<>();
        final List<String> inserts = new ArrayList<>();
        final List<String> devices = new ArrayList<>(List.of("crusher_1", "furnace_2"));

        /** Was in den Geräten liegt, für {@code items()}. */
        final List<Value> contents = new ArrayList<>();

        /** Wie viel der nächste Einfügeversuch schafft. */
        long accepts = 64;

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
            return devices.contains(name);
        }

        @Override
        public String suggestDevice(String name) {
            return null;
        }

        @Override
        public long insertInto(String device, Value selection) {
            inserts.add(device + " <- " + selection.describe());
            return accepts;
        }

        @Override
        public List<Value> itemsIn(String device) {
            return contents;
        }
    }

    private static Interpreter interpreterFor(String source, TestHost host) {
        Parser.ParseResult result = Parser.parse(source);
        assertTrue(result.diagnostics().stream().noneMatch(d -> d.isError()),
                () -> "Übersetzungsfehler: " + result.diagnostics());
        return new Interpreter(result.program(), host);
    }

    @Test
    @DisplayName("insert legt etwas ins Gerät und meldet, wie viel ankam")
    void insertPutsSomethingIn() {
        TestHost host = new TestHost();
        host.accepts = 40;
        Interpreter interpreter = interpreterFor("""
                fn füllen() {
                    let angekommen = crusher_1.insert(64 item:iron_ore)
                    log(angekommen)
                }""", host);

        interpreter.call("füllen", List.of());

        assertEquals(1, host.inserts.size(), () -> host.inserts.toString());
        assertTrue(host.inserts.get(0).startsWith("crusher_1 <- "),
                () -> host.inserts.toString());
        assertEquals(List.of("40"), host.logs,
                "weniger als gewünscht ist normal und muss ablesbar sein");
    }

    @Test
    @DisplayName("Was nicht hineinpasst, meldet insert als Null")
    void whatDoesNotFitIsReportedAsZero() {
        TestHost host = new TestHost();
        host.accepts = 0;
        Interpreter interpreter = interpreterFor("""
                fn füllen() {
                    log(crusher_1.insert(64 item:iron_ore))
                }""", host);

        interpreter.call("füllen", List.of());

        assertEquals(List.of("0"), host.logs,
                "eine volle Maschine ist kein Fehler");
    }

    @Test
    @DisplayName("items sagt, was im Gerät liegt")
    void itemsTellsWhatIsInside() {
        TestHost host = new TestHost();
        host.contents.add(new Value.Text("64 iron_ore"));
        host.contents.add(new Value.Text("3 coal"));
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(crusher_1.items())
                }""", host);

        interpreter.call("zeigen", List.of());

        assertEquals(List.of("2 Einträge"), host.logs);
    }

    @Test
    @DisplayName("Ein leeres Gerät liefert eine leere Liste, keinen Fehler")
    void anEmptyDeviceGivesAnEmptyList() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(crusher_1.items())
                }""", host);

        interpreter.call("zeigen", List.of());

        assertEquals(List.of("0 Einträge"), host.logs);
    }

    @Test
    @DisplayName("Ein Mitglied, das es nicht gibt, nennt die, die es gibt")
    void anUnknownMemberNamesTheKnownOnes() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                fn versuchen() {
                    crusher_1.gibtesnicht()
                }""", host);

        ScriptError error = assertThrows(ScriptError.class,
                () -> interpreter.call("versuchen", List.of()));

        assertTrue(error.getMessage().contains("gibtesnicht"), error.getMessage());
    }
}
