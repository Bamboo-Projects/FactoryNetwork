package dev.devpanda.factorynetwork.client.screen;

import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import dev.devpanda.factorynetwork.platform.client.Keys;
import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.FnFonts;
import dev.devpanda.factorynetwork.client.ClientNetworkState;
import dev.devpanda.factorynetwork.client.ClientStorageView;
import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import dev.devpanda.factorynetwork.network.packet.DeployProgramPacket;
import dev.devpanda.factorynetwork.network.packet.StorageTabPacket;
import dev.devpanda.factorynetwork.terminal.TerminalTab;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * The terminal.
 *
 * <p>A single window with tabs instead of a surface per block. The casing is
 * deliberately Minecraft: the same slot grid, the player inventory in the
 * place where it always sits. <b>What works blind is the position</b> — the
 * same spots, the same measurements, the same spacings.
 *
 * <p>The colour is not. The terminal is a device and looks like one: a metal
 * casing with a dark pane in it, into which all tabs draw. Before, only the
 * code tab was dark and everything else light grey — those were two designs
 * in one window.
 *
 * <p>Three layers, one rule: <b>what lies recessed is darker than its ground
 * and has a light edge at the bottom right.</b> Metal, pane, recess.
 */
public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {

    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/terminal.png");
    private static final Identifier WIDGETS = Identifier.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/widgets.png");

    // The measurements live in TerminalLayout, because the menu needs them
    // too and runs on a server without graphics. Here only the short names.
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

    /** How far apart the tab labels stand. */
    private static final int TAB_GAP = 12;

    // Text on a dark ground. The values are the same as in the mod's block
    // palette — anyone who looks at the controller and then the terminal
    // should recognise the same colour palette.
    static final int TEXT = 0xFFD3DBD5;
    static final int TEXT_DIM = 0xFF8B978F;
    static final int TEXT_FAINT = 0xFF5D6862;
    static final int ACCENT = 0xFF78DC8C;

    /**
     * State colours, chosen for a dark ground.
     *
     * <p>The old ones were meant for light grey: a dark green for "running",
     * a dark red for "failed". On metal the two silt up into the same dirty
     * surface, and then the colour says nothing any more.
     */
    static final int GOOD = 0xFF78DC8C;
    static final int WARN = 0xFFE8AC3E;
    static final int BAD = 0xFFE88388;

    /** Buttons in the screen: raised metal, lighter on hover. */
    static final int BUTTON = 0xFF232B27;
    static final int BUTTON_HOVER = 0xFF39443D;

    private TerminalTab tab = TerminalTab.STORAGE;
    private StorageTabView storageView;
    private NetworkTabView networkView;
    private DashboardsTabView dashboardsView;
    private CraftingTabView craftingView;
    private LogTabView logView;
    private CodeTabView codeView;

    public TerminalScreen(TerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
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
        craftingView = new CraftingTabView(font, leftPos + WORK_X, topPos + WORK_Y,
                WORK_W, WORK_H);
        logView = new LogTabView(font, leftPos + WORK_X, topPos + WORK_Y, WORK_W, WORK_H);
        codeView = new CodeTabView(this, font, leftPos + WORK_X, topPos + WORK_Y,
                WORK_W, WORK_H);
        announceTab();
    }

    /** The server learns only whether storage is open — nothing else. */
    private void announceTab() {
        ClientPacketDistributor.sendToServer(new StorageTabPacket(tab == TerminalTab.STORAGE));
    }

    /**
     * The tabs this window shows.
     *
     * <p>At the block, all of them. From a distance everything except code —
     * unless it is a laptop. <b>The decision is the server's to make</b>; here
     * it is only asked once more, so that nothing is drawn that would be
     * rejected on the other side.
     */
    private java.util.List<TerminalTab> tabs() {
        return java.util.Arrays.stream(TerminalTab.values())
                .filter(menu::allows)
                .toList();
    }

    private void switchTo(TerminalTab next) {
        if (next == tab || !next.isReady() || !menu.allows(next)) {
            return;
        }
        tab = next;
        announceTab();
    }

    TerminalTab tab() {
        return tab;
    }

    // ---- Drawing ----------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        renderTabTooltips(graphics, mouseX, mouseY);
        renderStatusTooltip(graphics, mouseX, mouseY);
        if (tab == TerminalTab.CODE) {
            codeView.renderTooltip(graphics, mouseX, mouseY);
        }
        if (tab == TerminalTab.STORAGE) {
            storageView.renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0, 0, WIDTH, HEIGHT, 512, 512);
        drawTabs(graphics, mouseX, mouseY);
        switch (tab) {
            case STORAGE -> storageView.extractRenderState(graphics, mouseX, mouseY);
            case NETWORK -> networkView.extractRenderState(graphics, mouseX, mouseY);
            case DASHBOARDS -> dashboardsView.extractRenderState(graphics, mouseX, mouseY);
            case CRAFTING -> craftingView.extractRenderState(graphics, mouseX, mouseY);
            case LOG -> logView.extractRenderState(graphics, mouseX, mouseY);
            case CODE -> codeView.extractRenderState(graphics, mouseX, mouseY);
            default -> { }
        }
        // Last, so it lies over everything a tab draws at the bottom.
        drawStatus(graphics);
    }

    /**
     * How wide a tab label is.
     *
     * <p>By the text and not by a fifth of the window: tabs are now labels and
     * not index cards, and a label is as wide as it is. Along the way this
     * removes the bug where a fixed width had to match a fixed-width graphic.
     */
    private int tabWidth(TerminalTab tab) {
        return font.width(FnFonts.mono(tab.title()));
    }

    /**
     * The gap between two labels.
     *
     * <p><b>Shrinks when space gets tight.</b> With a fixed gap the sixth tab
     * ran off the window, and the seventh would do it again. The bar would
     * rather do the arithmetic itself than have someone remember it at the
     * next tab.
     */
    private int tabGap() {
        int labels = 0;
        for (TerminalTab candidate : tabs()) {
            labels += tabWidth(candidate);
        }
        int room = SCREEN_X1 - (SCREEN_X0 + 6) - labels;
        // Fewer tabs, wider gaps: five labels should fill the bar and not
        // clump together on the left.
        int gaps = Math.max(1, tabs().size() - 1);
        return Math.max(3, Math.min(TAB_GAP, room / gaps));
    }

    /** Where a tab label begins, measured within the window. */
    private int tabX(TerminalTab wanted) {
        int x = SCREEN_X0 + 6;
        int gap = tabGap();
        for (TerminalTab candidate : tabs()) {
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
     * The tabs: flat text in the pane, the active one with an underline.
     *
     * <p>Index cards are the creative inventory. A device has labels, and
     * which one applies is told by a line beneath it — as in every editor.
     */
    private void drawTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (TerminalTab candidate : tabs()) {
            int x = leftPos + tabX(candidate);
            int y = topPos + tabY();
            boolean active = candidate == tab;
            int colour = active ? TEXT : candidate.isReady() ? TEXT_DIM : TEXT_FAINT;
            graphics.text(font, FnFonts.mono(candidate.title()), x, y, colour, false);
            if (active) {
                graphics.fill(x, y + 11, x + tabWidth(candidate), y + 12, 0xFF000000 | ACCENT);
            }
        }
    }

    /** Does the cursor hit this label? */
    private boolean overTab(TerminalTab candidate, double mouseX, double mouseY) {
        int x = leftPos + tabX(candidate);
        int y = topPos + tabY();
        return mouseX >= x && mouseX < x + tabWidth(candidate)
                && mouseY >= y && mouseY < y + TAB_ROW - 2;
    }

    /**
     * The status line at the bottom edge of the pane.
     *
     * <p>On the right, what the network is doing — the same on every tab. On
     * the left, what the tab has to say. <b>Status lines belong at the
     * bottom</b>; everyone who uses an editor knows that, and at the top it
     * would be a third line of header above the actual content.
     *
     * <p>Both texts are measured and checked against each other. In the draft
     * I had measured only the right one, and they slid into each other.
     */
    private void drawStatus(GuiGraphicsExtractor graphics) {
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
            case BOOTING -> 0xFFD08A3C;
            case OFF -> 0xFFA03030;
        };
        Component netz = FnFonts.mono(Component.translatable(
                "screen.factorynetwork.terminal.status",
                dev.devpanda.factorynetwork.client.ClientStorageView.shortAmount(
                        supply.stored()),
                compute.occupied(), compute.threads(),
                compute.program(), compute.disk()).getString());
        int netzBreite = font.width(netz);
        graphics.text(font, netz, right - netzBreite, y + 2, TEXT_DIM, false);
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
        graphics.text(font, gekuerzt, left, y + 2,
                tab == TerminalTab.STORAGE && storageView.isFull() ? 0xFFD08A3C : TEXT_FAINT,
                false);
    }

    /** What the active tab shows on the left of the status line. */
    private Component statusLeft() {
        return switch (tab) {
            case STORAGE -> storageView.statusText();
            case CODE -> codeView.cursorPosition();
            default -> null;
        };
    }

    /** On hover over the status line, it shows what the numbers mean. */
    private void renderStatusTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
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
        graphics.setTooltipForNextFrame(font, lines, java.util.Optional.empty(), mouseX, mouseY);
    }

    private void renderTabTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (TerminalTab candidate : tabs()) {
            if (!candidate.isReady() && overTab(candidate, mouseX, mouseY)) {
                graphics.setTooltipForNextFrame(candidate.notReadyHint(), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // The window title is in the tab bar; only the inventory is labelled,
        // as in every vanilla window.
        graphics.text(font, playerInventoryTitle, (WIDTH - 9 * 18) / 2,
                inventoryLabelY, Widgets.CASE_TEXT, false);
    }

    /**
     * Applies the program — and leaves the window open.
     *
     * <p>Before, it closed in the process. On a rejection you stood outside
     * with a chat line and had to reopen the terminal to look for what did not
     * work. What became of it now sits in the editor's footer.
     */
    void deploy() {
        ClientPacketDistributor.sendToServer(
                DeployProgramPacket.of(menu.position(), codeView.project()));
    }

    /**
     * Opens the editor in the whole window.
     *
     * <p>The terminal stays open while this happens — it is only covered. No
     * {@code closeContainer} goes to the server, so the player stays signed in
     * at the controller, and on coming back the window is there with the same
     * numbers.
     */
    void openBigEditor() {
        // The built-in editor is the one every player has. The web editor is
        // taken where BambooCEF is installed and its runtime is up; where it
        // is still on its way, the waiting screen says so. In every other
        // case the built-in one opens at once — nobody should have to click
        // past a message about something they never installed.
        if (dev.devpanda.factorynetwork.client.WebSupport.installed()) {
            switch (dev.devpanda.factorynetwork.client.WebBridge
                    .openEditor(minecraft, this, menu)) {
                case OPENED -> {
                    return;
                }
                case PREPARING -> {
                    minecraft.setScreen(dev.devpanda.factorynetwork.client.WebBridge
                            .waitingScreen(this, menu));
                    return;
                }
                case UNAVAILABLE -> {
                }
            }
        }
        minecraft.setScreen(new CodeScreen(this, menu.position()));
    }

    // ---- Input ------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        // Tab bar first
        for (TerminalTab candidate : tabs()) {
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
            case CRAFTING -> craftingView.mouseClicked(mouseX, mouseY, button);
            case LOG -> logView.mouseClicked(mouseX, mouseY, button);
            default -> false;
        };
        return handled || super.mouseClicked(event, doubleClick);
    }

    /**
     * Dragging belongs to the code tab: there it selects text.
     *
     * <p>The other tabs know no dragging — there the gesture goes back to
     * Minecraft, so that sliders and buttons keep working.
     */
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (tab == TerminalTab.CODE && codeView.mouseDragged(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        boolean handled = switch (tab) {
            case STORAGE -> storageView.mouseScrolled(mouseX, mouseY, deltaY);
            case CODE -> codeView.mouseScrolled(mouseX, mouseY, deltaY);
            case LOG -> logView.mouseScrolled(deltaY);
            case NETWORK -> networkView.mouseScrolled(deltaY);
            default -> false;
        };
        return handled || super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    /**
     * Keys for the terminal.
     *
     * <p><b>Escape passes through the tab before it closes the window.</b>
     * Before, it sat at the very top, and neither the storage search bar nor
     * the search in the editor ever saw it — you closed the whole terminal
     * where you only wanted to end an input. Now the first Escape ends
     * whatever is open, and the second the window.
     *
     * <p>And the inventory key must not reach a tab that is typing: a window
     * with an inventory closes on "e", and then you are standing in the middle
     * of the word "iron ore" back in the world.
     */
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        int scanCode = event.scancode();
        int modifiers = event.modifiers();
        if (tab == TerminalTab.CODE) {
            // Ctrl+Enter applies the program.
            if ((key == 257 || key == 335) && Keys.control()) {
                deploy();
                return true;
            }
            if (key == 83 && Keys.control()) {
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
            // Swallow everything else, so that no keystroke in the text
            // triggers something outside.
            return true;
        }
        if (tab == TerminalTab.STORAGE && storageView.keyPressed(key, scanCode, modifiers)) {
            return true;
        }
        if (key == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    /**
     * Since 26.1 a whole code point arrives, without modifier flags. A
     * character beyond the basic plane goes on as its two halves, the way
     * Minecraft used to hand it over; the flags are queried.
     */
    @Override
    public boolean charTyped(CharacterEvent event) {
        boolean taken = false;
        for (char unit : Character.toChars(event.codepoint())) {
            taken |= typed(unit, dev.devpanda.factorynetwork.platform.client.Keys.held());
        }
        return taken;
    }

    private boolean typed(char character, int modifiers) {
        if (tab == TerminalTab.CODE) {
            return codeView.charTyped(character, modifiers);
        }
        if (tab == TerminalTab.STORAGE) {
            return storageView.charTyped(character, modifiers);
        }
        return super.charTyped(new CharacterEvent(character));
    }

    @Override
    public void onClose() {
        // Save first: after this the window is gone, and with it the occasion
        // to look once more.
        dev.devpanda.factorynetwork.client.ClientProjectState.flush();
        ClientPacketDistributor.sendToServer(new StorageTabPacket(false));
        ClientStorageView.clear();
        // What was in the devices applied to this visit. On the next opening
        // it is asked anew.
        dev.devpanda.factorynetwork.client.ClientDeviceState.clear();
        dev.devpanda.factorynetwork.client.LiveValues.clear();
        dev.devpanda.factorynetwork.client.ClientBreakpoints.clear();
        dev.devpanda.factorynetwork.client.EditorInsert.setTarget(null);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
