package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.client.ClientDeployState;
import dev.devpanda.factorynetwork.client.ClientProjectState;
import dev.devpanda.factorynetwork.client.FnFonts;
import dev.devpanda.factorynetwork.lang.Diagnostic;
import dev.devpanda.factorynetwork.lang.Project;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The code tab: a project made of several files.
 *
 * <p>Above the editor a row with the files, below it a footer with the
 * compiler's message and the button to apply.
 *
 * <p><b>The files run across, not down a column.</b> A column would have cost
 * a fifth of the editor width — at forty-four columns that is twelve. A row
 * costs one line, and it is also the layout familiar from editor tabs.
 *
 * <p><b>The whole project is compiled, only the open file is marked.</b>
 * Without that separation, an error on line three of
 * {@code worker.mf} marked the third line of the currently open
 * {@code anzeigen.mf} — the error you spend longest hunting for, because
 * nothing is wrong there at all.
 *
 * <p><b>This is where you look things up and fix them, not where you write.</b>
 * Eight lines are what fits beside a player inventory. Anyone who renames or
 * deletes a file, or actually builds a program, clicks the symbol on the
 * right of the file row and gets {@link CodeScreen} — the same project, the
 * same draft, only without the 288 pixels.
 */
public class CodeTabView {

    /** Height of the file row above the editor. */
    private static final int FILE_ROW = 11;

    /** Height of the footer below it. */
    private static final int FOOTER = 14;

    private static final int BUTTON_WIDTH = 66;
    private static final int BUTTON_HEIGHT = 12;
    private static final int FILE_GAP = 8;

    private final TerminalScreen screen;
    private final Font font;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final CodeEditor editor;

    private Project project;
    private String open;

    /** All the project's messages, and among them those of the open file. */
    private List<Diagnostic> problems = List.of();
    private List<Diagnostic> openProblems = List.of();
    private String status = "";
    private int statusColour = TerminalScreen.TEXT_FAINT;

    /**
     * The feedback for the last marker, or {@code null}.
     *
     * <p><b>Without it the gesture is invisible.</b> The marker sits in the
     * world, and you cannot see that while the terminal is in front of it — a
     * Ctrl+click thus looked as if nothing had happened. The standalone window
     * has had this line for a long time; here it was missing, because the
     * gesture was missing here entirely.
     */
    private Component located;

    public CodeTabView(TerminalScreen screen, Font font, int x, int y, int width, int height) {
        this.screen = screen;
        this.font = font;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        // From the draft and not from what the server last reported: someone
        // coming back from the big window would otherwise not find their
        // unsaved lines again.
        this.project = ClientProjectState.draft();
        this.open = project.names().get(0);
        this.editor = new CodeEditor(font, x + 2, y + FILE_ROW + 2, width - 4,
                height - FILE_ROW - FOOTER - 2, project.source(open));
        this.editor.setChangeListener(this::onChanged);
        recheck();
    }

    private void onChanged(String text) {
        // What the server last said applies to the text from back then.
        ClientDeployState.clear();
        // And the marker applied to the name that was clicked.
        located = null;
        project = project.with(open, text);
        ClientProjectState.setDraft(project);
        recheck();
    }

    /** Compiles the whole project and sorts the messages. */
    private void recheck() {
        // A file someone else is holding can be read but not changed. The
        // server would reject a change anyway — not allowing it here in the
        // first place spares the moment when your own text vanishes on the
        // next state.
        editor.setReadOnly(
                dev.devpanda.factorynetwork.client.ClientProjectState.heldBy(open) != null);
        problems = project.parse(
                dev.devpanda.factorynetwork.client.ClientNetworkView.INSTANCE)
                .diagnostics();
        openProblems = problems.stream()
                .filter(problem -> problem.file().equals(open))
                .toList();
        if (problems.isEmpty()) {
            status = Component.translatable("screen.factorynetwork.terminal.ok").getString();
            statusColour = TerminalScreen.TEXT_FAINT;
            return;
        }
        Diagnostic first = problems.get(0);
        // The file name only when it is not the open one — otherwise it
        // appears in every message and says nothing.
        String where = first.file().equals(open) ? "" : first.file() + " ";
        status = where + first.span().line() + ": " + first.message();
        statusColour = first.isError() ? TerminalScreen.BAD : TerminalScreen.WARN;
    }

    /** The project with whatever is currently in the editor. */
    public Project project() {
        return project;
    }

    public String text() {
        return editor.text();
    }

    /** Where the cursor is, for the window's status line. */
    public Component cursorPosition() {
        return Component.translatable("screen.factorynetwork.terminal.cursor",
                editor.cursorLine() + 1, editor.cursorColumn() + 1);
    }

