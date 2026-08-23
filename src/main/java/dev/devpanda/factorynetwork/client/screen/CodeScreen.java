package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.client.ClientDeployState;
import dev.devpanda.factorynetwork.client.ClientProjectState;
import dev.devpanda.factorynetwork.client.FnFonts;
import dev.devpanda.factorynetwork.lang.Diagnostic;
import dev.devpanda.factorynetwork.lang.Project;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Der Editor im ganzen Fenster.
 *
 * <p><b>Warum ein zweites Fenster und nicht ein größerer Reiter:</b> Das
 * Terminal ist 288 Pixel breit, weil darunter das Spielerinventar sitzt und
 * das Slotraster nicht verhandelbar ist. In dieser Breite bleiben nach
 * Bundsteg und Rand vierundvierzig Spalten und acht Zeilen — genug, um
 * nachzusehen, zu wenig, um zu arbeiten. Eine Dateispalte hätte davon noch
 * ein Viertel gekostet.
 *
 * <p>Hier gibt es kein Inventar, also auch keine feste Breite. Links der
 * Dateibaum, rechts der Editor, unten die Meldung des Übersetzers und der
 * Knopf zum Übernehmen.
 *
 * <p><b>Beide Fenster arbeiten am selben Entwurf.</b> Er liegt in
 * {@link ClientProjectState} und nicht in einer der beiden Ansichten — sonst
 * wäre jeder Wechsel ein Verlust.
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
     * Die Farben des Gehäuses.
     *
     * <p>Dieselbe Regel wie im Terminal — <b>was vertieft liegt, ist dunkler
     * als sein Grund</b> —, aber mit größeren Schritten. Im Terminal liegen
     * die drei Ebenen auf 288 Pixeln dicht beieinander und grenzen überall
     * aneinander; da reichen vier Werte Unterschied, weil das Auge die Kante
     * sieht und nicht die Fläche. Hier ist eine Fläche einen halben Bildschirm
     * groß, und dann sieht man nur noch die Fläche. Der erste Entwurf setzte
     * den Dateibaum auf {@code 0x14181A} vor eine Scheibe aus
     * {@code 0x181E1B} — er war unsichtbar, und die Frage war zu Recht, wo
     * die Seitenspalte bleibt.
     */
    private static final int CASE = 0xFF232B27;
    private static final int GLASS = 0xFF181E1B;

    /** Die Kante zwischen zwei Ebenen — hell genug, um sie zu sehen. */
    static final int EDGE = 0xFF39443D;

    private final Screen parent;
    private final BlockPos controller;

    private ProjectPanel panel;
    private CodeEditor editor;

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
        recheck();
    }

    // ---- Der Entwurf -------------------------------------------------------

    private void onTextChanged(String text) {
        ClientDeployState.clear();
        project = project.with(open, text);
        publish();
    }

    /** Der Dateibaum hat Dateien angelegt, umbenannt oder gelöscht. */
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
        editor.setText(project.source(name));
        recheck();
    }

    /** Übersetzt das ganze Projekt und sortiert die Meldungen. */
    private void recheck() {
        problems = project.parse().diagnostics();
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
        statusColour = first.isError() ? TerminalScreen.BAD : TerminalScreen.WARN;
    }

    private void deploy() {
        PacketDistributor.sendToServer(
                dev.devpanda.factorynetwork.network.packet.DeployProgramPacket
                        .of(controller, project));
    }

    // ---- Zeichnen ----------------------------------------------------------

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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, CASE);
        graphics.fill(MARGIN - 4, MARGIN - 4, width - MARGIN + 4, height - MARGIN + 4, GLASS);
        // Die helle Kante um die Scheibe: Ohne sie schwimmt der Inhalt im
        // Blech, weil zwischen beiden nur ein paar Werte liegen.
        outline(graphics, MARGIN - 4, MARGIN - 4, width - MARGIN + 4, height - MARGIN + 4,
                0xFF2E3833);

        drawHeader(graphics);
        panel.render(graphics, mouseX, mouseY);
        editor.render(graphics, openProblems);
        drawFooter(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    /**
     * Die Kopfzeile: wo man ist und wie man zurückkommt.
     *
     * <p>Der Hinweis auf Escape steht da, weil dieses Fenster kein Inventar
     * hat und damit auch nicht so aussieht, als könnte man es wie jedes andere
     * schließen.
     */
    private void drawHeader(GuiGraphics graphics) {
        graphics.drawString(font, FnFonts.bold(title), MARGIN, MARGIN, TerminalScreen.TEXT,
                false);
        Component hint = FnFonts.mono(
                Component.translatable("screen.factorynetwork.code.back"));
        graphics.drawString(font, hint, width - MARGIN - font.width(hint), MARGIN,
                TerminalScreen.TEXT_FAINT, false);
        graphics.fill(MARGIN, MARGIN + 12, width - MARGIN, MARGIN + 13, EDGE);
    }

    /** Ein Rahmen aus vier Strichen. */
    static void outline(GuiGraphics graphics, int left, int top, int right, int bottom,
                        int colour) {
        graphics.fill(left, top, right, top + 1, colour);
        graphics.fill(left, bottom - 1, right, bottom, colour);
        graphics.fill(left, top, left + 1, bottom, colour);
        graphics.fill(right - 1, top, right, bottom, colour);
    }

    private void drawFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        int footerY = height - MARGIN - FOOTER + 6;
        graphics.fill(MARGIN, height - MARGIN - FOOTER, width - MARGIN,
                height - MARGIN - FOOTER + 1, EDGE);
        String shown = ClientDeployState.hasMessage() ? ClientDeployState.message() : status;
        int colour = ClientDeployState.hasMessage()
                ? ClientDeployState.wasAccepted() ? TerminalScreen.GOOD : TerminalScreen.BAD
                : statusColour;
        graphics.drawString(font,
                FnFonts.mono(font.plainSubstrByWidth(shown, buttonX() - MARGIN - 90)),
                MARGIN, footerY, colour, false);

        // Rechts neben dem Knopf: wo der Cursor steht, und ob etwas offen ist.
        Component position = FnFonts.mono(Component.translatable(
                "screen.factorynetwork.terminal.cursor",
                editor.cursorLine() + 1, editor.cursorColumn() + 1));
        graphics.drawString(font, position, buttonX() - font.width(position) - 10, footerY,
                ClientProjectState.isDirty() ? TerminalScreen.WARN : TerminalScreen.TEXT_FAINT,
                false);

        boolean hovered = overButton(mouseX, mouseY);
        graphics.fill(buttonX(), buttonY(), buttonX() + BUTTON_WIDTH,
                buttonY() + BUTTON_HEIGHT,
                hovered ? TerminalScreen.BUTTON_HOVER : TerminalScreen.BUTTON);
        Component label = FnFonts.mono(
                Component.translatable("screen.factorynetwork.terminal.deploy"));
        graphics.drawString(font, label, buttonX() + (BUTTON_WIDTH - font.width(label)) / 2,
                buttonY() + 3, TerminalScreen.TEXT, false);
    }

    private void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (panel.hasMenu()) {
            return;
        }
        if (overButton(mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable(
                    "screen.factorynetwork.terminal.deploy.hint"), mouseX, mouseY);
            return;
        }
        Diagnostic problem = editor.diagnosticAt(openProblems, mouseX, mouseY);
        if (problem == null) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(problem.message()));
        if (problem.hint() != null) {
            lines.add(Component.literal("§7" + problem.hint()));
        }
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    // ---- Bedienung ---------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (panel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && overButton(mouseX, mouseY)) {
            deploy();
            return true;
        }
        // Ein Klick auf die Fußleiste springt zur ersten Meldung — auch quer
        // durch die Dateien.
        if (!problems.isEmpty() && mouseY >= height - MARGIN - FOOTER && mouseX < buttonX()) {
            Diagnostic first = problems.get(0);
            openFile(first.file().isEmpty() ? open : first.file());
            editor.jumpTo(first);
            return true;
        }
        if (editor.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX,
                                double dragY) {
        return editor.mouseDragged(mouseX, mouseY, button)
                || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        return editor.mouseScrolled(mouseX, mouseY, deltaY)
                || super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        // Strg+Eingabe übernimmt, egal wo der Griff gerade liegt.
        if ((key == 257 || key == 335) && hasControlDown()) {
            deploy();
            return true;
        }
        // Der Dateibaum bekommt die Tasten nur, solange er etwas Offenes hat —
        // eine Eingabe oder ein Menü — sowie F2. Alles andere gehört dem
        // Editor, damit die Pfeiltasten immer dasselbe tun.
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
        // Alles andere verschlucken: Ein „e" im Editor darf nicht das
        // Inventar öffnen.
        return true;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (panel.isEditing()) {
            return panel.charTyped(character, modifiers);
        }
        return editor.charTyped(character, modifiers);
    }

    /** Zurück ins Terminal, nicht ins Spiel. */
    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
