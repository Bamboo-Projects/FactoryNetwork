package dev.devpanda.factorynetwork.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.devpanda.factorynetwork.lang.parse.Parser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was eine Auswahl über sich sagt.
 *
 * <p>{@code describe()} steht im Protokoll, im Netz-Reiter und in jeder
 * Fehlermeldung, die einen Wert nennt. Die Texte sind deshalb Zusagen an den
 * Spieler und nicht Nebenwirkungen des Wertemodells — ein Umbau darunter darf
 * sie nicht bewegen.
 *
 * <p>Gegenstände und Flüssigkeiten stehen hier nur als leere Auswahl: Eine
 * einzelne Sorte braucht die Registry, und die gibt es ohne laufendes Spiel
 * nicht. Was die Zahl davor angeht, ist das kein Verlust — sie zählt Einträge
 * und keine Sorten.
 */
class ResourceValueTest {

    @Test
    @DisplayName("Eine Gegenstandsauswahl zählt Arten")
    void anitemSelectionCountsKinds() {
        assertEquals("0 Arten", Value.Selection.ofItems(List.of(), 64).describe());
    }

    @Test
    @DisplayName("Eine Flüssigkeitsauswahl zählt Flüssigkeiten")
    void afluidSelectionCountsFluids() {
        assertEquals("0 Flüssigkeiten", Value.Selection.ofFluids(List.of(), 1000).describe());
    }

    @Test
    @DisplayName("Eine Chemikalienauswahl zählt Chemikalien")
    void achemicalSelectionCountsChemicals() {
        assertEquals("2 Chemikalien", Value.Selection.ofChemicals(
                List.of("mekanism:hydrogen", "mekanism:oxygen"), 500).describe());
    }

    @Test
    @DisplayName("Eine Auswahl trägt eine Art und nicht zwei")
    void aselectionCarriesOneKindAndNotTwo() {
        // Eine Auswahl über Wasser und Stein wäre an jeder Verwendungsstelle
        // etwas anderes — dieselbe Regel, die FilterKind für Vorlagen
        // aufstellt. Vorher war sie durch drei getrennte Records erzwungen;
        // jetzt steht sie im Konstruktor, und deshalb wird sie hier geprüft.
        assertThrows(IllegalArgumentException.class, () -> new Value.Selection(
                ResourceKind.ITEM, List.of("mekanism:hydrogen"), 5));
    }

    @Test
    @DisplayName("Die Art einer aufgelösten Auswahl ist ablesbar")
    void thekindOfAresolvedSelectionCanBeRead() {
        // Genau diese Frage wählt in move und count den Weg. Vorher gab es
        // sie zweimal — einmal für Flüssigkeiten, einmal für Chemikalien —,
        // und die zweite kannte die aufgelöste Auswahl nicht.
        assertEquals(ResourceKind.CHEMICAL, ResourceKind.of(
                Value.Selection.ofChemicals(List.of("mekanism:hydrogen"), 100)));
        assertEquals(ResourceKind.CHEMICAL, ResourceKind.of(
                Value.Resource.ofChemical("mekanism:hydrogen")));
        assertEquals(ResourceKind.FLUID,
                ResourceKind.of(new Value.Request("fluid:water", -1)));
        assertEquals(ResourceKind.ITEM,
                ResourceKind.of(new Value.Request("tag:c/ores", -1)));
    }

    @Test
    @DisplayName("all ist keine Ressourcenart")
    void allIsNotAresourceKind() {
        // Es ist die Ansage, dass es keinen Filter gibt. Käme hier ITEM
        // heraus, entschiede „all" die Art mit — und wer es schreibt, meint
        // gerade das nicht.
        assertNull(ResourceKind.of(new Value.Request("all", -1)));
        assertNull(ResourceKind.of(Value.Nothing.get()));
    }

    @Test
    @DisplayName("Der Hinweis nennt die Sorte, nach der gefragt wurde")
    void thehintNamesTheMemberThatWasAsked() {
        // Vorher stand in jedem dieser Hinweise „it.item" — auch dann, wenn
        // jemand it.fluid geschrieben hatte. Der Zwilling war kopiert, der
        // Text darin nicht mit angepasst.
        TestHost host = new TestHost();
        host.stored.add(Value.Selection.ofFluids(List.of(), 1000));
        Parser.ParseResult result = Parser.parse("""
                fn zeigen() {
                    for posten in storage.items() {
                        log(posten.fluid)
                    }
                }""");
        Interpreter interpreter = new Interpreter(result.program(), host);

        ScriptError error = assertThrows(ScriptError.class,
                () -> interpreter.call("zeigen", List.of()));

        assertTrue(error.getMessage().contains("it.fluid")
                        || String.valueOf(error.hint()).contains("it.fluid"),
                () -> "gemeldet wurde: " + error.getMessage() + " / " + error.hint());
    }

    /** Eine Welt aus Papier mit einem Bestand, den sie sich ausdenkt. */
    private static final class TestHost implements Interpreter.Host {

        final List<Value> stored = new ArrayList<>();

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

        @Override
        public List<Value> storedItems() {
            return stored;
        }
    }
}
