package dev.devpanda.factorynetwork.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.devpanda.factorynetwork.client.ClientNetworkState;
import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import dev.devpanda.factorynetwork.lang.Diagnostic;
import dev.devpanda.factorynetwork.lang.parse.Parser;
import dev.devpanda.factorynetwork.network.packet.DeployProgramPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Der Code-Editor.
 *
 * <p>Bewusst kein eingebettetes VS Code, sondern eine schmale Oberfläche, die
 * das kann, was in einer Fabrik gebraucht wird: schreiben, sehen was falsch
 * ist, übernehmen. Links steht, was im Netz hängt — ohne diese Liste ist ein
 * Name im Code nur geraten.
 *
 * <p>Die Farben folgen einer gedeckten Palette statt der bunten Vanilla-GUI,
 * weil hier über Minuten Text gelesen wird und nicht über Sekunden ein
 * Inventar überflogen.
 */
public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {

    private static final int WIDTH = 340;
    private static final int HEIGHT = 200;

    private static final int SIDEBAR_WIDTH = 96;
    private static final int STATUS_HEIGHT = 14;
    private static final int PADDING = 6;

    // Gedeckte Palette, dunkel — der Editor wird gelesen, nicht überflogen.
    private static final int COLOR_BACKGROUND = 0xF01A1C1F;
    private static final int COLOR_PANEL = 0xFF232629;
    private static final int COLOR_BORDER = 0xFF3C4147;
    private static final int COLOR_TEXT = 0xFFD8DEE4;
    private static final int COLOR_MUTED = 0xFF8A939C;
    private static final int COLOR_KEYWORD = 0xFF8AB4F8;
    private static final int COLOR_SELECTOR = 0xFFA3D9A5;
    private static final int COLOR_COMMENT = 0xFF6B7480;
    private static final int COLOR_ERROR = 0xFFE88388;
    private static final int COLOR_CURSOR = 0xFFD8DEE4;

    private CodeEditor editor;
    private List<Diagnostic> diagnostics = List.of();
    private String status = "";
    private int statusColor = COLOR_MUTED;

    public TerminalScreen(TerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - WIDTH) / 2;
        this.topPos = (this.height - HEIGHT) / 2;

        int codeLeft = leftPos + SIDEBAR_WIDTH + PADDING;
        int codeTop = topPos + PADDING + 10;
        int codeWidth = WIDTH - SIDEBAR_WIDTH - PADDING * 2;
        int codeHeight = HEIGHT - STATUS_HEIGHT - PADDING * 2 - 12;

        String initial = editor != null ? editor.text() : ClientNetworkState.source();
        editor = new CodeEditor(font, codeLeft, codeTop, codeWidth, codeHeight, initial);
        editor.setChangeListener(this::onCodeChanged);
        onCodeChanged(editor.text());
    }

    private void onCodeChanged(String text) {
        Parser.ParseResult result = Parser.parse(text);
        diagnostics = result.diagnostics();
        if (diagnostics.isEmpty()) {
            status = Component.translatable("screen.factorynetwork.terminal.ok").getString();
            statusColor = COLOR_MUTED;
        } else {
            Diagnostic first = diagnostics.get(0);
            status = first.span().line() + ": " + first.message()
                    + (first.hint() == null ? "" : " " + first.hint());
            statusColor = first.isError() ? COLOR_ERROR : COLOR_MUTED;
        }
    }

    // ---- Zeichnen ---------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.enableBlend();
        graphics.fill(leftPos, topPos, leftPos + WIDTH, topPos + HEIGHT, COLOR_BACKGROUND);
        drawBorder(graphics, leftPos, topPos, WIDTH, HEIGHT, COLOR_BORDER);

        drawSidebar(graphics);
        drawCodeArea(graphics);
        drawStatusBar(graphics);
    }

    private void drawSidebar(GuiGraphics graphics) {
        int x = leftPos + PADDING;
        int y = topPos + PADDING;
        int width = SIDEBAR_WIDTH - PADDING;
        int height = HEIGHT - STATUS_HEIGHT - PADDING * 2;
        graphics.fill(x, y, x + width, y + height, COLOR_PANEL);

        int line = y + 4;
        graphics.drawString(font,
                Component.translatable("screen.factorynetwork.terminal.network"),
                x + 4, line, COLOR_MUTED, false);
        line += 12;

        List<String> connectors = ClientNetworkState.connectors();
        if (connectors.isEmpty()) {
            graphics.drawString(font,
                    Component.translatable("screen.factorynetwork.terminal.no_connectors"),
                    x + 4, line, COLOR_COMMENT, false);
        } else {
            for (String connector : connectors) {
                if (line > y + height - 22) {
                    graphics.drawString(font, "…", x + 4, line, COLOR_COMMENT, false);
                    break;
                }
                graphics.drawString(font, font.plainSubstrByWidth(connector, width - 10),
                        x + 4, line, COLOR_TEXT, false);
                line += 10;
            }
        }

        List<String> workers = ClientNetworkState.workers();
        if (!workers.isEmpty() && line < y + height - 24) {
            line += 6;
            graphics.drawString(font,
                    Component.translatable("screen.factorynetwork.terminal.workers"),
                    x + 4, line, COLOR_MUTED, false);
            line += 12;
            for (String worker : workers) {
                if (line > y + height - 12) {
                    break;
                }
                graphics.drawString(font, font.plainSubstrByWidth(worker, width - 10),
                        x + 4, line, COLOR_SELECTOR, false);
                line += 10;
            }
        }
    }

    private void drawCodeArea(GuiGraphics graphics) {
        int x = leftPos + SIDEBAR_WIDTH + PADDING;
        int y = topPos + PADDING;
        int width = WIDTH - SIDEBAR_WIDTH - PADDING * 2;
        int height = HEIGHT - STATUS_HEIGHT - PADDING * 2;
        graphics.fill(x, y, x + width, y + height, COLOR_PANEL);
        graphics.drawString(font, Component.translatable("screen.factorynetwork.terminal.code"),
                x + 4, y + 3, COLOR_MUTED, false);
        editor.render(graphics, diagnostics);
    }

    private void drawStatusBar(GuiGraphics graphics) {
        int y = topPos + HEIGHT - STATUS_HEIGHT;
        graphics.fill(leftPos, y, leftPos + WIDTH, topPos + HEIGHT, COLOR_PANEL);
        String shown = font.plainSubstrByWidth(status, WIDTH - 90);
        graphics.drawString(font, shown, leftPos + PADDING, y + 3, statusColor, false);

        Component hint = Component.translatable("screen.factorynetwork.terminal.deploy_hint");
        int hintWidth = font.width(hint);
        graphics.drawString(font, hint, leftPos + WIDTH - hintWidth - PADDING, y + 3,
                COLOR_MUTED, false);
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height,
                                   int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Der Editor hat keine Slots, also auch keine Beschriftungen darüber.
    }

    // ---- Eingabe ----------------------------------------------------------

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        // Strg+Eingabe übernimmt — Eingabe allein macht eine neue Zeile.
        if ((key == 257 || key == 335) && hasControlDown()) {
            deploy();
            return true;
        }
        if (key == 256) {
            onClose();
            return true;
        }
        if (editor.keyPressed(key, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (editor.charTyped(character, modifiers)) {
            return true;
        }
        return super.charTyped(character, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (editor.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (editor.mouseScrolled(mouseX, mouseY, deltaY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    private void deploy() {
        PacketDistributor.sendToServer(
                new DeployProgramPacket(menu.position(), editor.text()));
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- Farben für die Hervorhebung, vom Editor gebraucht -----------------

    static int colorText() {
        return COLOR_TEXT;
    }

    static int colorMuted() {
        return COLOR_MUTED;
    }

    static int colorKeyword() {
        return COLOR_KEYWORD;
    }

    static int colorSelector() {
        return COLOR_SELECTOR;
    }

    static int colorComment() {
        return COLOR_COMMENT;
    }

    static int colorError() {
        return COLOR_ERROR;
    }

    static int colorCursor() {
        return COLOR_CURSOR;
    }
}
