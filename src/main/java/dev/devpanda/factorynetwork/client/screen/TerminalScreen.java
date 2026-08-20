package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.ClientNetworkState;
import dev.devpanda.factorynetwork.client.ClientStorageView;
import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import dev.devpanda.factorynetwork.network.packet.DeployProgramPacket;
import dev.devpanda.factorynetwork.network.packet.StorageTabPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Das Terminal.
 *
 * <p>Ein Fenster mit Reitern statt einer Oberfläche je Block. Das Gehäuse ist
 * bewusst Minecraft: dieselben Grautöne, dasselbe Slotraster, das
 * Spielerinventar an der Stelle, an der es immer sitzt. Ein Inventar soll sich
 * anfühlen wie jedes andere.
 *
 * <p>Die Ausnahme ist der Code-Reiter. Der ist kein Inventar, sondern ein
 * Bildschirm, und sitzt als dunkle Fläche im hellen Gehäuse — wie ein Gerät,
 * das man eingebaut hat.
 */
public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {

    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/terminal.png");
    private static final ResourceLocation WIDGETS = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/widgets.png");

    static final int WIDTH = 288;
    static final int HEIGHT = 236;
    static final int TAB_HEIGHT = 18;
    static final int WORK_X = 7;
    static final int WORK_Y = 22;
    static final int WORK_W = 274;
    static final int WORK_H = 118;

    static final int TEXT = 0x404040;
    static final int TEXT_DIM = 0x6E6E6E;

    private TerminalTab tab = TerminalTab.STORAGE;
    private StorageTabView storageView;
    private NetworkTabView networkView;
    private CodeTabView codeView;

    public TerminalScreen(TerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.inventoryLabelY = 144;
    }

    @Override
    protected void init() {
        super.init();
        storageView = new StorageTabView(this, font, leftPos + WORK_X, topPos + WORK_Y,
                WORK_W, WORK_H);
        networkView = new NetworkTabView(font, leftPos + WORK_X, topPos + WORK_Y, WORK_W, WORK_H);
        codeView = new CodeTabView(font, leftPos + WORK_X, topPos + WORK_Y, WORK_W, WORK_H,
                codeView == null ? ClientNetworkState.source() : codeView.text());
        announceTab();
    }

    /** Der Server erfährt nur, ob der Speicher offen ist — sonst nichts. */
    private void announceTab() {
        PacketDistributor.sendToServer(new StorageTabPacket(tab == TerminalTab.STORAGE));
    }

    private void switchTo(TerminalTab next) {
        if (next == tab || !next.isReady()) {
            return;
        }
        tab = next;
        announceTab();
    }

    TerminalTab tab() {
        return tab;
    }

    // ---- Zeichnen ---------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTabTooltips(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, WIDTH, HEIGHT, 512, 512);
        drawTabs(graphics, mouseX, mouseY);
        switch (tab) {
            case STORAGE -> storageView.render(graphics, mouseX, mouseY);
            case NETWORK -> networkView.render(graphics, mouseX, mouseY);
            case CODE -> codeView.render(graphics);
            default -> { }
        }
    }

    private int tabWidth() {
        return (WIDTH - 10) / TerminalTab.values().length;
    }

    private void drawTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int width = tabWidth();
        for (TerminalTab candidate : TerminalTab.values()) {
            int x = leftPos + 5 + candidate.ordinal() * width;
            int y = topPos + 4;
            boolean active = candidate == tab;
            // Reiterhintergrund aus dem Kleinteil-Atlas
            graphics.blit(WIDGETS, x, y, 0, active ? 16 : 0, width - 1, 14, 512, 512);
            Component title = candidate.title();
            int colour = active ? TEXT : candidate.isReady() ? TEXT_DIM : 0x8B8B8B;
            String shown = font.plainSubstrByWidth(title.getString(), width - 6);
            graphics.drawString(font, shown, x + (width - font.width(shown)) / 2, y + 4,
                    colour, false);
        }
    }

    private void renderTabTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        int width = tabWidth();
        for (TerminalTab candidate : TerminalTab.values()) {
            if (candidate.isReady()) {
                continue;
            }
            int x = leftPos + 5 + candidate.ordinal() * width;
            int y = topPos + 4;
            if (mouseX >= x && mouseX < x + width - 1 && mouseY >= y && mouseY < y + 14) {
                graphics.renderTooltip(font, candidate.notReadyHint(), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Der Fenstertitel steht in der Reiterleiste; nur das Inventar wird
        // beschriftet, so wie in jedem Vanilla-Fenster.
        graphics.drawString(font, playerInventoryTitle, 63, inventoryLabelY,
                TEXT, false);
    }

    // ---- Eingabe ----------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Reiterleiste zuerst
        int width = tabWidth();
        for (TerminalTab candidate : TerminalTab.values()) {
            int x = leftPos + 5 + candidate.ordinal() * width;
            int y = topPos + 4;
            if (mouseX >= x && mouseX < x + width - 1 && mouseY >= y && mouseY < y + 14) {
                switchTo(candidate);
                return true;
            }
        }
        boolean handled = switch (tab) {
            case STORAGE -> storageView.mouseClicked(mouseX, mouseY, button);
            case CODE -> codeView.mouseClicked(mouseX, mouseY, button);
            case NETWORK -> networkView.mouseClicked(mouseX, mouseY, button);
            default -> false;
        };
        return handled || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        boolean handled = switch (tab) {
            case STORAGE -> storageView.mouseScrolled(mouseX, mouseY, deltaY);
            case CODE -> codeView.mouseScrolled(mouseX, mouseY, deltaY);
            default -> false;
        };
        return handled || super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == 256) {
            onClose();
            return true;
        }
        if (tab == TerminalTab.CODE) {
            // Strg+Eingabe übernimmt das Programm.
            if ((key == 257 || key == 335) && hasControlDown()) {
                PacketDistributor.sendToServer(
                        new DeployProgramPacket(menu.position(), codeView.text()));
                onClose();
                return true;
            }
            if (codeView.keyPressed(key, scanCode, modifiers)) {
                return true;
            }
            // Wichtig: Im Editor darf die Inventartaste das Fenster nicht
            // schließen, sonst verliert man beim Tippen eines "e" den Code.
            return true;
        }
        if (tab == TerminalTab.STORAGE && storageView.keyPressed(key, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (tab == TerminalTab.CODE) {
            return codeView.charTyped(character, modifiers);
        }
        if (tab == TerminalTab.STORAGE) {
            return storageView.charTyped(character, modifiers);
        }
        return super.charTyped(character, modifiers);
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(new StorageTabPacket(false));
        ClientStorageView.clear();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
