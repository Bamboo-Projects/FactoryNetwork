package dev.devpanda.factorynetwork.client.screen;

import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import dev.devpanda.factorynetwork.platform.client.Keys;
import dev.devpanda.factorynetwork.client.ClientDeployState;
import dev.devpanda.factorynetwork.client.ClientNetworkState;
import dev.devpanda.factorynetwork.client.ClientProjectState;
import dev.devpanda.factorynetwork.client.FnFonts;
import dev.devpanda.factorynetwork.lang.Diagnostic;
import dev.devpanda.factorynetwork.lang.Project;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The editor across the whole window.
 *
 * <p><b>Why a second window and not a wider tab:</b> The terminal is 288
 * pixels wide, because the player inventory sits beneath it and the slot grid
 * is not negotiable. At that width, after gutter and margin, forty-four
 * columns and eight lines remain — enough to look something up, too few to
 * work. A file column would have cost a quarter of that.
 *
 * <p>Here there is no inventory, and so no fixed width either. The file tree
 * on the left, the editor on the right, the compiler's message and the deploy
 * button below.
 *
 * <p><b>Both windows work on the same draft.</b> It lives in
 * {@link ClientProjectState} and not in either of the two views — otherwise
 * every switch would be a loss.
 */
public class CodeScreen extends Screen {

    private static final int MARGIN = 12;
    private static final int HEADER = 18;
    private static final int FOOTER = 20;
    private static final int PANEL_WIDTH = 116;
    private static final int GAP = 7;

    private static final int BUTTON_WIDTH = 78;
    private static final int BUTTON_HEIGHT = 14;

    /**
     * The colours of the casing.
     *
     * <p>The same rule as in the terminal — <b>what sits recessed is darker
     * than its ground</b> — but with larger steps. In the terminal the three
     * layers sit close together across 288 pixels and border each other
     * everywhere; there four values of difference are enough, because the eye
     * sees the edge and not the surface. Here a surface is half a screen wide,
     * and then all you see is the surface. The first draft put the file tree
     * at {@code 0x14181A} in front of a pane of {@code 0x181E1B} — it was
     * invisible, and the question of where the side column had gone was a fair
     * one.
     */
    private static final int CASE = 0xFF232B27;
    private static final int GLASS = 0xFF181E1B;

    /** The edge between two layers — bright enough to see it. */
    static final int EDGE = 0xFF39443D;

    private final Screen parent;
    private final BlockPos controller;

    /**
     * The shortcuts there are.
     *
     * <p><b>Because you won't find them otherwise.</b> An editor in the game
     * has no menu bar to check and no docs lying next to it. Ctrl+Space,
     * Ctrl+H and F2 are there, but anyone who doesn't know they exist never
     * uses them — and the editor feels poorer than it is.
     *
     * <p>Hard-coded and not in the language file: these are key names, and
     * those are the same in every language. What they do sits beside them and
     * comes from the language file.
     */
    private static final String[][] HELP = {
            {"Strg+Leer", "help.suggest"},
            {"Tab", "help.accept"},
            {"Strg+F", "help.find"},
            {"Strg+H", "help.replace"},
            {"Strg+Z", "help.undo"},
            {"Strg+Umschalt+Z", "help.redo"},
            {"Strg+D", "help.duplicate"},
            {"Tab / Umschalt+Tab", "help.indent"},
            {"Strg+Links / Rechts", "help.word"},
            {"Strg+Pos1 / Ende", "help.ends"},
            {"Umschalt+Rollen", "help.sideways"},
            {"Strg+Eingabe", "help.deploy"},
            {"Strg+Klick", "help.goto"},
            {"F2", "help.rename"},
            {"F4", "help.request"},
            {"F8", "help.problems"},
            {"Rechtsklick", "help.menu"},
            {"F1", "help.close"},
    };

    private ProjectPanel panel;
    private CodeEditor editor;

    /** Whether the shortcut list is open. */
    private boolean showingHelp;

    /**
     * Whether the list of all messages is open.
     *
     * <p>The footer has room for one message, and for a long time that was
     * all there was: whoever had five errors in three files saw the first,
     * fixed it, and learned of the next. The list shows them together and
     * takes a click as "take me there".
     */
    private boolean showingProblems;

