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

    /** Eine Welt aus Papier: merkt sich, was das Programm getan hat. */
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
    @DisplayName("Rechnen und Ablauf")
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
                    fn zaehle() {
                        let summe = 0
                        let i = 0
                        while i < 5 {
                            summe = summe + i
                            i = i + 1
                        }
                        return summe
                    }""", host);
            assertEquals(10L, ((Value.Int) interpreter.call("zaehle", List.of())).value());
        }

        @Test
        @DisplayName("break verlässt die Schleife")
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
        @DisplayName("Eine Endlosschleife wird angehalten, nicht der Server")
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
    @DisplayName("Fabrik")
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
        @DisplayName("Ein Ereignisblock läuft, wenn das Ereignis kommt")
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
        @DisplayName("Mehrere Blöcke für dasselbe Ereignis laufen alle")
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
        @DisplayName("Ein Tippfehler im Gerätenamen schlägt den richtigen vor")
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
        @DisplayName("await sagt ehrlich, dass es das noch nicht gibt")
        void awaitIsHonestAboutBeingMissing() {
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
            assertTrue(error.hint().contains("Continuations"), error::hint);
        }
    }
}
