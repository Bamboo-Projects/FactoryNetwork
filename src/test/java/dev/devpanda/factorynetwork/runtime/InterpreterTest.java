package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.parse.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterpreterTest {

    /** A paper world: remembers what the program has done. */
    private static final class TestHost implements Interpreter.Host {

        final List<String> logs = new ArrayList<>();
        final List<String> moves = new ArrayList<>();
        final Map<String, Integer> redstone = new HashMap<>();
        final Map<String, Long> counts = new HashMap<>();
        final List<String> devices = new ArrayList<>(List.of("chest", "crusher_1", "sensor"));

        @Override
        public long move(Value amount, Value from, Value to) {
            moves.add((from == null ? "?" : from.describe()) + " -> " + to.describe()
                    + " (" + amount.describe() + ")");
            return 1;
        }

        @Override
        public long count(Value what) {
            return counts.getOrDefault(what.describe(), 0L);
        }

        @Override
        public int redstone(String device) {
            return redstone.getOrDefault(device, 0);
        }

        @Override
        public void setRedstone(String device, int strength) {
            redstone.put(device, strength);
        }

        @Override
        public void log(String message) {
            logs.add(message);
        }

        @Override
        public boolean hasDevice(String name) {
            return devices.contains(name);
        }

        /** The members per group. */
        final List<String> groupMembers = new ArrayList<>(List.of("chest", "crusher_1"));

        @Override
        public List<String> membersOf(String group) {
            return groupMembers;
        }

        @Override
        public String suggestDevice(String name) {
            return devices.stream()
                    .filter(candidate -> dev.devpanda.factorynetwork.util.NameDistance
                            .isCloseEnough(name,
                                    dev.devpanda.factorynetwork.util.NameDistance
                                            .between(name, candidate)))
                    .findFirst().orElse(null);
        }
    }

    private static Interpreter interpreterFor(String source, TestHost host) {
        Parser.ParseResult result = Parser.parse(source);
        assertTrue(result.diagnostics().stream().noneMatch(d -> d.isError()),
                () -> "Übersetzungsfehler: " + result.diagnostics());
        return new Interpreter(result.program(), host);
    }

    @Nested
    @DisplayName("Arithmetic and control flow")
    class Basics {

        @Test
        void returnsAValue() {
            TestHost host = new TestHost();
            Interpreter interpreter = interpreterFor("""
                    fn verdopple(n: Int) {
                        return n * 2
                    }""", host);
            Value result = interpreter.call("verdopple", List.of(new Value.Int(21)));
            assertEquals(42L, ((Value.Int) result).value());
        }

        @Test
        void branchesTakeTheRightPath() {
            TestHost host = new TestHost();
            Interpreter interpreter = interpreterFor("""
                    fn test(n: Int) {
                        if n > 10 {
                            return "viel"
                        } else {
                            return "wenig"
                        }
                    }""", host);
            assertEquals("viel",
                    ((Value.Text) interpreter.call("test", List.of(new Value.Int(11)))).value());
            assertEquals("wenig",
                    ((Value.Text) interpreter.call("test", List.of(new Value.Int(2)))).value());
        }

        @Test
        void whileLoopCounts() {
            TestHost host = new TestHost();
            Interpreter interpreter = interpreterFor("""
                    fn zähle() {
                        let summe = 0
                        let i = 0
                        while i < 5 {
                            summe = summe + i
                            i = i + 1
                        }
                        return summe
                    }""", host);
            assertEquals(10L, ((Value.Int) interpreter.call("zähle", List.of())).value());
        }

        @Test
        @DisplayName("break leaves the loop")
        void breakLeavesTheLoop() {
            TestHost host = new TestHost();
            Interpreter interpreter = interpreterFor("""
                    fn test() {
                        let i = 0
                        while true {
                            i = i + 1
                            if i >= 3 {
                                break
                            }
                        }
                        return i
                    }""", host);
            assertEquals(3L, ((Value.Int) interpreter.call("test", List.of())).value());
        }

        @Test
        @DisplayName("An endless loop is halted, not the server")
        void endlessLoopIsStopped() {
            TestHost host = new TestHost();
            Interpreter interpreter = interpreterFor("""
                    fn test() {
                        let x = 0
                        while true {
                            x = x + 1
                        }
                    }""", host);
            ScriptError error = assertThrows(ScriptError.class,
                    () -> interpreter.call("test", List.of()));
            assertTrue(error.getMessage().contains("zu lange"), error::getMessage);
            assertTrue(error.hint().contains("Worker"), error::hint);
        }

        @Test
        void divisionByZeroIsReported() {
            TestHost host = new TestHost();
            Interpreter interpreter = interpreterFor("""
                    fn test() {
                        return 1 / 0
                    }""", host);
            ScriptError error = assertThrows(ScriptError.class,
                    () -> interpreter.call("test", List.of()));
            assertTrue(error.getMessage().contains("null"));
        }
    }

    @Nested
    @DisplayName("Factory")
    class Factory {

        @Test
        void moveReachesTheHost() {
            TestHost host = new TestHost();
            Interpreter interpreter = interpreterFor("""
                    fn test() {
                        move 64 item:iron_ore from chest to crusher_1
                    }""", host);
            interpreter.call("test", List.of());
            assertEquals(1, host.moves.size());
            assertTrue(host.moves.get(0).contains("chest"), host.moves::toString);
            assertTrue(host.moves.get(0).contains("crusher_1"), host.moves::toString);
        }

        @Test
        void redstoneIsReadAndWritten() {
            TestHost host = new TestHost();
            host.redstone.put("sensor", 12);
            Interpreter interpreter = interpreterFor("""
                    fn test() {
                        let stärke = sensor.redstone()
                        crusher_1.redstone(stärke)
                        return stärke
                    }""", host);
            assertEquals(12L, ((Value.Int) interpreter.call("test", List.of())).value());
            assertEquals(12, host.redstone.get("crusher_1"));
        }

        @Test
        @DisplayName("An event block runs when the event arrives")
        void handlerRunsOnEvent() {
            TestHost host = new TestHost();
            Interpreter interpreter = interpreterFor("""
                    on redstone_changed(sensor, strength) {
                        if strength >= 12 {
                            log("stark")
                        } else {
                            log("schwach")
                        }
                    }""", host);
            interpreter.fire("redstone_changed",
                    List.of(new Value.Device("sensor"), new Value.Int(15)));
            assertEquals(List.of("stark"), host.logs);
        }

        @Test
        @DisplayName("Several blocks for the same event all run")
        void allHandlersRun() {
            TestHost host = new TestHost();
            Interpreter interpreter = interpreterFor("""
                    on device_online(device) {
                        log("eins")
                    }

                    on device_online(device) {
                        log("zwei")
                    }""", host);
            interpreter.fire("device_online", List.of(new Value.Device("chest")));
            assertEquals(2, host.logs.size());
        }

        @Test
        @DisplayName("A typo in the device name suggests the right one")
        void unknownDeviceSuggests() {
            TestHost host = new TestHost();
            Interpreter interpreter = interpreterFor("""
                    fn test() {
                        return cruhser_1.online
                    }""", host);
            ScriptError error = assertThrows(ScriptError.class,
                    () -> interpreter.call("test", List.of()));
            assertTrue(error.getMessage().contains("cruhser_1"), error::getMessage);
            assertTrue(error.hint().contains("crusher_1"), error::hint);
        }

        @Test
        @DisplayName("await says where it belongs when it stands in the wrong place")
        void awaitPointsToWhereItBelongs() {
            TestHost host = new TestHost();
            Interpreter interpreter = interpreterFor("""
                    fn test() {
                        let r = await BatchFinished timeout 30s else {
                            return 0
                        }
                        return 1
                    }""", host);
            ScriptError error = assertThrows(ScriptError.class,
                    () -> interpreter.call("test", List.of()));
            // The ordinary path cannot wait; that is no gap, but the division
            // of labour. The message must point the way.
            assertTrue(error.hint().contains("Abläufen"), error::hint);
        }
    }

    @Test
    @DisplayName("move returns how much was moved")
    void moveReportsWhatItMoved() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                fn holen() {
                    let bewegt = move 64 item:iron_ore from chest to crusher_1
                    log(bewegt)
                }""", host);

        interpreter.call("holen", List.of());

        // The test host always reports one movement; what matters is that it
        // arrives instead of being discarded.
        assertEquals(List.of("1"), host.logs);
        assertEquals(1, host.moves.size(), () -> host.moves.toString());
    }

    @Test
    @DisplayName("move also stands in a condition")
    void moveWorksInACondition() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                fn holen() {
                    if move 64 item:iron_ore from chest to crusher_1 > 0 {
                        log("etwas kam an")
                    }
                }""", host);

        interpreter.call("holen", List.of());

        assertEquals(List.of("etwas kam an"), host.logs);
    }

    @Test
    @DisplayName("move as a statement stays a statement")
    void moveAsAStatementStillWorks() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                fn holen() {
                    move 64 item:iron_ore from chest to crusher_1
                }""", host);

        interpreter.call("holen", List.of());

        assertEquals(1, host.moves.size(), () -> host.moves.toString());
        assertTrue(host.logs.isEmpty(), () -> host.logs.toString());
    }

    @Test
    @DisplayName("members() gives the devices of a group")
    void membersYieldsTheDevices() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                group brecher {
                    members chest, crusher_1
                }

                fn zeigen() {
                    for maschine in brecher.members() {
                        log(maschine.name)
                    }
                }""", host);

        interpreter.call("zeigen", List.of());

        assertEquals(List.of("chest", "crusher_1"), host.logs);
    }

    @Test
    @DisplayName("send() sends from storage to the group")
    void sendGoesFromStorageToTheGroup() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                group brecher {
                    members chest, crusher_1
                }

                fn schicken() {
                    brecher.send(64 item:iron_ore)
                }""", host);

        interpreter.call("schicken", List.of());

        assertEquals(1, host.moves.size(), () -> host.moves.toString());
        assertTrue(host.moves.get(0).startsWith("storage -> brecher"),
                () -> "aus dem Speicher an die Gruppe: " + host.moves);
    }

    @Test
    @DisplayName("A group can do nothing that a device can")
    void aGroupIsNoDevice() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                group brecher {
                    members chest, crusher_1
                }

                fn fragen() {
                    log(brecher.redstone())
                }""", host);

        ScriptError error = org.junit.jupiter.api.Assertions.assertThrows(
                ScriptError.class, () -> interpreter.call("fragen", List.of()));
        assertTrue(error.getMessage().contains("Gruppe"), error.getMessage());
    }

    @Test
    @DisplayName("min and max take the smaller and the larger number")
    void minAndMax() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                fn rechnen() {
                    log(min(64, 20))
                    log(max(64, 20))
                    log(min(3, 7, 5))
                }""", host);

        interpreter.call("rechnen", List.of());

        assertEquals(List.of("20", "64", "3"), host.logs);
    }

    @Test
    @DisplayName("Whole numbers stay whole")
    void wholeNumbersStayWhole() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                fn rechnen() {
                    log(min(64, 20))
                    log(min(64, 19.5))
                }""", host);

        interpreter.call("rechnen", List.of());

        // As soon as one side is fractional, it stays fractional — the same
        // rule as when calculating with + and *.
        assertEquals(List.of("20", "19.5"), host.logs);
    }

    @Test
    @DisplayName("abs, round, floor and ceil")
    void theOtherFour() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                fn rechnen() {
                    log(abs(0 - 7))
                    log(round(3.6))
                    log(floor(3.6))
                    log(ceil(3.2))
                }""", host);

        interpreter.call("rechnen", List.of());

        assertEquals(List.of("7", "4", "3", "4"), host.logs);
    }

    @Test
    @DisplayName("random stays between the bounds")
    void randomStaysInside() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                fn rechnen() {
                    let gezogen = random(5, 7)
                    if gezogen >= 5 && gezogen <= 7 {
                        log("drin")
                    }
                }""", host);

        for (int versuch = 0; versuch < 20; versuch++) {
            interpreter.call("rechnen", List.of());
        }

        assertEquals(20, host.logs.size(), () -> "jede Ziehung lag drin: " + host.logs);
    }

    @Test
    @DisplayName("A math function without a number complains")
    void aMathFunctionWithoutANumberComplains() {
        TestHost host = new TestHost();
        Interpreter interpreter = interpreterFor("""
                fn rechnen() {
                    log(min())
                }""", host);

        ScriptError error = org.junit.jupiter.api.Assertions.assertThrows(
                ScriptError.class, () -> interpreter.call("rechnen", List.of()));
        assertTrue(error.getMessage().contains("min"), error.getMessage());
    }
}