    /** This many messages are listed; the rest is counted. */
    private static final int PROBLEM_ROWS = 14;

    /**
     * The feedback for the last marker, or {@code null}.
     *
     * <p>Without it the action would be invisible: the marker sits out in the
     * world, and you only see that once the window is closed.
     */
    private Component located;

    private Project project;
    private String open;
    private List<Diagnostic> problems = List.of();
    private List<Diagnostic> openProblems = List.of();
    private String status = "";
    private int statusColour = TerminalScreen.TEXT_FAINT;

    public CodeScreen(Screen parent, BlockPos controller) {
        super(Component.translatable("screen.factorynetwork.code.title"));
        this.parent = parent;
        this.controller = controller;
        this.project = ClientProjectState.draft();
        this.open = project.names().get(0);
    }

    @Override
    protected void init() {
        int contentTop = MARGIN + HEADER;
        int contentHeight = height - contentTop - MARGIN - FOOTER;
        int editorX = MARGIN + PANEL_WIDTH + GAP;

        panel = new ProjectPanel(font, MARGIN + 3, contentTop, PANEL_WIDTH - 3, contentHeight,
                this::onProjectChanged, this::openFile);
        panel.setProject(project);
        panel.setOpen(open);

        editor = new CodeEditor(font, editorX, contentTop, width - editorX - MARGIN,
                contentHeight, project.source(open));
        editor.setChangeListener(this::onTextChanged);
        editor.nameFile(open);
        recheck();
    }

    // ---- The draft -------------------------------------------------------

    private void onTextChanged(String text) {
        ClientDeployState.clear();
        located = null;
        project = project.with(open, text);
        publish();
    }

    /** The file tree created, renamed or deleted files. */
    private void onProjectChanged(Project next) {
        ClientDeployState.clear();
        project = next;
        publish();
    }

    private void publish() {
        ClientProjectState.setDraft(project);
        panel.setProject(project);
        recheck();
    }

    private void openFile(String name) {
        if (!project.files().containsKey(name)) {
            return;
        }
        open = name;
        panel.setOpen(name);
        editor.openFile(name, project.source(name));
        recheck();
    }

    /** Compiles the whole project and sorts the messages. */
    private void recheck() {
        // A file that someone else holds can be read but not changed. The
        // server would reject an edit anyway — not allowing it here in the
        // first place spares the moment when your own text vanishes on the
        // next state update.
        editor.setReadOnly(
                dev.devpanda.factorynetwork.client.ClientProjectState.heldBy(open) != null);
        problems = project.parse(
                dev.devpanda.factorynetwork.client.ClientNetworkView.INSTANCE)
                .diagnostics();
        openProblems = problems.stream()
                .filter(problem -> problem.file().equals(open))
                .toList();
        panel.setProblems(problems);
        if (problems.isEmpty()) {
            status = Component.translatable("screen.factorynetwork.terminal.ok").getString();
            statusColour = TerminalScreen.TEXT_FAINT;
            return;
        }
        Diagnostic first = problems.get(0);
        String where = first.file().equals(open) ? "" : first.file() + " ";
        status = where + first.span().line() + ": " + first.message();
        if (problems.size() > 1) {
            // The first one is spelled out, the others are at least counted:
            // a footer that shows one message reads as "there is one".
            status = Component.translatable("screen.factorynetwork.code.problems.more",
                    problems.size() - 1).getString() + "  " + status;
        }
        statusColour = first.isError() ? TerminalScreen.BAD : TerminalScreen.WARN;
    }

