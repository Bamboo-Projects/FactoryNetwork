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
 *
 * <p><b>Hier wird nachgesehen und korrigiert, nicht geschrieben.</b> Acht
 * Zeilen sind das, was neben einem Spielerinventar Platz hat. Wer eine Datei
 * umbenennt, löscht oder ein Programm wirklich baut, drückt auf das Zeichen
 * rechts in der Dateizeile und bekommt {@link CodeScreen} — dasselbe Projekt,
 * derselbe Entwurf, nur ohne die 288 Pixel.
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
        // Aus dem Entwurf und nicht aus dem, was der Server zuletzt gemeldet
        // hat: Wer aus dem großen Fenster zurückkommt, findet sonst seine
        // ungesicherten Zeilen nicht wieder.
        this.project = ClientProjectState.draft();
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
        ClientProjectState.setDraft(project);
        recheck();
    }

    /** Übersetzt das ganze Projekt und sortiert die Meldungen. */
    private void recheck() {
        // Eine Datei, die jemand anders hält, lässt sich lesen und nicht
        // ändern. Der Server würde eine Änderung ohnehin verwerfen — sie
        // hier gar nicht erst zuzulassen, erspart den Moment, in dem der
        // eigene Text beim nächsten Zustand verschwindet.
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

    /**
     * Der Platz hinter der letzten Datei — dort sitzt das Pluszeichen.
     *
     * <p>Höchstens bis vor das Zeichen für das große Fenster: Bei sechs
     * Dateien liefe es sonst darüber hinweg, und zwei Knöpfe an derselben
     * Stelle sind einer zu viel.
     */
    private int plusX() {
        int at = x + 3;
        for (String name : project.names()) {
            at += fileWidth(name) + FILE_GAP;
        }
        return Math.min(at, expandX() - 10);
    }

    /** Das Zeichen ganz rechts, das den Editor im ganzen Fenster öffnet. */
    private int expandX() {
        return x + width - 12;
    }

    private boolean overExpand(double mouseX, double mouseY) {
        return overFiles(mouseY) && mouseX >= expandX() - 2 && mouseX < expandX() + 10;
    }

    /**
     * Ein Rahmen mit einer offenen Ecke.
     *
     * <p>Gezeichnet und nicht getippt: Die Zeichen, die dafür gemeint sind,
     * stehen nicht in der Schrift, und ein Kästchen aus vier Strichen kostet
     * vier Zeilen.
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
     * Legt eine Datei an — mit einem Platzhalternamen.
     *
     * <p>Benannt wird sie im großen Fenster; hier ist keine Zeile frei für
     * ein Eingabefeld. Wer gleich einen Namen tippen will, macht dort auch
     * gleich die neue Datei auf.
     */
    private void addFile() {
        String name = project.freeNameLike("datei");
        project = project.with(name, "");
        ClientProjectState.setDraft(project);
        openFile(name);
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
        var signature = editor.signatureAt(mouseX, mouseY);
        if (signature != null) {
            graphics.renderComponentTooltip(font, List.of(
                    FnFonts.mono(signature.shape()),
                    Component.literal("§7" + signature.help())), mouseX, mouseY);
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
