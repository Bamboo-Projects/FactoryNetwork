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
 * A multi-line text field with syntax highlighting.
 *
 * <p>Minecraft ships none — {@code MultiLineEditBox} can do neither per-word
 * colours nor line numbers. Hence a small, home-grown version: lines as a
 * list, one cursor, no wrapping. No wrapping is deliberate; in code it would
 * be confusing, because the line number would no longer match.
 */
public class CodeEditor {

    private static final int LINE_HEIGHT = 10;
    private static final int GUTTER_WIDTH = 18;
    private static final int TEXT_LEFT = 4;

    /** How far one level indents — here and in {@code newLine}. */
    private static final int INDENT = 4;

    /** A line ending, so the building blocks below need no escapes. */
    private static final String NEWLINE = "\n";

    /**
     * Normalises line endings and replaces tabs with spaces.
     *
     * <p>Otherwise anyone pasting from another editor drags in control
     * characters that have no business here — and a tab tears apart the
     * indentation, because the editor counts in spaces.
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
     * The first visible column.
     *
     * <p><b>Without it a long line simply ran out of the window</b> — past the
     * edge, past the footer, past whatever lay to the right. Wrapping is out of
     * the question, because then the line number would no longer match the
     * line; so the view scrolls sideways, as in every editor.
     */
    private int scrollColumn;
    private Consumer<String> changeListener = text -> { };

    /**
     * Whether only reading is allowed.
     *
     * <p>For a file someone else is editing right now. Moving, selecting,
     * copying and searching still work — you do want to see what's there;
     * only changing it doesn't.
     */
    private boolean readOnly;
    private long lastBlink;
    private boolean cursorVisible = true;

    private List<Completions.Entry> suggestions = List.of();
    private int selectedSuggestion;

    /**
     * Where a selection began, or -1.
     *
     * <p>The anchor stays put while the cursor moves — so the selection grows
     * and shrinks in both directions, without having to remember which side
     * was the earlier one.
     */
    private int anchorLine = -1;
    private int anchorColumn;

    /** The font's advance, measured once. */
    private float advance;

    /** What can be undone, and what can then be redone. */
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

    /** Which line the cursor is on, counting from zero. */
    public int cursorLine() {
        return cursorLine;
    }

    /** Which column the cursor is on, counting from zero. */
    public int cursorColumn() {
        return cursorColumn;
    }

    /**
     * Selects a range.
     *
     * <p>Anchor and cursor in one go — the selection you would otherwise drag
     * with the Shift key held down.
     */
    public void select(int fromLine, int fromColumn, int toLine, int toColumn) {
        anchorLine = Mth.clamp(fromLine, 0, lines.size() - 1);
        anchorColumn = Mth.clamp(fromColumn, 0, lines.get(anchorLine).length());
        cursorLine = Mth.clamp(toLine, 0, lines.size() - 1);
        cursorColumn = Mth.clamp(toColumn, 0, lines.get(cursorLine).length());
        ensureVisible();
    }