    /**
     * Jumps to where the word under the pointer is defined.
     *
     * <p>All files share one namespace, and so from the point of use there is
     * no seeing where the definition sits. With three files you can still hunt
     * for it, with eight no longer.
     *
     * @return whether a jump happened
     */
    private boolean goToDefinition(double mouseX, double mouseY) {
        String word = editor.wordAt(mouseX, mouseY);
        NameJump.Jump jump = NameJump.resolve(word, project);
        if (jump == null) {
            // A word of the language has no definition in the code: its
            // explanation is the page in the manual, with its scenes.
            return dev.devpanda.factorynetwork.client.ManualTopics.open(word);
        }
        if (jump.inCode() != null) {
            openFile(jump.inCode().file());
            editor.jumpTo(jump.inCode().line(), jump.inCode().column());
            return true;
        }
        // A name from the world: the question is not "where is this in the
        // code" but "which block is this", and a marker answers that.
        dev.devpanda.factorynetwork.client.LocateMarker.mark(jump.inWorld(), word);
        located = Component.translatable("screen.factorynetwork.code.located", word,
                jump.inWorld().getX(), jump.inWorld().getY(), jump.inWorld().getZ());
        return true;
    }

    private void deploy() {
        ClientPacketDistributor.sendToServer(
                dev.devpanda.factorynetwork.network.packet.DeployProgramPacket
                        .of(controller, project));
    }

    // ---- Drawing ----------------------------------------------------------

    private int buttonX() {
        return width - MARGIN - BUTTON_WIDTH;
    }

    private int buttonY() {
        return height - MARGIN - FOOTER + 3;
    }

    private boolean overButton(double mouseX, double mouseY) {
        return mouseX >= buttonX() && mouseX < buttonX() + BUTTON_WIDTH
                && mouseY >= buttonY() && mouseY < buttonY() + BUTTON_HEIGHT;
    }

