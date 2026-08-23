package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.FnFonts;
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
 * bewusst Minecraft: dasselbe Slotraster, das Spielerinventar an der Stelle,
 * an der es immer sitzt. <b>Was blind funktioniert, ist die Position</b> —
 * dieselben Stellen, dieselben Maße, dieselben Abstände.
 *
 * <p>Die Farbe ist es nicht. Das Terminal ist ein Gerät und sieht auch so
 * aus: ein Blechgehäuse mit einer dunklen Scheibe darin, in die alle Reiter
 * zeichnen. Vorher war nur der Code-Reiter dunkel und alles andere hellgrau —
 * das waren zwei Entwürfe in einem Fenster.
 *
 * <p>Drei Ebenen, eine Regel: <b>Was vertieft liegt, ist dunkler als sein
 * Grund und hat unten rechts eine helle Kante.</b> Blech, Scheibe, Mulde.
 */
public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {

    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/terminal.png");
    private static final ResourceLocation WIDGETS = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/widgets.png");

    static final int WIDTH = 288;

    /** Die Scheibe im Blech — dieselben Zahlen wie in {@code gui.py}. */
    static final int SCREEN_X0 = 6;
    static final int SCREEN_X1 = WIDTH - 7;
    static final int SCREEN_TOP = 6;
    static final int SCREEN_PAD = 3;
    static final int TAB_ROW = 14;
    static final int STATUS_ROW = 11;

    /**
     * Die Höhe fällt aus der Rechnung heraus und wird nicht gesetzt.
     *
     * <p>Beim Entwurf hatte ich sie geraten, und die Statuszeile lag im
     * Raster. Jede Zeile kennt ihre Höhe; was das Fenster misst, ist die
     * Summe.
     */
    static final int WORK_X = SCREEN_X0 + 3;
    static final int WORK_Y = SCREEN_TOP + SCREEN_PAD + TAB_ROW + 1;
    static final int WORK_W = (SCREEN_X1 - 2) - WORK_X;
    static final int WORK_H = 123;
    static final int SCREEN_BOTTOM = WORK_Y + WORK_H + 2 + STATUS_ROW;
    static final int HEIGHT = SCREEN_BOTTOM + 14 + 82;

    /** Wie weit die Reiterbeschriftungen auseinanderstehen. */
    private static final int TAB_GAP = 12;

    // Schrift auf dunklem Grund. Die Werte sind dieselben wie in der
    // Blockpalette der Mod — wer den Controller ansieht und dann das
    // Terminal, soll denselben Farbklang wiedererkennen.
    static final int TEXT = 0xD3DBD5;
    static final int TEXT_DIM = 0x8B978F;
    static final int TEXT_FAINT = 0x5D6862;
    static final int ACCENT = 0x78DC8C;

    private TerminalTab tab = TerminalTab.STORAGE;
    private StorageTabView storageView;
    private NetworkTabView networkView;
    private DashboardsTabView dashboardsView;
    private CodeTabView codeView;

    public TerminalScreen(TerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.inventoryLabelY = HEIGHT - 82 - 11;
    }

    @Override
    protected void init() {
        super.init();
        storageView = new StorageTabView(this, font, leftPos + WORK_X, topPos + WORK_Y,
                WORK_W, WORK_H);
        networkView = new NetworkTabView(font, leftPos + WORK_X, topPos + WORK_Y, WORK_W, WORK_H);
        dashboardsView = new DashboardsTabView(font, leftPos + WORK_X, topPos + WORK_Y,
                WORK_W, WORK_H);
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
        if (tab == TerminalTab.STORAGE) {
            storageView.renderTooltip(graphics, mouseX, mouseY);
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, WIDTH, HEIGHT, 512, 512);
        drawTabs(graphics, mouseX, mouseY);
        switch (tab) {
            case STORAGE -> storageView.render(graphics, mouseX, mouseY);
            case NETWORK -> networkView.render(graphics, mouseX, mouseY);
            case DASHBOARDS -> dashboardsView.render(graphics, mouseX, mouseY);
            case CODE -> codeView.render(graphics);
            default -> { }
        }
    }

    /**
     * Wie breit eine Reiterbeschriftung ist.
     *
     * <p>Nach dem Text und nicht nach dem Fünftel des Fensters: Reiter sind
     * jetzt Beschriftungen und keine Karteikarten, und eine Beschriftung ist
     * so breit, wie sie ist. Nebenbei fällt damit der Fehler weg, dass eine
     * feste Breite zu einer Grafik fester Breite passen musste.
     */
    private int tabWidth(TerminalTab tab) {
        return font.width(FnFonts.mono(tab.title()));
    }

    /** Wo eine Reiterbeschriftung anfängt, im Fenster gerechnet. */
    private int tabX(TerminalTab wanted) {
        int x = SCREEN_X0 + 6;
        for (TerminalTab candidate : TerminalTab.values()) {
            if (candidate == wanted) {
                return x;
            }
            x += tabWidth(candidate) + TAB_GAP;
        }
        return x;
    }

    private static int tabY() {
        return SCREEN_TOP + SCREEN_PAD;
    }

    /**
     * Die Reiter: flacher Text in der Scheibe, der aktive mit einem Strich.
     *
     * <p>Karteikarten sind das Kreativ-Inventar. Ein Gerät hat
     * Beschriftungen, und welche gilt, sagt eine Linie darunter — so wie in
     * jedem Editor.
     */
    private void drawTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        for (TerminalTab candidate : TerminalTab.values()) {
            int x = leftPos + tabX(candidate);
            int y = topPos + tabY();
            boolean active = candidate == tab;
            int colour = active ? TEXT : candidate.isReady() ? TEXT_DIM : TEXT_FAINT;
            graphics.drawString(font, FnFonts.mono(candidate.title()), x, y, colour, false);
            if (active) {
                graphics.fill(x, y + 11, x + tabWidth(candidate), y + 12, 0xFF000000 | ACCENT);
            }
        }
    }

    /** Trifft der Zeiger diese Beschriftung? */
    private boolean overTab(TerminalTab candidate, double mouseX, double mouseY) {
        int x = leftPos + tabX(candidate);
        int y = topPos + tabY();
        return mouseX >= x && mouseX < x + tabWidth(candidate)
                && mouseY >= y && mouseY < y + TAB_ROW - 2;
    }

    private void renderTabTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        for (TerminalTab candidate : TerminalTab.values()) {
            if (!candidate.isReady() && overTab(candidate, mouseX, mouseY)) {
                graphics.renderTooltip(font, candidate.notReadyHint(), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Der Fenstertitel steht in der Reiterleiste; nur das Inventar wird
        // beschriftet, so wie in jedem Vanilla-Fenster.
        graphics.drawString(font, playerInventoryTitle, (WIDTH - 9 * 18) / 2,
                inventoryLabelY, TEXT_DIM, false);
    }

    // ---- Eingabe ----------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Reiterleiste zuerst
        for (TerminalTab candidate : TerminalTab.values()) {
            if (overTab(candidate, mouseX, mouseY)) {
                switchTo(candidate);
                return true;
            }
        }
        boolean handled = switch (tab) {
            case STORAGE -> storageView.mouseClicked(mouseX, mouseY, button);
            case CODE -> codeView.mouseClicked(mouseX, mouseY, button);
            case NETWORK -> networkView.mouseClicked(mouseX, mouseY, button);
            case DASHBOARDS -> dashboardsView.mouseClicked(mouseX, mouseY, button);
            default -> false;
        };
        return handled || super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Ziehen gehört dem Code-Reiter: Dort wählt es Text aus.
     *
     * <p>Die anderen Reiter kennen kein Ziehen — dort geht der Griff an
     * Minecraft zurück, damit Schieberegler und Knöpfe weiter arbeiten.
     */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (tab == TerminalTab.CODE && codeView.mouseDragged(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
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
