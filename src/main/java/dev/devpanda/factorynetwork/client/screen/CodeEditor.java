package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.lang.Diagnostic;
import dev.devpanda.factorynetwork.lang.Lexer;
import dev.devpanda.factorynetwork.lang.Token;
import dev.devpanda.factorynetwork.lang.TokenType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.ArrayList;
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
            graphics.drawString(font, number,
                    x + GUTTER_WIDTH - font.width(number) - 2, lineY,
                    EditorColours.COMMENT, false);

            // Die Auswahl liegt unter dem Text, sonst verschluckt sie ihn.
            drawSelection(graphics, lineIndex, textX, lineY);

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
                    graphics.drawString(font, gap, drawX, lineY, EditorColours.TEXT, false);
                    drawX += font.width(gap);
                    consumed = start;
                }
                int end = Math.min(token.span().end(), code.length());
                if (end <= consumed) {
                    continue;
                }
                String piece = code.substring(consumed, end);
                graphics.drawString(font, piece, drawX, lineY, colorFor(token), false);
                drawX += font.width(piece);
                consumed = end;
            }
            if (consumed < code.length()) {
                String rest = code.substring(consumed);
                graphics.drawString(font, rest, drawX, lineY, EditorColours.TEXT, false);
                drawX += font.width(rest);
            }
        } else {
            graphics.drawString(font, code, drawX, lineY, EditorColours.TEXT, false);
            drawX += font.width(code);
        }

        if (comment >= 0) {
            graphics.drawString(font, line.substring(comment), drawX, lineY,
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
        int cursorX = textX + font.width(line.substring(0, column));
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
        var keyboard = Minecraft.getInstance().keyboardHandler;
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
                    keyboard.setClipboard(selectedText());
                }
                return true;
            }
            case 88 -> { // X: ausschneiden
                if (hasSelection()) {
                    keyboard.setClipboard(selectedText());
                    deleteSelection();
                }
                return true;
            }
            case 86 -> { // V: einfügen
                String inhalt = keyboard.getClipboard();
                if (inhalt != null && !inhalt.isEmpty()) {
                    insertMultiline(inhalt);
                    clearSuggestions();
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Bewegt den Cursor und führt dabei die Auswahl nach. */
    private void move(Runnable bewegung) {
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            anchorIfNeeded();
        } else {
            clearSelection();
        }
        bewegung.run();
    }

    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (handleShortcut(key)) {
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
                if (hasSelection()) {
                    deleteSelection();
                    return true;
                }
                backspace();
                return true;
            }
            case 261 -> { // Entfernen
                if (hasSelection()) {
                    deleteSelection();
                    return true;
                }
                delete();
                return true;
            }
            case 257, 335 -> { // Eingabe
                newLine();
                return true;
            }
            case 258 -> { // Tabulator: vier Leerzeichen, nie ein Tabulatorzeichen
                insert("    ");
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
        insert(String.valueOf(character));
        return true;
    }

    /**
     * Ein Klick setzt den Cursor — mit Umschalt zieht er die Auswahl dorthin.
     *
     * <p>Dasselbe Verhalten wie in jedem Editor. Ohne die Umschalt-Variante
     * müsste man längere Stellen mit den Pfeiltasten einsammeln.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            anchorIfNeeded();
        } else {
            clearSelection();
        }
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
            return false;
        }
        clearSuggestions();
        int row = (int) ((mouseY - y - 10) / LINE_HEIGHT);
        int lineIndex = Mth.clamp(scrollLine + row, 0, lines.size() - 1);
        cursorLine = lineIndex;

        String line = lines.get(lineIndex);
        int textX = x + GUTTER_WIDTH + TEXT_LEFT;
        int column = 0;
        while (column < line.length()
                && textX + font.width(line.substring(0, column + 1)) < mouseX) {
            column++;
        }
        cursorColumn = column;
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
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
        int von = lineIndex == b[0] ? Math.min(b[1], line.length()) : 0;
        int bis = lineIndex == b[2] ? Math.min(b[3], line.length()) : line.length();
        int links = textX + font.width(line.substring(0, von));
        // Eine leere Zeile mitten in der Auswahl bekommt einen schmalen
        // Streifen — sonst sieht es aus, als wäre sie nicht mit dabei.
        int rechts = von == bis && lineIndex != b[2]
                ? links + 4
                : textX + font.width(line.substring(0, bis));
        graphics.fill(links, lineY - 1, Math.min(rechts, x + width - 2),
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
            int hilfsZeile = startLine;
            int hilfsSpalte = startColumn;
            startLine = endLine;
            startColumn = endColumn;
            endLine = hilfsZeile;
            endColumn = hilfsSpalte;
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
        String kopf = first.substring(0, Math.min(b[1], first.length()));
        String schwanz = last.substring(Math.min(b[3], last.length()));
        for (int i = b[2]; i > b[0]; i--) {
            lines.remove(i);
        }
        lines.set(b[0], kopf + schwanz);
        cursorLine = b[0];
        cursorColumn = kopf.length();
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
        String bereinigt = normalise(text);
        String[] teile = bereinigt.split("\n", -1);
        if (teile.length == 1) {
            insert(teile[0]);
            return;
        }
        String line = lines.get(cursorLine);
        int column = Math.min(cursorColumn, line.length());
        String vor = line.substring(0, column);
        String nach = line.substring(column);

        lines.set(cursorLine, vor + teile[0]);
        for (int i = 1; i < teile.length; i++) {
            lines.add(cursorLine + i, teile[i]);
        }
        int letzte = cursorLine + teile.length - 1;
        cursorColumn = lines.get(letzte).length();
        lines.set(letzte, lines.get(letzte) + nach);
        cursorLine = letzte;
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
        int listX = textX + font.width(line.substring(0, column - word.length()));
        int listY = y + 10 + (row + 1) * LINE_HEIGHT;

        int listWidth = 0;
        for (Completions.Entry entry : suggestions) {
            listWidth = Math.max(listWidth, font.width(entry.text()) + 8);
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
            graphics.drawString(font, font.plainSubstrByWidth(entry.text(), listWidth - 8),
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
}
