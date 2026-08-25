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

    // Die Maße stehen in TerminalLayout, weil das Menü sie auch braucht und
    // auf einem Server ohne Grafik läuft. Hier nur die kurzen Namen.
    static final int WIDTH = dev.devpanda.factorynetwork.client.menu.TerminalLayout.WIDTH;
    static final int HEIGHT = dev.devpanda.factorynetwork.client.menu.TerminalLayout.HEIGHT;
    static final int SCREEN_X0 =
            dev.devpanda.factorynetwork.client.menu.TerminalLayout.SCREEN_X0;
    static final int SCREEN_X1 =
            dev.devpanda.factorynetwork.client.menu.TerminalLayout.SCREEN_X1;
    static final int SCREEN_TOP =
            dev.devpanda.factorynetwork.client.menu.TerminalLayout.SCREEN_TOP;
    static final int SCREEN_PAD =
            dev.devpanda.factorynetwork.client.menu.TerminalLayout.SCREEN_PAD;
    static final int SCREEN_BOTTOM =
            dev.devpanda.factorynetwork.client.menu.TerminalLayout.SCREEN_BOTTOM;
    static final int TAB_ROW = dev.devpanda.factorynetwork.client.menu.TerminalLayout.TAB_ROW;
    static final int STATUS_ROW =
            dev.devpanda.factorynetwork.client.menu.TerminalLayout.STATUS_ROW;
    static final int WORK_X = dev.devpanda.factorynetwork.client.menu.TerminalLayout.WORK_X;
    static final int WORK_Y = dev.devpanda.factorynetwork.client.menu.TerminalLayout.WORK_Y;
    static final int WORK_W = dev.devpanda.factorynetwork.client.menu.TerminalLayout.WORK_W;
    static final int WORK_H = dev.devpanda.factorynetwork.client.menu.TerminalLayout.WORK_H;

    /** Wie weit die Reiterbeschriftungen auseinanderstehen. */
    private static final int TAB_GAP = 12;

    // Schrift auf dunklem Grund. Die Werte sind dieselben wie in der
    // Blockpalette der Mod — wer den Controller ansieht und dann das
    // Terminal, soll denselben Farbklang wiedererkennen.
    static final int TEXT = 0xD3DBD5;
    static final int TEXT_DIM = 0x8B978F;
    static final int TEXT_FAINT = 0x5D6862;
    static final int ACCENT = 0x78DC8C;

    /**
     * Zustandsfarben, für dunklen Grund gewählt.
     *
     * <p>Die alten waren für helles Grau gedacht: ein dunkles Grün für
     * „läuft", ein dunkles Rot für „gescheitert". Auf Blech verschlammen die
     * beiden zu derselben schmutzigen Fläche, und dann sagt die Farbe nichts
     * mehr.
     */
    static final int GOOD = 0x78DC8C;
    static final int WARN = 0xE8AC3E;
    static final int BAD = 0xE88388;

    /** Knöpfe im Bildschirm: erhabenes Blech, beim Zeigen heller. */
    static final int BUTTON = 0xFF232B27;
    static final int BUTTON_HOVER = 0xFF39443D;

    private TerminalTab tab = TerminalTab.STORAGE;
    private StorageTabView storageView;
    private NetworkTabView networkView;
    private DashboardsTabView dashboardsView;
    private LogTabView logView;
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
        logView = new LogTabView(font, leftPos + WORK_X, topPos + WORK_Y, WORK_W, WORK_H);
        codeView = new CodeTabView(this, font, leftPos + WORK_X, topPos + WORK_Y,
                WORK_W, WORK_H);
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
        renderStatusTooltip(graphics, mouseX, mouseY);
        if (tab == TerminalTab.CODE) {
            codeView.renderTooltip(graphics, mouseX, mouseY);
        }
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
            case LOG -> logView.render(graphics, mouseX, mouseY);
            case CODE -> codeView.render(graphics, mouseX, mouseY);
            default -> { }
        }
        // Zuletzt, damit sie über allem liegt, was ein Reiter unten zeichnet.
        drawStatus(graphics);
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

    /**
     * Der Abstand zwischen zwei Beschriftungen.
     *
     * <p><b>Schrumpft, wenn es eng wird.</b> Mit einem festen Abstand lief
     * der sechste Reiter aus dem Fenster, und der siebte täte es wieder. Die
     * Leiste rechnet lieber selbst nach, als dass jemand beim nächsten Reiter
     * daran denken muss.
     */
    private int tabGap() {
        int labels = 0;
        for (TerminalTab candidate : TerminalTab.values()) {
            labels += tabWidth(candidate);
        }
        int room = SCREEN_X1 - (SCREEN_X0 + 6) - labels;
        int gaps = Math.max(1, TerminalTab.values().length - 1);
        return Math.max(3, Math.min(TAB_GAP, room / gaps));
    }

    /** Wo eine Reiterbeschriftung anfängt, im Fenster gerechnet. */
    private int tabX(TerminalTab wanted) {
        int x = SCREEN_X0 + 6;
        int gap = tabGap();
        for (TerminalTab candidate : TerminalTab.values()) {
            if (candidate == wanted) {
                return x;
            }
            x += tabWidth(candidate) + gap;
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

    /**
     * Die Statuszeile am unteren Rand der Scheibe.
     *
     * <p>Rechts, was das Netz macht — auf jedem Reiter dasselbe. Links, was
     * der Reiter zu sagen hat. <b>Statuszeilen gehören nach unten</b>; das
     * weiß jeder, der einen Editor benutzt, und oben wäre sie eine dritte
     * Zeile Kopf über dem eigentlichen Inhalt.
     *
     * <p>Beide Texte werden gemessen und gegeneinander geprüft. Beim Entwurf
     * hatte ich nur den rechten gemessen, und sie schoben sich ineinander.
     */
    private void drawStatus(GuiGraphics graphics) {
        int y = topPos + SCREEN_BOTTOM - STATUS_ROW + 1;
        int right = leftPos + SCREEN_X1 - 4;
        int left = leftPos + WORK_X + 1;

        var supply = dev.devpanda.factorynetwork.client.ClientFlowState.supply();
        var compute = dev.devpanda.factorynetwork.client.ClientFlowState.compute();
        var state = dev.devpanda.factorynetwork.network.NetworkPower.State.values()[
                Math.min(supply.state(), dev.devpanda.factorynetwork.network
                        .NetworkPower.State.values().length - 1)];
        int lamp = switch (state) {
            case RUNNING -> ACCENT;
            case BOOTING -> 0xD08A3C;
            case OFF -> 0xA03030;
        };
        Component netz = FnFonts.mono(Component.translatable(
                "screen.factorynetwork.terminal.status",
                dev.devpanda.factorynetwork.client.ClientStorageView.shortAmount(
                        supply.stored()),
                compute.occupied(), compute.threads(),
                compute.program(), compute.disk()).getString());
        int netzBreite = font.width(netz);
        graphics.drawString(font, netz, right - netzBreite, y + 2, TEXT_DIM, false);
        graphics.fill(right - netzBreite - 8, y + 4, right - netzBreite - 4, y + 8,
                0xFF000000 | lamp);

        Component eigenes = statusLeft();
        if (eigenes == null) {
            return;
        }
        int platz = (right - netzBreite - 14) - left;
        if (platz < 20) {
            return;
        }
        Component gekuerzt = FnFonts.mono(
                font.plainSubstrByWidth(eigenes.getString(), platz));
        graphics.drawString(font, gekuerzt, left, y + 2,
                tab == TerminalTab.STORAGE && storageView.isFull() ? 0xD08A3C : TEXT_FAINT,
                false);
    }

    /** Was der aktive Reiter links in der Statuszeile stehen hat. */
    private Component statusLeft() {
        return switch (tab) {
            case STORAGE -> storageView.statusText();
            case CODE -> codeView.cursorPosition();
            default -> null;
        };
    }

    /** Beim Zeigen auf die Statuszeile steht dort, was die Zahlen bedeuten. */
    private void renderStatusTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = topPos + SCREEN_BOTTOM - STATUS_ROW + 1;
        if (mouseY < y || mouseY >= y + STATUS_ROW
                || mouseX < leftPos + SCREEN_X0 || mouseX >= leftPos + SCREEN_X1) {
            return;
        }
        java.util.List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.translatable("screen.factorynetwork.terminal.status.hint"));
        if (tab == TerminalTab.STORAGE && !storageView.isFull()) {
            lines.add(storageView.roomHint());
        }
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
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

    /**
     * Übernimmt das Programm — und lässt das Fenster offen.
     *
     * <p>Vorher schloss es sich dabei. Bei einer Ablehnung stand man draußen
     * mit einer Chatzeile und musste das Terminal wieder aufmachen, um zu
     * suchen, was nicht ging. Was daraus geworden ist, steht jetzt in der
     * Fußleiste des Editors.
     */
    void deploy() {
        PacketDistributor.sendToServer(
                DeployProgramPacket.of(menu.position(), codeView.project()));
    }

    /**
     * Öffnet den Editor im ganzen Fenster.
     *
     * <p>Das Terminal bleibt dabei offen — es wird nur überdeckt. Es geht
     * kein {@code closeContainer} an den Server, also bleibt der Spieler beim
     * Controller angemeldet, und beim Zurückkommen steht das Fenster mit
     * denselben Zahlen da.
     */
    void openBigEditor() {
        minecraft.setScreen(new CodeScreen(this, menu.position()));
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
            case LOG -> logView.mouseClicked(mouseX, mouseY, button);
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
            case LOG -> logView.mouseScrolled(deltaY);
            default -> false;
        };
        return handled || super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    /**
     * Tasten für das Terminal.
     *
     * <p><b>Escape geht durch den Reiter, bevor es das Fenster schließt.</b>
     * Vorher stand es ganz oben, und die Suchzeile des Speichers wie auch die
     * Suche im Editor sahen es nie — man schloss das ganze Terminal, wo man
     * nur eine Eingabe beenden wollte. Jetzt beendet der erste Escape, was
     * offen ist, und der zweite das Fenster.
     *
     * <p>Und die Inventartaste darf keinen Reiter treffen, der tippt: Ein
     * Fenster mit Inventar schließt sich bei „e", und dann steht man mitten
     * im Wort „Eisenerz" wieder in der Welt.
     */
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (tab == TerminalTab.CODE) {
            // Strg+Eingabe übernimmt das Programm.
            if ((key == 257 || key == 335) && hasControlDown()) {
                deploy();
                return true;
            }
            if (key == 83 && hasControlDown()) {
                dev.devpanda.factorynetwork.client.ClientProjectState.flush();
                return true;
            }
            if (codeView.keyPressed(key, scanCode, modifiers)) {
                return true;
            }
            if (key == 256) {
                onClose();
                return true;
            }
            // Alles Übrige verschlucken, damit kein Tastendruck im Text
            // draußen etwas auslöst.
            return true;
        }
        if (tab == TerminalTab.STORAGE && storageView.keyPressed(key, scanCode, modifiers)) {
            return true;
        }
        if (key == 256) {
            onClose();
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
        // Zuerst sichern: Danach ist das Fenster weg, und mit ihm der Anlass,
        // noch einmal nachzusehen.
        dev.devpanda.factorynetwork.client.ClientProjectState.flush();
        PacketDistributor.sendToServer(new StorageTabPacket(false));
        ClientStorageView.clear();
        // Was in den Geräten lag, galt für diesen Besuch. Beim nächsten Öffnen
        // wird neu gefragt.
        dev.devpanda.factorynetwork.client.ClientDeviceState.clear();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
