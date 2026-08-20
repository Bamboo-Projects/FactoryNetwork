package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.lang.Diagnostic;
import dev.devpanda.factorynetwork.lang.parse.Parser;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Der Code-Reiter.
 *
 * <p>Die eine Stelle, an der das Terminal nicht wie ein Inventar aussieht: ein
 * dunkler Bildschirm, versenkt ins helle Gehäuse. Ein Texteditor im grauen
 * Panel läse sich falsch — ComputerCraft macht seinen Terminalschirm aus
 * demselben Grund schwarz.
 */
public class CodeTabView {

    private static final ResourceLocation SCREEN = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/screen.png");

    private final Font font;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final CodeEditor editor;

    private List<Diagnostic> diagnostics = List.of();
    private String status = "";
    private int statusColour = 0x6E7A70;

    public CodeTabView(Font font, int x, int y, int width, int height, String initial) {
        this.font = font;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.editor = new CodeEditor(font, x + 2, y + 2, width - 4, height - 12, initial);
        this.editor.setChangeListener(this::onChanged);
        onChanged(editor.text());
    }

    private void onChanged(String text) {
        Parser.ParseResult result = Parser.parse(text);
        diagnostics = result.diagnostics();
        if (diagnostics.isEmpty()) {
            status = Component.translatable("screen.factorynetwork.terminal.ok").getString();
            statusColour = 0x6E7A70;
        } else {
            Diagnostic first = diagnostics.get(0);
            status = first.span().line() + ": " + first.message()
                    + (first.hint() == null ? "" : " " + first.hint());
            statusColour = first.isError() ? 0xE88388 : 0xE8B888;
        }
    }

    public String text() {
        return editor.text();
    }

    public void render(GuiGraphics graphics) {
        graphics.blit(SCREEN, x, y, 0, 0, width, height, 256, 256);
        editor.render(graphics, diagnostics);
        graphics.drawString(font, font.plainSubstrByWidth(status, width - 6),
                x + 3, y + height - 9, statusColour, false);
    }

    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return editor.keyPressed(key, scanCode, modifiers);
    }

    public boolean charTyped(char character, int modifiers) {
        return editor.charTyped(character, modifiers);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return editor.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return editor.mouseScrolled(mouseX, mouseY, delta);
    }
}
