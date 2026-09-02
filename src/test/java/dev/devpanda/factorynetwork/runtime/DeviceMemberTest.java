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
 * What stands on a device — beyond {@code online} and {@code name}.
 *
 * <p>{@code sprache.md} §6 names four members. Two of them are unambiguous
 * and live here: {@code insert} puts something in, {@code items} says what is
 * inside. {@code output()} and {@code send()} still need a decision — see
 * {@code offene-punkte.md}.
 */
class DeviceMemberTest {

    /** A paper world that remembers what has wandered into the devices. */
    private static final class TestHost implements Interpreter.Host {

        final List<String> logs = new ArrayList<>();
        final List<String> inserts = new ArrayList<>();
        final List<String> devices = new ArrayList<>(List.of("crusher_1", "furnace_2"));

        /** What lies in the devices, for {@code items()}. */
        final List<Value> contents = new ArrayList<>();

        /** How much the next insertion attempt manages. */
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

        /** What was counted in the device — device and selection. */
        final List<String> counted = new ArrayList<>();

        /** What the look into the device finds. */
        long inDevice = 7;

        @Override
        public long countIn(String device, Value what) {
            counted.add(device + " ? " + what.describe());
            return inDevice;
        }
    }

    private static Interpreter interpreterFor(String source, TestHost host) {
        Parser.ParseResult result = Parser.parse(source);
        assertTrue(result.diagnostics().stream().noneMatch(d -> d.isError()),
                () -> "Übersetzungsfehler: " + result.diagnostics());
        return new Interpreter(result.program(), host);
    }

    @Test
    @DisplayName("insert puts something into the device and reports how much arrived")
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
    @DisplayName("What does not fit, insert reports as zero")
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
    @DisplayName("items says what lies in the device")
    void itemsTellsWhatIsInside() {
        TestHost host = new TestHost();
        host.contents.add(new Value.Text("64 iron_ore"));
        host.contents.add(new Value.Text("3 coal"));
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(crusher_1.items())
                }""", host);

        interpreter.call("zeigen", List.of());

        // The number alone helped no one: whoever writes a list to the log
        // or looks at a global list value in the network tab wants to know
        // what is in it.
        assertEquals(List.of("[64 iron_ore, 3 coal]"), host.logs);
    }

    @Test
    @DisplayName("An empty device returns an empty list, not an error")
    void anEmptyDeviceGivesAnEmptyList() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(crusher_1.items())
                }""", host);

        interpreter.call("zeigen", List.of());

        assertEquals(List.of("[]"), host.logs);
    }

    @Test
    @DisplayName("A long list is shortened, not cut off")
    void alongListIsShortenedNotCutOff() {
        // A list that ends silently reads like a complete one — the same
        // rule as for the enumeration on a display board.
        TestHost host = new TestHost();
        for (int i = 0; i < 9; i++) {
            host.contents.add(new Value.Int(i));
        }
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(crusher_1.items())
                }""", host);

        interpreter.call("zeigen", List.of());

        assertEquals(List.of("[0, 1, 2, 3, 4, 5, … +3]"), host.logs);
    }

    @Test
    @DisplayName("A member that does not exist names the ones that do")
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

    @Test
    @DisplayName("count on the device asks the device, not the storage")
    void countAsksTheDevice() {
        TestHost host = new TestHost();
        // The storage of this host counts zero. If the number came from
        // there, it would be zero here instead of seven.
        host.inDevice = 7;
        Interpreter interpreter = interpreterFor("""
                fn zaehlen() {
                    log(crusher_1.count(item:iron_ore))
                }""", host);

        interpreter.call("zaehlen", List.of());

        assertEquals(List.of("7"), host.logs);
        assertTrue(host.counted.stream().anyMatch(entry -> entry.startsWith("crusher_1 ?")),
                () -> "gefragt wurde: " + host.counted);
    }

    @Test
    @DisplayName("Without a selection it counts everything in the device")
    void countWithoutASelection() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                fn zaehlen() {
                    log(crusher_1.count())
                }""", host);

        interpreter.call("zaehlen", List.of());

        assertTrue(host.counted.contains("crusher_1 ? nichts"),
                () -> "ohne Auswahl muss die Frage trotzdem am Gerät ankommen: "
                        + host.counted);
    }

    @Test
    @DisplayName("count on the storage stays the storage")
    void countOnStorageIsUnchanged() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                fn zaehlen() {
                    log(storage.count(item:iron_ore))
                }""", host);

        interpreter.call("zaehlen", List.of());

        assertEquals(List.of("0"), host.logs);
        assertTrue(host.counted.isEmpty(),
                () -> "der Speicher darf dafür nicht ins Gerät sehen: " + host.counted);
    }
}