    // ---- The file row ------------------------------------------------------

    private int fileWidth(String name) {
        return font.width(FnFonts.mono(name));
    }

    private int fileX(String wanted) {
        int at = x + 3;
        for (String name : project.names()) {
            if (name.equals(wanted)) {
                return at;
            }
            at += fileWidth(name) + FILE_GAP;
        }
        return at;
    }

    /**
     * The space after the last file — that is where the plus sign sits.
     *
     * <p>At most up to just before the symbol for the big window: with six
     * files it would otherwise run over it, and two buttons in the same place
     * are one too many.
     */
    private int plusX() {
        int at = x + 3;
        for (String name : project.names()) {
            at += fileWidth(name) + FILE_GAP;
        }
        return Math.min(at, expandX() - 10);
    }

    /** The symbol at the far right that opens the editor in the whole window. */
    private int expandX() {
        return x + width - 12;
    }

    private boolean overExpand(double mouseX, double mouseY) {
        return overFiles(mouseY) && mouseX >= expandX() - 2 && mouseX < expandX() + 10;
    }

    /**
     * A frame with one open corner.
     *
     * <p>Drawn, not typed: the characters meant for this are not in the font,
     * and a little box made of four strokes costs four lines.
     */
    private void drawExpandIcon(GuiGraphics graphics, boolean hovered) {
        int left = expandX();
        int top = y + 2;
        int colour = 0xFF000000 | (hovered ? TerminalScreen.TEXT : TerminalScreen.TEXT_FAINT);
        graphics.fill(left, top, left + 8, top + 1, colour);
        graphics.fill(left, top + 7, left + 8, top + 8, colour);
        graphics.fill(left, top, left + 1, top + 8, colour);
        graphics.fill(left + 7, top, left + 8, top + 8, colour);
        graphics.fill(left + 3, top + 3, left + 6, top + 4, colour);
        graphics.fill(left + 5, top + 2, left + 6, top + 5, colour);
    }

    private void drawFiles(GuiGraphics graphics, int mouseX, int mouseY) {
        for (String name : project.names()) {
            int at = fileX(name);
            boolean active = name.equals(open);
            boolean broken = problems.stream()
                    .anyMatch(problem -> problem.isError() && problem.file().equals(name));
            int colour = broken ? TerminalScreen.BAD
                    : active ? TerminalScreen.TEXT : TerminalScreen.TEXT_DIM;
            graphics.drawString(font, FnFonts.mono(name), at, y + 1, colour, false);
            if (active) {
                graphics.fill(at, y + FILE_ROW - 2, at + fileWidth(name), y + FILE_ROW - 1,
                        0xFF000000 | TerminalScreen.ACCENT);
            }
        }
        boolean hovered = overPlus(mouseX, mouseY);
        graphics.drawString(font, FnFonts.mono("+"), plusX(), y + 1,
                hovered ? TerminalScreen.TEXT : TerminalScreen.TEXT_FAINT, false);
        drawExpandIcon(graphics, overExpand(mouseX, mouseY));
    }

    private boolean overFiles(double mouseY) {
        return mouseY >= y && mouseY < y + FILE_ROW;
    }

    private boolean overPlus(double mouseX, double mouseY) {
        return overFiles(mouseY) && mouseX >= plusX()
                && mouseX < plusX() + fileWidth("+") + 2;
    }

    /**
     * Switches the open file.
     *
     * <p>The text of the previous one is already in the project — every
     * keystroke writes it there. Here we only fetch what is in the new one.
     */
    private void openFile(String name) {
        if (name.equals(open)) {
            return;
        }
        open = name;
        editor.setText(project.source(name));
        recheck();
    }

    /**
     * Creates a file — with a placeholder name.
     *
     * <p>It is named in the big window; here no line is free for an input
     * field. Anyone who wants to type a name right away opens the new file
     * there in the first place.
     */
    private void addFile() {
        String name = project.freeNameLike("datei");
        project = project.with(name, "");
        ClientProjectState.setDraft(project);
        openFile(name);
    }

    // ---- Drawing -----------------------------------------------------------

    private int buttonX() {
        return x + width - BUTTON_WIDTH - 2;
    }

