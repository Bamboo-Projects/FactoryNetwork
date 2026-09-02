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
 * The file tree of a project.
 *
 * <p>A column with the files, and within it everything you do to a file:
 * create, rename, duplicate, delete. <b>That was missing, and the hint text
 * in the window told people to leave the game for it</b> — the folder next to
 * the world is a good second route, but no substitute for the first.
 *
 * <p>Renaming happens in place: the row becomes an input field. A separate
 * window for a file name would be a window on top of a window, and while
 * typing you would lose sight of what the other files are called — exactly
 * what you go by when naming.
 *
 * <p>A name that already exists or does not fit the pattern is shown red and
 * not accepted. The input stays put while that happens: a field that clears
 * itself on a typo is the kind of helpfulness you type it twice for.
 */
public class ProjectPanel {

    /** Height of a row. */
    private static final int ROW = 12;

    /** Height of the heading with the plus sign. */
    private static final int HEADER = 16;

    /** The floor of the column — the lowest of the three layers. */
    private static final int WELL = 0xFF0E1214;

    /** The band the heading sits on. */
    private static final int HEADER_BAND = 0xFF161C19;

    /** The open file: lighter than the floor, because it lies on top. */
    private static final int ROW_ACTIVE = 0xFF232B27;

    private final Font font;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    /** Where the changes go. */
    private final Consumer<Project> onChanged;

    /** What happens when a file is selected. */
    private final Consumer<String> onOpen;

    private Project project;
    private String open;
    private List<Diagnostic> problems = List.of();

    /** Which file is currently being renamed, or {@code null}. */
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

    /** Is an input that needs the keyboard currently active? */
    public boolean isEditing() {
        return renaming != null;
    }

    public boolean hasMenu() {
        return menu != null;
    }

    // ---- Drawing -----------------------------------------------------------

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

    /** Which row lies under the cursor, or -1. */
    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < x || mouseX >= x + width) {
            return -1;
        }
        int index = (int) Math.floor((mouseY - y - HEADER) / (double) ROW);
        return index >= 0 && index < project.names().size() ? index : -1;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        // A recess of its own next to the editor, so the column reads as an
        // area and not as text that happens to sit on the left. The value is
        // markedly darker than the pane and not just a little: on a surface
        // this size you no longer see an edge, only the shade.
        graphics.fill(x - 3, y - 2, x + width, y + height, WELL);
        // The vertical edge to the editor carries the separation. Light on
        // the right, as with every recess in this project.
        graphics.fill(x + width, y - 2, x + width + 1, y + height, CodeScreen.EDGE);
        // The heading sits on a band of its own — otherwise the plus sign
        // hangs free in the dark.
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
            // A locked file dim: you may open and read it, only not change
            // it. Grey is the more honest colour for that than red — it is
            // not an error, it is someone else.
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
     * The row as an input field.
     *
     * <p>The cursor is a stroke after the text and does not blink: in a field
     * that stays open exactly as long as it takes to type a name, a blink is
     * only motion.
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
     * A file name, shortened so that the name itself is what remains.
     *
     * <p>In {@code erz/eisen/schmelzen.mf} the important part is at the end.
     * Shortening from the right would give {@code erz/eisen/schm}, and two
     * files in the same folder would then look alike. So it is shortened from
     * the front: {@code …/schmelzen.mf}.
     *
     * <p>Without a folder it stays shortened from the right — that is where
     * the extension is, and it is the same for every file.
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

    // ---- Renaming ----------------------------------------------------------

    /**
     * Does this name pass?
     *
     * <p>The file's own old name passes — otherwise the row would sit there
     * red the moment you open the rename and have not changed anything yet.
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
     * Applies the typed name.
     *
     * @param force whether the input also closes when the name does not pass
     *              — as with a click elsewhere, which is a cancellation. The
     *              Enter key, by contrast, leaves the field open: someone who
     *              has just typed a red name wants to change it, not lose it.
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

    // ---- Create, Duplicate, Delete -----------------------------------------

    /**
     * Creates a file — and opens it immediately for naming.
     *
     * <p>The name {@code datei2.mf} is a placeholder, not a suggestion.
     * Anyone creating a file knows what it should be called; opening the input
     * right away saves the second step and the one case where a file is called
     * {@code datei2.mf} forever, because renaming was too much bother.
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
     * Deletes a file.
     *
     * <p>Without a confirmation prompt, but only through the menu: two clicks
     * in a place you do not reach by accident are the confirmation. A
     * confirmation window on top of the editor window would be the third
     * layer.
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

    // ---- Menu --------------------------------------------------------------

    private void closeMenu() {
        menu = null;
    }

    private void openMenu(String name, double mouseX, double mouseY) {
        boolean last = project.files().size() <= 1;
        // A file someone else is holding is neither renamed nor deleted —
        // that would be the same overwriting through the back door.
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

    // ---- Controls ----------------------------------------------------------

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (menu != null) {
            // A click elsewhere belongs to the menu too: it closes the menu,
            // and should not also move the cursor in the editor while doing so.
            boolean inside = menu.mouseClicked(mouseX, mouseY);
            closeMenu();
            return inside || contains(mouseX, mouseY);
        }
        if (renaming != null) {
            commitRename(true);
            // No return: the click that ends the input may, in the same
            // motion, hit the file it points at.
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
            // A second click on the open file renames it — like a slow double
            // click in a file manager.
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
     * Keys for the file tree.
     *
     * <p><b>Only while an input or a menu is open, and F2.</b> The tree
     * deliberately takes no keyboard focus: if it did, after a click on a file
     * the arrow keys would move between the files instead of through the text,
     * and Delete would remove a file instead of a character. Deleting goes
     * through the menu — you do not get there by accident.
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
        // Everything allowed in a file name, and nothing else. An uppercase
        // letter is lowercased rather than discarded: it is recognisably
        // intended, only not permitted.
        char lower = Character.toLowerCase(character);
        if ((lower >= 'a' && lower <= 'z') || (lower >= '0' && lower <= '9')
                || lower == '_' || lower == '.') {
            typed = typed + lower;
        }
        return true;
    }
}
