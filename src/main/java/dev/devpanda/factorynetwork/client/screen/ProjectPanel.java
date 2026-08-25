package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.client.FnFonts;
import dev.devpanda.factorynetwork.lang.Diagnostic;
import dev.devpanda.factorynetwork.lang.Project;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Der Dateibaum eines Projekts.
 *
 * <p>Eine Spalte mit den Dateien, und darin alles, was man mit einer Datei
 * macht: anlegen, umbenennen, verdoppeln, löschen. <b>Das fehlte, und der
 * Hinweistext im Fenster sagte den Leuten, sie sollen dafür das Spiel
 * verlassen</b> — der Ordner neben der Welt ist ein guter zweiter Weg, aber
 * kein Ersatz für den ersten.
 *
 * <p>Umbenannt wird an Ort und Stelle: Die Zeile wird zum Eingabefeld. Ein
 * eigenes Fenster für einen Dateinamen wäre ein Fenster über einem Fenster,
 * und man verlöre beim Tippen aus den Augen, wie die anderen Dateien heißen —
 * genau das, wonach man sich beim Benennen richtet.
 *
 * <p>Ein Name, den es schon gibt oder der nicht ins Muster passt, wird rot
 * gezeigt und nicht angenommen. Die Eingabe bleibt dabei stehen: Ein Feld,
 * das sich bei einem Tippfehler selbst leert, ist die Sorte Hilfsbereitschaft,
 * für die man es zweimal tippt.
 */
public class ProjectPanel {

    /** Höhe einer Zeile. */
    private static final int ROW = 12;

    /** Höhe der Überschrift mit dem Pluszeichen. */
    private static final int HEADER = 16;

    /** Der Grund der Spalte — die unterste der drei Ebenen. */
    private static final int WELL = 0xFF0E1214;

    /** Der Streifen, auf dem die Überschrift sitzt. */
    private static final int HEADER_BAND = 0xFF161C19;

    /** Die offene Datei: heller als der Grund, weil sie oben aufliegt. */
    private static final int ROW_ACTIVE = 0xFF232B27;

    private final Font font;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    /** Wohin die Änderungen gehen. */
    private final Consumer<Project> onChanged;

    /** Was passiert, wenn eine Datei gewählt wird. */
    private final Consumer<String> onOpen;

    private Project project;
    private String open;
    private List<Diagnostic> problems = List.of();

    /** Welche Datei gerade umbenannt wird, oder {@code null}. */
    private String renaming;
    private String typed = "";

    private ContextMenu menu;

    public ProjectPanel(Font font, int x, int y, int width, int height,
                        Consumer<Project> onChanged, Consumer<String> onOpen) {
        this.font = font;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.onChanged = onChanged;
        this.onOpen = onOpen;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public void setOpen(String open) {
        this.open = open;
    }

    public void setProblems(List<Diagnostic> problems) {
        this.problems = problems;
    }

    /** Läuft gerade eine Eingabe, die die Tastatur braucht? */
    public boolean isEditing() {
        return renaming != null;
    }

    public boolean hasMenu() {
        return menu != null;
    }

    // ---- Zeichnen ----------------------------------------------------------

    private int rowY(int index) {
        return y + HEADER + index * ROW;
    }

    private int plusX() {
        return x + width - 12;
    }

    private boolean overPlus(double mouseX, double mouseY) {
        return mouseX >= plusX() - 3 && mouseX < plusX() + 9
                && mouseY >= y + 1 && mouseY < y + HEADER;
    }

    /** Welche Zeile unter dem Zeiger liegt, oder -1. */
    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < x || mouseX >= x + width) {
            return -1;
        }
        int index = (int) Math.floor((mouseY - y - HEADER) / (double) ROW);
        return index >= 0 && index < project.names().size() ? index : -1;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        // Eine eigene Mulde neben dem Editor, damit die Spalte als Bereich
        // lesbar ist und nicht als Text, der zufällig links steht. Der Wert
        // ist deutlich dunkler als die Scheibe und nicht nur ein bisschen:
        // Auf einer Fläche dieser Größe sieht man keine Kante mehr, sondern
        // nur noch den Ton.
        graphics.fill(x - 3, y - 2, x + width, y + height, WELL);
        // Die senkrechte Kante zum Editor trägt die Trennung. Rechts hell,
        // wie bei jeder Mulde in diesem Projekt.
        graphics.fill(x + width, y - 2, x + width + 1, y + height, CodeScreen.EDGE);
        // Die Überschrift steht auf einem eigenen Streifen — sonst hängt das
        // Pluszeichen frei im Dunkeln.
        graphics.fill(x - 3, y - 2, x + width, y + HEADER - 2, HEADER_BAND);
        graphics.fill(x - 3, y + HEADER - 3, x + width, y + HEADER - 2, 0xFF232B27);

