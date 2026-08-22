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
    private Consumer<String> changeListener = text -> { };
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
        // Ein neuer Text ist kein Schritt, den man rückgängig machen kann:
        // Er kommt vom Server oder vom Öffnen des Fensters, nicht vom Tippen.
        undoStack.clear();
        redoStack.clear();
        lastKind = null;
    }

    /**
     * Misst in der Schrift, in der auch gezeichnet wird.
     *
     * <p><b>Das ist die ganze Vorsicht an dieser Umstellung:</b> Messen und
     * Zeichnen müssen dieselbe Schrift benutzen. Sonst steht der Cursor
     * woanders als der Text, und niemand fände den Grund.
     */
    private int widthOf(String text) {
        return font.width(mono(text));
    }

    private static net.minecraft.network.chat.Component mono(String text) {
        return dev.devpanda.factorynetwork.client.FnFonts.mono(text);
    }

    /** Schneidet auf eine Breite, ebenfalls in der Schrift des Editors. */
    private String plainSubstrByWidth(String text, int width) {
        String rest = text;
        while (!rest.isEmpty() && widthOf(rest) > width) {
            rest = rest.substring(0, rest.length() - 1);
        }
        return rest;
    }

    private int visibleLines() {
        return Math.max(1, (height - 12) / LINE_HEIGHT);
    }

    // ---- Zeichnen ---------------------------------------------------------

    public void render(GuiGraphics graphics, List<Diagnostic> diagnostics) {
        int visible = visibleLines();
        int textX = x + GUTTER_WIDTH + TEXT_LEFT;

        for (int i = 0; i < visible; i++) {
            int lineIndex = scrollLine + i;
            if (lineIndex >= lines.size()) {
                break;
            }
            int lineY = y + 10 + i * LINE_HEIGHT;

            // Zeilennummer, gedämpft — sie soll da sein, aber nicht ablenken.
            String number = String.valueOf(lineIndex + 1);
            graphics.drawString(font, mono(number),
                    x + GUTTER_WIDTH - widthOf(number) - 2, lineY,
                    EditorColours.COMMENT, false);

            // Die Auswahl liegt unter dem Text, sonst verschluckt sie ihn.
            drawSelection(graphics, lineIndex, textX, lineY);
            drawMatches(graphics, lineIndex, textX, lineY);

            // Zeilen mit Fehler bekommen einen Streifen statt einer Welle:
            // Wellen sind bei zehn Pixel Zeilenhöhe nicht zu erkennen.
            if (hasErrorInLine(diagnostics, lineIndex + 1)) {
                graphics.fill(textX - 2, lineY - 1, x + width - 2, lineY + LINE_HEIGHT - 2,
                        EditorColours.ERROR_LINE);
            }

            drawHighlighted(graphics, lines.get(lineIndex), textX, lineY);
        }

        drawCursor(graphics, textX);
        drawScrollHint(graphics, visible);
        drawSearchBar(graphics);
        drawSuggestions(graphics, textX);
    }

    /** Färbt eine Zeile nach Token-Arten. */
    private void drawHighlighted(GuiGraphics graphics, String line, int startX, int lineY) {
        if (line.isEmpty()) {
            return;
        }
        int comment = line.indexOf("//");
        String code = comment >= 0 ? line.substring(0, comment) : line;

        int drawX = startX;
        if (!code.isBlank()) {
            List<Token> tokens = Lexer.tokenize(code).tokens();
            int consumed = 0;
            for (Token token : tokens) {
                if (token.is(TokenType.EOF) || token.is(TokenType.NL)) {
                    continue;
                }
                int start = token.span().start();
                if (start > consumed && start <= code.length()) {
                    String gap = code.substring(consumed, start);
                    graphics.drawString(font, mono(gap), drawX, lineY, EditorColours.TEXT, false);
                    drawX += widthOf(gap);
                    consumed = start;
                }
                int end = Math.min(token.span().end(), code.length());
                if (end <= consumed) {
                    continue;
                }
                String piece = code.substring(consumed, end);
                graphics.drawString(font, mono(piece), drawX, lineY, colorFor(token), false);
                drawX += widthOf(piece);
                consumed = end;
            }
            if (consumed < code.length()) {
                String rest = code.substring(consumed);
                graphics.drawString(font, mono(rest), drawX, lineY, EditorColours.TEXT, false);
                drawX += widthOf(rest);
            }
        } else {
            graphics.drawString(font, mono(code), drawX, lineY, EditorColours.TEXT, false);
            drawX += widthOf(code);
        }

        if (comment >= 0) {
            graphics.drawString(font, mono(line.substring(comment)), drawX, lineY,
                    EditorColours.COMMENT, false);
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

    private static boolean hasErrorInLine(List<Diagnostic> diagnostics, int line) {
        return diagnostics.stream()
                .anyMatch(diagnostic -> diagnostic.isError() && diagnostic.span().line() == line);
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
        int cursorX = textX + widthOf(line.substring(0, column));
        int cursorY = y + 10 + row * LINE_HEIGHT;
        graphics.fill(cursorX, cursorY - 1, cursorX + 1, cursorY + LINE_HEIGHT - 2,
                EditorColours.CURSOR);
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
                trackTop + offset + thumbHeight, EditorColours.COMMENT);
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
            case 70 -> { // F: suchen
                if (searching) {
                    closeSearch();
                } else {
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

    /** Bewegt den Cursor und führt dabei die Auswahl nach. */
    private void move(Runnable movement) {
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            anchorIfNeeded();
        } else {
            clearSelection();
        }
        movement.run();
    }

    public boolean keyPressed(int key, int scanCode, int modifiers) {
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

    public boolean charTyped(char character, int modifiers) {
        if (character == '\n' || character == '\r') {
            return false;
        }
        if (Character.isISOControl(character)) {
            return false;
        }
        if (searching) {
            setSearchTerm(searchTerm + character);
            return true;
        }
        remember(EditKind.TYPING);
        insert(String.valueOf(character));
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!inside(mouseX, mouseY)) {
            return false;
        }
        int max = Math.max(0, lines.size() - visibleLines());
        scrollLine = Mth.clamp(scrollLine - (int) Math.signum(delta) * 3, 0, max);
        return true;
    }

    // ---- Bearbeiten -------------------------------------------------------

    /**
     * Zeichnet den ausgewählten Bereich einer Zeile.
     *
     * <p>Eine Auswahl ohne Anzeige ist schlimmer als keine: Man weiss nicht,
     * was das nächste Zeichen ersetzt.
     */
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
        int left = textX + widthOf(line.substring(0, from));
        // Eine leere Zeile mitten in der Auswahl bekommt einen schmalen
        // Streifen — sonst sieht es aus, als wäre sie nicht mit dabei.
        int right = from == to && lineIndex != b[2]
                ? left + 4
                : textX + widthOf(line.substring(0, to));
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

    private void newLine() {
        String line = lines.get(cursorLine);
        int column = Math.min(cursorColumn, line.length());
        String before = line.substring(0, column);
        String after = line.substring(column);

        // Einrückung der Zeile übernehmen, und nach { eine Stufe mehr.
        int indent = 0;
        while (indent < before.length() && before.charAt(indent) == ' ') {
            indent++;
        }
        if (before.stripTrailing().endsWith("{")) {
            indent += 4;
        }
        String padding = " ".repeat(indent);

        lines.set(cursorLine, before);
        lines.add(cursorLine + 1, padding + after);
        cursorLine++;
        cursorColumn = padding.length();
        ensureVisible();
        changed();
    }

    private void backspace() {
        String line = lines.get(cursorLine);
        if (cursorColumn > 0) {
            int column = Math.min(cursorColumn, line.length());
            // Vier Leerzeichen am Zeilenanfang gehen gemeinsam weg.
            int remove = 1;
            if (column >= 4 && line.substring(0, column).endsWith("    ")
                    && line.substring(0, column).isBlank()) {
                remove = 4;
            }
            lines.set(cursorLine, line.substring(0, column - remove) + line.substring(column));
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

    private void ensureVisible() {
        int visible = visibleLines();
        if (cursorLine < scrollLine) {
            scrollLine = cursorLine;
        } else if (cursorLine >= scrollLine + visible) {
            scrollLine = cursorLine - visible + 1;
        }
        scrollLine = Mth.clamp(scrollLine, 0, Math.max(0, lines.size() - visible));
    }

    private void changed() {
        cursorVisible = true;
        lastBlink = System.currentTimeMillis();
        updateSuggestions();
        changeListener.accept(text());
    }

    // ---- Vorschläge -------------------------------------------------------

    private void updateSuggestions() {
        suggestions = Completions.at(lines, cursorLine, cursorColumn);
        selectedSuggestion = 0;
    }

    private void clearSuggestions() {
        suggestions = List.of();
        selectedSuggestion = 0;
    }

    /** Übernimmt den ausgewählten Vorschlag und ersetzt das angefangene Wort. */
    private void applySuggestion() {
        if (suggestions.isEmpty()) {
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
        int listX = textX + widthOf(line.substring(0, column - word.length()));
        int listY = y + 10 + (row + 1) * LINE_HEIGHT;

        int listWidth = 0;
        for (Completions.Entry entry : suggestions) {
            listWidth = Math.max(listWidth, widthOf(entry.text()) + 8);
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
        for (int column = 0; column < line.length(); column++) {
            int left = textX + widthOf(line.substring(0, column));
            int right = textX + widthOf(line.substring(0, column + 1));
            if (mouseX < (left + right) / 2.0) {
                return column;
            }
        }
        return line.length();
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
        clearSuggestions();
        jumpToMatch();
    }

    void closeSearch() {
        searching = false;
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
                from = at + 1;
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
            int left = textX + widthOf(line.substring(0, at[1]));
            int right = textX + widthOf(line.substring(0, end));
            graphics.fill(left, lineY - 1, right, lineY + LINE_HEIGHT - 2,
                    i == current ? EditorColours.MATCH_CURRENT : EditorColours.MATCH);
        }
    }

    /** Die Suchzeile über dem Text. */
    private void drawSearchBar(GuiGraphics graphics) {
        if (!searching) {
            return;
        }
        graphics.fill(x, y, x + width, y + 9, EditorColours.SEARCH_BAR);
        int count = matches().size();
        String label = searchTerm.isEmpty()
                ? net.minecraft.network.chat.Component
                        .translatable("screen.factorynetwork.editor.find").getString()
                : searchTerm + "  " + matchNumber() + "/" + count;
        graphics.drawString(font, mono(plainSubstrByWidth(label, width - 6)), x + 3, y,
                count == 0 && !searchTerm.isEmpty() ? EditorColours.ERROR : EditorColours.TEXT,
                false);
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
            case 257, 335 -> { // Eingabe
                step(net.minecraft.client.gui.screens.Screen.hasShiftDown() ? -1 : 1);
                return true;
            }
            case 259 -> { // Rücktaste
                if (!searchTerm.isEmpty()) {
                    setSearchTerm(searchTerm.substring(0, searchTerm.length() - 1));
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
