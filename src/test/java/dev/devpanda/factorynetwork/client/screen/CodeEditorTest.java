package dev.devpanda.factorynetwork.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prüft die Bearbeitungsschritte des Editors.
 *
 * <p>Nicht über die Tastatur: {@code hasControlDown} fragt das Fenster, und
 * im Test gibt es keines. Geprüft wird deshalb, was ein Tastendruck auslöst,
 * nicht der Tastendruck selbst — die Zuordnung von Taste zu Schritt ist eine
 * Zeile im {@code switch} und trägt kein Verhalten.
 *
 * <p>Die Schrift ist {@code null}. Der Editor braucht sie erst beim
 * Zeichnen, und Zeichnen steht hier nicht zur Prüfung.
 */
class CodeEditorTest {

    private static CodeEditor editor(String text) {
        return new CodeEditor(null, 0, 0, 200, 120, text);
    }

    private static void type(CodeEditor editor, String text) {
        for (char c : text.toCharArray()) {
            editor.charTyped(c, 0);
        }
    }

    @Test
    void dieMeldungEinerZeileWirdGefunden() {
        CodeEditor editor = editor(String.join("\n", "fn a() {", "    let b =", "}"));
        var ergebnis = dev.devpanda.factorynetwork.lang.parse.Parser.parse(editor.text());
        assertTrue(!ergebnis.diagnostics().isEmpty(), "der Text muss eine Meldung haben");
        var erste = ergebnis.diagnostics().get(0);
        assertEquals(erste, editor.diagnosticIn(ergebnis.diagnostics(), erste.span().line()));
        assertEquals(null, editor.diagnosticIn(ergebnis.diagnostics(), 999),
                "eine Zeile ohne Meldung hat keine");
    }

    @Test
    void derSprungLandetAufDerStelle() {
        CodeEditor editor = editor(String.join("\n", "fn a() {", "    let b =", "}"));
        var ergebnis = dev.devpanda.factorynetwork.lang.parse.Parser.parse(editor.text());
        var erste = ergebnis.diagnostics().get(0);
        editor.jumpTo(erste);
        assertEquals(erste.span().line() - 1, editor.cursorLine(), "Zeile");
        assertTrue(editor.cursorColumn() <= erste.span().column(), "höchstens die Spalte");
    }

    @Test
    void eineKlammerSchliesstSichSelbst() {
        CodeEditor editor = editor("");
        type(editor, "fn a(");
        assertEquals("fn a()", editor.text(), "die schließende kommt mit");
        assertEquals(5, editor.cursorColumn(), "und der Cursor bleibt dazwischen");
    }

    @Test
    void ueberEineStehendeKlammerHinweg() {
        CodeEditor editor = editor("");
        type(editor, "fn a()");
        assertEquals("fn a()", editor.text(), "keine zweite schließende");
        assertEquals(6, editor.cursorColumn(), "sondern darüber hinweg");
    }

    @Test
    void mittenImWortWirdNichtsErgaenzt() {
        // Vor einem Buchstaben meint man das eine Zeichen. Ergaenzte der
        // Editor auch hier, muesste man jede zweite Klammer wieder loeschen.
        CodeEditor editor = editor("abc");
        editor.setCursor(0, 0);
        type(editor, "(");
        assertEquals("(abc", editor.text());
    }

    @Test
    void einLeeresPaarGehtZusammenWiederWeg() {
        CodeEditor editor = editor("");
        type(editor, "(");
        editor.backspace();
        assertEquals("", editor.text(), "was zusammen kam, geht zusammen");
    }

    @Test
    void zeilenumbruchZwischenKlammernOeffnetDenBlock() {
        CodeEditor editor = editor("fn a() {}");
        editor.setCursor(0, 8);
        editor.newLine();
        assertEquals(String.join("\n", "fn a() {", "    ", "}"), editor.text(),
                "die schließende Klammer bekommt eine eigene Zeile");
        assertEquals(1, editor.cursorLine());
        assertEquals(4, editor.cursorColumn(), "und der Cursor steht eingerückt dazwischen");
    }

    @Test
    void einLaufVonAnschlaegenIstEinSchritt() {
        CodeEditor editor = editor("");
        type(editor, "worker");
        assertEquals("worker", editor.text());

        editor.undo();
        assertEquals("", editor.text(), "ein Lauf geht am Stück zurück");

        editor.redo();
        assertEquals("worker", editor.text());
    }

    @Test
    void loeschenUndTippenSindZweiSchritte() {
        CodeEditor editor = editor("abc");
        editor.setCursor(0, 3);
        type(editor, "de");
        editor.remember(CodeEditor.EditKind.DELETING);
        editor.deleteWordLeft();
        assertEquals("", editor.text());

        editor.undo();
        assertEquals("abcde", editor.text(), "erst das Löschen zurück");
        editor.undo();
        assertEquals("abc", editor.text(), "dann der Lauf davor");
    }

    @Test
    void einNeuerTextLoeschtDieGeschichte() {
        CodeEditor editor = editor("");
        type(editor, "abc");
        editor.setText("etwas anderes");
        editor.undo();
        assertEquals("etwas anderes", editor.text(),
                "ein gesetzter Text ist kein Schritt, den man zurücknimmt");
    }

