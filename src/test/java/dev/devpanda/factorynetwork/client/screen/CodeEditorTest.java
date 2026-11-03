package dev.devpanda.factorynetwork.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the editing steps of the editor.
 *
 * <p>Not via the keyboard: {@code hasControlDown} asks the window, and in the
 * test there is none. What is checked is therefore what a key press triggers,
 * not the key press itself — the mapping from key to step is one line in the
 * {@code switch} and carries no behavior.
 *
 * <p>The font is {@code null}. The editor needs it only when drawing, and
 * drawing is not up for checking here.
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
        // Before a letter one means the single character. If the editor added
        // one here too, you would have to delete every second bracket again.
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

        // In a gap the gap is taken — a double click that sometimes does
        // nothing feels broken.
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
        // All the way to the front, then backspace into nothing: this must not
        // use up the way back and must not erase the way forward.
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

        // Without regard to upper and lower case: whoever searches usually
        // only knows roughly how it was written.
        assertEquals(2, editor.matches().size());
        assertEquals(1, editor.matchNumber());
        assertEquals("alpha", editor.selectedText());

        editor.step(1);
        assertEquals(2, editor.matchNumber());
        assertEquals("ALPHA", editor.selectedText());

        // Continuing past the last spot starts at the front again.
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
    // ---- Replace ---------------------------------------------------------

    @Test
    void ersetzenTrifftDieAngefahreneStelle() {
        CodeEditor editor = editor(String.join("\n",
                "let kiste_1 = 1", "let kiste_2 = 2", "let ofen = 3"));
        editor.openSearch();
        editor.setSearchTerm("kiste_1");
        editor.setReplaceTerm("truhe_1");
        editor.replaceCurrent();

        assertEquals(String.join("\n",
                "let truhe_1 = 1", "let kiste_2 = 2", "let ofen = 3"), editor.text());
    }

    @Test
    void alleErsetzenNimmtJedeStelle() {
        CodeEditor editor = editor(String.join("\n",
                "from kiste to kiste", "let kiste = 1"));
        editor.openSearch();
        editor.setSearchTerm("kiste");
        editor.setReplaceTerm("fass");
        editor.replaceAll();

        assertEquals(String.join("\n", "from fass to fass", "let fass = 1"), editor.text());
    }

    @Test
    void alleErsetzenIstEinSchrittZurueck() {
        // Undoing twenty spots one by one would be twenty moves.
        CodeEditor editor = editor("a a a a");
        editor.openSearch();
        editor.setSearchTerm("a");
        editor.setReplaceTerm("b");
        editor.replaceAll();
        assertEquals("b b b b", editor.text());

        editor.undo();
        assertEquals("a a a a", editor.text());
    }

    @Test
    void ersetzenDurchNichtsLoeschtDieStelle() {
        CodeEditor editor = editor("let x = 1 // weg");
        editor.openSearch();
        editor.setSearchTerm(" // weg");
        editor.setReplaceTerm("");
        editor.replaceAll();

        assertEquals("let x = 1", editor.text());
    }

    @Test
    void fundstellenUeberlappenNicht() {
        // Without this rule the search for "aa" in "aaa" would find two spots,
        // and replacing all would write into its own replacement.
        CodeEditor editor = editor("aaa");
        editor.openSearch();
        editor.setSearchTerm("aa");
        assertEquals(1, editor.matches().size());

        editor.setReplaceTerm("b");
        editor.replaceAll();
        assertEquals("ba", editor.text());
    }

    @Test
    void ohneFundstelleAendertErsetzenNichts() {
        CodeEditor editor = editor("let x = 1");
        editor.openSearch();
        editor.setSearchTerm("gibtsnicht");
        editor.setReplaceTerm("egal");
        editor.replaceAll();
        editor.replaceCurrent();

        assertEquals("let x = 1", editor.text());
    }

    // ---- Horizontal scrolling ----------------------------------------------

    @Test
    void derCursorBleibtAuchInDerBreiteImBild() {
        // 200 points wide, gutter and margin off, advance six: about thirty
        // columns. A line with two hundred characters simply ran off the
        // window before.
        CodeEditor editor = editor("x".repeat(200));
        editor.setCursor(0, 200);
        assertTrue(editor.firstVisibleColumn() > 0,
                "der Ausschnitt muss dem Cursor gefolgt sein");

        editor.setCursor(0, 0);
        assertEquals(0, editor.firstVisibleColumn(),
                "und am Zeilenanfang wieder ganz nach links");
    }

    @Test
    void undoUeberlebtDenDateiwechsel() {
        // Switching to another file and back used to throw the history away:
        // one look into a second file, and the last ten minutes could no
        // longer be taken back.
        CodeEditor editor = editor("");
        editor.openFile("eins.mf", "alpha");
        editor.setCursor(0, 5);
        type(editor, " beta");
        assertEquals("alpha beta", editor.text());

        editor.openFile("zwei.mf", "gamma");
        assertEquals("gamma", editor.text());
        editor.openFile("eins.mf", "alpha beta");

        editor.undo();
        assertEquals("alpha", editor.text(), "the step from before the switch is still there");
        editor.redo();
        assertEquals("alpha beta", editor.text());
    }

    @Test
    void jedeDateiHatIhreEigeneGeschichte() {
        CodeEditor editor = editor("");
        editor.openFile("eins.mf", "a");
        editor.setCursor(0, 1);
        type(editor, "1");
        editor.openFile("zwei.mf", "b");
        editor.setCursor(0, 1);
        type(editor, "2");

        editor.undo();
        assertEquals("b", editor.text(), "undo in the second file touches the second file");
        editor.undo();
        assertEquals("b", editor.text(), "and does not reach into the first one");

        editor.openFile("eins.mf", "a1");
        editor.undo();
        assertEquals("a", editor.text());
    }

    @Test
    void cursorUndBildlaufKommenMitZurueck() {
        CodeEditor editor = editor("");
        String text = String.join("\n", "eins", "zwei", "drei");
        editor.openFile("eins.mf", text);
        editor.setCursor(2, 3);
        editor.openFile("zwei.mf", "x");
        editor.openFile("eins.mf", text);
        assertEquals(2, editor.cursorLine());
        assertEquals(3, editor.cursorColumn());
    }

    @Test
    void fremdGeaenderterTextBeginntNeu() {
        // The text came back different — another player saved, or the server
        // rejected a draft. Steps that led to the old text would lead nowhere
        // from the new one.
        CodeEditor editor = editor("");
        editor.openFile("eins.mf", "alpha");
        editor.setCursor(0, 5);
        type(editor, " beta");
        editor.openFile("zwei.mf", "x");
        editor.openFile("eins.mf", "etwas ganz anderes");

        editor.undo();
        assertEquals("etwas ganz anderes", editor.text(), "nothing to take back");
    }

    private static dev.devpanda.factorynetwork.lang.Diagnostic meldung(int line, int column,
            int length, String text) {
        return new dev.devpanda.factorynetwork.lang.Diagnostic(
                dev.devpanda.factorynetwork.lang.Diagnostic.Severity.ERROR,
                new dev.devpanda.factorynetwork.lang.Span(0, length, line, column), text, null);
    }

    @Test
    void zweiMeldungenAufEinerZeileBleibenZwei() {
        CodeEditor editor = editor("move foo to bar");
        var erste = meldung(1, 6, 3, "foo gibt es nicht");
        var zweite = meldung(1, 13, 3, "bar gibt es nicht");
        var alle = java.util.List.of(erste, zweite);
        assertEquals(alle, editor.diagnosticsIn(alle, 1));
        assertEquals(erste, editor.diagnosticIn(alle, 1), "the first stays the first");
    }

    @Test
    void derZeigerEntscheidetWelcheGemeintIst() {
        CodeEditor editor = editor("move foo to bar");
        var erste = meldung(1, 6, 3, "foo gibt es nicht");
        var zweite = meldung(1, 13, 3, "bar gibt es nicht");
        var alle = java.util.List.of(erste, zweite);
        assertEquals(erste, editor.diagnosticCovering(alle, 1, 6), "on foo");
        assertEquals(zweite, editor.diagnosticCovering(alle, 1, 13), "on bar");
        assertEquals(erste, editor.diagnosticCovering(alle, 1, 0), "next to both: the first");
        assertEquals(null, editor.diagnosticCovering(alle, 2, 0), "a line without a message");
    }

    @Test
    void eineMeldungOhneBreiteZeigtAufEineSpalte() {
        CodeEditor editor = editor("let b =");
        // Something is missing at the end of the line: the place has no width.
        var fehlt = meldung(1, 8, 0, "hier fehlt ein Wert");
        int[] spalten = editor.columnsOf(fehlt, 7);
        assertEquals(7, spalten[0]);
        assertEquals(8, spalten[1], "one column wide, so that it can be seen");
        // And one that claims more than the line holds stops at its end.
        int[] zuLang = editor.columnsOf(meldung(1, 5, 40, "zu lang"), 7);
        assertEquals(4, zuLang[0]);
        assertEquals(7, zuLang[1]);
    }

    private static CodeEditor langerText() {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= 100; i++) {
            text.append("zeile ").append(i).append(i < 100 ? "\n" : "");
        }
        // 120 pixels high: ten lines fit, ninety lie outside.
        return editor(text.toString());
    }

    @Test
    void einKlickAufDenBalkenSchiebtDenText() {
        CodeEditor editor = langerText();
        // The bar sits at the right edge; the editor is 200 wide.
        assertTrue(editor.mouseClicked(198, 119, 0), "the bar takes the click");
        assertEquals(editor.maxScrollLine(), editor.scrollLine(), "at the bottom: the end");
        assertTrue(editor.mouseClicked(198, 11, 0));
        assertEquals(0, editor.scrollLine(), "at the top: the beginning");
    }

    @Test
    void ziehenAmBalkenFolgtDemZeiger() {
        CodeEditor editor = langerText();
        editor.mouseClicked(198, 11, 0);
        editor.mouseDragged(198, 65, 0);
        int mitte = editor.scrollLine();
        assertTrue(mitte > 30 && mitte < 60, "about the middle, was " + mitte);
        editor.mouseDragged(198, 400, 0);
        assertEquals(editor.maxScrollLine(), editor.scrollLine(), "past the end stays at the end");
    }

    @Test
    void derBalkenVerschiebtDenCursorNicht() {
        CodeEditor editor = langerText();
        editor.setCursor(3, 2);
        editor.mouseClicked(198, 119, 0);
        editor.mouseDragged(198, 60, 0);
        assertEquals(3, editor.cursorLine(), "scrolling is not a click into the text");
        assertEquals(2, editor.cursorColumn());
    }

    @Test
    void einKurzerTextHatKeinenBalken() {
        // Whether the click then lands in the text cannot be checked here:
        // that path asks the game for the Shift key.
        CodeEditor editor = editor("eins" + (char) 10 + "zwei");
        assertEquals(0, editor.maxScrollLine(), "nothing to scroll");
        editor.mouseDragged(198, 100, 0);
        assertEquals(0, editor.scrollLine());
    }

    @org.junit.jupiter.api.Test
    void itemsStartsAGridOnlyInFrontOfAValue() {
        var grid = dev.devpanda.factorynetwork.lang.parse.Lexer.tokenize("    items storage.items()").tokens();
        org.junit.jupiter.api.Assertions.assertSame(grid.get(0), CodeEditor.displayGridWord(grid));
        for (String name : new String[]{"items = 3", "items.count()", "items(1)", "items"}) {
            var tokens = dev.devpanda.factorynetwork.lang.parse.Lexer.tokenize(name).tokens();
            org.junit.jupiter.api.Assertions.assertNull(CodeEditor.displayGridWord(tokens), name);
        }
    }
}