    /**
     * Draws the window.
     *
     * <p><b>No {@code super.render} and no {@code renderBackground}.</b>
     * {@code Screen.render} first calls {@code renderBackground}, and that
     * calls {@code processBlurEffect} — a post-processing step that blurs the
     * image in the buffer. In vanilla the call comes at the start, so it hits
     * the world behind the window; the window itself is then painted sharply
     * on top.
     *
     * <p>The first draft called {@code super.render} at the <b>end</b>. By
     * then everything drawn here was already in the buffer, and the blur ran
     * over our own surface: a one-pixel bright edge became a twenty-pixel
     * gradient, text became a smear. In a screenshot it looked like a wrong
     * choice of colour, and I first went and fiddled with the colours too —
     * it only became measurable at a cross-section through an edge that was
     * no longer there.
     *
     * <p>The world behind needs no blur: the window covers it completely.
     * What remains of {@code Screen.render} is the loop over the widgets —
     * in case any are ever added here.
     */
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, CASE);
        graphics.fill(MARGIN - 4, MARGIN - 4, width - MARGIN + 4, height - MARGIN + 4, GLASS);
        // The bright edge around the pane: without it the content floats in
        // the metal, because only a few values lie between the two.
        outline(graphics, MARGIN - 4, MARGIN - 4, width - MARGIN + 4, height - MARGIN + 4,
                0xFF2E3833);

        drawHeader(graphics);
        panel.extractRenderState(graphics, mouseX, mouseY);
        editor.extractRenderState(graphics, openProblems);
        drawFooter(graphics, mouseX, mouseY);
        if (showingHelp) {
            drawHelp(graphics);
        } else if (showingProblems) {
            drawProblems(graphics, mouseX, mouseY);
        }

        for (Renderable renderable : this.renderables) {
            renderable.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    /**
     * The header: where you are and how you get back.
     *
     * <p>The hint about Escape is there because this window has no inventory
     * and so doesn't look like something you could close like any other.
     */
    private void drawHeader(GuiGraphicsExtractor graphics) {
        graphics.text(font, FnFonts.bold(title), MARGIN, MARGIN, TerminalScreen.TEXT,
                false);
        Component hint = FnFonts.mono(Component.translatable(showingHelp
                ? "screen.factorynetwork.code.back"
                : "screen.factorynetwork.code.help"));
        graphics.text(font, hint, width - MARGIN - font.width(hint), MARGIN,
                TerminalScreen.TEXT_FAINT, false);
        graphics.fill(MARGIN, MARGIN + 12, width - MARGIN, MARGIN + 13, EDGE);
    }

    /** A frame made of four lines. */
    static void outline(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom,
                        int colour) {
        graphics.fill(left, top, right, top + 1, colour);
        graphics.fill(left, bottom - 1, right, bottom, colour);
        graphics.fill(left, top, left + 1, bottom, colour);
        graphics.fill(right - 1, top, right, bottom, colour);
    }

    private void drawFooter(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int footerY = height - MARGIN - FOOTER + 6;
        graphics.fill(MARGIN, height - MARGIN - FOOTER, width - MARGIN,
                height - MARGIN - FOOTER + 1, EDGE);
        String shown = ClientDeployState.hasMessage() ? ClientDeployState.message() : status;
        int colour = ClientDeployState.hasMessage()
                ? ClientDeployState.wasAccepted() ? TerminalScreen.GOOD : TerminalScreen.BAD
                : statusColour;
        if (located != null) {
            shown = located.getString();
            colour = TerminalScreen.ACCENT;
        }
        graphics.text(font,
                FnFonts.mono(font.plainSubstrByWidth(shown, buttonX() - MARGIN - 90)),
                MARGIN, footerY, colour, false);

        // To the right of the button: where the cursor is.
        Component position = FnFonts.mono(Component.translatable(
                "screen.factorynetwork.terminal.cursor",
                editor.cursorLine() + 1, editor.cursorColumn() + 1));
        int positionX = buttonX() - font.width(position) - 10;
        graphics.text(font, position, positionX, footerY,
                TerminalScreen.TEXT_FAINT, false);

        // And before it, what state the text is in. Three cases, one place:
        // not yet at the server, at the server but not deployed, or identical
        // to what is running. In the last case nothing is shown there — a
        // hint that is always present goes unread.
        String state = null;
        int stateColour = TerminalScreen.TEXT_FAINT;
        String holder = ClientProjectState.heldBy(open);
        if (holder != null) {
            // Before anything else: as long as someone else holds the file,
            // any other information about its state is irrelevant.
            Component label = FnFonts.mono(Component.translatable(
                    "screen.factorynetwork.code.held_by", holder));
            graphics.text(font, label, positionX - font.width(label) - 12, footerY,
                    TerminalScreen.WARN, false);
            return;
        }
        if (ClientProjectState.isDirty()) {
            state = "screen.factorynetwork.code.saving";
        } else if (!ClientProjectState.isDeployed()) {
            state = "screen.factorynetwork.code.unsaved";
            stateColour = TerminalScreen.WARN;
        }
        if (state != null) {
            Component label = FnFonts.mono(Component.translatable(state));
            graphics.text(font, label, positionX - font.width(label) - 12, footerY,
                    stateColour, false);
        }

        boolean hovered = overButton(mouseX, mouseY);
        graphics.fill(buttonX(), buttonY(), buttonX() + BUTTON_WIDTH,
                buttonY() + BUTTON_HEIGHT,
                hovered ? TerminalScreen.BUTTON_HOVER : TerminalScreen.BUTTON);
        Component label = FnFonts.mono(
                Component.translatable("screen.factorynetwork.terminal.deploy"));
        graphics.text(font, label, buttonX() + (BUTTON_WIDTH - font.width(label)) / 2,
                buttonY() + 3, TerminalScreen.TEXT, false);
    }

    /**
     * The shortcut list over everything.
     *
     * <p>In two columns and centred: it is a reference card, not a region of
     * the window. It covers the code while it is open — and rightly so,
     * because whoever opens it is looking something up and not writing right
     * now.
     */
    private void drawHelp(GuiGraphicsExtractor graphics) {
        int rows = HELP.length;
        int keyWidth = 0;
        int textWidth = 0;
        for (String[] entry : HELP) {
            keyWidth = Math.max(keyWidth, font.width(FnFonts.mono(entry[0])));
            textWidth = Math.max(textWidth, font.width(Component.translatable(
                    "screen.factorynetwork.code." + entry[1])));
        }
        int boxWidth = keyWidth + textWidth + 40;
        int boxHeight = rows * 12 + 28;
        int left = (width - boxWidth) / 2;
        int top = (height - boxHeight) / 2;

        graphics.fill(left, top, left + boxWidth, top + boxHeight, 0xF60E1214);
        outline(graphics, left, top, left + boxWidth, top + boxHeight, EDGE);
        graphics.text(font, FnFonts.bold(
                        Component.translatable("screen.factorynetwork.code.help.title")),
                left + 14, top + 8, TerminalScreen.TEXT, false);
        graphics.fill(left + 14, top + 20, left + boxWidth - 14, top + 21, EDGE);

        for (int i = 0; i < rows; i++) {
            int rowY = top + 26 + i * 12;
            graphics.text(font, FnFonts.mono(HELP[i][0]), left + 14, rowY,
                    TerminalScreen.ACCENT, false);
            graphics.text(font, Component.translatable(
                            "screen.factorynetwork.code." + HELP[i][1]),
                    left + 26 + keyWidth, rowY, TerminalScreen.TEXT_DIM, false);
        }
    }

    /** Where the list of messages sits: left, top, width, rows shown. */
    private int[] problemsBox() {
        int rows = Math.min(problems.size(), PROBLEM_ROWS);
        int more = problems.size() > rows ? 1 : 0;
        int boxWidth = Math.min(width - 2 * MARGIN - 40, 460);
        int boxHeight = (rows + more) * 12 + 28;
        return new int[] {(width - boxWidth) / 2, (height - boxHeight) / 2, boxWidth, rows};
    }

    /** The row of the list under the pointer, or -1. */
    private int problemRowAt(double mouseX, double mouseY) {
        int[] box = problemsBox();
        if (mouseX < box[0] || mouseX >= box[0] + box[2]) {
            return -1;
        }
        int row = (int) Math.floor((mouseY - box[1] - 26) / 12.0);
        return row >= 0 && row < box[3] ? row : -1;
    }

    /**
     * All messages of the project, one per row.
     *
     * <p>File and line first, in the accent colour: that is what one looks
     * for when deciding where to start. The row under the pointer is backed,
     * so that it is plain which one a click will take.
     */
    private void drawProblems(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int[] box = problemsBox();
        int left = box[0];
        int top = box[1];
        int boxWidth = box[2];
        int rows = box[3];
        int boxHeight = (rows + (problems.size() > rows ? 1 : 0)) * 12 + 28;

        graphics.fill(left, top, left + boxWidth, top + boxHeight, 0xF60E1214);
        outline(graphics, left, top, left + boxWidth, top + boxHeight, EDGE);
        graphics.text(font, FnFonts.bold(Component.translatable(
                        "screen.factorynetwork.code.problems.title", problems.size())),
                left + 14, top + 8, TerminalScreen.TEXT, false);
        graphics.fill(left + 14, top + 20, left + boxWidth - 14, top + 21, EDGE);

        int hovered = problemRowAt(mouseX, mouseY);
        for (int i = 0; i < rows; i++) {
            Diagnostic problem = problems.get(i);
            int rowY = top + 26 + i * 12;
            if (i == hovered) {
                graphics.fill(left + 8, rowY - 2, left + boxWidth - 8, rowY + 10, 0x30FFFFFF);
            }
            String where = (problem.file().isEmpty() ? open : problem.file())
                    + ":" + problem.span().line();
            Component place = FnFonts.mono(where);
            graphics.text(font, place, left + 14, rowY, TerminalScreen.ACCENT, false);
            int textX = left + 22 + font.width(place);
            graphics.text(font,
                    font.plainSubstrByWidth(problem.message(), left + boxWidth - 14 - textX),
                    textX, rowY, problem.isError() ? TerminalScreen.BAD : TerminalScreen.WARN,
                    false);
        }
        if (problems.size() > rows) {
            graphics.text(font, Component.translatable(
                            "screen.factorynetwork.code.problems.more", problems.size() - rows),
                    left + 14, top + 26 + rows * 12, TerminalScreen.TEXT_FAINT, false);
        }
    }

    private void renderTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (panel.hasMenu() || showingHelp || showingProblems) {
            return;
        }
        if (overButton(mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(Component.translatable(
                    "screen.factorynetwork.terminal.deploy.hint"), mouseX, mouseY);
            return;
        }
        EditorTooltip.render(graphics, font, editor, project, openProblems, mouseX, mouseY);
    }

    // ---- Input ---------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (showingHelp) {
            showingHelp = false;
            return true;
        }
        if (showingProblems) {
            // A click on a row goes there; a click anywhere else only closes.
            int row = problemRowAt(mouseX, mouseY);
            showingProblems = false;
            if (row >= 0 && row < problems.size()) {
                Diagnostic chosen = problems.get(row);
                openFile(chosen.file().isEmpty() ? open : chosen.file());
                editor.jumpTo(chosen);
            }
            return true;
        }
        if (panel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && overButton(mouseX, mouseY)) {
            deploy();
            return true;
        }
        // Ctrl and click jump to the definition — across the files.
        if (button == 0 && Keys.control() && goToDefinition(mouseX, mouseY)) {
            return true;
        }
        // A click on the footer jumps to the message if there is one, and
        // opens the list if there are several — again across the files.
        if (problems.size() > 1 && mouseY >= height - MARGIN - FOOTER && mouseX < buttonX()) {
            showingProblems = true;
            return true;
        }
        if (!problems.isEmpty() && mouseY >= height - MARGIN - FOOTER && mouseX < buttonX()) {
            Diagnostic first = problems.get(0);
            openFile(first.file().isEmpty() ? open : first.file());
            editor.jumpTo(first);
            return true;
        }
        if (editor.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        return editor.mouseDragged(mouseX, mouseY, button)
                || super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        return editor.mouseScrolled(mouseX, mouseY, deltaY)
                || super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        int scanCode = event.scancode();
        int modifiers = event.modifiers();
        // F1 before anything else: the list must be closable even while an
        // input is open below.
        if (key == 290) {
            showingHelp = !showingHelp;
            return true;
        }
        if (showingHelp) {
            // While it is open, nothing reaches the text. Otherwise you would
            // go on typing behind the card without seeing it.
            showingHelp = false;
            return true;
        }
        // F8 opens and closes the list of all messages. Without messages
        // there is nothing to open.
        if (key == 297) {
            showingProblems = !showingProblems && !problems.isEmpty();
            return true;
        }
        if (showingProblems) {
            showingProblems = false;
            return true;
        }
        // Ctrl+Enter deploys, no matter where the focus currently is.
        if ((key == 257 || key == 335) && Keys.control()) {
            deploy();
            return true;
        }
        // Ctrl+S saves the draft immediately. It goes out a second after the
        // last keystroke anyway — the shortcut is for the moment when you
        // step away from the machine and want to be sure.
        // F4 asks for a file that someone else holds.
        if (key == RequestEdit.KEY && RequestEdit.ask(open)) {
            return true;
        }
        if (key == 83 && Keys.control()) {
            ClientProjectState.flush();
            return true;
        }
        // The file tree gets the keys only while it has something open — an
        // input or a menu — plus F2. Everything else belongs to the editor,
        // so that the arrow keys always do the same thing.
        if (panel.keyPressed(key, scanCode, modifiers)) {
            return true;
        }
        if (panel.isEditing() || panel.hasMenu()) {
            return true;
        }
        if (editor.keyPressed(key, scanCode, modifiers)) {
            return true;
        }
        if (key == 256) {
            onClose();
            return true;
        }
        // Swallow everything else: an "e" in the editor must not open the
        // inventory.
        return true;
    }

    /**
     * Since 26.1 a whole code point arrives, without modifier flags. A
     * character beyond the basic plane goes on as its two halves, the way
     * Minecraft used to hand it over; the flags are queried.
     */
    @Override
    public boolean charTyped(CharacterEvent event) {
        boolean taken = false;
        for (char unit : Character.toChars(event.codepoint())) {
            taken |= typed(unit, dev.devpanda.factorynetwork.platform.client.Keys.held());
        }
        return taken;
    }

    private boolean typed(char character, int modifiers) {
        if (panel.isEditing()) {
            return panel.charTyped(character, modifiers);
        }
        return editor.charTyped(character, modifiers);
    }

    /** Back to the terminal, not to the game — and take the draft along. */
    @Override
    public void onClose() {
        ClientProjectState.flush();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
