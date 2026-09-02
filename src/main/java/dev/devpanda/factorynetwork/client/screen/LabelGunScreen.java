package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.client.FnFonts;
import dev.devpanda.factorynetwork.item.ConnectorNaming;
import dev.devpanda.factorynetwork.item.LabelGunItem;
import dev.devpanda.factorynetwork.network.packet.SetLabelPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The name entry of the label gun.
 *
 * <p>Ever since the connector and the display have their own window, the gun
 * is no longer a prerequisite for being able to name anything at all. In
 * return it can do something a window on the block cannot: <b>type a name once
 * and hand it out twenty times.</b> That is what this is about — the list of
 * recently used names is the real content.
 *
 * <p><b>The area is computed, not hard-set.</b> There used to be guessed
 * offsets here: the buttons at {@code top + 60 + i * 22}, the border at
 * {@code top + 64 + Zeilen * 22}, the warning eight pixels above the first
 * button. With an explanation running over two lines, or with five remembered
 * names, that no longer worked out. Now every line knows its height, and the
 * frame is their sum.
 */
public class LabelGunScreen extends Screen {

    private static final int WIDTH = 220;
    private static final int PAD = 10;
    private static final int LINE = 10;
    private static final int TITLE = 13;
    private static final int FIELD = 20;
    private static final int BUTTON = 20;
    private static final int RECENT = 22;
    private static final int GAP = 5;

    /** This many remembered names are offered to choose from. */
    private static final int MAX_RECENT = 5;

    private final ItemStack gun;
    private EditBox input;
    private Component note = Component.empty();
    private int noteColour = TerminalScreen.TEXT_DIM;

    private List<FormattedCharSequence> explain = List.of();
    private int panelTop;
    private int panelBottom;

    public LabelGunScreen(ItemStack gun) {
        super(Component.translatable("screen.factorynetwork.label_gun.title"));
        this.gun = gun;
    }

    private int recentRows() {
        return Math.min(LabelGunItem.recentLabels(gun).size(), MAX_RECENT);
    }

    /** How tall the area must be so that everything fits inside. */
    private int panelHeight() {
        int height = PAD + TITLE + explain.size() * LINE + GAP
                + FIELD + 3 + LINE + GAP + BUTTON + PAD;
        if (recentRows() > 0) {
            height += GAP + LINE + recentRows() * RECENT;
        }
        return height;
    }

    @Override
    protected void init() {
        explain = font.split(
                Component.translatable("screen.factorynetwork.label_gun.explain"), WIDTH);

        int left = (width - WIDTH) / 2;
        panelTop = (height - panelHeight()) / 2;
        panelBottom = panelTop + panelHeight();

        int y = panelTop + PAD + TITLE + explain.size() * LINE + GAP;
        input = new EditBox(font, left, y, WIDTH, FIELD,
                Component.translatable("screen.factorynetwork.label_gun.name"));
        input.setMaxLength(64);
        input.setValue(LabelGunItem.activeLabel(gun));
        input.setResponder(this::onTyped);
        addRenderableWidget(input);
        setInitialFocus(input);

        y += FIELD + 3 + LINE + GAP;
        int half = (WIDTH - 4) / 2;
        addRenderableWidget(Button.builder(
                Component.translatable("screen.factorynetwork.label_gun.apply"),
                button -> confirm()).bounds(left, y, half, BUTTON).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),
                button -> onClose()).bounds(left + WIDTH - half, y, half, BUTTON).build());

        if (recentRows() > 0) {
            y += BUTTON + GAP + LINE;
            List<String> recent = LabelGunItem.recentLabels(gun);
            for (int i = 0; i < recentRows(); i++) {
                String label = recent.get(i);
                addRenderableWidget(Button.builder(Component.literal(label), button -> {
                    input.setValue(label);
                    confirm();
                }).bounds(left, y + i * RECENT, WIDTH, BUTTON).build());
            }
        }
        onTyped(input.getValue());
    }

    /**
     * Checks while typing what stands out about the name.
     *
     * <p>The same rules as the compiler, but without blocking: a keyword is
     * allowed as a name, in code it only needs backticks. Whether the name is
     * already taken the client does not know here — the gun notices that on
     * the click on the connector.
     */
    private void onTyped(String text) {
        ConnectorNaming.Warning result = ConnectorNaming.check(text, null);
        switch (result.kind()) {
            case EMPTY -> {
                note = Component.translatable("screen.factorynetwork.label_gun.empty");
                noteColour = TerminalScreen.TEXT_FAINT;
            }
            case NOT_AN_IDENTIFIER -> {
                note = Component.translatable("screen.factorynetwork.label_gun.invalid_hint");
                noteColour = TerminalScreen.BAD;
            }
            case KEYWORD -> {
                note = Component.translatable(
                        "screen.factorynetwork.label_gun.keyword_hint", text);
                noteColour = TerminalScreen.WARN;
            }
            default -> {
                note = Component.translatable("screen.factorynetwork.label_gun.ready");
                noteColour = TerminalScreen.GOOD;
            }
        }
    }

    private void confirm() {
        String name = ConnectorNaming.normalize(input.getValue());
        if (!name.isEmpty() && !ConnectorNaming.isValidDeviceName(name)) {
            return;
        }
        PacketDistributor.sendToServer(new SetLabelPacket(name));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int left = (width - WIDTH) / 2;
        graphics.fill(left - PAD - 1, panelTop - 1, left + WIDTH + PAD + 1, panelBottom + 1,
                0xFF080A09);
        graphics.fill(left - PAD, panelTop, left + WIDTH + PAD, panelBottom, 0xFF232B27);

        // Lift the text above the panel: Minecraft collects labels in a buffer
        // of their own and draws them together — without this head start they
        // land behind every fill that comes afterwards.
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 100);
        graphics.drawString(font, FnFonts.mono(title), left, panelTop + PAD,
                TerminalScreen.TEXT, false);
        int line = panelTop + PAD + TITLE;
        for (FormattedCharSequence row : explain) {
            graphics.drawString(font, row, left, line, TerminalScreen.TEXT_DIM, false);
            line += LINE;
        }
        graphics.drawString(font, note, left, line + GAP + FIELD + 3, noteColour, false);
        if (recentRows() > 0) {
            graphics.drawString(font,
                    Component.translatable("screen.factorynetwork.label_gun.recent"),
                    left, line + GAP + FIELD + 3 + LINE + GAP + BUTTON + GAP,
                    TerminalScreen.TEXT_FAINT, false);
        }
        graphics.pose().popPose();

        // Only the widgets, not super.render: that would call renderBackground
        // again and with it the blur — over everything already drawn up here.
        // The reason this window looked smeared. The blur belongs at the
        // start, and that is where it is.
        for (Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        // Enter confirms, as in every text field in the game.
        if (key == 257 || key == 335) {
            confirm();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