    @Test
    void einruecken() {
        CodeEditor editor = editor("eins\nzwei\ndrei");
        editor.select(0, 0, 1, 4);
        editor.indentLines(false);
        assertEquals("    eins\n    zwei\ndrei", editor.text());

        editor.indentLines(true);
        assertEquals("eins\nzwei\ndrei", editor.text());
    }

    @Test
    void ausrueckenNimmtHoechstensVierLeerzeichen() {
        CodeEditor editor = editor("  zwei");
        editor.setCursor(0, 6);
        editor.indentLines(true);
        assertEquals("zwei", editor.text(), "es sind nur zwei da, also gehen zwei");
        assertEquals(4, editor.cursorColumn(), "der Cursor rückt mit");
    }

    @Test
    void zeileVerdoppeln() {
        CodeEditor editor = editor("eins\nzwei");
        editor.setCursor(0, 2);
        editor.duplicateLine();
        assertEquals("eins\neins\nzwei", editor.text());
        assertEquals(1, editor.cursorLine(), "der Cursor steht in der Verdopplung");
    }

    @Test
    void wortweiseBewegen() {
        CodeEditor editor = editor("move alpha to beta");
        editor.setCursor(0, 18);
        editor.moveWordLeft();
        assertEquals(14, editor.cursorColumn(), "vor beta");
        editor.moveWordLeft();
        assertEquals(11, editor.cursorColumn(), "vor to");
        editor.moveWordRight();
        assertEquals(14, editor.cursorColumn(), "hinter to samt Lücke");
    }

    @Test
    void wortweiseLoeschen() {
        CodeEditor editor = editor("move alpha to beta");
        editor.setCursor(0, 18);
        editor.deleteWordLeft();
        assertEquals("move alpha to ", editor.text());
        editor.deleteWordLeft();
        assertEquals("move alpha ", editor.text());
    }

    @Test
    void ueberAnfangUndEndeHinausGehtNichtsKaputt() {
        CodeEditor editor = editor("wort");
        editor.setCursor(0, 0);
        editor.moveWordLeft();
        assertEquals(0, editor.cursorColumn());
        editor.deleteWordLeft();
        assertEquals("wort", editor.text());

        editor.setCursor(0, 4);
        editor.moveWordRight();
        assertEquals(4, editor.cursorColumn());
    }

    @Test
    void rueckgaengigOhneGeschichteTutNichts() {
        CodeEditor editor = editor("unberührt");
        editor.undo();
        editor.redo();
        assertEquals("unberührt", editor.text());
    }
    @Test
    void doppelklickNimmtDasWort() {
        CodeEditor editor = editor("move alpha to beta");
        editor.selectWordAt(0, 7);
        assertEquals("alpha", editor.selectedText());

        // In einer Lücke wird die Lücke genommen — ein Doppelklick, der
        // manchmal nichts tut, fühlt sich kaputt an.
        editor.selectWordAt(0, 4);
        assertEquals(" ", editor.selectedText());
    }

    @Test
    void doppelklickAufEineLeereZeile() {
        CodeEditor editor = editor("eins\n\ndrei");
        editor.selectWordAt(1, 0);
        assertEquals("", editor.selectedText());
    }

    @Test
    void einAnschlagOhneWirkungKostetKeinenSchritt() {
        CodeEditor editor = editor("");
        type(editor, "abc");
        // Ganz nach vorn, dann Rücktaste ins Leere: Das darf den Weg zurück
        // nicht aufbrauchen und den Weg vorwärts nicht löschen.
        editor.setCursor(0, 0);
        editor.remember(CodeEditor.EditKind.DELETING);
        editor.undo();
        assertEquals("abc", editor.text(),
                "ein gemerkter Leerlauf frisst einen Schritt");
    }
    @Test
    void suchenFindetAlleStellen() {
        CodeEditor editor = editor("move alpha to beta\nmove ALPHA to gamma\nfertig");
        editor.openSearch();
        editor.setSearchTerm("alpha");

        // Ohne Rücksicht auf Groß- und Kleinschreibung: Wer sucht, weiß meist
        // nur ungefähr, wie es geschrieben war.
        assertEquals(2, editor.matches().size());
        assertEquals(1, editor.matchNumber());
        assertEquals("alpha", editor.selectedText());

        editor.step(1);
        assertEquals(2, editor.matchNumber());
        assertEquals("ALPHA", editor.selectedText());

        // Weiter hinter der letzten Stelle fängt vorn wieder an.
        editor.step(1);
        assertEquals(1, editor.matchNumber());
        editor.step(-1);
        assertEquals(2, editor.matchNumber());
    }

    @Test
    void eineAuswahlWirdZumSuchwort() {
        CodeEditor editor = editor("move alpha to beta");
        editor.select(0, 5, 0, 10);
        editor.openSearch();
        assertEquals("alpha", editor.searchTerm());
        assertTrue(editor.isSearching());
    }

    @Test
    void ohneTrefferPassiertNichts() {
        CodeEditor editor = editor("move alpha to beta");
        editor.openSearch();
        editor.setSearchTerm("gibtesnicht");
        assertEquals(0, editor.matches().size());
        assertEquals(0, editor.matchNumber());
        editor.step(1);
        assertEquals("move alpha to beta", editor.text(), "der Text bleibt unberührt");
    }

    @Test
    void dieSucheLaesstSichSchliessen() {
        CodeEditor editor = editor("alpha");
        editor.openSearch();
        assertTrue(editor.isSearching());
        editor.closeSearch();
        assertTrue(!editor.isSearching());
    }
}
