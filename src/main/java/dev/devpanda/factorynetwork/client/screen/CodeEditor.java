package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.lang.Diagnostic;
import dev.devpanda.factorynetwork.lang.Lexer;
import dev.devpanda.factorynetwork.lang.Token;
import dev.devpanda.factorynetwork.lang.TokenType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * Ein mehrzeiliges Textfeld mit Syntaxhervorhebung.
 *
 * <p>Minecraft bringt keines mit — {@code MultiLineEditBox} kann keine Farben
 * pro Wort und keine Zeilennummern. Deshalb hier eine eigene, schmale Fassung:
 * Zeilen als Liste, ein Cursor, kein Umbruch. Kein Umbruch ist Absicht; in
 * Code wäre er verwirrend, weil die Zeilennummer nicht mehr stimmt.
 */
public class CodeEditor {

    private static final int LINE_HEIGHT = 10;
    private static final int GUTTER_WIDTH = 18;
    private static final int TEXT_LEFT = 4;

    /** So weit rückt eine Stufe ein — hier und in {@code newLine}. */
    private static final int INDENT = 4;

    /** Ein Zeilenende, damit die Bausteine unten ohne Escapes auskommen. */
    private static final String NEWLINE = "\n";

    /**
     * Vereinheitlicht Zeilenenden und ersetzt Tabulatoren durch Leerzeichen.
     *
     * <p>Wer aus einem fremden Editor kopiert, bringt sonst Steuerzeichen mit,
     * die hier nichts verloren haben — und ein Tabulator zerreißt die
     * Einrückung, weil der Editor in Leerzeichen rechnet.
     */
    private static String normalise(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r') {
                if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    continue;
                }
                out.append('\n');
            } else if (c == '\t') {
                out.append("    ");
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private final Font font;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    private final List<String> lines = new ArrayList<>();
    private int cursorLine;
    private int cursorColumn;
    private int scrollLine;

    /**
     * Die erste sichtbare Spalte.
     *
     * <p><b>Ohne sie lief eine lange Zeile einfach aus dem Fenster heraus</b>
     * — über den Rand, über die Fußleiste, über das, was rechts daneben lag.
     * Ein Umbruch kommt nicht in Frage, weil dann die Zeilennummer nicht mehr
     * zur Zeile passt; also wird geschoben, wie in jedem Editor.
     */
    private int scrollColumn;
    private Consumer<String> changeListener = text -> { };

    /**
     * Ob nur gelesen werden darf.
     *
     * <p>Für eine Datei, die gerade jemand anders bearbeitet. Bewegen,
     * markieren, kopieren und suchen gehen weiter — man will ja sehen, was
     * dort steht; nur ändern nicht.
     */
    private boolean readOnly;
    private long lastBlink;
    private boolean cursorVisible = true;

    private List<Completions.Entry> suggestions = List.of();
    private int selectedSuggestion;

    /**
     * Wo eine Auswahl begann, oder -1.
     *
     * <p>Der Anker bleibt stehen, während der Cursor wandert — so wächst und
     * schrumpft die Auswahl in beide Richtungen, ohne dass man sich merken
     * muss, welche Seite die frühere war.
     */
    private int anchorLine = -1;
    private int anchorColumn;

    /** Der Vorschub der Schrift, einmal gemessen. */
    private float advance;

    /** Was rückgängig gemacht werden kann, und was danach wieder vorwärts. */
    private final Deque<Snapshot> undoStack = new ArrayDeque<>();
    private final Deque<Snapshot> redoStack = new ArrayDeque<>();
    private EditKind lastKind;
    private int lastKindLine = -1;

    public CodeEditor(Font font, int x, int y, int width, int height, String initial) {
        this.font = font;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        setText(initial);
    }

    public void setChangeListener(Consumer<String> listener) {
        this.changeListener = listener;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
        if (readOnly) {
            clearSuggestions();
            closeSearch();
        }
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    /** In welcher Zeile der Cursor steht, von null an. */
    public int cursorLine() {
        return cursorLine;
    }

    /** In welcher Spalte der Cursor steht, von null an. */
    public int cursorColumn() {
        return cursorColumn;
    }

    /**
     * Wählt einen Bereich aus.
     *
     * <p>Anker und Cursor in einem Zug — die Auswahl, die man sonst mit
     * gedrückter Umschalttaste zieht.
     */
    public void select(int fromLine, int fromColumn, int toLine, int toColumn) {
        anchorLine = Mth.clamp(fromLine, 0, lines.size() - 1);
        anchorColumn = Mth.clamp(fromColumn, 0, lines.get(anchorLine).length());
        cursorLine = Mth.clamp(toLine, 0, lines.size() - 1);
        cursorColumn = Mth.clamp(toColumn, 0, lines.get(cursorLine).length());
        ensureVisible();
    }

    /** Setzt den Cursor, ohne die Auswahl mitzunehmen. */
    public void setCursor(int line, int column) {
        cursorLine = Mth.clamp(line, 0, lines.size() - 1);
        cursorColumn = Mth.clamp(column, 0, lines.get(cursorLine).length());
        clearSelection();
        ensureVisible();
    }

    public String text() {
        return String.join("\n", lines);
    }

    public final void setText(String text) {
        lines.clear();
        if (text == null || text.isEmpty()) {
            lines.add("");
        } else {
            for (String line : text.split("\n", -1)) {
                lines.add(line);
            }
        }
        cursorLine = 0;
        cursorColumn = 0;
        scrollLine = 0;
        scrollColumn = 0;
        // Ein neuer Text ist kein Schritt, den man rückgängig machen kann:
        // Er kommt vom Server oder vom Öffnen des Fensters, nicht vom Tippen.
        undoStack.clear();
        redoStack.clear();
        lastKind = null;
    }

    /**
     * Wie breit ein Zeichen ist, in Bildpunkten mit Nachkomma.
     *
     * <p><b>Hier steckt die ganze Vorsicht an der eigenen Schrift.</b>
     * {@code Font.width} rundet auf: Bei einem Vorschub von 5,4 Punkten kostet
     * jeder Aufruf bis zu 0,6 Punkte zu viel. Der Editor zeichnet Token für
     * Token und summierte diesen Fehler — nach acht Token stand der Text fünf
     * Punkte neben dem Cursor, und aus einem Bildschirmfoto wäre der Grund
     * nicht zu erraten gewesen.
     *
     * <p>Die Schrift ist dicktengleich. Also wird der Vorschub einmal
     * gemessen und danach nur noch multipliziert — jede Spalte landet an
     * derselben Stelle, egal wie viele Token davor lagen. So rechnet jeder
     * Code-Editor.
     */
    private float advance() {
        if (advance <= 0.0F) {
            // Ohne Schrift ein runder Wert. Das ist kein Notbehelf für den
            // Betrieb — dort gibt es immer eine —, sondern für den Test: Der
            // baut den Editor ohne Fenster, und seit der Cursor auch
            // waagerecht im Bild gehalten wird, rechnet jede Änderung mit dem
            // Vorschub. Gezeichnet wird dabei nichts, also ist jede Zahl
            // recht; sie darf nur nicht fehlen.
            if (font == null) {
                return 6.0F;
            }
            String probe = "M".repeat(32);
            advance = font.getSplitter().stringWidth(mono(probe)) / 32.0F;
        }
        return advance;
    }

    /** Wo eine Spalte anfängt — um den waagerechten Versatz verschoben. */
    private int columnX(int textX, int column) {
        return textX + Math.round((column - scrollColumn) * advance());
    }

    /** Wie viele Spalten nebeneinander passen. */
    private int visibleColumns() {
        return Math.max(8, (int) ((width - GUTTER_WIDTH - TEXT_LEFT - 6) / advance()));
    }

    private int widthOf(String text) {
        return Math.round(text.length() * advance());
    }

    private static net.minecraft.network.chat.Component mono(String text) {
        return dev.devpanda.factorynetwork.client.FnFonts.mono(text);
    }

    /** Schneidet auf eine Breite, ebenfalls in der Schrift des Editors. */
    private String plainSubstrByWidth(String text, int width) {
        int zeichen = (int) (width / advance());
        return zeichen >= text.length() ? text : text.substring(0, Math.max(0, zeichen));
    }

    private int visibleLines() {
        return Math.max(1, (height - 12) / LINE_HEIGHT);
    }

    // ---- Zeichnen ---------------------------------------------------------

    /**
     * Zeichnet den Ausschnitt.
     *
     * <p><b>Zwei Durchgänge, weil nur einer beschnitten werden darf.</b> Der
     * Bundsteg — Zeilennummern und Fehlermarken — steht links vom Text und
     * wandert beim waagerechten Schieben nicht mit; er muss also außerhalb
     * der Schere liegen. Der Text muss hinein, sonst zeichnet eine lange
     * Zeile über den Rand hinaus in das, was daneben liegt.
     *
     * <p>Und die Schere einmal um den ganzen Durchgang statt einmal je Zeile:
     * Jede Änderung am Beschnitt leert den Zeichenpuffer, und vierzig
     * Leerungen je Bild sind vierzig zu viel.
     */
    public void render(GuiGraphics graphics, List<Diagnostic> diagnostics) {
        int visible = visibleLines();
        int textX = x + GUTTER_WIDTH + TEXT_LEFT;
        int[] partner = matchingBracket();
        int last = Math.min(visible, lines.size() - scrollLine);

        // ---- Erster Durchgang: der Bundsteg, unbeschnitten ----
        for (int i = 0; i < last; i++) {
            int lineIndex = scrollLine + i;
            int lineY = y + 10 + i * LINE_HEIGHT;

            // Zeilennummer, gedämpft — sie soll da sein, aber nicht ablenken.
            // Die eigene heller: Sie ist der Bezugspunkt beim Aufsehen.
            String number = String.valueOf(lineIndex + 1);
            graphics.drawString(font, mono(number),
                    x + GUTTER_WIDTH - widthOf(number) - 2, lineY,
                    lineIndex == cursorLine
                            ? EditorColours.LINE_NUMBER_ACTIVE : EditorColours.COMMENT,
                    false);

            // Die Marke zu einer Meldung: Der Streifen im Text sagt, dass
            // etwas ist, aber nicht was. Die Marke ist der Ort, an dem die
            // Meldung hängt — beim Zeigen steht sie da.
            Diagnostic problem = diagnosticIn(diagnostics, lineIndex + 1);
            if (problem != null) {
                graphics.fill(x + 1, lineY - 1, x + 3, lineY + LINE_HEIGHT - 2,
                        problem.isError() ? 0xFFE88388 : 0xFFE8AC3E);
            }
        }

        // ---- Zweiter Durchgang: der Text, beschnitten ----
        graphics.enableScissor(x + GUTTER_WIDTH, y + 9, x + width - 2,
                y + 10 + visible * LINE_HEIGHT);
        for (int i = 0; i < last; i++) {
            int lineIndex = scrollLine + i;
            int lineY = y + 10 + i * LINE_HEIGHT;

            // Die Zeile unter dem Cursor bekommt einen Hauch Hintergrund.
            // Nicht bei einer Auswahl: Die ist schon eine Hinterlegung, und
            // zwei übereinander sind keine.
            if (lineIndex == cursorLine && !hasSelection()) {
                graphics.fill(x + GUTTER_WIDTH, lineY - 1, x + width - 2,
                        lineY + LINE_HEIGHT - 2, EditorColours.CURRENT_LINE);
            }

            drawIndentGuides(graphics, lineIndex, textX, lineY);

            // Die Auswahl liegt unter dem Text, sonst verschluckt sie ihn.
            drawSelection(graphics, lineIndex, textX, lineY);
            drawMatches(graphics, lineIndex, textX, lineY);
            drawBracket(graphics, lineIndex, textX, lineY, partner);

            // Zeilen mit Fehler bekommen einen Streifen statt einer Welle:
            // Wellen sind bei zehn Pixel Zeilenhöhe nicht zu erkennen.
            Diagnostic problem = diagnosticIn(diagnostics, lineIndex + 1);
            if (problem != null) {
                graphics.fill(x + GUTTER_WIDTH, lineY - 1, x + width - 2,
                        lineY + LINE_HEIGHT - 2,
                        problem.isError() ? EditorColours.ERROR_LINE
                                : EditorColours.WARNING_LINE);
            }

            drawHighlighted(graphics, lines.get(lineIndex), textX, lineY);
        }
        drawCursor(graphics, textX);
        graphics.disableScissor();

        drawColumnHint(graphics, visible);
        drawScrollHint(graphics, visible);
        drawSearchBar(graphics);
        // Beide stehen bewusst außerhalb der Schere: Sie dürfen über den
        // Textbereich hinausragen, das ist der Sinn eines Kastens.
        drawSignature(graphics, textX);
        drawSuggestions(graphics, textX);
    }

    /**
     * Die Form der Angabe, in der der Cursor steht.
     *
     * <p><b>Die Antwort auf „ich sehe nie, was ich wo angeben muss".</b> Eine
     * Vorschlagsliste sagt, welche Wörter es gibt; sie sagt nicht, dass hinter
     * {@code row} erst ein Text und dann ein Ausdruck kommt. Das stand nur in
     * der Grammatik, also neben dem Bildschirm.
     *
     * <p>Hier steht die ganze Form, und die Stelle, die gerade dran ist, ist
     * hervorgehoben. Sie erscheint von selbst und muss nicht angefordert
     * werden — sie ist kein Vorschlag, sondern eine Beschriftung.
     *
     * <p>Über der Zeile und nicht darunter: Darunter sitzt die
     * Vorschlagsliste, und zwei Kästen übereinander verdecken den Code, den
     * man gerade schreibt. Ganz oben rutscht sie nach unten, sonst stünde sie
     * außerhalb.
     */
    private void drawSignature(GuiGraphics graphics, int textX) {
        if (searching) {
            return;
        }
        var where = Completions.whereAt(lines, cursorLine, cursorColumn);
        if (where == null) {
            return;
        }
        int row = cursorLine - scrollLine;
        if (row < 0 || row >= visibleLines()) {
            return;
        }

        // Die Teile der Form: erst das Schlüsselwort, dann die Stellen. Eine
        // Stelle, die wegfallen darf, steht in eckigen Klammern — mitsamt
        // dem Wert, den sie einführt, so wie in der Grammatik.
        var slots = where.signature().slots();
        List<String> parts = new ArrayList<>();
        parts.add(where.signature().keyword());
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).optional() && i + 1 < slots.size()) {
                parts.add("[" + slots.get(i).label());
                parts.add(slots.get(i + 1).label() + "]");
                i++;
                continue;
            }
            parts.add(slots.get(i).label());
        }
        int active = where.slotIndex() + 1;

        // In einem niedrigen Fenster nur die Form ohne den Satz dazu: Im
        // Reiter des Terminals sind acht Zeilen sichtbar, und ein Kasten von
        // zwei Zeilen nähme davon ein Viertel.
        boolean roomy = height >= 120;
        String help = where.signature().help();
        int shapeWidth = widthOf(String.join(" ", parts));
        int boxWidth = Math.min(
                Math.max(shapeWidth, roomy ? widthOf(help) : 0) + 8, width - 4);
        int boxHeight = (roomy ? LINE_HEIGHT * 2 : LINE_HEIGHT) + 2;

        int boxX = Math.min(x + GUTTER_WIDTH, x + width - boxWidth - 2);
        int boxY = y + 10 + row * LINE_HEIGHT - boxHeight - 1;
        if (boxY < y + 10) {
            boxY = y + 10 + (row + 1) * LINE_HEIGHT + 1;
        }

        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xF0161C19);
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 1, 0xFF39443D);
        graphics.fill(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight,
                0xFF39443D);

        int at = boxX + 4;
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            // Die aktive Stelle hell und unterstrichen, die übrigen matt. Das
            // Schlüsselwort selbst ist nie aktiv — man steht immer dahinter.
            boolean current = i == active;
            graphics.drawString(font, mono(part), at, boxY + 2,
                    current ? EditorColours.SELECTOR
                            : i == 0 ? EditorColours.KEYWORD : EditorColours.COMMENT,
                    false);
            if (current) {
                graphics.fill(at, boxY + LINE_HEIGHT, at + widthOf(part),
                        boxY + LINE_HEIGHT + 1, 0xFF000000 | EditorColours.SELECTOR);
            }
            at += widthOf(part + " ");
        }
        if (roomy) {
            graphics.drawString(font, mono(plainSubstrByWidth(help, boxWidth - 8)),
                    boxX + 4, boxY + LINE_HEIGHT + 2, EditorColours.COMMENT, false);
        }
    }

    /** Färbt eine Zeile nach Token-Arten. */
    private void drawHighlighted(GuiGraphics graphics, String line, int startX, int lineY) {
        if (line.isEmpty()) {
            return;
        }
        int comment = line.indexOf("//");
        String code = comment >= 0 ? line.substring(0, comment) : line;

        // Gezeichnet wird nach Spalte, nicht nach aufsummierter Breite: Die
        // Schrift ist dicktengleich, also liegt jede Spalte fest.
        if (!code.isBlank()) {
            List<Token> tokens = Lexer.tokenize(code).tokens();
            int consumed = 0;
            for (Token token : tokens) {
                if (token.is(TokenType.EOF) || token.is(TokenType.NL)) {
                    continue;
                }
                int start = token.span().start();
                if (start > consumed && start <= code.length()) {
                    graphics.drawString(font, mono(code.substring(consumed, start)),
                            columnX(startX, consumed), lineY, EditorColours.TEXT, false);
                    consumed = start;
                }
                int end = Math.min(token.span().end(), code.length());
                if (end <= consumed) {
                    continue;
                }
                graphics.drawString(font, mono(code.substring(consumed, end)),
                        columnX(startX, consumed), lineY, colorFor(token), false);
                consumed = end;
            }
            if (consumed < code.length()) {
                graphics.drawString(font, mono(code.substring(consumed)),
                        columnX(startX, consumed), lineY, EditorColours.TEXT, false);
            }
        } else {
            graphics.drawString(font, mono(code), startX, lineY, EditorColours.TEXT, false);
        }

        if (comment >= 0) {
            graphics.drawString(font, mono(line.substring(comment)),
                    columnX(startX, comment), lineY, EditorColours.COMMENT, false);
        }
    }

    private static int colorFor(Token token) {
        TokenType type = token.type();
        if (type == TokenType.SELECTOR) {
            return EditorColours.SELECTOR;
        }
        if (type == TokenType.INVALID) {
            return EditorColours.ERROR;
        }
        if (type == TokenType.STRING || type == TokenType.INT || type == TokenType.FLOAT
                || type == TokenType.DURATION) {
            return EditorColours.MUTED;
        }
        // Ein Schlüsselwort erkennt man daran, dass seine Art zum Text passt.
        if (TokenType.keyword(token.text()) == type) {
            return EditorColours.KEYWORD;
        }
        return EditorColours.TEXT;
    }

    /**
     * Die erste Meldung zu einer Zeile, oder {@code null}.
     *
     * <p>Die erste und nicht alle: In eine Zeile passt eine Marke, und wer
     * den ersten Fehler behebt, sieht den zweiten von selbst. Ein Fehler
     * verschiebt außerdem oft alle folgenden.
     */
    public Diagnostic diagnosticIn(List<Diagnostic> diagnostics, int line) {
        return diagnostics.stream()
                .filter(diagnostic -> diagnostic.span().line() == line)
                .findFirst()
                .orElse(null);
    }

    /**
     * Was unter dem Zeiger steht — für die Erklärung beim Zeigen.
     *
     * <p>Die ganze Zeile zählt und nicht nur die Marke: Eine Marke ist zwei
     * Pixel breit, und niemand trifft zwei Pixel.
     */
    public Diagnostic diagnosticAt(List<Diagnostic> diagnostics, double mouseX,
                                   double mouseY) {
        if (!inside(mouseX, mouseY)) {
            return null;
        }
        return diagnosticIn(diagnostics, lineAt(mouseY) + 1);
    }

    /**
     * Die Form zum Schlüsselwort unter dem Zeiger, oder {@code null}.
     *
     * <p>Die Formzeile über dem Cursor sagt, was man gerade schreibt. Beim
     * <b>Lesen</b> hilft sie nicht: Da steht der Cursor woanders, und man
     * will wissen, was diese eine Zeile tut. Also dasselbe noch einmal für
     * den Mauszeiger.
     *
     * <p>Erkannt wird nur das erste Wort einer Zeile — das Schlüsselwort.
     * Was dahinter steht, sind Namen und Werte; die erklärt eine
     * Signaturtabelle nicht.
     */
    public dev.devpanda.factorynetwork.lang.Signatures.Signature signatureAt(double mouseX,
                                                                            double mouseY) {
        if (!inside(mouseX, mouseY)) {
            return null;
        }
        int lineIndex = lineAt(mouseY);
        String line = lines.get(lineIndex);
        String trimmed = line.trim();
        int wordEnd = trimmed.indexOf(' ');
        String keyword = wordEnd < 0 ? trimmed : trimmed.substring(0, wordEnd);
        if (keyword.isEmpty()) {
            return null;
        }
        // Nur, solange der Zeiger wirklich über dem Wort steht.
        int start = line.indexOf(keyword);
        int column = columnAt(lineIndex, mouseX);
        if (column < start || column > start + keyword.length()) {
            return null;
        }
        String block = Completions.enclosingBlock(lines, lineIndex);
        return dev.devpanda.factorynetwork.lang.Signatures.find(block, keyword);
    }

    /**
     * Das ganze Wort unter dem Zeiger, oder leer.
     *
     * <p>Für den Sprung zur Erklärung. Ein Wort ist hier, was auch ein Name
     * sein darf: Buchstaben, Ziffern, Unterstrich.
     */
    public String wordAt(double mouseX, double mouseY) {
        if (!inside(mouseX, mouseY)) {
            return "";
        }
        int lineIndex = lineAt(mouseY);
        String line = lines.get(lineIndex);
        int column = columnAt(lineIndex, mouseX);
        if (column >= line.length() || !isWordChar(line.charAt(column))) {
            // Einen Schritt zurück: Wer hinter das letzte Zeichen eines
            // Wortes klickt, meint das Wort.
            column--;
        }
        if (column < 0 || column >= line.length() || !isWordChar(line.charAt(column))) {
            return "";
        }
        int from = column;
        while (from > 0 && isWordChar(line.charAt(from - 1))) {
            from--;
        }
        int to = column;
        while (to + 1 < line.length() && isWordChar(line.charAt(to + 1))) {
            to++;
        }
        return line.substring(from, to + 1);
    }

    /** Springt an eine Zeile und Spalte, beide von eins an gezählt. */
    public void jumpTo(int line, int column) {
        setCursor(line - 1, Math.max(0, column - 1));
    }

    /** Setzt den Cursor auf die Stelle einer Meldung. */
    public void jumpTo(Diagnostic diagnostic) {
        setCursor(diagnostic.span().line() - 1, Math.max(0, diagnostic.span().column() - 1));
    }

    private void drawCursor(GuiGraphics graphics, int textX) {
        long now = System.currentTimeMillis();
        if (now - lastBlink > 500) {
            cursorVisible = !cursorVisible;
            lastBlink = now;
        }
        if (!cursorVisible) {
            return;
        }
        int row = cursorLine - scrollLine;
        if (row < 0 || row >= visibleLines()) {
            return;
        }
        String line = lines.get(cursorLine);
        int column = Math.min(cursorColumn, line.length());
        int cursorX = columnX(textX, column);
        int cursorY = y + 10 + row * LINE_HEIGHT;
        // <b>Mit Alphakanal.</b> Die Farben in EditorColours sind für Text
        // gedacht und stehen ohne einen da; Minecrafts Schriftsatz ergänzt
        // ihn selbst, wenn er fehlt. Eine Füllung tut das nicht — der Cursor
        // war deshalb durchsichtig, seit es ihn gibt.
        graphics.fill(cursorX, cursorY - 1, cursorX + 1, cursorY + LINE_HEIGHT - 2,
                0xFF000000 | EditorColours.CURSOR);
    }

    /**
     * Der waagerechte Balken, wenn eine Zeile breiter ist als das Fenster.
     *
     * <p>Nur dann: Ein Balken, der immer da ist und immer voll, sagt nichts
     * und kostet eine Zeile.
     */
    private void drawColumnHint(GuiGraphics graphics, int visible) {
        int longest = longestLine();
        int columns = visibleColumns();
        if (longest <= columns) {
            return;
        }
        int trackLeft = x + GUTTER_WIDTH;
        int trackWidth = x + width - 4 - trackLeft;
        int thumbWidth = Math.max(12, trackWidth * columns / longest);
        int maxScroll = longest - columns;
        int offset = maxScroll == 0 ? 0
                : (trackWidth - thumbWidth) * Math.min(scrollColumn, maxScroll) / maxScroll;
        int barY = y + 9 + visible * LINE_HEIGHT;
        graphics.fill(trackLeft + offset, barY, trackLeft + offset + thumbWidth, barY + 1,
                0xFF000000 | EditorColours.COMMENT);
    }

    private void drawScrollHint(GuiGraphics graphics, int visible) {
        if (lines.size() <= visible) {
            return;
        }
        int trackTop = y + 10;
        int trackHeight = visible * LINE_HEIGHT;
        int thumbHeight = Math.max(8, trackHeight * visible / lines.size());
        int maxScroll = lines.size() - visible;
        int offset = maxScroll == 0 ? 0 : (trackHeight - thumbHeight) * scrollLine / maxScroll;
        graphics.fill(x + width - 3, trackTop + offset, x + width - 1,
                trackTop + offset + thumbHeight, 0xFF000000 | EditorColours.COMMENT);
    }

    // ---- Eingabe ----------------------------------------------------------

    /**
     * Die Tastenbefehle, die jeder Editor hat.
     *
     * <p>Sie stehen vor allem anderen: Wer Strg und C drückt, will kopieren —
     * und nicht ein C tippen, weil die Abfrage weiter unten stand.
     */
    private boolean handleShortcut(int key) {
        if (!net.minecraft.client.gui.screens.Screen.hasControlDown()) {
            return false;
        }
        switch (key) {
            case 65 -> { // A: alles auswählen
                anchorLine = 0;
                anchorColumn = 0;
                cursorLine = lines.size() - 1;
                cursorColumn = lines.get(cursorLine).length();
                return true;
            }
            case 67 -> { // C: kopieren
                if (hasSelection()) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(selectedText());
                }
                return true;
            }
            case 88 -> { // X: ausschneiden
                if (hasSelection()) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(selectedText());
                    remember(EditKind.STRUCTURAL);
                    deleteSelection();
                }
                return true;
            }
            case 86 -> { // V: einfügen
                String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clipboard != null && !clipboard.isEmpty()) {
                    remember(EditKind.STRUCTURAL);
                    insertMultiline(clipboard);
                    clearSuggestions();
                }
                return true;
            }
            case 89, 90 -> {
                // Beide Tasten machen rückgängig, mit Umschalt wieder vorwärts.
                //
                // GLFW meldet die Taste nach ihrer Lage auf einer
                // US-Tastatur. Auf einer deutschen liegt dort, wo „Z" steht,
                // die Meldung „Y" — Strg+Z und Strg+Y wären damit je nach
                // Belegung vertauscht. Beide auf dasselbe zu legen ist die
                // Auflösung, die auf jeder Belegung das Erwartete tut.
                if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                    redo();
                } else {
                    undo();
                }
                return true;
            }
            case 32 -> {
                // Leertaste: um Vorschläge bitten.
                //
                // <b>Der Griff, ohne den eine Vervollständigung nicht
                // benutzbar ist.</b> Vorher erschien die Liste nur beim
                // Tippen und war weg, sobald man sie einmal weggeklickt
                // hatte — bis zum nächsten Anschlag. Wer wissen wollte, was
                // an einer Stelle erlaubt ist, musste ein Zeichen tippen und
                // wieder löschen.
                updateSuggestions();
                return true;
            }
            case 70 -> { // F: suchen
                if (searching && !replacing) {
                    closeSearch();
                } else {
                    replacing = false;
                    openSearch();
                }
                return true;
            }
            case 72 -> { // H: suchen und ersetzen
                if (searching && replacing) {
                    closeSearch();
                } else {
                    replacing = true;
                    openSearch();
                }
                return true;
            }
            case 68 -> { // D: Zeile verdoppeln
                remember(EditKind.STRUCTURAL);
                duplicateLine();
                return true;
            }
            case 259 -> { // Rücktaste: ein ganzes Wort
                if (hasSelection() || cursorColumn > 0 || cursorLine > 0) {
                    remember(EditKind.DELETING);
                }
                if (hasSelection()) {
                    deleteSelection();
                } else {
                    deleteWordLeft();
                }
                return true;
            }
            case 263 -> { // links: ein Wort
                move(this::moveWordLeft);
                return true;
            }
            case 262 -> { // rechts: ein Wort
                move(this::moveWordRight);
                return true;
            }
            case 268 -> { // Pos1: an den Anfang des Programms
                move(() -> {
                    cursorLine = 0;
                    cursorColumn = 0;
                    ensureVisible();
                });
                return true;
            }
            case 269 -> { // Ende: ans Ende des Programms
                move(() -> {
                    cursorLine = lines.size() - 1;
                    cursorColumn = lines.get(cursorLine).length();
                    ensureVisible();
                });
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Bewegt den Cursor und führt dabei die Auswahl nach.
     *
     * <p>Eine offene Vorschlagsliste geht dabei zu: Sie galt für die Stelle,
     * an der sie aufging. Sie stehen zu lassen hieße, dass sie nach zwei
     * Schritten nach links etwas anbietet, das dort nicht mehr passt. Die
     * Formzeile bleibt — die wird bei jedem Bild neu bestimmt.
     */
    private void move(Runnable movement) {
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            anchorIfNeeded();
        } else {
            clearSelection();
        }
        clearSuggestions();
        movement.run();
        // Danach und nicht in den Bewegungen selbst: moveRight innerhalb einer
        // Zeile rief es nicht, und genau dort läuft man aus dem Bild.
        ensureVisible();
    }

    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (readOnly) {
            return readOnlyKey(key, scanCode, modifiers);
        }
        if (handleShortcut(key)) {
            return true;
        }
        // Solange die Suche offen ist, gehören ihr die Tasten: Wer sucht,
        // tippt ein Wort und keinen Code.
        if (searching && handleSearchKey(key)) {
            return true;
        }
        // Solange Vorschläge offen sind, gehören ihnen Tab, Pfeile und Escape.
        if (!suggestions.isEmpty()) {
            switch (key) {
                case 258 -> { // Tabulator übernimmt
                    applySuggestion();
                    return true;
                }
                case 264 -> { // runter
                    selectedSuggestion = (selectedSuggestion + 1) % suggestions.size();
                    return true;
                }
                case 265 -> { // hoch
                    selectedSuggestion =
                            (selectedSuggestion - 1 + suggestions.size()) % suggestions.size();
                    return true;
                }
                case 256 -> { // Escape schließt nur die Liste, nicht den Editor
                    clearSuggestions();
                    return true;
                }
                default -> { }
            }
        }
        switch (key) {
            case 259 -> { // Rücktaste
                // Am Anfang des Textes passiert nichts. Es trotzdem zu merken
                // kostete einen Schritt Geschichte und löschte den Weg
                // vorwärts — für einen Anschlag, der nichts getan hat.
                if (hasSelection() || cursorColumn > 0 || cursorLine > 0) {
                    remember(EditKind.DELETING);
                }
                if (hasSelection()) {
                    deleteSelection();
                    return true;
                }
                backspace();
                return true;
            }
            case 261 -> { // Entfernen
                boolean atEnd = cursorLine == lines.size() - 1
                        && cursorColumn >= lines.get(cursorLine).length();
                if (hasSelection() || !atEnd) {
                    remember(EditKind.DELETING);
                }
                if (hasSelection()) {
                    deleteSelection();
                    return true;
                }
                delete();
                return true;
            }
            case 257, 335 -> { // Eingabe
                remember(EditKind.STRUCTURAL);
                newLine();
                return true;
            }
            case 258 -> { // Tabulator: vier Leerzeichen, nie ein Tabulatorzeichen
                remember(EditKind.STRUCTURAL);
                boolean shift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
                if (shift || hasSelection()) {
                    indentLines(shift);
                } else {
                    insert("    ");
                }
                return true;
            }
            case 263 -> { // links
                move(this::moveLeft);
                return true;
            }
            case 262 -> { // rechts
                move(this::moveRight);
                return true;
            }
            case 265 -> { // hoch
                move(() -> moveVertically(-1));
                return true;
            }
            case 264 -> { // runter
                move(() -> moveVertically(1));
                return true;
            }
            case 268 -> { // Pos1
                move(() -> cursorColumn = 0);
                return true;
            }
            case 269 -> { // Ende
                move(() -> cursorColumn = lines.get(cursorLine).length());
                return true;
            }
            case 266 -> { // Bild hoch
                moveVertically(-visibleLines());
                return true;
            }
            case 267 -> { // Bild runter
                moveVertically(visibleLines());
                return true;
            }
            default -> {
                if (Minecraft.getInstance().options.keyDrop.matches(key, scanCode)) {
                    return false;
                }
                return false;
            }
        }
    }

    /**
     * Tasten in einer gesperrten Datei.
     *
     * <p>Was nichts ändert, geht durch: Pfeile, Sprünge, Auswahl, Kopieren,
     * Suchen. Alles andere wird verschluckt — <b>und zwar verschluckt und
     * nicht durchgereicht</b>, sonst schlösse die Inventartaste das Fenster
     * über einer Datei, in der man gerade liest.
     */
    private boolean readOnlyKey(int key, int scanCode, int modifiers) {
        boolean control = net.minecraft.client.gui.screens.Screen.hasControlDown();
        if (control && (key == 67 || key == 65)) { // C und A
            if (key == 65) {
                anchorLine = 0;
                anchorColumn = 0;
                cursorLine = lines.size() - 1;
                cursorColumn = lines.get(cursorLine).length();
            } else if (hasSelection()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(selectedText());
            }
            return true;
        }
        if (control && key == 70) { // F: suchen
            if (searching) {
                closeSearch();
            } else {
                replacing = false;
                openSearch();
            }
            return true;
        }
        if (searching && handleSearchKey(key)) {
            return true;
        }
        switch (key) {
            case 263 -> move(control ? this::moveWordLeft : this::moveLeft);
            case 262 -> move(control ? this::moveWordRight : this::moveRight);
            case 265 -> move(() -> moveVertically(-1));
            case 264 -> move(() -> moveVertically(1));
            case 266 -> move(() -> moveVertically(-visibleLines()));
            case 267 -> move(() -> moveVertically(visibleLines()));
            case 268 -> move(() -> {
                if (control) {
                    cursorLine = 0;
                }
                cursorColumn = 0;
            });
            case 269 -> move(() -> {
                if (control) {
                    cursorLine = lines.size() - 1;
                }
                cursorColumn = lines.get(cursorLine).length();
            });
            case 256 -> {
                return false;
            }
            default -> { }
        }
        return true;
    }

    public boolean charTyped(char character, int modifiers) {
        if (readOnly && !searching) {
            // Verschluckt und nicht durchgereicht: Sonst löste ein „e" in
            // einer gesperrten Datei das Inventar aus.
            return true;
        }
        if (character == '\n' || character == '\r') {
            return false;
        }
        if (Character.isISOControl(character)) {
            return false;
        }
        if (searching) {
            if (editingReplacement) {
                replaceTerm = replaceTerm + character;
            } else {
                setSearchTerm(searchTerm + character);
            }
            return true;
        }
        remember(EditKind.TYPING);
        if (!typePair(character)) {
            insert(String.valueOf(character));
        }
        return true;
    }

    /**
     * Klammern und Anführungszeichen schließen sich selbst.
     *
     * <p>Zwei Fälle, und der zweite ist der wichtigere: Wer eine
     * schließende Klammer tippt, obwohl sie schon dasteht, will darüber
     * hinweg und nicht zwei davon. Ohne das wäre die Selbstergänzung
     * lästiger als hilfreich.
     *
     * <p>Ergänzt wird nur, wenn rechts vom Cursor nichts Handfestes mehr
     * steht — Zeilenende, Leerzeichen oder eine andere schließende Klammer.
     * Wer mitten in ein Wort tippt, meint das eine Zeichen.
     *
     * @return ob der Fall behandelt wurde
     */
    private boolean typePair(char character) {
        if (hasSelection()) {
            return false;
        }
        String line = lines.get(cursorLine);
        int column = Math.min(cursorColumn, line.length());
        char next = column < line.length() ? line.charAt(column) : '\0';

        // Über ein bereits stehendes Gegenstück hinweg.
        if ((CLOSERS.indexOf(character) >= 0 || character == '"') && next == character) {
            cursorColumn = column + 1;
            return true;
        }
        int opener = OPENERS.indexOf(character);
        boolean quote = character == '"';
        if (opener < 0 && !quote) {
            return false;
        }
        if (next != '\0' && next != ' ' && CLOSERS.indexOf(next) < 0) {
            return false;
        }
        char closing = quote ? '"' : CLOSERS.charAt(opener);
        insert(String.valueOf(character) + closing);
        cursorColumn--;
        return true;
    }

    /** Rollen schiebt den Text; mit Umschalt zur Seite. */
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!inside(mouseX, mouseY)) {
            return false;
        }
        int step = (int) Math.signum(delta) * 3;
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            int max = Math.max(0, longestLine() - visibleColumns());
            scrollColumn = Mth.clamp(scrollColumn - step * 2, 0, max);
            return true;
        }
        int max = Math.max(0, lines.size() - visibleLines());
        scrollLine = Mth.clamp(scrollLine - step, 0, max);
        return true;
    }

    // ---- Bearbeiten -------------------------------------------------------

    /**
     * Zeichnet den ausgewählten Bereich einer Zeile.
     *
     * <p>Eine Auswahl ohne Anzeige ist schlimmer als keine: Man weiss nicht,
     * was das nächste Zeichen ersetzt.
     */
    /**
     * Die senkrechten Striche der Einrückung.
     *
     * <p>Einer je vier Leerzeichen, aber nicht in der Spalte null: Der linke
     * Rand ist schon eine Kante. Für eine leere Zeile gilt die Einrückung
     * der Umgebung — sonst reißt jede Leerzeile alle Striche auf, und dann
     * beantworten sie die Frage nicht mehr, für die sie da sind.
     */
    private void drawIndentGuides(GuiGraphics graphics, int lineIndex, int textX, int lineY) {
        int indent = guideIndentAt(lineIndex);
        for (int column = INDENT; column < indent; column += INDENT) {
            int guideX = columnX(textX, column);
            graphics.fill(guideX, lineY - 1, guideX + 1, lineY + LINE_HEIGHT - 2,
                    EditorColours.INDENT_GUIDE);
        }
    }

    private int guideIndentAt(int lineIndex) {
        if (!lines.get(lineIndex).isBlank()) {
            return indentOf(lines.get(lineIndex));
        }
        // Eine leere Zeile erbt, was um sie herum steht — der kleinere der
        // beiden Nachbarn, damit ein Strich nicht ins Leere weiterläuft.
        int above = 0;
        for (int i = lineIndex - 1; i >= 0; i--) {
            if (!lines.get(i).isBlank()) {
                above = indentOf(lines.get(i));
                break;
            }
        }
        int below = 0;
        for (int i = lineIndex + 1; i < lines.size(); i++) {
            if (!lines.get(i).isBlank()) {
                below = indentOf(lines.get(i));
                break;
            }
        }
        return Math.min(above, below);
    }

    private static int indentOf(String line) {
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ') {
            indent++;
        }
        return indent;
    }

    // ---- Klammernpaare ----------------------------------------------------

    private static final String OPENERS = "{([";
    private static final String CLOSERS = "})]";

    /** Steht hier ein Paar ohne Inhalt — {@code ()}, {@code []}, {@code ""}? */
    private static boolean isEmptyPair(char before, char after) {
        if (before == '"') {
            return after == '"';
        }
        int opener = OPENERS.indexOf(before);
        return opener >= 0 && CLOSERS.charAt(opener) == after;
    }

    /**
     * Sucht das Gegenstück zur Klammer am Cursor.
     *
     * <p>Zuerst rechts vom Cursor, dann links: Wer gerade eine schließende
     * Klammer getippt hat, steht dahinter und meint sie. Gefunden wird über
     * eine Tiefenzählung, die Zeichenketten und Kommentare überspringt —
     * eine geschweifte Klammer in einem Text ist keine.
     *
     * @return vier Zahlen — Zeile und Spalte der Klammer am Cursor, dann die
     *         des Gegenstücks — oder {@code null}
     */
    private int[] matchingBracket() {
        String line = lines.get(cursorLine);
        int column = Math.min(cursorColumn, line.length());
        int found = bracketAt(line, column);
        if (found < 0 && column > 0) {
            found = bracketAt(line, column - 1);
            if (found >= 0) {
                column--;
            }
        }
        if (found < 0) {
            return null;
        }
        char bracket = line.charAt(column);
        boolean forward = OPENERS.indexOf(bracket) >= 0;
        char partner = forward
                ? CLOSERS.charAt(OPENERS.indexOf(bracket))
                : OPENERS.charAt(CLOSERS.indexOf(bracket));
        int[] hit = scanFor(cursorLine, column, bracket, partner, forward);
        if (hit == null) {
            return null;
        }
        return new int[] {cursorLine, column, hit[0], hit[1]};
    }

    /** Steht an dieser Stelle eine Klammer, die zählt? */
    private static int bracketAt(String line, int column) {
        if (column < 0 || column >= line.length()) {
            return -1;
        }
        char at = line.charAt(column);
        if (OPENERS.indexOf(at) < 0 && CLOSERS.indexOf(at) < 0) {
            return -1;
        }
        return inCode(line, column) ? column : -1;
    }

    /**
     * Liegt diese Spalte im Code — also nicht in einem Text und nicht hinter
     * einem Kommentarzeichen?
     */
    private static boolean inCode(String line, int column) {
        boolean inString = false;
        for (int i = 0; i < column && i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inString = !inString;
            } else if (!inString && c == '/' && i + 1 < line.length()
                    && line.charAt(i + 1) == '/') {
                return false;
            }
        }
        return !inString;
    }

    /**
     * Zählt sich durch das Dokument, bis die Tiefe wieder null ist.
     *
     * <p>Über Zeilengrenzen hinweg: Ein {@code fn}-Block geht über zwanzig
     * Zeilen, und genau dort will man wissen, wo er endet.
     */
    private int[] scanFor(int fromLine, int fromColumn, char bracket, char partner,
                          boolean forward) {
        int depth = 0;
        int lineIndex = fromLine;
        int column = fromColumn;
        // Eine Obergrenze, damit ein Dokument ohne Gegenstück nicht bei
        // jedem Bild ganz durchlaufen wird.
        int budget = 20_000;
        while (lineIndex >= 0 && lineIndex < lines.size() && budget-- > 0) {
            String line = lines.get(lineIndex);
            while (column >= 0 && column < line.length()) {
                char at = line.charAt(column);
                if ((at == bracket || at == partner) && inCode(line, column)) {
                    depth += at == bracket ? 1 : -1;
                    if (depth == 0) {
                        return new int[] {lineIndex, column};
                    }
                }
                column += forward ? 1 : -1;
            }
            lineIndex += forward ? 1 : -1;
            if (lineIndex < 0 || lineIndex >= lines.size()) {
                return null;
            }
            column = forward ? 0 : lines.get(lineIndex).length() - 1;
        }
        return null;
    }

    /** Hinterlegt beide Klammern eines Paares. */
    private void drawBracket(GuiGraphics graphics, int lineIndex, int textX, int lineY,
                             int[] partner) {
        if (partner == null) {
            return;
        }
        for (int pair = 0; pair < 2; pair++) {
            if (partner[pair * 2] != lineIndex) {
                continue;
            }
            int column = partner[pair * 2 + 1];
            int left = columnX(textX, column);
            int right = columnX(textX, column + 1);
            graphics.fill(left, lineY - 1, right, lineY + LINE_HEIGHT - 2,
                    EditorColours.BRACKET);
        }
    }

    private void drawSelection(GuiGraphics graphics, int lineIndex, int textX, int lineY) {
        if (!hasSelection()) {
            return;
        }
        int[] b = selectionBounds();
        if (lineIndex < b[0] || lineIndex > b[2]) {
            return;
        }
        String line = lines.get(lineIndex);
        int from = lineIndex == b[0] ? Math.min(b[1], line.length()) : 0;
        int to = lineIndex == b[2] ? Math.min(b[3], line.length()) : line.length();
        int left = columnX(textX, from);
        // Eine leere Zeile mitten in der Auswahl bekommt einen schmalen
        // Streifen — sonst sieht es aus, als wäre sie nicht mit dabei.
        int right = from == to && lineIndex != b[2]
                ? left + 4
                : columnX(textX, to);
        graphics.fill(left, lineY - 1, Math.min(right, x + width - 2),
                lineY + LINE_HEIGHT - 2, EditorColours.SELECTION);
    }

    // ---- Auswahl ----------------------------------------------------------

    public boolean hasSelection() {
        return anchorLine >= 0
                && (anchorLine != cursorLine || anchorColumn != cursorColumn);
    }

    /** Setzt den Anker, falls noch keiner steht — für Umschalt-Bewegungen. */
    private void anchorIfNeeded() {
        if (anchorLine < 0) {
            anchorLine = cursorLine;
            anchorColumn = cursorColumn;
        }
    }

    private void clearSelection() {
        anchorLine = -1;
    }

    /** Anfang und Ende der Auswahl, in Leserichtung sortiert. */
    private int[] selectionBounds() {
        int startLine = anchorLine;
        int startColumn = anchorColumn;
        int endLine = cursorLine;
        int endColumn = cursorColumn;
        if (startLine > endLine || (startLine == endLine && startColumn > endColumn)) {
            int swapLine = startLine;
            int swapColumn = startColumn;
            startLine = endLine;
            startColumn = endColumn;
            endLine = swapLine;
            endColumn = swapColumn;
        }
        return new int[] {startLine, startColumn, endLine, endColumn};
    }

    public String selectedText() {
        if (!hasSelection()) {
            return "";
        }
        int[] b = selectionBounds();
        if (b[0] == b[2]) {
            String line = lines.get(b[0]);
            return line.substring(Math.min(b[1], line.length()),
                    Math.min(b[3], line.length()));
        }
        StringBuilder out = new StringBuilder();
        String first = lines.get(b[0]);
        out.append(first.substring(Math.min(b[1], first.length())));
        for (int i = b[0] + 1; i < b[2]; i++) {
            out.append(NEWLINE).append(lines.get(i));
        }
        String last = lines.get(b[2]);
        out.append(NEWLINE).append(last, 0, Math.min(b[3], last.length()));
        return out.toString();
    }

    /** Löscht die Auswahl und setzt den Cursor an ihren Anfang. */
    private void deleteSelection() {
        if (!hasSelection()) {
            return;
        }
        int[] b = selectionBounds();
        String first = lines.get(b[0]);
        String last = lines.get(b[2]);
        String head = first.substring(0, Math.min(b[1], first.length()));
        String tail = last.substring(Math.min(b[3], last.length()));
        for (int i = b[2]; i > b[0]; i--) {
            lines.remove(i);
        }
        lines.set(b[0], head + tail);
        cursorLine = b[0];
        cursorColumn = head.length();
        clearSelection();
        changed();
    }

    /**
     * Fügt Text ein, der Zeilenumbrüche enthalten darf.
     *
     * <p>Das ist der Grund, warum es die Zwischenablage überhaupt braucht:
     * Ein Programm aus der Dokumentation abzutippen ist die Sorte Arbeit, die
     * niemand zweimal macht.
     */
    private void insertMultiline(String text) {
        if (hasSelection()) {
            deleteSelection();
        }
        // Fremde Zeilenenden und Tabulatoren vereinheitlichen: Wer aus einem
        // Editor kopiert, bringt sonst Steuerzeichen mit, die hier nichts
        // verloren haben.
        String cleaned = normalise(text);
        String[] parts = cleaned.split("\n", -1);
        if (parts.length == 1) {
            insert(parts[0]);
            return;
        }
        String line = lines.get(cursorLine);
        int column = Math.min(cursorColumn, line.length());
        String before = line.substring(0, column);
        String after = line.substring(column);

        lines.set(cursorLine, before + parts[0]);
        for (int i = 1; i < parts.length; i++) {
            lines.add(cursorLine + i, parts[i]);
        }
        int last = cursorLine + parts.length - 1;
        cursorColumn = lines.get(last).length();
        lines.set(last, lines.get(last) + after);
        cursorLine = last;
        changed();
    }

    private void insert(String text) {
        if (hasSelection()) {
            deleteSelection();
        }
        String line = lines.get(cursorLine);
        int column = Math.min(cursorColumn, line.length());
        lines.set(cursorLine, line.substring(0, column) + text + line.substring(column));
        cursorColumn = column + text.length();
        changed();
    }

    void newLine() {
        String line = lines.get(cursorLine);
        int column = Math.min(cursorColumn, line.length());
        String before = line.substring(0, column);
        String after = line.substring(column);

        // Einrückung der Zeile übernehmen, und nach { eine Stufe mehr.
        int indent = 0;
        while (indent < before.length() && before.charAt(indent) == ' ') {
            indent++;
        }
        boolean opened = before.stripTrailing().endsWith("{");
        if (opened) {
            indent += INDENT;
        }
        String padding = " ".repeat(indent);

        lines.set(cursorLine, before);
        lines.add(cursorLine + 1, padding + after);
        // Steht der Cursor zwischen den Klammern, kommt die schließende auf
        // eine eigene Zeile und der Cursor dazwischen — die Form, in der man
        // einen Block sowieso geschrieben hätte.
        if (opened && after.stripLeading().startsWith("}")) {
            lines.set(cursorLine + 1, padding);
            lines.add(cursorLine + 2, " ".repeat(indent - INDENT) + after.stripLeading());
        }
        cursorLine++;
        cursorColumn = padding.length();
        ensureVisible();
        changed();
    }

    void backspace() {
        String line = lines.get(cursorLine);
        if (cursorColumn > 0) {
            int column = Math.min(cursorColumn, line.length());
            // Vier Leerzeichen am Zeilenanfang gehen gemeinsam weg.
            int remove = 1;
            if (column >= INDENT && line.substring(0, column).endsWith("    ")
                    && line.substring(0, column).isBlank()) {
                remove = INDENT;
            }
            // Und ein leeres Paar auch: Was der Editor zusammen gesetzt hat,
            // nimmt er zusammen wieder weg. Sonst bleibt nach jedem Vertipper
            // eine einsame Klammer stehen.
            int after = column;
            if (column < line.length() && isEmptyPair(line.charAt(column - 1),
                    line.charAt(column))) {
                after = column + 1;
            }
            lines.set(cursorLine, line.substring(0, column - remove) + line.substring(after));
            cursorColumn = column - remove;
        } else if (cursorLine > 0) {
            String previous = lines.get(cursorLine - 1);
            cursorColumn = previous.length();
            lines.set(cursorLine - 1, previous + line);
            lines.remove(cursorLine);
            cursorLine--;
        } else {
            return;
        }
        ensureVisible();
        changed();
    }

    private void delete() {
        String line = lines.get(cursorLine);
        if (cursorColumn < line.length()) {
            lines.set(cursorLine,
                    line.substring(0, cursorColumn) + line.substring(cursorColumn + 1));
        } else if (cursorLine < lines.size() - 1) {
            lines.set(cursorLine, line + lines.get(cursorLine + 1));
            lines.remove(cursorLine + 1);
        } else {
            return;
        }
        changed();
    }

    private void moveLeft() {
        if (cursorColumn > 0) {
            cursorColumn--;
        } else if (cursorLine > 0) {
            cursorLine--;
            cursorColumn = lines.get(cursorLine).length();
            ensureVisible();
        }
    }

    private void moveRight() {
        if (cursorColumn < lines.get(cursorLine).length()) {
            cursorColumn++;
        } else if (cursorLine < lines.size() - 1) {
            cursorLine++;
            cursorColumn = 0;
            ensureVisible();
        }
    }

    private void moveVertically(int delta) {
        cursorLine = Mth.clamp(cursorLine + delta, 0, lines.size() - 1);
        cursorColumn = Math.min(cursorColumn, lines.get(cursorLine).length());
        ensureVisible();
    }

    /**
     * Holt den Cursor zurück in den Ausschnitt — senkrecht und waagerecht.
     *
     * <p>Waagerecht mit einem Rand von drei Spalten: Stünde der Cursor beim
     * Tippen genau am Rand, spränge der Ausschnitt bei jedem Zeichen, und man
     * schriebe gegen eine Wand.
     */
    private void ensureVisible() {
        int visible = visibleLines();
        if (cursorLine < scrollLine) {
            scrollLine = cursorLine;
        } else if (cursorLine >= scrollLine + visible) {
            scrollLine = cursorLine - visible + 1;
        }
        scrollLine = Mth.clamp(scrollLine, 0, Math.max(0, lines.size() - visible));

        int columns = visibleColumns();
        int margin = 3;
        if (cursorColumn < scrollColumn + margin) {
            scrollColumn = Math.max(0, cursorColumn - margin);
        } else if (cursorColumn >= scrollColumn + columns - margin) {
            scrollColumn = cursorColumn - columns + margin + 1;
        }
        scrollColumn = Math.max(0, scrollColumn);
    }

    /** Die längste Zeile, in Zeichen — für den waagerechten Balken. */
    private int longestLine() {
        int longest = 0;
        for (String line : lines) {
            longest = Math.max(longest, line.length());
        }
        return longest;
    }

    private void changed() {
        cursorVisible = true;
        lastBlink = System.currentTimeMillis();
        ensureVisible();
        updateSuggestions();
        changeListener.accept(text());
    }

    // ---- Vorschläge -------------------------------------------------------

    private void updateSuggestions() {
        if (readOnly) {
            return;
        }
        suggestions = Completions.at(lines, cursorLine, cursorColumn);
        selectedSuggestion = 0;
    }

    private void clearSuggestions() {
        suggestions = List.of();
        selectedSuggestion = 0;
    }

    /** Übernimmt den ausgewählten Vorschlag und ersetzt das angefangene Wort. */
    private void applySuggestion() {
        if (suggestions.isEmpty() || readOnly) {
            return;
        }
        Completions.Entry entry = suggestions.get(selectedSuggestion);
        String line = lines.get(cursorLine);
        int column = Math.min(cursorColumn, line.length());
        String word = Completions.currentWord(line.substring(0, column));
        int start = column - word.length();
        lines.set(cursorLine, line.substring(0, start) + entry.insert() + line.substring(column));
        cursorColumn = start + entry.insert().length();
        clearSuggestions();
        changeListener.accept(text());
    }

    private void drawSuggestions(GuiGraphics graphics, int textX) {
        if (suggestions.isEmpty()) {
            return;
        }
        int row = cursorLine - scrollLine;
        if (row < 0 || row >= visibleLines()) {
            return;
        }
        String line = lines.get(cursorLine);
        int column = Math.min(cursorColumn, line.length());
        String word = Completions.currentWord(line.substring(0, column));
        int listX = columnX(textX, column - word.length());
        int listY = y + 10 + (row + 1) * LINE_HEIGHT;

        // Breit genug für Wort und Form nebeneinander: „row" allein ist ein
        // Wort, „row  string expr" ist eine Anleitung.
        int listWidth = 0;
        for (Completions.Entry entry : suggestions) {
            int needed = widthOf(entry.text()) + 8;
            if (!entry.detail().isEmpty()) {
                needed += widthOf(entry.detail()) + 12;
            }
            listWidth = Math.max(listWidth, needed);
        }
        listWidth = Math.min(listWidth, width - 8);
        int listHeight = suggestions.size() * LINE_HEIGHT + 2;

        // Nach oben klappen, wenn unten kein Platz mehr ist.
        if (listY + listHeight > y + height) {
            listY = y + 10 + row * LINE_HEIGHT - listHeight;
        }
        listX = Math.min(listX, x + width - listWidth - 2);

        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, 0xF0202429);
        graphics.fill(listX, listY, listX + listWidth, listY + 1, 0xFF3C4147);
        graphics.fill(listX, listY + listHeight - 1, listX + listWidth, listY + listHeight,
                0xFF3C4147);

        for (int i = 0; i < suggestions.size(); i++) {
            Completions.Entry entry = suggestions.get(i);
            int entryY = listY + 1 + i * LINE_HEIGHT;
            if (i == selectedSuggestion) {
                graphics.fill(listX + 1, entryY - 1, listX + listWidth - 1,
                        entryY + LINE_HEIGHT - 1, 0xFF31363C);
            }
            graphics.drawString(font, mono(plainSubstrByWidth(entry.text(), listWidth - 8)),
                    listX + 4, entryY, colorForKind(entry.kind()), false);
            if (entry.detail().isEmpty()) {
                continue;
            }
            // Rechtsbündig und matt: Die Form ist die Erklärung zum Wort und
            // nicht das, was man auswählt.
            int detailWidth = widthOf(entry.detail());
            int detailX = listX + listWidth - 4 - detailWidth;
            if (detailX > listX + 4 + widthOf(entry.text()) + 6) {
                graphics.drawString(font, mono(entry.detail()), detailX, entryY,
                        EditorColours.COMMENT, false);
            }
        }
    }

    private static int colorForKind(Completions.Entry.Kind kind) {
        return switch (kind) {
            case CONNECTOR -> EditorColours.TEXT;
            case ITEM, TAG -> EditorColours.SELECTOR;
            case BUILTIN, KEYWORD -> EditorColours.KEYWORD;
        };
    }
    // ---- Rückgängig -------------------------------------------------------
    //
    // Die Bearbeitungsschritte ab hier stehen ohne Sichtbarkeitsangabe da:
    // Der Test im selben Paket ruft sie unmittelbar auf. Über die Tastatur
    // wären sie nicht zu prüfen — hasControlDown() fragt das Fenster, und im
    // Test gibt es keines.

    /**
     * Ein Stand des Textes samt Cursor.
     *
     * <p>Ganze Stände statt einzelner Änderungen: Ein Programm im Terminal
     * ist ein paar Dutzend Zeilen, und ein Änderungsprotokoll mit Positionen
     * ist die Sorte Code, in der sich ein Fehler erst zeigt, wenn der Text
     * schon kaputt ist.
     */
    private record Snapshot(List<String> lines, int cursorLine, int cursorColumn) {
    }

    /**
     * Wozu eine Änderung gehört.
     *
     * <p>Aufeinanderfolgende Anschläge derselben Art werden zu einem Schritt
     * zusammengefasst. Ohne das nähme ein Rückgängig genau ein Zeichen
     * zurück, und ein versehentlich überschriebenes Wort wären acht Griffe.
     */
    enum EditKind { TYPING, DELETING, STRUCTURAL }

    /** Weiter zurück reicht kein Mensch; darüber wächst nur der Speicher. */
    private static final int MAX_HISTORY = 200;

    private Snapshot snapshot() {
        return new Snapshot(List.copyOf(lines), cursorLine, cursorColumn);
    }

    private void restore(Snapshot state) {
        lines.clear();
        lines.addAll(state.lines());
        cursorLine = Mth.clamp(state.cursorLine(), 0, lines.size() - 1);
        cursorColumn = Math.min(state.cursorColumn(), lines.get(cursorLine).length());
        clearSelection();
        clearSuggestions();
        ensureVisible();
    }

    /**
     * Merkt sich den Stand vor einer Änderung.
     *
     * <p>Läuft eine Änderung derselben Art auf derselben Zeile weiter, wird
     * nichts gemerkt: Der Stand von vor dem Lauf steht schon oben auf dem
     * Stapel, und genau dorthin soll ein Rückgängig führen.
     */
    void remember(EditKind kind) {
        if (kind != EditKind.STRUCTURAL && kind == lastKind && cursorLine == lastKindLine) {
            return;
        }
        undoStack.push(snapshot());
        while (undoStack.size() > MAX_HISTORY) {
            undoStack.removeLast();
        }
        redoStack.clear();
        lastKind = kind;
        lastKindLine = cursorLine;
    }

    void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        redoStack.push(snapshot());
        restore(undoStack.pop());
        // Nach einem Sprung durch die Geschichte fängt jeder Lauf neu an.
        lastKind = null;
        changeListener.accept(text());
    }

    void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        undoStack.push(snapshot());
        restore(redoStack.pop());
        lastKind = null;
        changeListener.accept(text());
    }

    // ---- Wortweise bewegen ------------------------------------------------

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * Ein Wort nach links.
     *
     * <p>Erst über Trennzeichen hinweg, dann über das Wort: So landet man vor
     * dem Wort und nicht in der Lücke davor — sonst bräuchte jedes zweite
     * Wort zwei Anschläge.
     */
    void moveWordLeft() {
        if (cursorColumn == 0) {
            moveLeft();
            return;
        }
        String line = lines.get(cursorLine);
        int column = Math.min(cursorColumn, line.length());
        while (column > 0 && !isWordChar(line.charAt(column - 1))) {
            column--;
        }
        while (column > 0 && isWordChar(line.charAt(column - 1))) {
            column--;
        }
        cursorColumn = column;
    }

    void moveWordRight() {
        String line = lines.get(cursorLine);
        if (cursorColumn >= line.length()) {
            moveRight();
            return;
        }
        int column = cursorColumn;
        while (column < line.length() && isWordChar(line.charAt(column))) {
            column++;
        }
        while (column < line.length() && !isWordChar(line.charAt(column))) {
            column++;
        }
        cursorColumn = column;
    }

    /** Löscht das Wort links vom Cursor. */
    void deleteWordLeft() {
        if (cursorColumn == 0) {
            backspace();
            return;
        }
        int from = cursorColumn;
        moveWordLeft();
        String line = lines.get(cursorLine);
        lines.set(cursorLine, line.substring(0, cursorColumn)
                + line.substring(Math.min(from, line.length())));
        changed();
    }

    // ---- Zeilen umformen --------------------------------------------------

    /**
     * Rückt die betroffenen Zeilen ein oder aus.
     *
     * <p>Mit einer Auswahl alle darin, ohne nur die aktuelle. Das ist der
     * Grund, warum der Tabulator bei einer Auswahl etwas anderes tut als
     * sonst: Vier Leerzeichen einzufügen und dabei die Auswahl zu löschen
     * wäre bei mehreren Zeilen nie das Gemeinte.
     */
    void indentLines(boolean out) {
        int first = hasSelection() ? selectionBounds()[0] : cursorLine;
        int last = hasSelection() ? selectionBounds()[2] : cursorLine;
        for (int i = first; i <= last; i++) {
            String line = lines.get(i);
            if (out) {
                int removed = 0;
                while (removed < 4 && removed < line.length()
                        && line.charAt(removed) == ' ') {
                    removed++;
                }
                lines.set(i, line.substring(removed));
                if (i == cursorLine) {
                    cursorColumn = Math.max(0, cursorColumn - removed);
                }
                if (i == anchorLine) {
                    anchorColumn = Math.max(0, anchorColumn - removed);
                }
            } else {
                lines.set(i, "    " + line);
                if (i == cursorLine) {
                    cursorColumn += 4;
                }
                if (i == anchorLine) {
                    anchorColumn += 4;
                }
            }
        }
        changed();
    }

    /** Verdoppelt die aktuelle Zeile unter sich. */
    void duplicateLine() {
        lines.add(cursorLine + 1, lines.get(cursorLine));
        cursorLine++;
        clearSelection();
        ensureVisible();
        changed();
    }
    // ---- Maus -------------------------------------------------------------

    /** Wie lange zwei Klicks auseinanderliegen dürfen, um einer zu sein. */
    private static final long DOUBLE_CLICK_MILLIS = 260;

    private long lastClickTime;
    private int lastClickLine = -1;
    private int lastClickColumn = -1;

    /** In welcher Zeile ein Mauszeiger steht. */
    private int lineAt(double mouseY) {
        int row = (int) Math.floor((mouseY - y - 10) / LINE_HEIGHT);
        return Mth.clamp(scrollLine + row, 0, lines.size() - 1);
    }

    /**
     * In welcher Spalte ein Mauszeiger steht.
     *
     * <p>Gesucht wird die Lücke, der er am nächsten ist, nicht das Zeichen
     * unter ihm: Ein Klick auf die rechte Hälfte eines Buchstabens setzt den
     * Cursor dahinter. Ohne das kommt man nie ans Zeilenende.
     */
    private int columnAt(int lineIndex, double mouseX) {
        String line = lines.get(lineIndex);
        int textX = x + GUTTER_WIDTH + TEXT_LEFT;
        // Eine Division statt einer Schleife: Bei dicktengleicher Schrift ist
        // die Spalte der abgerundete Abstand. Das Runden setzt die Grenze in
        // die Mitte eines Zeichens — ein Klick auf die rechte Hälfte landet
        // dahinter, wie in jedem Editor.
        int column = (int) Math.round((mouseX - textX) / advance()) + scrollColumn;
        return Mth.clamp(column, 0, line.length());
    }

    private boolean inside(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    /**
     * Wählt das Wort an einer Stelle aus.
     *
     * <p>Steht der Cursor nicht in einem Wort, sondern in einer Lücke, wird
     * die Lücke ausgewählt. Nichts zu tun wäre die schlechtere Antwort: Ein
     * Doppelklick, der manchmal nichts bewirkt, fühlt sich kaputt an.
     */
    void selectWordAt(int lineIndex, int column) {
        String line = lines.get(lineIndex);
        if (line.isEmpty()) {
            select(lineIndex, 0, lineIndex, 0);
            return;
        }
        int at = Mth.clamp(column, 0, line.length() - 1);
        boolean word = isWordChar(line.charAt(at));
        int from = at;
        while (from > 0 && isWordChar(line.charAt(from - 1)) == word) {
            from--;
        }
        int to = at;
        while (to < line.length() && isWordChar(line.charAt(to)) == word) {
            to++;
        }
        select(lineIndex, from, lineIndex, to);
    }

    /**
     * Ein Klick setzt den Cursor — mit Umschalt zieht er die Auswahl dorthin,
     * doppelt nimmt er das Wort.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!inside(mouseX, mouseY)) {
            return false;
        }
        clearSuggestions();
        int lineIndex = lineAt(mouseY);
        int column = columnAt(lineIndex, mouseX);

        long now = System.currentTimeMillis();
        boolean doubled = now - lastClickTime < DOUBLE_CLICK_MILLIS
                && lineIndex == lastClickLine && column == lastClickColumn;
        lastClickTime = now;
        lastClickLine = lineIndex;
        lastClickColumn = column;
        if (doubled) {
            selectWordAt(lineIndex, column);
            return true;
        }

        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            anchorIfNeeded();
        } else {
            clearSelection();
        }
        cursorLine = lineIndex;
        cursorColumn = column;
        return true;
    }

    /**
     * Ziehen wählt aus.
     *
     * <p>Wandert der Zeiger über den Rand hinaus, rollt der Text mit — sonst
     * endet jede Auswahl am sichtbaren Ausschnitt, und längere Stellen wären
     * nur über die Tastatur zu erreichen.
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (button != 0 || lastClickLine < 0) {
            return false;
        }
        if (mouseY < y + 10) {
            scrollLine = Math.max(0, scrollLine - 1);
        } else if (mouseY > y + height - LINE_HEIGHT) {
            scrollLine = Math.min(Math.max(0, lines.size() - visibleLines()), scrollLine + 1);
        }
        anchorIfNeeded();
        cursorLine = lineAt(mouseY);
        cursorColumn = columnAt(cursorLine, mouseX);
        return true;
    }
    // ---- Suchen -----------------------------------------------------------

    /**
     * Die Suche im Editor.
     *
     * <p>Ein Programm wird länger, als es auf den Bildschirm passt, lange
     * bevor es kompliziert wird. Ohne Suche bleibt nur Scrollen, und wer
     * einen Connectornamen ändert, muss jede Stelle von Hand finden.
     *
     * <p>Gesucht wird ohne Rücksicht auf Groß- und Kleinschreibung: Die
     * Sprache unterscheidet sie zwar, aber wer sucht, weiß meist nur
     * ungefähr, wie es geschrieben war.
     */
    private boolean searching;
    private String searchTerm = "";
    private int matchIndex;

    /**
     * Ob die Zeile auch ein Ersetzen anbietet.
     *
     * <p>Dieselbe Zeile für beides und kein zweites Fenster: Suchen und
     * Ersetzen sind eine Handlung mit einem Feld mehr. Strg+F öffnet sie
     * ohne, Strg+H mit — und aus dem einen wird das andere, ohne dass man
     * schließt und neu aufmacht.
     */
    private boolean replacing;
    private String replaceTerm = "";

    /** Welches der beiden Felder gerade tippt. Tabulator wechselt. */
    private boolean editingReplacement;

    public boolean isSearching() {
        return searching;
    }

    String searchTerm() {
        return searchTerm;
    }

    /** Öffnet die Suche. Eine Auswahl wird zum Suchwort. */
    void openSearch() {
        searching = true;
        if (hasSelection()) {
            String selected = selectedText();
            if (!selected.contains(NEWLINE)) {
                searchTerm = selected;
            }
        }
        matchIndex = 0;
        editingReplacement = false;
        clearSuggestions();
        jumpToMatch();
    }

    void closeSearch() {
        searching = false;
        replacing = false;
        editingReplacement = false;
    }

    /** Wodurch ersetzt wird. */
    void setReplaceTerm(String term) {
        replaceTerm = term == null ? "" : term;
    }

    /** Die erste sichtbare Spalte — für die Prüfung des Schiebens. */
    int firstVisibleColumn() {
        return scrollColumn;
    }

    void setSearchTerm(String term) {
        searchTerm = term == null ? "" : term;
        matchIndex = 0;
        jumpToMatch();
    }

    /**
     * Alle Fundstellen, von oben nach unten.
     *
     * <p>Bei jedem Tastendruck neu gesucht. Ein Programm im Terminal ist ein
     * paar Dutzend Zeilen — eine Fundstellenliste zu pflegen wäre Buchführung
     * für eine Rechnung, die niemand merkt.
     */
    List<int[]> matches() {
        if (searchTerm.isEmpty()) {
            return List.of();
        }
        String needle = searchTerm.toLowerCase(java.util.Locale.ROOT);
        List<int[]> found = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String haystack = lines.get(i).toLowerCase(java.util.Locale.ROOT);
            int from = 0;
            while (true) {
                int at = haystack.indexOf(needle, from);
                if (at < 0) {
                    break;
                }
                found.add(new int[] {i, at});
                // Hinter die Fundstelle und nicht ein Zeichen weiter: Sonst
                // fände die Suche nach „aa" in „aaa" zwei Stellen, die
                // einander überlappen — und „alle ersetzen" schriebe in die
                // eigene Ersetzung hinein.
                from = at + needle.length();
            }
        }
        return found;
    }

    /** Zur nächsten Fundstelle, oder zur vorherigen. */
    void step(int direction) {
        List<int[]> found = matches();
        if (found.isEmpty()) {
            return;
        }
        matchIndex = Math.floorMod(matchIndex + direction, found.size());
        jumpToMatch();
    }

    /** Welche Fundstelle gerade dran ist, von eins an. Null heißt keine. */
    int matchNumber() {
        List<int[]> found = matches();
        return found.isEmpty() ? 0 : Math.floorMod(matchIndex, found.size()) + 1;
    }

    private void jumpToMatch() {
        List<int[]> found = matches();
        if (found.isEmpty()) {
            return;
        }
        int[] at = found.get(Math.floorMod(matchIndex, found.size()));
        // Die Fundstelle wird ausgewählt, nicht nur angefahren: Dann trägt
        // Strg+C sie weiter, und ein Tippen ersetzt sie.
        select(at[0], at[1], at[0], at[1] + searchTerm.length());
        ensureVisible();
    }

    /** Legt einen matten Streifen unter jede Fundstelle in dieser Zeile. */
    private void drawMatches(GuiGraphics graphics, int lineIndex, int textX, int lineY) {
        if (!searching || searchTerm.isEmpty()) {
            return;
        }
        List<int[]> found = matches();
        int current = found.isEmpty() ? -1 : Math.floorMod(matchIndex, found.size());
        String line = lines.get(lineIndex);
        for (int i = 0; i < found.size(); i++) {
            int[] at = found.get(i);
            if (at[0] != lineIndex) {
                continue;
            }
            int end = Math.min(at[1] + searchTerm.length(), line.length());
            int left = columnX(textX, at[1]);
            int right = columnX(textX, end);
            graphics.fill(left, lineY - 1, right, lineY + LINE_HEIGHT - 2,
                    i == current ? EditorColours.MATCH_CURRENT : EditorColours.MATCH);
        }
    }

    /**
     * Die Such- und Ersetzenzeile über dem Text.
     *
     * <p>Das aktive Feld trägt einen Strich; ohne ihn wüsste man nach einem
     * Tabulator nicht, wohin man tippt. Die Zahl der Fundstellen steht rechts
     * und wird rot, wenn es keine gibt — ein leeres Ergebnis ist eine
     * Auskunft und kein Fehler, aber man soll es sehen.
     */
    private void drawSearchBar(GuiGraphics graphics) {
        if (!searching) {
            return;
        }
        graphics.fill(x, y, x + width, y + 9, EditorColours.SEARCH_BAR);
        int count = matches().size();

        String find = searchTerm.isEmpty()
                ? net.minecraft.network.chat.Component
                        .translatable("screen.factorynetwork.editor.find").getString()
                : searchTerm;
        int at = x + 3;
        at = drawField(graphics, at, find, !editingReplacement,
                count == 0 && !searchTerm.isEmpty());

        if (replacing) {
            graphics.drawString(font, mono("→"), at + 2, y, EditorColours.COMMENT, false);
            at += widthOf("→ ") + 4;
            String to = replaceTerm.isEmpty()
                    ? net.minecraft.network.chat.Component
                            .translatable("screen.factorynetwork.editor.replace").getString()
                    : replaceTerm;
            at = drawField(graphics, at, to, editingReplacement, false);
        }

        String tally = matchNumber() + "/" + count;
        graphics.drawString(font, mono(tally), x + width - widthOf(tally) - 4, y,
                count == 0 && !searchTerm.isEmpty() ? EditorColours.ERROR
                        : EditorColours.COMMENT, false);
    }

    /** Ein Feld der Suchzeile; das aktive bekommt einen Strich darunter. */
    private int drawField(GuiGraphics graphics, int at, String text, boolean active,
                          boolean bad) {
        String shown = plainSubstrByWidth(text, width / 3);
        graphics.drawString(font, mono(shown), at, y,
                bad ? EditorColours.ERROR : active ? EditorColours.TEXT : EditorColours.MUTED,
                false);
        int shownWidth = Math.max(widthOf(shown), 12);
        if (active) {
            graphics.fill(at, y + 8, at + shownWidth, y + 9,
                    0xFF000000 | EditorColours.SELECTOR);
        }
        return at + shownWidth + 6;
    }

    /**
     * Tastendrücke, solange die Suche offen ist.
     *
     * <p>Sie gehen an die Suche und nicht an den Text: Wer sucht, tippt ein
     * Wort und keinen Code. Escape schließt, Eingabe geht weiter, Umschalt
     * und Eingabe zurück.
     */
    private boolean handleSearchKey(int key) {
        switch (key) {
            case 256 -> { // Escape
                closeSearch();
                return true;
            }
            case 258 -> { // Tabulator wechselt zwischen den beiden Feldern
                if (replacing) {
                    editingReplacement = !editingReplacement;
                }
                return true;
            }
            case 257, 335 -> { // Eingabe
                if (!replacing) {
                    step(net.minecraft.client.gui.screens.Screen.hasShiftDown() ? -1 : 1);
                    return true;
                }
                // Mit Alt alle auf einmal — Strg und Eingabe ist im Terminal
                // schon das Übernehmen, und zwei Bedeutungen für einen Griff
                // sind eine zu viel.
                if (net.minecraft.client.gui.screens.Screen.hasAltDown()) {
                    replaceAll();
                } else {
                    replaceCurrent();
                }
                return true;
            }
            case 259 -> { // Rücktaste
                if (editingReplacement) {
                    if (!replaceTerm.isEmpty()) {
                        replaceTerm = replaceTerm.substring(0, replaceTerm.length() - 1);
                    }
                } else if (!searchTerm.isEmpty()) {
                    setSearchTerm(searchTerm.substring(0, searchTerm.length() - 1));
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Ersetzt die angefahrene Fundstelle und geht zur nächsten.
     *
     * <p>Die Fundstelle ist ausgewählt — {@link #jumpToMatch()} sorgt dafür.
     * Ersetzt wird also über die Auswahl, mit denselben Handgriffen wie beim
     * Tippen, und ein Rückgängig nimmt es als einen Schritt zurück.
     */
    void replaceCurrent() {
        if (searchTerm.isEmpty() || matches().isEmpty()) {
            return;
        }
        if (!hasSelection()) {
            jumpToMatch();
            if (!hasSelection()) {
                return;
            }
        }
        remember(EditKind.STRUCTURAL);
        deleteSelection();
        if (!replaceTerm.isEmpty()) {
            insert(replaceTerm);
        }
        // Nach dem Ersetzen steht die nächste Fundstelle an derselben Nummer:
        // Die eben ersetzte ist aus der Liste heraus.
        List<int[]> rest = matches();
        if (rest.isEmpty()) {
            return;
        }
        matchIndex = Math.floorMod(matchIndex, rest.size());
        jumpToMatch();
    }

    /**
     * Ersetzt alle Fundstellen auf einmal.
     *
     * <p>Zeile für Zeile und von hinten nach vorn innerhalb einer Zeile —
     * sonst verschöbe die erste Ersetzung die Stellen aller folgenden. Ein
     * Rückgängig nimmt das Ganze als einen Schritt zurück; alles andere wäre
     * bei zwanzig Stellen zwanzig Griffe.
     */
    void replaceAll() {
        if (searchTerm.isEmpty()) {
            return;
        }
        List<int[]> found = matches();
        if (found.isEmpty()) {
            return;
        }
        remember(EditKind.STRUCTURAL);
        for (int i = found.size() - 1; i >= 0; i--) {
            int[] at = found.get(i);
            String line = lines.get(at[0]);
            int end = Math.min(at[1] + searchTerm.length(), line.length());
            lines.set(at[0], line.substring(0, at[1]) + replaceTerm + line.substring(end));
        }
        clearSelection();
        cursorLine = Mth.clamp(cursorLine, 0, lines.size() - 1);
        cursorColumn = Math.min(cursorColumn, lines.get(cursorLine).length());
        matchIndex = 0;
        changed();
    }
}
