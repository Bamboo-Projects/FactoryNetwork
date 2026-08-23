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
 * Der Code-Reiter: ein Projekt aus mehreren Dateien.
 *
 * <p>Über dem Editor eine Zeile mit den Dateien, darunter eine Fußleiste mit
 * der Meldung des Übersetzers und dem Knopf zum Übernehmen.
 *
 * <p><b>Die Dateien stehen quer und nicht als Spalte.</b> Eine Spalte hätte
 * ein Fünftel der Editorbreite gekostet — bei vierundvierzig Spalten sind das
 * zwölf. Eine Zeile kostet eine Zeile, und sie ist auch das Bild, das man von
 * Editor-Reitern kennt.
 *
 * <p><b>Übersetzt wird das ganze Projekt, markiert nur die offene Datei.</b>
 * Ohne diese Trennung markierte ein Fehler in Zeile drei von
 * {@code worker.mf} die dritte Zeile der gerade offenen {@code anzeigen.mf} —
 * der Fehler, den man am längsten sucht, weil dort gar nichts falsch ist.
 */
public class CodeTabView {

    /** Höhe der Dateizeile über dem Editor. */
    private static final int FILE_ROW = 11;

    /** Höhe der Fußleiste darunter. */
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

    /** Alle Meldungen des Projekts, und davon die der offenen Datei. */
    private List<Diagnostic> problems = List.of();
    private List<Diagnostic> openProblems = List.of();
    private String status = "";
    private int statusColour = TerminalScreen.TEXT_FAINT;

    public CodeTabView(TerminalScreen screen, Font font, int x, int y, int width, int height) {
        this.screen = screen;
        this.font = font;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.project = ClientProjectState.project();
        this.open = project.names().get(0);
        this.editor = new CodeEditor(font, x + 2, y + FILE_ROW + 2, width - 4,
                height - FILE_ROW - FOOTER - 2, project.source(open));
        this.editor.setChangeListener(this::onChanged);
        recheck();
    }

    private void onChanged(String text) {
        // Was der Server zuletzt gesagt hat, gilt für den Text von damals.
        ClientDeployState.clear();
        project = project.with(open, text);
        recheck();
    }

    /** Übersetzt das ganze Projekt und sortiert die Meldungen. */
    private void recheck() {
        problems = project.parse().diagnostics();
        openProblems = problems.stream()
                .filter(problem -> problem.file().equals(open))
                .toList();
        if (problems.isEmpty()) {
            status = Component.translatable("screen.factorynetwork.terminal.ok").getString();
            statusColour = TerminalScreen.TEXT_FAINT;
            return;
        }
        Diagnostic first = problems.get(0);
        // Der Dateiname nur, wenn er nicht der offene ist — sonst steht er
        // in jeder Meldung und sagt nichts.
        String where = first.file().equals(open) ? "" : first.file() + " ";
        status = where + first.span().line() + ": " + first.message();
        statusColour = first.isError() ? TerminalScreen.BAD : TerminalScreen.WARN;
    }

    /** Das Projekt mit dem, was gerade im Editor steht. */
    public Project project() {
        return project;
    }

    public String text() {
        return editor.text();
    }

    /** Wo der Cursor steht, für die Statuszeile des Fensters. */
    public Component cursorPosition() {
        return Component.translatable("screen.factorynetwork.terminal.cursor",
                editor.cursorLine() + 1, editor.cursorColumn() + 1);
    }

    // ---- Die Dateizeile ----------------------------------------------------

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

    /** Der Platz hinter der letzten Datei — dort sitzt das Pluszeichen. */
    private int plusX() {
        int at = x + 3;
        for (String name : project.names()) {
            at += fileWidth(name) + FILE_GAP;
        }
        return at;
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
    }

    private boolean overFiles(double mouseY) {
        return mouseY >= y && mouseY < y + FILE_ROW;
    }

    private boolean overPlus(double mouseX, double mouseY) {
        return overFiles(mouseY) && mouseX >= plusX()
                && mouseX < plusX() + fileWidth("+") + 2;
    }

    /**
     * Wechselt die offene Datei.
     *
     * <p>Der Text der bisherigen steht schon im Projekt — jeder Anschlag
     * schreibt ihn dorthin. Hier wird nur geholt, was in der neuen steht.
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
     * Legt eine Datei an.
     *
     * <p>Mit einem durchnummerierten Namen und ohne Frage: Ein Fenster für
     * einen Dateinamen wäre ein Fenster über einem Fenster. Umbenennen geht
     * im Ordner neben der Welt — dort ist ohnehin der Ort für alles, was mit
     * der Gliederung zu tun hat.
     */
    private void addFile() {
        for (int number = 2; number < 100; number++) {
            String name = "datei" + number + ".mf";
            if (!project.files().containsKey(name)) {
                project = project.with(name, "");
                openFile(name);
                return;
            }
        }
    }

    // ---- Zeichnen ----------------------------------------------------------

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
        if (overPlus(mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable(
                    "screen.factorynetwork.terminal.newfile"), mouseX, mouseY);
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

    /** Ein Klick auf die Fußleiste springt zur ersten Meldung — auch quer. */
    private boolean jumpToFirstProblem(double mouseX, double mouseY) {
        if (problems.isEmpty() || mouseY < y + height - FOOTER || mouseX >= buttonX()) {
            return false;
        }
        Diagnostic first = problems.get(0);
        openFile(first.file().isEmpty() ? open : first.file());
        editor.jumpTo(first);
        return true;
    }

    // ---- Bedienung ---------------------------------------------------------

    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return editor.keyPressed(key, scanCode, modifiers);
    }

    public boolean charTyped(char character, int modifiers) {
        return editor.charTyped(character, modifiers);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return editor.mouseClicked(mouseX, mouseY, button);
        }
        if (overButton(mouseX, mouseY)) {
            screen.deploy();
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
