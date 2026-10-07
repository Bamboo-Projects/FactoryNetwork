package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.bamboocef.web.api.FnWeb;
import dev.devpanda.factorynetwork.client.EditorApp;
import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Keeps the terminal usable while the browser runtime downloads or starts. */
public final class WebEditorLoadingScreen extends Screen {
    private final Screen parent;
    private final TerminalMenu menu;
    private int ticks;

    public WebEditorLoadingScreen(Screen parent, TerminalMenu menu) {
        super(Component.literal("Manifold-Editor"));
        this.parent = parent;
        this.menu = menu;
    }

    @Override
    protected void init() {
        int y = height / 2 + 28;
        addRenderableWidget(Button.builder(Component.literal("Erneut versuchen"), b -> tryOpen())
                .bounds(width / 2 - 155, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Klassischer Editor"),
                        b -> minecraft.setScreen(new CodeScreen(parent, menu.position())))
                .bounds(width / 2 + 5, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Zurück zum Terminal"), b -> onClose())
                .bounds(width / 2 - 100, y + 26, 200, 20).build());
    }

    private void tryOpen() {
        EditorApp.open(minecraft, parent, menu);
    }

    @Override
    public void tick() {
        if (minecraft.player == null || minecraft.player.containerMenu != menu) {
            minecraft.setScreen(null);
            return;
        }
        if (++ticks % 20 == 0) {
            boolean preparing = switch (FnWeb.status().state()) {
                case NOT_STARTED, NOT_DOWNLOADED, READY -> true;
                default -> false;
            };
            if (preparing) tryOpen();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 48, 0x78DC8C);
        var status = FnWeb.status();
        String text = switch (status.state()) {
            case NOT_STARTED -> "Der Editor wird vorbereitet …";
            case NOT_DOWNLOADED -> "BambooCEF wird heruntergeladen. Der Editor öffnet sich anschließend automatisch.";
            case READY -> "Der Editor wird geöffnet …";
            case UNSUPPORTED -> "Der Web-Editor ist auf diesem Betriebssystem noch nicht verfügbar.";
            default -> "BambooCEF konnte den Editor nicht starten. Der klassische Editor bleibt verfügbar.";
        };
        graphics.drawWordWrap(font, Component.literal(text), Math.max(16, width / 2 - 210),
                height / 2 - 20, Math.min(420, width - 32), 0xD3DBD5);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