    /** Sets the cursor without carrying the selection along. */
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
        // Fresh text is not a step you can undo: it comes from the server or
        // from opening the window, not from typing.
        undoStack.clear();
        redoStack.clear();
        lastKind = null;
    }

    /**
     * How wide a character is, in pixels with a fraction.
     *
     * <p><b>Here is all the caution around the custom font.</b>
     * {@code Font.width} rounds up: at an advance of 5.4 points each call costs
     * up to 0.6 points too much. The editor draws token by token and used to
     * accumulate this error — after eight tokens the text sat five points off
     * the cursor, and from a screenshot the reason would have been
     * unguessable.
     *
     * <p>The font is monospaced. So the advance is measured once and after
     * that only multiplied — every column lands in the same place, no matter
     * how many tokens came before it. This is how every code editor computes
     * it.
     */
    private float advance() {
        if (advance <= 0.0F) {
            // Without a font, a round value. This is no stopgap for running —
            // in operation there is always one — but for the test: it builds
            // the editor without a window, and since the cursor is now kept in
            // view horizontally too, every change computes with the advance.
            // Nothing is drawn in the process, so any number will do; it just
            // must not be missing.
            if (font == null) {
                return 6.0F;
            }
            String probe = "M".repeat(32);
            advance = font.getSplitter().stringWidth(mono(probe)) / 32.0F;
        }
        return advance;
    }

    /** Where a column starts — shifted by the horizontal offset. */
    private int columnX(int textX, int column) {
        return textX + Math.round((column - scrollColumn) * advance());
    }

    /** How many columns fit side by side. */
    private int visibleColumns() {
        return Math.max(8, (int) ((width - GUTTER_WIDTH - TEXT_LEFT - 6) / advance()));
    }

    private int widthOf(String text) {
        return Math.round(text.length() * advance());
    }

    private static net.minecraft.network.chat.Component mono(String text) {
        return dev.devpanda.factorynetwork.client.FnFonts.mono(text);
    }

    /** Cuts to a width, likewise in the editor's font. */
    private String plainSubstrByWidth(String text, int width) {
        int zeichen = (int) (width / advance());
        return zeichen >= text.length() ? text : text.substring(0, Math.max(0, zeichen));
    }

    private int visibleLines() {
        return Math.max(1, (height - 12) / LINE_HEIGHT);
    }

    // ---- Drawing ---------------------------------------------------------

    /**
     * Draws the visible section.
     *
     * <p><b>Two passes, because only one may be clipped.</b> The gutter — line
     * numbers and error markers — stands to the left of the text and doesn't
     * move along with horizontal scrolling; so it has to lie outside the
     * scissor. The text has to lie inside, otherwise a long line draws past
     * the edge into whatever sits beside it.
     *
     * <p>And the scissor once around the whole pass instead of once per line:
     * every change to the clip flushes the draw buffer, and forty flushes per
     * frame are forty too many.
     */
    public void render(GuiGraphics graphics, List<Diagnostic> diagnostics) {
        int visible = visibleLines();
        int textX = x + GUTTER_WIDTH + TEXT_LEFT;
        int[] partner = matchingBracket();
        int last = Math.min(visible, lines.size() - scrollLine);

        // ---- First pass: the gutter, unclipped ----
        for (int i = 0; i < last; i++) {
            int lineIndex = scrollLine + i;
            int lineY = y + 10 + i * LINE_HEIGHT;

            // Line number, muted — it should be there but not distract. The
            // current one brighter: it's the reference point when you look up.
            String number = String.valueOf(lineIndex + 1);
            graphics.drawString(font, mono(number),
                    x + GUTTER_WIDTH - widthOf(number) - 2, lineY,
                    lineIndex == cursorLine
                            ? EditorColours.LINE_NUMBER_ACTIVE : EditorColours.COMMENT,
                    false);

            // The marker for a diagnostic: the stripe in the text says that
            // something is there, but not what. The marker is the place the
            // message hangs on — on hover, it shows there.
            Diagnostic problem = diagnosticIn(diagnostics, lineIndex + 1);
            if (problem != null) {
                graphics.fill(x + 1, lineY - 1, x + 3, lineY + LINE_HEIGHT - 2,
                        problem.isError() ? 0xFFE88388 : 0xFFE8AC3E);
            }
        }

        // ---- Second pass: the text, clipped ----
        graphics.enableScissor(x + GUTTER_WIDTH, y + 9, x + width - 2,
                y + 10 + visible * LINE_HEIGHT);
        for (int i = 0; i < last; i++) {
            int lineIndex = scrollLine + i;
            int lineY = y + 10 + i * LINE_HEIGHT;

            // The line under the cursor gets a hint of background. Not during
            // a selection: that is already a backing, and two on top of each
            // other are none.
            if (lineIndex == cursorLine && !hasSelection()) {
                graphics.fill(x + GUTTER_WIDTH, lineY - 1, x + width - 2,
                        lineY + LINE_HEIGHT - 2, EditorColours.CURRENT_LINE);
            }

            drawIndentGuides(graphics, lineIndex, textX, lineY);

            // The selection sits under the text, otherwise it swallows it.
            drawSelection(graphics, lineIndex, textX, lineY);
            drawMatches(graphics, lineIndex, textX, lineY);
            drawBracket(graphics, lineIndex, textX, lineY, partner);

            // Lines with an error get a stripe instead of a squiggle:
            // squiggles can't be made out at ten pixels of line height.
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
        // Both sit deliberately outside the scissor: they may extend beyond
        // the text area, that is the whole point of a box.
        drawSignature(graphics, textX);
        drawSuggestions(graphics, textX);
    }

    /**
     * The shape of the statement the cursor is in.
     *
     * <p><b>The answer to "I never see what I have to give where".</b> A
     * suggestion list says which words exist; it doesn't say that after
     * {@code row} comes first a text and then an expression. That was only in
     * the grammar, so off to the side of the screen.
     *
     * <p>Here the whole shape stands, and the slot that is currently up is
     * highlighted. It appears on its own and needn't be requested — it is no
     * suggestion but a label.
     *
     * <p>Above the line and not below it: below sits the suggestion list, and
     * two boxes on top of each other hide the code you are writing. At the very
     * top it slides down, otherwise it would stand outside.
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

        // The parts of the shape: first the keyword, then the slots. A slot
        // that may be omitted stands in square brackets — together with the
        // value it introduces, just as in the grammar.
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

        // In a low window only the shape without the sentence alongside: in the
        // terminal's tab eight lines are visible, and a box of two lines would
        // take a quarter of them.
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
            // The active slot bright and underlined, the rest muted. The
            // keyword itself is never active — you always stand behind it.
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

    /** Colours a line by token kind. */
    private void drawHighlighted(GuiGraphics graphics, String line, int startX, int lineY) {
        if (line.isEmpty()) {
            return;
        }
        int comment = line.indexOf("//");
        String code = comment >= 0 ? line.substring(0, comment) : line;

        // Drawn by column, not by accumulated width: the font is monospaced,
        // so every column is fixed.
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
        // A keyword is recognised by its kind matching its text.
        if (TokenType.keyword(token.text()) == type) {
            return EditorColours.KEYWORD;
        }
        return EditorColours.TEXT;
    }

    /**
     * The first diagnostic for a line, or {@code null}.
     *
     * <p>The first and not all: one line has room for one marker, and whoever
     * fixes the first error sees the second on their own. An error also often
     * shifts all the ones that follow.
     */
    public Diagnostic diagnosticIn(List<Diagnostic> diagnostics, int line) {
        return diagnostics.stream()
                .filter(diagnostic -> diagnostic.span().line() == line)
                .findFirst()
                .orElse(null);
    }

    /**
     * What sits under the pointer — for the explanation on hover.
     *
     * <p>The whole line counts, not just the marker: a marker is two pixels
     * wide, and no one hits two pixels.
     */
    public Diagnostic diagnosticAt(List<Diagnostic> diagnostics, double mouseX,
                                   double mouseY) {
        if (!inside(mouseX, mouseY)) {
            return null;
        }
        return diagnosticIn(diagnostics, lineAt(mouseY) + 1);
    }

    /**
     * The shape for the keyword under the pointer, or {@code null}.
     *
     * <p>The shape line above the cursor says what you are writing. When
     * <b>reading</b> it doesn't help: there the cursor is elsewhere, and you
     * want to know what this one line does. So the same thing again for the
     * mouse pointer.
     *
     * <p>Only the first word of a line is recognised — the keyword. What comes
     * after are names and values; a signature table doesn't explain those.
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
        // Only while the pointer really stands over the word.
        int start = line.indexOf(keyword);
        int column = columnAt(lineIndex, mouseX);
        if (column < start || column > start + keyword.length()) {
            return null;
        }
        String block = Completions.enclosingBlock(lines, lineIndex);
        return dev.devpanda.factorynetwork.lang.Signatures.find(block, keyword);
    }

    /**
     * The whole word under the pointer, or empty.
     *
     * <p>For the jump to the explanation. A word here is whatever a name may
     * also be: letters, digits, underscore.
     */
    /**
     * The selector expression under the pointer, or empty text.
     *
     * <p>A selector is more than a word — {@code item:*_ore} carries a colon,
     * star and underscore. {@link #wordAt} stops at each of those; the rule for
     * it lives in {@code Selectors}, because both editors need the same one.
     */
    public String selectorAt(double mouseX, double mouseY) {
        if (!inside(mouseX, mouseY)) {
            return "";
        }
        int lineIndex = lineAt(mouseY);
        String line = lines.get(lineIndex);
        return dev.devpanda.factorynetwork.lang.Selectors.at(
                line, columnAt(lineIndex, mouseX));
    }

    public String wordAt(double mouseX, double mouseY) {
        if (!inside(mouseX, mouseY)) {
            return "";
        }
        int lineIndex = lineAt(mouseY);
        String line = lines.get(lineIndex);
        int column = columnAt(lineIndex, mouseX);
        if (column >= line.length() || !isWordChar(line.charAt(column))) {
            // One step back: whoever clicks past the last character of a word
            // means the word.
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

    /** Jumps to a line and column, both counted from one. */
    public void jumpTo(int line, int column) {
        setCursor(line - 1, Math.max(0, column - 1));
    }

    /** Sets the cursor to the location of a diagnostic. */
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
        // <b>With an alpha channel.</b> The colours in EditorColours are meant
        // for text and stand there without one; Minecraft's font supplies it
        // itself when it's missing. A fill does not — the cursor was therefore
        // transparent for as long as it existed.
        graphics.fill(cursorX, cursorY - 1, cursorX + 1, cursorY + LINE_HEIGHT - 2,
                0xFF000000 | EditorColours.CURSOR);
    }

    /**
     * The horizontal bar, when a line is wider than the window.
     *
     * <p>Only then: a bar that is always there and always full says nothing
     * and costs a line.
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

    // ---- Input ----------------------------------------------------------

    /**
     * The keyboard commands every editor has.
     *
     * <p>They come before everything else: whoever presses Ctrl and C wants to
     * copy — and not type a C because the check stood further down.
     */
    private boolean handleShortcut(int key) {
        if (!net.minecraft.client.gui.screens.Screen.hasControlDown()) {
            return false;
        }
        switch (key) {
            case 65 -> { // A: select all
                anchorLine = 0;
                anchorColumn = 0;
                cursorLine = lines.size() - 1;
                cursorColumn = lines.get(cursorLine).length();
                return true;
            }
            case 67 -> { // C: copy
                if (hasSelection()) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(selectedText());
                }
                return true;
            }
            case 88 -> { // X: cut
                if (hasSelection()) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(selectedText());
                    remember(EditKind.STRUCTURAL);
                    deleteSelection();
                }
                return true;
            }
            case 86 -> { // V: paste
                String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clipboard != null && !clipboard.isEmpty()) {
                    remember(EditKind.STRUCTURAL);
                    insertMultiline(clipboard);
                    clearSuggestions();
                }
                return true;
            }
            case 89, 90 -> {
                // Both keys undo, with Shift they redo.
                //
                // GLFW reports the key by its position on a US keyboard. On a
                // German one, where "Z" sits GLFW reports "Y" — so Ctrl+Z and
                // Ctrl+Y would be swapped depending on the layout. Mapping both
                // to the same thing is the resolution that does the expected
                // thing on every layout.
                if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                    redo();
                } else {
                    undo();
                }
                return true;
            }
            case 32 -> {
                // Space bar: ask for suggestions.
                //
                // <b>The move without which completion isn't usable.</b>
                // Before, the list appeared only while typing and was gone the
                // moment you clicked it away once — until the next keystroke.
                // Whoever wanted to know what's allowed at a spot had to type a
                // character and delete it again.
                updateSuggestions();
                return true;
            }
            case 70 -> { // F: search
                if (searching && !replacing) {
                    closeSearch();
                } else {
                    replacing = false;
                    openSearch();
                }
                return true;
            }
            case 72 -> { // H: search and replace
                if (searching && replacing) {
                    closeSearch();
                } else {
                    replacing = true;
                    openSearch();
                }
                return true;
            }
            case 68 -> { // D: duplicate line
                remember(EditKind.STRUCTURAL);
                duplicateLine();
                return true;
            }
            case 259 -> { // Backspace: a whole word
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
            case 263 -> { // left: one word
                move(this::moveWordLeft);
                return true;
            }
            case 262 -> { // right: one word
                move(this::moveWordRight);
                return true;
            }
            case 268 -> { // Home: to the start of the program
                move(() -> {
                    cursorLine = 0;
                    cursorColumn = 0;
                    ensureVisible();
                });
                return true;
            }
            case 269 -> { // End: to the end of the program
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
     * Moves the cursor and carries the selection along with it.
     *
     * <p>An open suggestion list closes in the process: it applied to the spot
     * where it opened. Leaving it standing would mean that after two steps to
     * the left it offers something that no longer fits there. The shape line
     * stays — it is recomputed on every frame.
     */
    private void move(Runnable movement) {
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            anchorIfNeeded();
        } else {
            clearSelection();
        }
        clearSuggestions();
        movement.run();
        // Afterwards and not in the movements themselves: moveRight within a
        // line didn't call it, and that's exactly where you run off-screen.
        ensureVisible();
    }

    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (readOnly) {
            return readOnlyKey(key, scanCode, modifiers);
        }
        if (handleShortcut(key)) {
            return true;
        }
        // While search is open, the keys belong to it: whoever searches types
        // a word, not code.
        if (searching && handleSearchKey(key)) {
            return true;
        }
        // While suggestions are open, Tab, arrows and Escape belong to them.
        if (!suggestions.isEmpty()) {
            switch (key) {
                case 258 -> { // Tab accepts
                    applySuggestion();
                    return true;
                }
                case 264 -> { // down
                    selectedSuggestion = (selectedSuggestion + 1) % suggestions.size();
                    return true;
                }
                case 265 -> { // up
                    selectedSuggestion =
                            (selectedSuggestion - 1 + suggestions.size()) % suggestions.size();
                    return true;
                }
                case 256 -> { // Escape closes only the list, not the editor
                    clearSuggestions();
                    return true;
                }
                default -> { }
            }
        }
        switch (key) {
            case 259 -> { // Backspace
                // At the start of the text nothing happens. Recording it anyway
                // would cost a step of history and wipe the way forward — for a
                // keystroke that did nothing.
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
            case 261 -> { // Delete
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
            case 257, 335 -> { // Enter
                remember(EditKind.STRUCTURAL);
                newLine();
                return true;
            }
            case 258 -> { // Tab: four spaces, never a tab character
                remember(EditKind.STRUCTURAL);
                boolean shift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
                if (shift || hasSelection()) {
                    indentLines(shift);
                } else {
                    insert("    ");
                }
                return true;
            }
            case 263 -> { // left
                move(this::moveLeft);
                return true;
            }
            case 262 -> { // right
                move(this::moveRight);
                return true;
            }
            case 265 -> { // up
                move(() -> moveVertically(-1));
                return true;
            }
            case 264 -> { // down
                move(() -> moveVertically(1));
                return true;
            }
            case 268 -> { // Home
                move(() -> cursorColumn = 0);
                return true;
            }
            case 269 -> { // End
                move(() -> cursorColumn = lines.get(cursorLine).length());
                return true;
            }
            case 266 -> { // Page up
                moveVertically(-visibleLines());
                return true;
            }
            case 267 -> { // Page down
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
     * Keys in a locked file.
     *
     * <p>Whatever changes nothing passes through: arrows, jumps, selection,
     * copying, searching. Everything else is swallowed — <b>swallowed and not
     * passed on</b>, otherwise the inventory key would close the window over a
     * file you are reading.
     */
    private boolean readOnlyKey(int key, int scanCode, int modifiers) {
        boolean control = net.minecraft.client.gui.screens.Screen.hasControlDown();
        if (control && (key == 67 || key == 65)) { // C and A
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
        if (control && key == 70) { // F: search
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
            // Swallowed and not passed on: otherwise an "e" in a locked file
            // would trigger the inventory.
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
     * Brackets and quotation marks close themselves.
     *
     * <p>Two cases, and the second is the more important: whoever types a
     * closing bracket though it's already there wants to step over it and not
     * have two of them. Without this the auto-closing would be more of a
     * nuisance than a help.
     *
     * <p>It only completes when nothing solid stands to the right of the
     * cursor — line end, space or another closing bracket. Whoever types in
     * the middle of a word means the single character.
     *
     * @return whether the case was handled
     */
    private boolean typePair(char character) {
        if (hasSelection()) {
            return false;
        }
        String line = lines.get(cursorLine);
        int column = Math.min(cursorColumn, line.length());
        char next = column < line.length() ? line.charAt(column) : '\0';

        // Step over a counterpart that's already there.
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

    /** Scrolling shifts the text; with Shift, sideways. */
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

    // ---- Editing -------------------------------------------------------

    /**
     * Draws the selected range of a line.
     *
     * <p>A selection without display is worse than none: you don't know what
     * the next character replaces.
     */
    /**
     * The vertical strokes of the indentation.
     *
     * <p>One per four spaces, but not in column zero: the left margin is
     * already an edge. For a blank line the surrounding indentation applies —
     * otherwise every blank line tears all the strokes open, and then they no
     * longer answer the question they are there for.
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
        // A blank line inherits what stands around it — the smaller of the two
        // neighbours, so that a stroke doesn't run on into nothing.
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

    // ---- Bracket pairs ----------------------------------------------------

    private static final String OPENERS = "{([";
    private static final String CLOSERS = "})]";

    /** Is there an empty pair here — {@code ()}, {@code []}, {@code ""}? */
    private static boolean isEmptyPair(char before, char after) {
        if (before == '"') {
            return after == '"';
        }
        int opener = OPENERS.indexOf(before);
        return opener >= 0 && CLOSERS.charAt(opener) == after;
    }

    /**
     * Finds the counterpart to the bracket at the cursor.
     *
     * <p>First to the right of the cursor, then left: whoever has just typed a
     * closing bracket stands behind it and means it. It's found via a depth
     * count that skips strings and comments — a curly brace inside a string
     * isn't one.
     *
     * @return four numbers — line and column of the bracket at the cursor, then
     *         those of the counterpart — or {@code null}
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

    /** Is there a bracket at this position that counts? */
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
     * Does this column lie in code — that is, not inside a string and not
     * behind a comment marker?
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
     * Counts its way through the document until the depth is back to zero.
     *
     * <p>Across line boundaries: an {@code fn} block runs over twenty lines,
     * and that's exactly where you want to know where it ends.
     */
    private int[] scanFor(int fromLine, int fromColumn, char bracket, char partner,
                          boolean forward) {
        int depth = 0;
        int lineIndex = fromLine;
        int column = fromColumn;
        // An upper bound, so that a document without a counterpart isn't
        // walked in full on every frame.
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

    /** Highlights both brackets of a pair. */
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
        // A blank line in the middle of the selection gets a thin stripe —
        // otherwise it looks as if it weren't part of it.
        int right = from == to && lineIndex != b[2]
                ? left + 4
                : columnX(textX, to);
        graphics.fill(left, lineY - 1, Math.min(right, x + width - 2),
                lineY + LINE_HEIGHT - 2, EditorColours.SELECTION);
    }

    // ---- Selection ----------------------------------------------------------

    public boolean hasSelection() {
        return anchorLine >= 0
                && (anchorLine != cursorLine || anchorColumn != cursorColumn);
    }

    /** Sets the anchor if none is set yet — for Shift movements. */
    private void anchorIfNeeded() {
        if (anchorLine < 0) {
            anchorLine = cursorLine;
            anchorColumn = cursorColumn;
        }
    }

    private void clearSelection() {
        anchorLine = -1;
    }

    /** Start and end of the selection, sorted in reading order. */
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

    /** Deletes the selection and sets the cursor to its start. */
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
     * Inserts text that may contain line breaks.
     *
     * <p>This is the reason the clipboard is needed at all: typing out a
     * program from the documentation is the sort of work no one does twice.
     */
    private void insertMultiline(String text) {
        if (hasSelection()) {
            deleteSelection();
        }
        // Normalise foreign line endings and tabs: otherwise whoever copies
        // from an editor drags in control characters that have no business
        // here.
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

        // Take over the line's indentation, and one level more after {.
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
        // If the cursor is between the braces, the closing one goes on its own
        // line with the cursor in between — the shape you would have written a
        // block in anyway.
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
            // Four spaces at the line start go away together.
            int remove = 1;
            if (column >= INDENT && line.substring(0, column).endsWith("    ")
                    && line.substring(0, column).isBlank()) {
                remove = INDENT;
            }
            // And an empty pair too: what the editor set together, it removes
            // together. Otherwise after every typo a lone bracket stays behind.
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
     * Brings the cursor back into view — vertically and horizontally.
     *
     * <p>Horizontally with a margin of three columns: if the cursor sat right
     * at the edge while typing, the view would jump on every character, and
     * you'd be writing against a wall.
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

    /** The longest line, in characters — for the horizontal bar. */
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

    // ---- Suggestions -------------------------------------------------------

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

    /** Accepts the selected suggestion and replaces the started word. */
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

        // Wide enough for word and shape side by side: "row" alone is a word,
        // "row  string expr" is an instruction.
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

        // Flip upward when there's no room left below.
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
            // Right-aligned and muted: the shape is the explanation of the word
            // and not what you select.
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
    // ---- Undo -------------------------------------------------------
    //
    // The editing steps from here on stand without a visibility modifier: the
    // test in the same package calls them directly. Over the keyboard they
    // couldn't be checked — hasControlDown() asks the window, and in the test
    // there is none.

    /**
     * A state of the text together with the cursor.
     *
     * <p>Whole states instead of individual changes: a program in the terminal
     * is a few dozen lines, and a change log with positions is the sort of code
     * where a bug only shows once the text is already broken.
     */
    private record Snapshot(List<String> lines, int cursorLine, int cursorColumn) {
    }

    /**
     * What a change belongs to.
     *
     * <p>Consecutive keystrokes of the same kind are merged into one step.
     * Without this an undo would take back exactly one character, and an
     * accidentally overwritten word would be eight moves.
     */
    enum EditKind { TYPING, DELETING, STRUCTURAL }

    /** No one reaches back further; beyond that only memory grows. */
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
     * Remembers the state before a change.
     *
     * <p>If a change of the same kind continues on the same line, nothing is
     * remembered: the state from before the run is already on top of the
     * stack, and that's exactly where an undo should lead.
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
        // After a jump through history every run starts anew.
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

    // ---- Word-wise movement ------------------------------------------------

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * One word to the left.
     *
     * <p>First over separators, then over the word: this lands you before the
     * word and not in the gap ahead of it — otherwise every other word would
     * need two keystrokes.
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

    /** Deletes the word to the left of the cursor. */
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

    // ---- Reshaping lines --------------------------------------------------

    /**
     * Indents the affected lines in or out.
     *
     * <p>With a selection all lines in it, without one only the current. This
     * is why Tab does something different during a selection than otherwise:
     * inserting four spaces and deleting the selection in the process would,
     * with several lines, never be what was meant.
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

    /** Duplicates the current line below itself. */
    void duplicateLine() {
        lines.add(cursorLine + 1, lines.get(cursorLine));
        cursorLine++;
        clearSelection();
        ensureVisible();
        changed();
    }
    // ---- Mouse -------------------------------------------------------------

    /** How far apart two clicks may be to count as one. */
    private static final long DOUBLE_CLICK_MILLIS = 260;

    private long lastClickTime;
    private int lastClickLine = -1;
    private int lastClickColumn = -1;

    /** Which line a mouse pointer is on. */
    private int lineAt(double mouseY) {
        int row = (int) Math.floor((mouseY - y - 10) / LINE_HEIGHT);
        return Mth.clamp(scrollLine + row, 0, lines.size() - 1);
    }

    /**
     * Which column a mouse pointer is on.
     *
     * <p>What's sought is the gap it's closest to, not the character under it:
     * a click on the right half of a letter puts the cursor behind it. Without
     * this you'd never reach the line end.
     */
    private int columnAt(int lineIndex, double mouseX) {
        String line = lines.get(lineIndex);
        int textX = x + GUTTER_WIDTH + TEXT_LEFT;
        // A division instead of a loop: with a monospaced font the column is
        // the rounded distance. The rounding puts the boundary in the middle of
        // a character — a click on the right half lands behind it, as in every
        // editor.
        int column = (int) Math.round((mouseX - textX) / advance()) + scrollColumn;
        return Mth.clamp(column, 0, line.length());
    }

    private boolean inside(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    /**
     * Selects the word at a position.
     *
     * <p>If the cursor is not in a word but in a gap, the gap is selected.
     * Doing nothing would be the worse answer: a double-click that sometimes
     * does nothing feels broken.
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
     * A click sets the cursor — with Shift it drags the selection there, a
     * double-click takes the word.
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
     * Dragging selects.
     *
     * <p>If the pointer wanders past the edge, the text scrolls along —
     * otherwise every selection ends at the visible view, and longer spans
     * could only be reached with the keyboard.
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
    // ---- Search -----------------------------------------------------------

    /**
     * The search within the editor.
     *
     * <p>A program grows longer than fits on the screen long before it grows
     * complicated. Without search only scrolling is left, and whoever changes
     * a connector name has to find every spot by hand.
     *
     * <p>The search ignores case: the language does distinguish it, but
     * whoever searches usually only roughly knows how it was written.
     */
    private boolean searching;
    private String searchTerm = "";
    private int matchIndex;

    /**
     * Whether the bar also offers a replace.
     *
     * <p>The same bar for both and no second window: search and replace are one
     * action with one field more. Ctrl+F opens it without, Ctrl+H with — and
     * one turns into the other without closing and reopening.
     */
    private boolean replacing;
    private String replaceTerm = "";

    /** Which of the two fields is typing right now. Tab switches. */
    private boolean editingReplacement;

    public boolean isSearching() {
        return searching;
    }

    String searchTerm() {
        return searchTerm;
    }

    /** Opens the search. A selection becomes the search term. */
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

    /** What to replace with. */
    void setReplaceTerm(String term) {
        replaceTerm = term == null ? "" : term;
    }

    /** The first visible column — for the scroll test. */
    int firstVisibleColumn() {
        return scrollColumn;
    }

    void setSearchTerm(String term) {
        searchTerm = term == null ? "" : term;
        matchIndex = 0;
        jumpToMatch();
    }

    /**
     * All matches, from top to bottom.
     *
     * <p>Searched anew on every keystroke. A program in the terminal is a few
     * dozen lines — maintaining a match list would be bookkeeping for a cost no
     * one would notice.
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
                // Past the match and not one character further: otherwise a
                // search for "aa" in "aaa" would find two spots that overlap
                // each other — and "replace all" would write into its own
                // replacement.
                from = at + needle.length();
            }
        }
        return found;
    }

    /** To the next match, or the previous one. */
    void step(int direction) {
        List<int[]> found = matches();
        if (found.isEmpty()) {
            return;
        }
        matchIndex = Math.floorMod(matchIndex + direction, found.size());
        jumpToMatch();
    }

    /** Which match is currently active, from one. Zero means none. */
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
        // The match is selected, not just moved to: then Ctrl+C carries it on,
        // and typing replaces it.
        select(at[0], at[1], at[0], at[1] + searchTerm.length());
        ensureVisible();
    }

    /** Lays a muted stripe under every match in this line. */
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
     * The search-and-replace bar above the text.
     *
     * <p>The active field carries an underline; without it you wouldn't know
     * after a Tab where you're typing. The number of matches stands on the
     * right and turns red when there are none — an empty result is information
     * and not an error, but you should see it.
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

    /** A field of the search bar; the active one gets an underline. */
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
     * Key presses while the search is open.
     *
     * <p>They go to the search and not to the text: whoever searches types a
     * word, not code. Escape closes, Enter goes forward, Shift and Enter go
     * back.
     */
    private boolean handleSearchKey(int key) {
        switch (key) {
            case 256 -> { // Escape
                closeSearch();
                return true;
            }
            case 258 -> { // Tab switches between the two fields
                if (replacing) {
                    editingReplacement = !editingReplacement;
                }
                return true;
            }
            case 257, 335 -> { // Enter
                if (!replacing) {
                    step(net.minecraft.client.gui.screens.Screen.hasShiftDown() ? -1 : 1);
                    return true;
                }
                // With Alt all at once — Ctrl and Enter is already the accept
                // in the terminal, and two meanings for one gesture are one too
                // many.
                if (net.minecraft.client.gui.screens.Screen.hasAltDown()) {
                    replaceAll();
                } else {
                    replaceCurrent();
                }
                return true;
            }
            case 259 -> { // Backspace
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
     * Replaces the match currently moved to and goes to the next.
     *
     * <p>The match is selected — {@link #jumpToMatch()} sees to that. So the
     * replace goes through the selection, with the same motions as when typing,
     * and an undo takes it back as one step.
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
        // After the replace the next match sits at the same number: the one
        // just replaced is out of the list.
        List<int[]> rest = matches();
        if (rest.isEmpty()) {
            return;
        }
        matchIndex = Math.floorMod(matchIndex, rest.size());
        jumpToMatch();
    }

    /**
     * Replaces all matches at once.
     *
     * <p>Line by line and from back to front within a line — otherwise the
     * first replacement would shift the spots of all the following ones. An
     * undo takes the whole thing back as one step; anything else would, with
     * twenty spots, be twenty moves.
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
