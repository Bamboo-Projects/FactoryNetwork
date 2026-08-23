package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.client.ClientDeployState;
import dev.devpanda.factorynetwork.lang.Diagnostic;
import dev.devpanda.factorynetwork.lang.parse.Parser;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Der Code-Reiter.
 *
 * <p>Der Editor und darunter eine Fußleiste: links, was der Übersetzer sagt,
 * rechts der Knopf zum Übernehmen.
 *
 * <p><b>Der Knopf ist neu, und er war überfällig.</b> Übernehmen ging nur mit
 * Strg+Eingabe, und nichts im Fenster sagte das — eine Tastenkombination, die
 * man kennen muss, ist keine Bedienung. Die Kombination bleibt; wer sie kennt,
 * ist schneller.
 */
public class CodeTabView {

    /** Höhe der Fußleiste unter dem Editor. */
    private static final int FOOTER = 14;

    /** Der Knopf rechts darin. */
    private static final int BUTTON_WIDTH = 66;
    private static final int BUTTON_HEIGHT = 12;

    private final TerminalScreen screen;
    private final Font font;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final CodeEditor editor;

    private List<Diagnostic> diagnostics = List.of();
    private String status = "";
    private int statusColour = TerminalScreen.TEXT_FAINT;

    public CodeTabView(TerminalScreen screen, Font font, int x, int y, int width, int height,
                       String initial) {
        this.screen = screen;
        this.font = font;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.editor = new CodeEditor(font, x + 2, y + 2, width - 4, height - FOOTER - 2,
                initial);
        this.editor.setChangeListener(this::onChanged);
        onChanged(editor.text());
    }

    private void onChanged(String text) {
        // Was der Server zuletzt gesagt hat, gilt für den Text von damals.
        // Eine stehengebliebene Erfolgsmeldung über geändertem Code ist
        // schlimmer als keine.
        ClientDeployState.clear();
        Parser.ParseResult result = Parser.parse(text);
        diagnostics = result.diagnostics();
        if (diagnostics.isEmpty()) {
            status = Component.translatable("screen.factorynetwork.terminal.ok").getString();
            statusColour = TerminalScreen.TEXT_FAINT;
        } else {
            Diagnostic first = diagnostics.get(0);
            status = first.span().line() + ": " + first.message()
                    + (first.hint() == null ? "" : " " + first.hint());
            statusColour = first.isError() ? TerminalScreen.BAD : TerminalScreen.WARN;
        }
    }

    public String text() {
        return editor.text();
    }

    /** Wo der Cursor steht, für die Statuszeile des Fensters. */
    public Component cursorPosition() {
        return Component.translatable("screen.factorynetwork.terminal.cursor",
                editor.cursorLine() + 1, editor.cursorColumn() + 1);
    }

    private int buttonX() {
        return x + width - BUTTON_WIDTH - 2;
    }

    private int buttonY() {
        return y + height - FOOTER + 1;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        editor.render(graphics, diagnostics);

        // Die Fußleiste: die Meldung so breit, wie der Knopf sie lässt.
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

    /** Beim Zeigen auf den Knopf steht die Tastenkombination dabei. */
    public void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (overButton(mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable(
                    "screen.factorynetwork.terminal.deploy.hint"), mouseX, mouseY);
        }
    }

    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return editor.keyPressed(key, scanCode, modifiers);
    }

    public boolean charTyped(char character, int modifiers) {
        return editor.charTyped(character, modifiers);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && overButton(mouseX, mouseY)) {
            screen.deploy();
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
