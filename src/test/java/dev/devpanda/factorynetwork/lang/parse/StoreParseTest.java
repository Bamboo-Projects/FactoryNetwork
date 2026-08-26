package dev.devpanda.factorynetwork.lang.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code store kiste_1 { … }} — ein fremdes Inventar zählt zum Netzspeicher.
 *
 * <p>Der Speicherbus, wie AE2 ihn hat, nur ohne eigenen Block: Der Connector
 * hängt ohnehin an der Kiste, und die Zeile sagt, dass ihr Inhalt zum Netz
 * gehört. Dieselbe Haltung wie bei {@code recipe … at …} — was das Netz tut,
 * steht im Programm und nicht in einem Fenster, das niemand versionieren
 * kann. Die Begründung steht in {@code speicherbus.md}.
 */
class StoreParseTest {

    @Test
    @DisplayName("Ein Speicher ohne Angaben trägt nur seinen Gerätenamen")
    void astoreWithoutEntriesCarriesItsDevice() {
        var result = Parser.parse("store kiste_1 { }");

        assertFalse(result.diagnostics().stream().anyMatch(d -> d.isError()),
                () -> "Übersetzungsfehler: " + result.diagnostics());
        Decl.Store store = assertInstanceOf(Decl.Store.class,
                result.program().declarations().get(0));
        assertEquals("kiste_1", store.device());
        assertEquals(0, store.priority(), "ohne Angabe steht er gleichauf mit den Zellen");
        assertEquals(null, store.filter(), "und nimmt alles");
    }

    @Test
    @DisplayName("Mit Priorität und Filter")
    void withPriorityAndFilter() {
        var result = Parser.parse("""
                store kiste_1 {
                    priority 5
                    filter tag:c/ores
                }""");

        assertFalse(result.diagnostics().stream().anyMatch(d -> d.isError()),
                () -> "Übersetzungsfehler: " + result.diagnostics());
        Decl.Store store = assertInstanceOf(Decl.Store.class,
                result.program().declarations().get(0));
        assertEquals(5, store.priority());
        assertTrue(store.filter() != null, "der Filter muss dastehen");
    }

    @Test
    @DisplayName("Ein Speicher ohne Gerät ist ein Fehler")
    void astoreWithoutAdeviceIsAnerror() {
        // Anders als bei einem Rezept gibt es hier nichts zu raten: Ohne das
        // Gerät wüsste niemand, wessen Inhalt gemeint ist.
        var result = Parser.parse("store { }");

        assertTrue(result.diagnostics().stream().anyMatch(d -> d.isError()),
                "das muss auffallen");
    }
}
