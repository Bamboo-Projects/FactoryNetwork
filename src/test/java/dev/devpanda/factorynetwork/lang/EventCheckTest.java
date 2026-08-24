package dev.devpanda.factorynetwork.lang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ob ein {@code on}-Block je laufen kann.
 *
 * <p><b>Der Fehler, den das hier verhindert, ist unsichtbar.</b> Ein
 * {@code on} braucht keine Deklaration, also nimmt der Übersetzer jeden Namen
 * an. Wer sich vertippt, bekommt kein rotes Wort und keinen Fehler beim
 * ersten Lauf — es gibt keinen ersten Lauf. Der Block liegt im Programm und
 * schweigt.
 */
class EventCheckTest {

    private static List<Diagnostic> check(String source) {
        return new Project(Map.of("main.mf", source)).parse().diagnostics();
    }

    @Test
    @DisplayName("Ein Ereignis, das niemand auslöst, wird gemeldet")
    void anEventNobodyFiresIsReported() {
        // inventory_changed stand im Editor zur Auswahl und wurde nie
        // ausgelöst — der Anlass für diese Prüfung.
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
    @DisplayName("Ein Vertipper bekommt den richtigen Namen vorgeschlagen")
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
    @DisplayName("Ein Name ohne Ähnlichkeit bekommt die Liste zu sehen")
    void anUnrelatedNameGetsTheFullList() {
        List<Diagnostic> problems = check("""
                on quatsch(x) {
                    log("nie")
                }""");

        // Ohne die Liste bleibt offen, ob man sich vertippt hat oder ob es
        // dieses Ereignis überhaupt nicht gibt.
        assertTrue(problems.stream().anyMatch(problem ->
                        problem.hint() != null
                                && problem.hint().contains("redstone_changed")
                                && problem.hint().contains("emit")),
                () -> "die Liste fehlt: " + problems);
    }

    @Test
    @DisplayName("Die vier eingebauten Ereignisse gehen durch")
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
    @DisplayName("Ein Name zu viel wird gemeldet, ein Name zu wenig nicht")
    void tooManyNamesAreReportedButTooFewAreNot() {
        // Genau der Fehler, den das alte Editor-Muster erzeugte: die
        // Parameterliste von redstone_changed an einem Gerätemeldung.
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

        // Wer die Stärke nicht braucht, darf sie weglassen.
        assertTrue(check("""
                on redstone_changed(gerät) {
                    log("signal")
                }""").isEmpty(), "ein Name weniger ist erlaubt");
    }

    @Test
    @DisplayName("Ein selbst erklärtes Ereignis gilt")
    void anOwnEventCounts() {
        assertTrue(check("""
                event Fertig(nummer: Int)

                on Fertig(nummer) {
                    log("fertig " + nummer)
                }""").isEmpty(), "ein erklärtes Ereignis ist bekannt");
    }

    @Test
    @DisplayName("Das event darf in einer anderen Datei stehen")
    void theEventMayLiveInAnotherFile() {
        // Alle Dateien teilen einen Namensraum. Die Prüfung je Datei hätte
        // hier gemeldet, was die Nachbardatei erklärt.
        List<Diagnostic> problems = new Project(Map.of(
                "ereignisse.mf", "event Fertig(nummer: Int)",
                "main.mf", """
                        on Fertig(nummer) {
                            log("fertig")
                        }""")).parse().diagnostics();

        assertTrue(problems.isEmpty(), () -> "nichts zu melden, aber: " + problems);
    }

    @Test
    @DisplayName("Es bleibt bei einer Warnung — das Programm läuft")
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
