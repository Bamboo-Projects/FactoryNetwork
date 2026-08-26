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
 * Was am Netz selbst abzulesen ist.
 *
 * <p>`strom.md` §8 nennt `network.power`, und bis heute gab es das nicht:
 * `network` war ein Ziel für Strom und sonst nichts. Wer einen Worker
 * anhalten wollte, solange der Vorrat knapp ist, hatte keine Zahl dafür — der
 * Stand stand nur im Netz-Reiter, und den liest kein Programm.
 *
 * <p><b>Ohne Klammern</b>, anders als {@code crusher_1.energy()}. Der
 * Unterschied ist nicht Geschmack: {@code energy()} ist ein Blick in eine
 * fremde Maschine, der eine Abfrage kostet. Der eigene Vorrat liegt im
 * Controller und ist keine Nachfrage in der Welt — dieselbe Lage wie bei
 * {@code online}.
 */
class NetworkMemberTest {

    /** Eine Welt aus Papier mit einem Stromvorrat. */
    private static final class TestHost implements Interpreter.Host {

        final List<String> logs = new ArrayList<>();

        long stored = 12_000;
        long capacity = 40_000;

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
        public long networkPower() {
            return stored;
        }

        @Override
        public long networkCapacity() {
            return capacity;
        }
    }

    private static Interpreter interpreterFor(String source, TestHost host) {
        Parser.ParseResult result = Parser.parse(source);
        assertTrue(result.diagnostics().stream().noneMatch(d -> d.isError()),
                () -> "Übersetzungsfehler: " + result.diagnostics());
        return new Interpreter(result.program(), host);
    }

    @Test
    @DisplayName("network.power ist der Vorrat des Netzes")
    void networkPowerIsTheStoredEnergy() {
        TestHost host = new TestHost();
        host.stored = 12_000;
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(network.power)
                }""", host);

        interpreter.call("zeigen", List.of());

        assertEquals(List.of("12000"), host.logs);
    }

    @Test
    @DisplayName("network.capacity ist, was hineinpasst")
    void networkCapacityIsWhatFits() {
        TestHost host = new TestHost();
        host.capacity = 40_000;
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(network.capacity)
                }""", host);

        interpreter.call("zeigen", List.of());

        assertEquals(List.of("40000"), host.logs);
    }

    @Test
    @DisplayName("Beides zusammen ergibt einen Anteil")
    void bothTogetherMakeAshare() {
        // Der Grund, warum capacity mitkommt: Eine Zahl ohne Bezugsgröße
        // lässt sich nicht anzeigen, und progress() will einen Anteil.
        TestHost host = new TestHost();
        host.stored = 10_000;
        host.capacity = 40_000;
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(network.power * 100 / network.capacity)
                }""", host);

        interpreter.call("zeigen", List.of());

        assertEquals(List.of("25"), host.logs);
    }

    @Test
    @DisplayName("Der Anteil für einen Balken braucht eine Kommazahl")
    void theShareForAbarNeedsAdecimal() {
        // Das Handbuch zeigt „network.power * 1.0 / network.capacity", und
        // das * 1.0 ist keine Zierde: Ohne es teilt eine ganze Zahl durch
        // eine ganze, und der Balken stünde immer auf null.
        TestHost host = new TestHost();
        host.stored = 10_000;
        host.capacity = 40_000;
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(network.power / network.capacity)
                    log(network.power * 1.0 / network.capacity)
                }""", host);

        interpreter.call("zeigen", List.of());

        assertEquals("0", host.logs.get(0), "ganz geteilt bleibt nichts übrig");
        assertTrue(host.logs.get(1).startsWith("0.25"),
                () -> "mit Komma wird es ein Anteil: " + host.logs.get(1));
    }

    @Test
    @DisplayName("Ein anderes Mitglied am Netz sagt, was es gibt")
    void anotherMemberSaysWhatExists() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                fn zeigen() {
                    log(network.strom)
                }""", host);

        var error = assertThrows(ScriptError.class,
                () -> interpreter.call("zeigen", List.of()));
        assertTrue(error.getMessage().contains("power")
                        || String.valueOf(error.hint()).contains("power"),
                () -> "Die Meldung muss die bekannten nennen: " + error.getMessage()
                        + " / " + error.hint());
    }

    @Test
    @DisplayName("Ohne Welt wird der Vorrat nicht erfunden")
    void withoutAworldTheReserveIsNotInvented() {
        // Dieselbe Haltung wie bei countIn: Ein Host ohne Welt kann nicht
        // nachsehen, und eine erfundene Null wäre eine Antwort auf eine Frage,
        // die er nicht beantworten kann.
        Interpreter.Host bare = new Interpreter.Host() {
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
            }

            @Override
            public boolean hasDevice(String name) {
                return true;
            }

            @Override
            public String suggestDevice(String name) {
                return null;
            }
        };
        Parser.ParseResult result = Parser.parse("""
                fn zeigen() {
                    log(network.power)
                }""");
        Interpreter interpreter = new Interpreter(result.program(), bare);

        assertThrows(ScriptError.class, () -> interpreter.call("zeigen", List.of()));
    }
}
