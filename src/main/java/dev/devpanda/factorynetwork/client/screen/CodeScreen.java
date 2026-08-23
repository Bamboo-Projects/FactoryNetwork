package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.client.ClientDeployState;
import dev.devpanda.factorynetwork.client.ClientNetworkState;
import dev.devpanda.factorynetwork.client.ClientProjectState;
import dev.devpanda.factorynetwork.client.FnFonts;
import dev.devpanda.factorynetwork.lang.Diagnostic;
import dev.devpanda.factorynetwork.lang.Project;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
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

    /**
     * Die Griffe, die es gibt.
     *
     * <p><b>Weil man sie sonst nicht findet.</b> Ein Editor im Spiel hat
     * keine Menüleiste, in der man nachsieht, und keine Doku, die daneben
     * liegt. Strg+Leertaste, Strg+H und F2 sind da, aber wer nicht weiß, dass
     * es sie gibt, benutzt sie nie — und der Editor fühlt sich ärmer an, als
     * er ist.
     *
     * <p>Fest im Code und nicht in der Sprachdatei: Es sind Tastennamen, und
     * die heißen in jeder Sprache gleich. Was sie tun, steht daneben und
     * kommt aus der Sprachdatei.
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
            {"Rechtsklick", "help.menu"},
            {"F1", "help.close"},
    };

    private ProjectPanel panel;
    private CodeEditor editor;

    /** Ob die Griffliste offen ist. */
    private boolean showingHelp;

    /**
     * Die Rückmeldung zur letzten Marke, oder {@code null}.
     *
     * <p>Ohne sie wäre der Griff unsichtbar: Die Marke steht in der Welt,
     * und die sieht man erst, wenn das Fenster zu ist.
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
        recheck();
    }

    // ---- Der Entwurf -------------------------------------------------------

    private void onTextChanged(String text) {
        ClientDeployState.clear();
        located = null;
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
        statusColour = first.isError() ? TerminalScreen.BAD : TerminalScreen.WARN;
    }

    /**
     * Springt zu der Stelle, an der das Wort unter dem Zeiger erklärt wird.
     *
     * <p>Alle Dateien teilen einen Namensraum, und deshalb ist von der Stelle
     * des Gebrauchs aus nicht zu sehen, wo die Erklärung steht. Bei drei
     * Dateien sucht man sie noch, bei acht nicht mehr.
     *
     * @return ob gesprungen wurde
     */
    private boolean goToDefinition(double mouseX, double mouseY) {
        String word = editor.wordAt(mouseX, mouseY);
        if (word.isEmpty()) {
            return false;
        }
        var target = dev.devpanda.factorynetwork.lang.Definitions.find(project, word);
        if (target.isPresent()) {
            var location = target.get();
            openFile(location.file());
            editor.jumpTo(location.line(), location.column());
            return true;
        }
        // Kein Name aus dem Programm — aber vielleicht einer aus der Welt.
        // Dann ist die Frage nicht „wo steht das im Code", sondern „welcher
        // Block ist das", und die beantwortet eine Marke.
        var place = ClientNetworkState.placeOf(word);
        if (place == null) {
            return false;
        }
        dev.devpanda.factorynetwork.client.LocateMarker.mark(place, word);
        located = Component.translatable("screen.factorynetwork.code.located", word,
                place.getX(), place.getY(), place.getZ());
        return true;
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

    /**
     * Zeichnet das Fenster.
     *
     * <p><b>Kein {@code super.render} und kein {@code renderBackground}.</b>
     * {@code Screen.render} ruft als erstes {@code renderBackground}, und das
     * ruft {@code processBlurEffect} — einen Nachbearbeitungsschritt, der das
     * Bild im Puffer weichzeichnet. In Vanilla steht der Aufruf am Anfang,
     * also trifft er die Welt hinter dem Fenster; das Fenster selbst wird
     * danach scharf darübergemalt.
     *
     * <p>Der erste Entwurf rief {@code super.render} am <b>Ende</b>. Damit lag
     * schon alles im Puffer, was hier gezeichnet wird, und der Weichzeichner
     * ging über die eigene Oberfläche: Aus einer ein Pixel breiten hellen
     * Kante wurde ein zwanzig Pixel breiter Verlauf, aus Text ein Schmier.
     * Auf einem Bildschirmfoto sah es aus wie eine falsche Farbwahl, und ich
     * habe zuerst auch an den Farben gedreht — messbar war es erst am
     * Querschnitt durch eine Kante, die es gar nicht mehr gab.
     *
     * <p>Die Welt dahinter braucht keinen Weichzeichner: Das Fenster deckt
     * sie vollständig ab. Was von {@code Screen.render} bleibt, ist die
     * Schleife über die Widgets — für den Fall, dass hier einmal welche
     * hinzukommen.
     */
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
        if (showingHelp) {
            drawHelp(graphics);
        }

        for (Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
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
        Component hint = FnFonts.mono(Component.translatable(showingHelp
                ? "screen.factorynetwork.code.back"
                : "screen.factorynetwork.code.help"));
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
        if (located != null) {
            shown = located.getString();
            colour = TerminalScreen.ACCENT;
        }
        graphics.drawString(font,
                FnFonts.mono(font.plainSubstrByWidth(shown, buttonX() - MARGIN - 90)),
                MARGIN, footerY, colour, false);

        // Rechts neben dem Knopf: wo der Cursor steht.
        Component position = FnFonts.mono(Component.translatable(
                "screen.factorynetwork.terminal.cursor",
                editor.cursorLine() + 1, editor.cursorColumn() + 1));
        int positionX = buttonX() - font.width(position) - 10;
        graphics.drawString(font, position, positionX, footerY,
                TerminalScreen.TEXT_FAINT, false);

        // Und davor, in welchem Zustand der Text ist. Drei Fälle, ein Ort:
        // noch nicht beim Server, beim Server aber nicht übernommen, oder
        // deckungsgleich mit dem, was läuft. Im letzten Fall steht dort
        // nichts — ein Hinweis, der immer da ist, wird nicht gelesen.
        String state = null;
        int stateColour = TerminalScreen.TEXT_FAINT;
        if (ClientProjectState.isDirty()) {
            state = "screen.factorynetwork.code.saving";
        } else if (!ClientProjectState.isDeployed()) {
            state = "screen.factorynetwork.code.unsaved";
            stateColour = TerminalScreen.WARN;
        }
        if (state != null) {
            Component label = FnFonts.mono(Component.translatable(state));
            graphics.drawString(font, label, positionX - font.width(label) - 12, footerY,
                    stateColour, false);
        }

        boolean hovered = overButton(mouseX, mouseY);
        graphics.fill(buttonX(), buttonY(), buttonX() + BUTTON_WIDTH,
                buttonY() + BUTTON_HEIGHT,
                hovered ? TerminalScreen.BUTTON_HOVER : TerminalScreen.BUTTON);
        Component label = FnFonts.mono(
                Component.translatable("screen.factorynetwork.terminal.deploy"));
        graphics.drawString(font, label, buttonX() + (BUTTON_WIDTH - font.width(label)) / 2,
                buttonY() + 3, TerminalScreen.TEXT, false);
    }

    /**
     * Die Griffliste über allem.
     *
     * <p>In zwei Spalten und mittig: Sie ist eine Nachschlagetafel, kein
     * Bereich des Fensters. Sie deckt den Code ab, solange sie offen ist —
     * das ist richtig so, denn wer sie aufmacht, sucht etwas und schreibt
     * gerade nicht.
     */
    private void drawHelp(GuiGraphics graphics) {
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
        graphics.drawString(font, FnFonts.bold(
                        Component.translatable("screen.factorynetwork.code.help.title")),
                left + 14, top + 8, TerminalScreen.TEXT, false);
        graphics.fill(left + 14, top + 20, left + boxWidth - 14, top + 21, EDGE);

        for (int i = 0; i < rows; i++) {
            int rowY = top + 26 + i * 12;
            graphics.drawString(font, FnFonts.mono(HELP[i][0]), left + 14, rowY,
                    TerminalScreen.ACCENT, false);
            graphics.drawString(font, Component.translatable(
                            "screen.factorynetwork.code." + HELP[i][1]),
                    left + 26 + keyWidth, rowY, TerminalScreen.TEXT_DIM, false);
        }
    }

    /**
     * Erklärt den Namen unter dem Zeiger.
     *
     * <p>Wo er erklärt wird und wo er sonst noch steht. <b>Das ist die Frage,
     * die man vor jeder Umbenennung stellt</b> — und bisher nur beantworten
     * konnte, indem man jede Datei einzeln durchsah.
     *
     * <p>Die Fundstellen kommen aus einer Textsuche über ganze Wörter, nicht
     * aus dem Baum. Damit steht gelegentlich eine Zeile aus einem Kommentar
     * dabei. Das ist der bessere Fehler: Man sieht ihn sofort, und keine
     * Stelle fehlt.
     *
     * @return ob etwas gezeigt wurde
     */
    private boolean describeName(GuiGraphics graphics, int mouseX, int mouseY) {
        String word = editor.wordAt(mouseX, mouseY);
        if (word.isEmpty()) {
            return false;
        }
        var declared = dev.devpanda.factorynetwork.lang.Definitions.find(project, word);
        var inWorld = ClientNetworkState.placeOf(word);
        if (declared.isEmpty() && inWorld == null) {
            return false;
        }
        var places = dev.devpanda.factorynetwork.lang.Definitions.references(project, word);
        List<Component> lines = new ArrayList<>();
        lines.add(FnFonts.mono(word));
        if (inWorld != null) {
            // Die Stelle zuerst: Bei einem Namen aus dem Netz ist „wo ist
            // das" die Frage, die man stellt.
            lines.add(Component.translatable("screen.factorynetwork.code.at",
                    inWorld.getX(), inWorld.getY(), inWorld.getZ())
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            lines.add(Component.translatable("screen.factorynetwork.code.locate_hint")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
        if (declared.isEmpty()) {
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            return true;
        }
        lines.add(Component.translatable("screen.factorynetwork.code.declared_in",
                declared.get().file(), declared.get().line()).withStyle(
                        net.minecraft.ChatFormatting.GRAY));
        // Die Erklärung selbst ist eine Fundstelle; gezählt wird, was sonst
        // noch da ist.
        int used = Math.max(0, places.size() - 1);
        lines.add(Component.translatable("screen.factorynetwork.code.used", used)
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        // Höchstens fünf, sonst deckt der Kasten den halben Bildschirm.
        int shown = 0;
        for (var place : places) {
            if (place.line() == declared.get().line()
                    && place.file().equals(declared.get().file())) {
                continue;
            }
            if (shown++ >= 5) {
                break;
            }
            lines.add(Component.literal("§8  " + place.file() + ":" + place.line()));
        }
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        return true;
    }

    private void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (panel.hasMenu() || showingHelp) {
            return;
        }
        if (overButton(mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable(
                    "screen.factorynetwork.terminal.deploy.hint"), mouseX, mouseY);
            return;
        }
        if (describeName(graphics, mouseX, mouseY)) {
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

    // ---- Bedienung ---------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (showingHelp) {
            showingHelp = false;
            return true;
        }
        if (panel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && overButton(mouseX, mouseY)) {
            deploy();
            return true;
        }
        // Strg und Klick springt zur Erklärung — quer durch die Dateien.
        if (button == 0 && hasControlDown() && goToDefinition(mouseX, mouseY)) {
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
        // F1 vor allem anderen: Die Liste muss sich auch schließen lassen,
        // während unten eine Eingabe offen steht.
        if (key == 290) {
            showingHelp = !showingHelp;
            return true;
        }
        if (showingHelp) {
            // Solange sie offen ist, geht nichts an den Text. Sonst tippte
            // man hinter der Tafel weiter, ohne es zu sehen.
            showingHelp = false;
            return true;
        }
        // Strg+Eingabe übernimmt, egal wo der Griff gerade liegt.
        if ((key == 257 || key == 335) && hasControlDown()) {
            deploy();
            return true;
        }
        // Strg+S sichert den Entwurf sofort. Er geht ohnehin eine Sekunde
        // nach dem letzten Anschlag hinaus — der Griff ist für den Moment,
        // in dem man vom Rechner weggeht und es genau wissen will.
        if (key == 83 && hasControlDown()) {
            ClientProjectState.flush();
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

    /** Zurück ins Terminal, nicht ins Spiel — und den Entwurf mitnehmen. */
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
