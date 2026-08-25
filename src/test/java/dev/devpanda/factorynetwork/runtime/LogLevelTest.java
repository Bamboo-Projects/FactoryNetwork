package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.Diagnostic;
import dev.devpanda.factorynetwork.lang.Project;
import dev.devpanda.factorynetwork.lang.ast.Program;
import dev.devpanda.factorynetwork.lang.parse.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Stufen des Protokolls.
 *
 * <p>Ohne Minecraft: Ein Host, der nur mitschreibt, reicht — die Frage ist,
 * ob der Interpreter die richtige Stufe wählt und ob {@code log()} weiter
 * geht.
 */
class LogLevelTest {

    /** Ein Host, der nur zuhört. */
    private static final class Listener implements Interpreter.Host {

        private final List<LogEntry> written = new ArrayList<>();
        private String source = "";

        @Override
        public void log(String message) {
            log(LogLevel.INFO, message);
        }

        @Override
        public void log(LogLevel level, String message) {
            written.add(new LogEntry(level, 1L, source, message));
        }

        @Override
        public void setLogSource(String value) {
            source = value;
        }

        @Override
        public long move(Value amount, Value from, Value to) {
            return 0;
        }

        @Override
        public String suggestDevice(String name) {
            return null;
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
        public boolean hasDevice(String name) {
            return true;
        }
    }

    private static List<LogEntry> run(String source) {
        Program program = Parser.parse(source).program();
        Listener host = new Listener();
        new Interpreter(program, host).call("test", List.of());
        return host.written;
    }

    @Test
    @DisplayName("Jede Stufe kommt als sie selbst an")
    void everyLevelArrivesAsItself() {
        List<LogEntry> written = run("""
                fn test() {
                    debug("a")
                    info("b")
                    warn("c")
                    error("d")
                }""");

        assertEquals(List.of(LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR),
                written.stream().map(LogEntry::level).toList());
        assertEquals("c", written.get(2).text(), "Der Text bleibt der Text");
    }

    @Test
    @DisplayName("log() schreibt weiter, und zwar als info")
    void plainLogStillWritesAsInfo() {
        // Es steht in jedem Programm, das es schon gibt, und in jeder Doku.
        List<LogEntry> written = run("""
                fn test() {
                    log("Es läuft")
                }""");

        assertEquals(1, written.size());
        assertEquals(LogLevel.INFO, written.get(0).level());
        assertEquals("Es läuft", written.get(0).text());
    }

    @Test
    @DisplayName("Auch eine Zahl oder ein Gerät lässt sich schreiben")
    void numbersAndDevicesCanBeWrittenToo() {
        List<LogEntry> written = run("""
                fn test() {
                    warn(3 + 4)
                }""");

        assertEquals("7", written.get(0).text(), "describe() macht den Text");
    }

    @Test
    @DisplayName("Eine Stufe kennt ihre Rangfolge")
    void levelsKnowTheirOrder() {
        // Der Filter im Terminal zeigt alles ab einer Stufe.
        assertTrue(LogLevel.ERROR.atLeast(LogLevel.WARN));
        assertTrue(LogLevel.WARN.atLeast(LogLevel.WARN));
        assertTrue(!LogLevel.INFO.atLeast(LogLevel.WARN));
        assertTrue(!LogLevel.DEBUG.atLeast(LogLevel.INFO));
    }

    @Test
    @DisplayName("Eine eigene Funktion namens error wird gemeldet")
    void anOwnFunctionCalledErrorIsReported() {
        // Sie wäre nie erreichbar: Der Interpreter prüft die eingebauten
        // zuerst. Still hinzunehmen hieße, dass ein fn dasteht und nie läuft.
        List<Diagnostic> problems = new Project(Map.of("main.mf", """
                fn error(text) {
                    return text
                }""")).parse().diagnostics();

        assertTrue(problems.stream().anyMatch(problem ->
                        problem.message().contains("eingebaut")),
                () -> "die Meldung fehlt: " + problems);
    }

    @Test
    @DisplayName("Ein Eintrag übersteht das Speichern")
    void anEntrySurvivesBeingWritten() {
        LogEntry entry = new LogEntry(LogLevel.WARN, 1234L, "ofen_import", "Kohle knapp");
        LogEntry gelesen = LogEntry.read(entry.write());

        assertEquals(entry, gelesen, "Stufe, Zeit, Herkunft und Text");
    }
}
