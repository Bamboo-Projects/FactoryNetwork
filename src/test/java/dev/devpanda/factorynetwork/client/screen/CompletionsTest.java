package dev.devpanda.factorynetwork.client.screen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Was der Editor an welcher Stelle vorschlägt.
 *
 * <p>Geprüft werden nur die Stellen, die ohne Registry auskommen —
 * Gegenstände und Tags brauchen ein geladenes Spiel und stehen deshalb in
 * den GameTests.
 *
 * <p>Die Frage dahinter ist immer dieselbe: <b>Passt der Vorschlag zu dem,
 * was die Grammatik an dieser Stelle erlaubt?</b> Ein Vorschlag, der überall
 * dasselbe zeigt, hilft nirgends — und einer, der Verbotenes anbietet, ist
 * schlechter als keiner.
 */
class CompletionsTest {

    private static List<String> texts(List<Completions.Entry> entries) {
        return entries.stream().map(Completions.Entry::text).toList();
    }

    /** Vorschläge am Ende der letzten Zeile. */
    private static List<String> at(String... lines) {
        List<String> code = List.of(lines);
        int last = code.size() - 1;
        return texts(Completions.at(code, last, code.get(last).length()));
    }

    @Test
    @DisplayName("In einer Anzeige stehen ihre Angaben, keine Anweisungen")
    void insideDisplayOnlyDisplayEntries() {
        List<String> shown = at("display halle {", "    ");

        assertTrue(shown.contains("title"), () -> "title fehlt: " + shown);
        assertTrue(shown.contains("row"), () -> "row fehlt: " + shown);
        assertTrue(shown.contains("progress"), () -> "progress fehlt: " + shown);
        assertFalse(shown.contains("if"),
                () -> "eine Anzeige kennt keine Anweisungen: " + shown);
        assertFalse(shown.contains("let"), () -> shown.toString());
        assertFalse(shown.contains("from"),
                () -> "from gehört in einen Worker, nicht in eine Anzeige: " + shown);
    }

    @Test
    @DisplayName("In einem Worker stehen seine Angaben")
    void insideWorkerOnlyWorkerEntries() {
        List<String> shown = at("worker haul {", "    ");

        assertTrue(shown.contains("from"), () -> "from fehlt: " + shown);
        assertFalse(shown.contains("title"),
                () -> "title gehört in eine Anzeige: " + shown);
    }

    @Test
    @DisplayName("In einer Gruppe stehen members und strategy")
    void insideGroupOnlyGroupEntries() {
        List<String> shown = at("group oefen {", "    ");

        assertEquals(List.of("members", "strategy"), shown);
    }

    @Test
    @DisplayName("Hinter einem fertigen Deklarationsnamen kommt die Klammer")
    void nothingAfterADeclarationName() {
        assertTrue(at("display halle ").isEmpty(),
                "hinter dem Namen gibt es nichts vorzuschlagen");
        assertTrue(at("worker haul ").isEmpty(), "und im Worker genauso");
    }

    @Test
    @DisplayName("Was schon vollständig dasteht, ist kein Vorschlag")
    void noSuggestionForAnExactMatch() {
        // „worker" ist selbst eine Deklaration; wer es zu Ende getippt hat,
        // braucht es nicht angeboten zu bekommen.
        assertFalse(at("worker").contains("worker"),
                "ein Eintrag, der nichts ändert, verdeckt nur die Zeile darunter");
    }

    @Test
    @DisplayName("Auf oberster Ebene stehen die Deklarationen")
    void topLevelOffersDeclarations() {
        List<String> shown = at("disp");

        assertTrue(shown.contains("display"), () -> shown.toString());
    }
}