        graphics.drawString(font, FnFonts.bold(
                        Component.translatable("screen.factorynetwork.project.title")),
                x, y + 2, TerminalScreen.TEXT_DIM, false);
        graphics.drawString(font, FnFonts.mono("+"), plusX(), y + 2,
                overPlus(mouseX, mouseY) ? TerminalScreen.ACCENT : TerminalScreen.TEXT_DIM,
                false);

        int hovered = rowAt(mouseX, mouseY);
        List<String> names = project.names();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            int rowTop = rowY(i);
            if (rowTop + ROW > y + height) {
                break;
            }
            boolean active = name.equals(open);
            if (active) {
                graphics.fill(x - 3, rowTop - 1, x + width, rowTop + ROW - 1, ROW_ACTIVE);
                graphics.fill(x - 3, rowTop - 1, x, rowTop + ROW - 1,
                        0xFF000000 | TerminalScreen.ACCENT);
            } else if (i == hovered) {
                graphics.fill(x - 3, rowTop - 1, x + width, rowTop + ROW - 1, 0x22FFFFFF);
            }

            if (name.equals(renaming)) {
                drawRenameField(graphics, rowTop);
                continue;
            }

            boolean broken = problems.stream()
                    .anyMatch(problem -> problem.isError() && problem.file().equals(name));
            String holder = dev.devpanda.factorynetwork.client.ClientProjectState.heldBy(name);
            // Eine gesperrte Datei matt: Man darf sie öffnen und lesen, nur
            // nicht ändern. Grau ist dafür die ehrlichere Farbe als Rot —
            // es ist kein Fehler, es ist jemand anders.
            int colour = holder != null ? TerminalScreen.TEXT_FAINT
                    : active ? TerminalScreen.TEXT : TerminalScreen.TEXT_DIM;
            graphics.drawString(font, FnFonts.mono(clipName(name, width - 12)),
                    x + 2, rowTop + 1, colour, false);
            if (holder != null) {
                graphics.fill(x + width - 8, rowTop + 3, x + width - 4, rowTop + 8,
                        0xFF000000 | TerminalScreen.WARN);
            } else if (broken) {
                graphics.fill(x + width - 7, rowTop + 4, x + width - 4, rowTop + 7,
                        0xFF000000 | TerminalScreen.BAD);
            }
        }

        if (menu != null) {
            menu.render(graphics, mouseX, mouseY);
        }
    }

    /**
     * Die Zeile als Eingabefeld.
     *
     * <p>Der Cursor ist ein Strich hinter dem Text und blinkt nicht: Bei einem
     * Feld, das genau so lange offen ist, wie man einen Namen tippt, ist ein
     * Blinken nur Bewegung.
     */
    private void drawRenameField(GuiGraphics graphics, int rowTop) {
        boolean valid = isAcceptable(typed);
        graphics.fill(x - 1, rowTop - 1, x + width - 2, rowTop + ROW - 1, 0xFF0E1113);
        graphics.drawString(font, FnFonts.mono(clip(typed, width - 14)), x + 2, rowTop + 1,
                valid ? TerminalScreen.TEXT : TerminalScreen.BAD, false);
        int caret = x + 2 + font.width(FnFonts.mono(clip(typed, width - 14)));
        graphics.fill(caret, rowTop, caret + 1, rowTop + ROW - 2,
                0xFF000000 | EditorColours.CURSOR);
    }

    private String clip(String text, int available) {
        return font.width(FnFonts.mono(text)) <= available
                ? text
                : font.plainSubstrByWidth(text, available);
    }

    /**
     * Ein Dateiname, so gekürzt, dass der Name übrig bleibt.
     *
     * <p>Bei {@code erz/eisen/schmelzen.mf} steht das Wichtige hinten. Von
     * rechts zu kürzen ergäbe {@code erz/eisen/schm}, und damit sähen zwei
     * Dateien desselben Ordners gleich aus. Gekürzt wird deshalb von vorn:
     * {@code …/schmelzen.mf}.
     *
     * <p>Ohne Ordner bleibt es beim Kürzen von rechts — dort steht die
     * Endung, und die ist bei jeder Datei dieselbe.
     */
    private String clipName(String name, int available) {
        if (font.width(FnFonts.mono(name)) <= available || name.indexOf('/') < 0) {
            return clip(name, available);
        }
        String rest = name;
        while (rest.indexOf('/') >= 0) {
            rest = rest.substring(rest.indexOf('/') + 1);
            if (font.width(FnFonts.mono("…/" + rest)) <= available) {
                return "…/" + rest;
            }
        }
        return clip(rest, available);
    }

    // ---- Umbenennen --------------------------------------------------------

    /**
     * Geht dieser Name durch?
     *
     * <p>Der eigene alte Name geht durch — sonst stünde die Zeile rot da,
     * sobald man das Umbenennen öffnet und noch nichts geändert hat.
     */
    private boolean isAcceptable(String name) {
        if (name.equals(renaming)) {
            return true;
        }
        return Project.isValidName(name) && !project.files().containsKey(name);
    }

    public void beginRename(String name) {
        if (name == null
                || dev.devpanda.factorynetwork.client.ClientProjectState.heldBy(name) != null) {
            return;
        }
        closeMenu();
        renaming = name;
        typed = name;
    }

    /**
     * Übernimmt den getippten Namen.
     *
     * @param force ob die Eingabe auch dann zugeht, wenn der Name nicht
     *              durchgeht — so beim Klick daneben, der ein Abbruch ist.
     *              Die Eingabetaste lässt das Feld dagegen offen stehen: Wer
     *              gerade einen roten Namen getippt hat, will ihn ändern und
     *              nicht verlieren.
     */
    private void commitRename(boolean force) {
        if (renaming == null) {
            return;
        }
        String from = renaming;
        String to = typed;
        if (!to.equals(from) && !isAcceptableFor(from, to)) {
            if (force) {
                renaming = null;
            }
            return;
        }
        renaming = null;
        if (to.equals(from)) {
            return;
        }
        Project next = project.renamed(from, to);
        project = next;
        onChanged.accept(next);
        if (from.equals(open)) {
            onOpen.accept(to);
        }
    }

    private boolean isAcceptableFor(String from, String to) {
        return Project.isValidName(to)
                && (to.equals(from) || !project.files().containsKey(to));
    }

    private void cancelRename() {
        renaming = null;
    }

    // ---- Anlegen, Verdoppeln, Löschen --------------------------------------

    /**
     * Legt eine Datei an — und öffnet sie sofort zum Benennen.
     *
     * <p>Der Name {@code datei2.mf} ist ein Platzhalter und kein Vorschlag.
     * Wer eine Datei anlegt, weiß, wie sie heißen soll; die Eingabe gleich zu
     * öffnen erspart den zweiten Griff und den einen Fall, in dem eine Datei
     * für immer {@code datei2.mf} heißt, weil das Umbenennen zu umständlich
     * war.
     */
    public void addFile() {
        String name = project.freeNameLike("datei");
        project = project.with(name, "");
        onChanged.accept(project);
        onOpen.accept(name);
        beginRename(name);
    }

    private void duplicate(String name) {
        String copy = project.freeNameLike(name);
        project = project.with(copy, project.source(name));
        onChanged.accept(project);
        onOpen.accept(copy);
        beginRename(copy);
    }

    /**
     * Löscht eine Datei.
     *
     * <p>Ohne Rückfrage, aber nur über das Menü: Zwei Klicks an einer Stelle,
     * an die man nicht aus Versehen kommt, sind die Rückfrage. Ein
     * Bestätigungsfenster über dem Editorfenster wäre die dritte Ebene.
     */
    private void delete(String name) {
        if (project.files().size() <= 1) {
            return;
        }
        project = project.without(name);
        onChanged.accept(project);
        if (name.equals(open)) {
            onOpen.accept(project.names().get(0));
        }
    }

    // ---- Menü --------------------------------------------------------------

    private void closeMenu() {
        menu = null;
    }

    private void openMenu(String name, double mouseX, double mouseY) {
        boolean last = project.files().size() <= 1;
        // Eine Datei, die jemand anders hält, wird weder umbenannt noch
        // gelöscht — das wäre dasselbe Überschreiben durch die Hintertür.
        boolean free = dev.devpanda.factorynetwork.client.ClientProjectState.heldBy(name) == null;
        if (!free) {
            menu = new ContextMenu(font, (int) mouseX, (int) mouseY, List.of(
                    new ContextMenu.Entry("screen.factorynetwork.project.rename", false,
                            () -> { }),
                    new ContextMenu.Entry("screen.factorynetwork.project.duplicate", true,
                            () -> duplicate(name)),
                    new ContextMenu.Entry("screen.factorynetwork.project.delete", false,
                            () -> { })));
            return;
        }
        menu = new ContextMenu(font, (int) mouseX, (int) mouseY, List.of(
                new ContextMenu.Entry("screen.factorynetwork.project.rename", true,
                        () -> beginRename(name)),
                new ContextMenu.Entry("screen.factorynetwork.project.duplicate", true,
                        () -> duplicate(name)),
                new ContextMenu.Entry("screen.factorynetwork.project.delete", !last,
                        () -> delete(name))));
    }

    // ---- Bedienung ---------------------------------------------------------

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (menu != null) {
            // Auch ein Klick daneben gehört dem Menü: Er schließt es, und er
            // soll dabei nicht zusätzlich den Cursor im Editor versetzen.
            boolean inside = menu.mouseClicked(mouseX, mouseY);
            closeMenu();
            return inside || contains(mouseX, mouseY);
        }
        if (renaming != null) {
            commitRename(true);
            // Kein return: Der Klick, der die Eingabe beendet, darf im selben
            // Zug die Datei treffen, auf die er zeigt.
        }
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        if (button == 0 && overPlus(mouseX, mouseY)) {
            addFile();
            return true;
        }
        int index = rowAt(mouseX, mouseY);
        if (index < 0) {
            return true;
        }
        String name = project.names().get(index);
        if (button == 1) {
            openMenu(name, mouseX, mouseY);
            return true;
        }
        if (name.equals(open)) {
            // Ein zweiter Klick auf die offene Datei benennt um — so wie ein
            // langsamer Doppelklick im Dateimanager.
            beginRename(name);
            return true;
        }
        onOpen.accept(name);
        return true;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x - 3 && mouseX < x + width && mouseY >= y - 2 && mouseY < y + height;
    }

    /**
     * Tasten für den Dateibaum.
     *
     * <p><b>Nur solange eine Eingabe oder ein Menü offen ist, und F2.</b> Der
     * Baum nimmt bewusst keinen Tastaturgriff: Hätte er einen, wanderten die
     * Pfeiltasten nach einem Klick auf eine Datei zwischen den Dateien statt
     * durch den Text, und Entfernen löschte eine Datei statt eines Zeichens.
     * Gelöscht wird über das Menü — dorthin kommt man nicht aus Versehen.
     */
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (menu != null) {
            if (key == 256) {
                closeMenu();
                return true;
            }
            return false;
        }
        if (renaming == null) {
            if (key == 291) { // F2
                beginRename(open);
                return true;
            }
            return false;
        }
        switch (key) {
            case 257, 335 -> commitRename(false);
            case 256 -> cancelRename();
            case 259 -> {
                if (!typed.isEmpty()) {
                    typed = typed.substring(0, typed.length() - 1);
                }
            }
            default -> { }
        }
        return true;
    }

    public boolean charTyped(char character, int modifiers) {
        if (renaming == null) {
            return false;
        }
        // Alles, was in einen Dateinamen darf, und sonst nichts. Ein
        // Grossbuchstabe wird kleingeschrieben statt verworfen: Er ist
        // erkennbar gemeint, nur nicht erlaubt.
        char lower = Character.toLowerCase(character);
        if ((lower >= 'a' && lower <= 'z') || (lower >= '0' && lower <= '9')
                || lower == '_' || lower == '.') {
            typed = typed + lower;
        }
        return true;
    }
}
