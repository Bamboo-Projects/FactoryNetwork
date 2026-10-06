package dev.devpanda.factorynetwork.runtime.flow;

import dev.devpanda.factorynetwork.lang.parse.Parser;
import dev.devpanda.factorynetwork.runtime.Interpreter;
import dev.devpanda.factorynetwork.runtime.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowEnginePoisonTest {

    /** A world whose move call fails with something that is not a script error. */
    private static final class ExplodingHost implements Interpreter.Host {

        int moveCalls;

        @Override
        public long move(Value amount, Value from, Value to) {
            moveCalls++;
            throw new IllegalStateException("kaputter Aufruf");
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
        public List<String> membersOf(String group) {
            return List.of();
        }

        @Override
        public String suggestDevice(String name) {
            return null;
        }
    }

    @Test
    @DisplayName("An unexpected exception fails the flow instead of aborting every tick")
    void unexpectedExceptionFailsTheFlow() {
        Parser.ParseResult result = Parser.parse("""
                fn boom() {
                    move 1 item:coal from chest to crusher_1
                }
                """);
        assertTrue(result.diagnostics().stream().noneMatch(d -> d.isError()),
                () -> "Übersetzungsfehler: " + result.diagnostics());
        ExplodingHost host = new ExplodingHost();
        FlowEngine engine = new FlowEngine(result.program(),
                new Interpreter(result.program(), host));
        Flow flow = engine.start("boom", List.of());

        assertDoesNotThrow(() -> engine.tick(1));

        assertEquals(Flow.Status.FAILED, flow.status(),
                "the flow has to end, not stay RUNNING");
        assertDoesNotThrow(() -> engine.tick(2));
        assertEquals(1, host.moveCalls,
                "a failed flow must not run the failing step again on the next tick");
    }
}