    private int buttonY() {
        return y + height - FOOTER + 1;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        drawFiles(graphics, mouseX, mouseY);
        editor.render(graphics, openProblems);

        int footerY = y + height - FOOTER + 3;
        String shown = ClientDeployState.hasMessage() ? ClientDeployState.message() : status;
        int colour = ClientDeployState.hasMessage()
                ? ClientDeployState.wasAccepted() ? TerminalScreen.GOOD : TerminalScreen.BAD
                : statusColour;
        // The marker takes precedence: it is the answer to a gesture that
        // just happened.
        if (located != null) {
            shown = located.getString();
            colour = TerminalScreen.ACCENT;
        }
        graphics.drawString(font,
                font.plainSubstrByWidth(shown, buttonX() - x - 8), x + 3, footerY,
                colour, false);

        boolean hovered = overButton(mouseX, mouseY);
        graphics.fill(buttonX(), buttonY(), buttonX() + BUTTON_WIDTH,
                buttonY() + BUTTON_HEIGHT,
                hovered ? TerminalScreen.BUTTON_HOVER : TerminalScreen.BUTTON);
        Component label = Component.translatable("screen.factorynetwork.terminal.deploy");
        graphics.drawString(font, label,
                buttonX() + (BUTTON_WIDTH - font.width(label)) / 2, buttonY() + 2,
                TerminalScreen.TEXT, false);
    }

    private boolean overButton(double mouseX, double mouseY) {
        return mouseX >= buttonX() && mouseX < buttonX() + BUTTON_WIDTH
                && mouseY >= buttonY() && mouseY < buttonY() + BUTTON_HEIGHT;
    }

    public void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (overButton(mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable(
                    "screen.factorynetwork.terminal.deploy.hint"), mouseX, mouseY);
            return;
        }
        if (overExpand(mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable(
                    "screen.factorynetwork.terminal.expand"), mouseX, mouseY);
            return;
        }
        if (overPlus(mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable(
                    "screen.factorynetwork.terminal.newfile"), mouseX, mouseY);
            return;
        }
        EditorTooltip.render(graphics, font, editor, project, openProblems, mouseX, mouseY);
    }

    /**
     * Ctrl+click on a name.
     *
     * <p>In the code it leads to the declaration, in the world it sets a
     * marker. Unlike in the standalone window there is no footer here to add
     * the coordinate — the marker itself is the answer, and it stands visible
     * through the wall as soon as the terminal closes.
     */
    private boolean jumpToName(double mouseX, double mouseY) {
        String word = editor.wordAt(mouseX, mouseY);
        NameJump.Jump jump = NameJump.resolve(word, project);
        if (jump == null) {
            return false;
        }
        if (jump.inCode() != null) {
            openFile(jump.inCode().file());
            editor.jumpTo(jump.inCode().line(), jump.inCode().column());
            return true;
        }
        dev.devpanda.factorynetwork.client.LocateMarker.mark(jump.inWorld(), word);
        located = Component.translatable("screen.factorynetwork.code.located", word,
                jump.inWorld().getX(), jump.inWorld().getY(), jump.inWorld().getZ());
        return true;
    }

    /** A click on the footer jumps to the first message — across files too. */
    private boolean jumpToFirstProblem(double mouseX, double mouseY) {
        if (problems.isEmpty() || mouseY < y + height - FOOTER || mouseX >= buttonX()) {
            return false;
        }
        Diagnostic first = problems.get(0);
        openFile(first.file().isEmpty() ? open : first.file());
        editor.jumpTo(first);
        return true;
    }

    // ---- Controls ----------------------------------------------------------

    public boolean keyPressed(int key, int scanCode, int modifiers) {
        // The same key as in the standalone window: anyone in the tab facing
        // someone else's file has the same question.
        if (key == RequestEdit.KEY && RequestEdit.ask(open)) {
            return true;
        }
        return editor.keyPressed(key, scanCode, modifiers);
    }

    public boolean charTyped(char character, int modifiers) {
        return editor.charTyped(character, modifiers);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return editor.mouseClicked(mouseX, mouseY, button);
        }
        // Ctrl+click on a name: jump to the declaration or mark the block in
        // the world. For a long time this existed only in the standalone
        // window — the same gesture that led nowhere here.
        if (net.minecraft.client.gui.screens.Screen.hasControlDown()
                && jumpToName(mouseX, mouseY)) {
            return true;
        }
        if (overButton(mouseX, mouseY)) {
            screen.deploy();
            return true;
        }
        if (overExpand(mouseX, mouseY)) {
            screen.openBigEditor();
            return true;
        }
        if (overPlus(mouseX, mouseY)) {
            addFile();
            return true;
        }
        if (overFiles(mouseY)) {
            for (String name : project.names()) {
                int at = fileX(name);
                if (mouseX >= at && mouseX < at + fileWidth(name)) {
                    openFile(name);
                    return true;
                }
            }
            return true;
        }
        if (jumpToFirstProblem(mouseX, mouseY)) {
            return true;
        }
        return editor.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        return editor.mouseDragged(mouseX, mouseY, button);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return editor.mouseScrolled(mouseX, mouseY, delta);
    }
}
