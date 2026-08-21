package dev.devpanda.factorynetwork.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
