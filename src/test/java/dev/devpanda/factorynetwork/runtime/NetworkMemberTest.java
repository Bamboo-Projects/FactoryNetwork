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
 * What can be read off the network itself.
 *
 * <p>`strom.md` §8 names `network.power`, and until today that did not exist:
 * `network` was a target for power and nothing else. Whoever wanted to halt a
 * worker while the reserve is low had no number for it — the level stood only
 * in the network tab, and no program reads that.
 *
 * <p><b>Without parentheses</b>, unlike {@code crusher_1.energy()}. The
 * difference is not taste: {@code energy()} is a look into a foreign machine
 * that costs a query. The own reserve lies in the controller and is no query
 * into the world — the same situation as with {@code online}.
 */
class NetworkMemberTest {

    /** A paper world with a power reserve. */
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
    @DisplayName("network.power is the reserve of the network")
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
    @DisplayName("network.capacity is what fits in")
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
    @DisplayName("Both together make a share")
    void bothTogetherMakeAshare() {
        // The reason capacity comes along: a number without a reference value
        // cannot be displayed, and progress() wants a share.
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
    @DisplayName("The share for a bar needs a decimal number")
    void theShareForAbarNeedsAdecimal() {
        // The manual shows „network.power * 1.0 / network.capacity", and
        // the * 1.0 is no ornament: without it a whole number divides by a
        // whole one, and the bar would always stand at zero.
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
    @DisplayName("Another member on the network says what exists")
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
    @DisplayName("Without a world the reserve is not invented")
    void withoutAworldTheReserveIsNotInvented() {
        // The same stance as with countIn: a host without a world cannot look
        // it up, and an invented zero would be an answer to a question it
        // cannot answer.
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
