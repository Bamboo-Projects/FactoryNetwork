package dev.devpanda.factorynetwork.lang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether an {@code on} block can ever run.
 *
 * <p><b>The mistake this prevents is invisible.</b> An
 * {@code on} needs no declaration, so the compiler accepts any name.
 * A typo produces no red word and no error on the first run — there is
 * no first run. The block sits in the program and stays silent.
 */
class EventCheckTest {

    private static List<Diagnostic> check(String source) {
        return new Project(Map.of("main.mf", source)).parse().diagnostics();
    }

    @Test
    @DisplayName("An event nobody fires is reported")
    void anEventNobodyFiresIsReported() {
        // inventory_changed was offered in the editor's list and was never
        // fired — the reason for this check.
        List<Diagnostic> problems = check("""
                on inventory_changed(kiste) {
                    log("nie")
                }""");

        assertTrue(problems.stream().anyMatch(problem ->
                        problem.message().contains("inventory_changed")
                                && problem.message().contains("löst niemand aus")),
                () -> "die Meldung fehlt: " + problems);
    }

    @Test
    @DisplayName("A typo gets the correct name suggested")
    void aTypoGetsTheRealNameSuggested() {
        List<Diagnostic> problems = check("""
                on device_onlin(gerät) {
                    log("fast")
                }""");

        assertTrue(problems.stream().anyMatch(problem ->
                        problem.hint() != null && problem.hint().contains("device_online")),
                () -> "der Vorschlag fehlt: " + problems);
    }

    @Test
    @DisplayName("A name with no similarity is shown the list")
    void anUnrelatedNameGetsTheFullList() {
        List<Diagnostic> problems = check("""
                on quatsch(x) {
                    log("nie")
                }""");

        // Without the list it stays open whether it was a typo or whether
        // this event simply does not exist.
        assertTrue(problems.stream().anyMatch(problem ->
                        problem.hint() != null
                                && problem.hint().contains("redstone_changed")
                                && problem.hint().contains("emit")),
                () -> "die Liste fehlt: " + problems);
    }

    @Test
    @DisplayName("The four built-in events pass")
    void theFourBuiltInEventsPass() {
        List<Diagnostic> problems = check("""
                on device_online(gerät) {
                    log("da")
                }

                on device_offline(name) {
                    log("weg")
                }

                on device_changed(gerät) {
                    log("anders")
                }

                on redstone_changed(gerät, stärke) {
                    log("signal")
                }""");

        assertTrue(problems.isEmpty(), () -> "nichts zu melden, aber: " + problems);
    }

    @Test
    @DisplayName("One name too many is reported, one name too few is not")
    void tooManyNamesAreReportedButTooFewAreNot() {
        // Exactly the mistake the old editor template produced: the
        // parameter list of redstone_changed on a device event.
        List<Diagnostic> tooMany = check("""
                on device_online(gerät, stärke) {
                    log("da")
                }""");

        assertTrue(tooMany.stream().anyMatch(problem ->
                        problem.message().contains("device_online")
                                && problem.message().contains("2 Namen")),
                () -> "die Meldung fehlt: " + tooMany);
        assertTrue(tooMany.stream().anyMatch(problem ->
                        problem.hint() != null && problem.hint().contains("redstone_changed")),
                () -> "der Hinweis auf den einzigen mit zwei Werten fehlt: " + tooMany);

        // If you do not need the strength, you may leave it out.
        assertTrue(check("""
                on redstone_changed(gerät) {
                    log("signal")
                }""").isEmpty(), "ein Name weniger ist erlaubt");
    }

    @Test
    @DisplayName("A self-declared event counts")
    void anOwnEventCounts() {
        assertTrue(check("""
                event Fertig(nummer: Int)

                on Fertig(nummer) {
                    log("fertig " + nummer)
                }""").isEmpty(), "ein erklärtes Ereignis ist bekannt");
    }

    @Test
    @DisplayName("The event may live in another file")
    void theEventMayLiveInAnotherFile() {
        // All files share a single namespace. A per-file check would have
        // reported here what the neighbouring file declares.
        List<Diagnostic> problems = new Project(Map.of(
                "ereignisse.mf", "event Fertig(nummer: Int)",
                "main.mf", """
                        on Fertig(nummer) {
                            log("fertig")
                        }""")).parse().diagnostics();

        assertTrue(problems.isEmpty(), () -> "nichts zu melden, aber: " + problems);
    }

    @Test
    @DisplayName("It stays a warning — the program runs")
    void itStaysAWarning() {
        List<Diagnostic> problems = check("""
                on inventory_changed(kiste) {
                    log("nie")
                }""");

        assertEquals(1, problems.size(), () -> "genau eine Meldung: " + problems);
        assertTrue(problems.stream().noneMatch(Diagnostic::isError),
                () -> "eine Datei, die morgen vollständig wird, muss heute laufen: " + problems);
    }
}
