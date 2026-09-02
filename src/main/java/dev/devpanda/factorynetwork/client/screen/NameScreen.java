package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.block.entity.DisplayBlockEntity;
import dev.devpanda.factorynetwork.client.FnFonts;
import dev.devpanda.factorynetwork.client.menu.NameMenu;
import dev.devpanda.factorynetwork.item.ConnectorNaming;
import dev.devpanda.factorynetwork.network.packet.SetBlockNamePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The window where a block gets its name.
 *
 * <p>One field, two buttons, one line of feedback. <b>Every height is a
 * sum</b> — the label gun's interface had guessed its values, and the buttons
 * stood out past the edge. Whoever draws an area computes it all the way from
 * one edge to the other.
 */
public class NameScreen extends AbstractContainerScreen<NameMenu> {

    private static final int PAD = 8;
    private static final int FIELD = 20;
    private static final int BUTTON = 20;
    private static final int LINE = 11;

    /** The slash that separates a plant from its role. */
    private static final char SEPARATOR =
            dev.devpanda.factorynetwork.runtime.MultiblockInstances.SEPARATOR;

    /**
     * The plant this device belongs to — or empty.
     *
     * <p><b>A field of its own, not a slash to type.</b> A plant comes about
     * from its devices being named {@code werk_1/eingang}; that is how it is
     * written in {@code anlagen.md}. Only, the window gave no sign that this
     * existed — and until now it could not even be typed there.
     *
     * <p>What goes out is the same name as before. The two fields are a
     * reading aid and not a second format: whoever types the slash into the
     * name field gets the same result.
     */
    private EditBox instance;

    private EditBox input;
    private Component note = Component.empty();
    private int noteColour = TerminalScreen.TEXT_DIM;

    public NameScreen(NameMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 192;
        // Border, title, plant, title, field, hint line, buttons, border.
        this.imageHeight = PAD + LINE + 4 + FIELD + 4
                + LINE + 4 + FIELD + 3 + LINE + 4 + BUTTON + PAD;
        // At a gateway the upper field and its label are missing.
        if (isGateway()) {
            this.imageHeight -= LINE + 4 + FIELD + 4;
        }
    }

    /** The part before the slash, or empty. */
    private static String instancePart(String label) {
        int cut = label.indexOf(SEPARATOR);
        return cut <= 0 ? "" : label.substring(0, cut);
    }

    /** The part after it — or the whole name, if none is there. */
    private static String rolePart(String label) {
        int cut = label.indexOf(SEPARATOR);
        return cut <= 0 ? label : label.substring(cut + 1);
    }

    /** What goes out in the end. */
    private String composed() {
        String rolle = ConnectorNaming.normalize(input.getValue());
        if (instance == null) {
            return rolle;
        }
        String anlage = ConnectorNaming.normalize(instance.getValue());
        return anlage.isEmpty() ? rolle : anlage + SEPARATOR + rolle;
    }

    /** What it currently reads — from the block, not from the packet. */
    private String currentName() {
        var level = minecraft == null ? null : minecraft.level;
        if (level == null) {
            return "";
        }
        var entity = level.getBlockEntity(menu.position());
        if (entity instanceof DisplayBlockEntity display) {
            return display.displayName();
        }
        // With the face, if the click named one: at the cable block the
        // position alone is not a connection.
        var part = menu.side() == null
                ? dev.devpanda.factorynetwork.block.entity.Connectors.at(level, menu.position())
                : dev.devpanda.factorynetwork.block.entity.Connectors.at(
                        level, menu.position(), menu.side());
        if (part != null) {
            return part.label();
        }
        if (entity instanceof dev.devpanda.factorynetwork.block.entity
                .GatewayBlockEntity gateway) {
            return gateway.instance();
        }
        return "";
    }

    /**
     * Whether a gateway hangs on the other end.
     *
     * <p>Then <b>only</b> the plant stands there: a gateway has no role, and a
     * second field for it would be a question without an answer.
     */
    private boolean isGateway() {
        var level = minecraft == null ? null : minecraft.level;
        return level != null && level.getBlockEntity(menu.position())
                instanceof dev.devpanda.factorynetwork.block.entity.GatewayBlockEntity;
    }

    /** Whether a display hangs on the other end — only for the heading. */
    private boolean isDisplay() {
        var level = minecraft == null ? null : minecraft.level;
        return level != null && level.getBlockEntity(menu.position())
                instanceof DisplayBlockEntity;
    }

    @Override
    protected void init() {
        super.init();
        int left = leftPos + PAD;
        int width = imageWidth - 2 * PAD;
        int top = topPos + PAD + LINE + 4;
        String label = currentName();

        // At a gateway there is only the plant: it has no role, and an empty
        // second field would be a question that has no answer.
        if (!isGateway()) {
            instance = new EditBox(font, left, top, width, FIELD,
                    Component.translatable("screen.factorynetwork.name.instance"));
            instance.setMaxLength(64);
            instance.setValue(instancePart(label));
            instance.setResponder(text -> onTyped(input == null ? "" : input.getValue()));
            addRenderableWidget(instance);
        }

        int y = isGateway() ? top : top + FIELD + 4 + LINE + 4;
        input = new EditBox(font, left, y, width, FIELD,
                Component.translatable("screen.factorynetwork.name.field"));
        input.setMaxLength(64);
        input.setValue(isGateway() ? label : rolePart(label));
        input.setResponder(this::onTyped);
        addRenderableWidget(input);
        setInitialFocus(input);

        int buttons = y + FIELD + 3 + LINE + 4;
        int half = (width - 4) / 2;
        addRenderableWidget(Button.builder(
                Component.translatable("screen.factorynetwork.name.apply"),
                button -> confirm()).bounds(left, buttons, half, BUTTON).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),
                button -> onClose()).bounds(left + width - half, buttons, half, BUTTON).build());
        onTyped(input.getValue());
    }

    /**
     * While typing, the same rules as on applying.
     *
     * <p>Whether the name is already taken the client does not know — the
     * server checks that. Here stands only what can be said without knowledge
     * of the network.
     */
    private void onTyped(String text) {
        // What is checked is the composed name and not the field: what goes
        // out is werk_1/eingang, and the rules hang on that.
        ConnectorNaming.Warning result = ConnectorNaming.check(composed(), null);
        switch (result.kind()) {
            case EMPTY -> {
                note = Component.translatable("screen.factorynetwork.name.empty");
                noteColour = TerminalScreen.TEXT_FAINT;
            }
            case NOT_AN_IDENTIFIER -> {
                note = Component.translatable("screen.factorynetwork.name.invalid");
                noteColour = TerminalScreen.BAD;
            }
            case KEYWORD -> {
                note = Component.translatable("screen.factorynetwork.name.keyword", text);
                noteColour = TerminalScreen.WARN;
            }
            default -> {
                // The full name as feedback: in a plant, what ends up there is
                // something other than what you are currently typing.
                note = FnFonts.mono(Component.literal(composed()));
                noteColour = TerminalScreen.GOOD;
            }
        }
    }

    private void confirm() {
        String name = composed();
        if (!name.isEmpty() && !ConnectorNaming.isValidDeviceName(name)) {
            return;
        }
        PacketDistributor.sendToServer(new SetBlockNamePacket(menu.position(),
                menu.side() == null
                        ? SetBlockNamePacket.NO_SIDE : menu.side().get3DDataValue(),
                name));
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        // The Enter key applies — otherwise you have to go back with the mouse.
        if (key == 257 || key == 335) {
            confirm();
            return true;
        }
        if (key == 256) {
            onClose();
            return true;
        }
        // <b>As long as the field is typing, all remaining keys belong to
        // it.</b> AbstractContainerScreen.keyPressed closes the window when the
        // key is the inventory key — and that is "e" by default. Whoever wanted
        // to name a connector "extruder_1" was back in the world after the
        // second letter. Vanilla solves it the same way in the anvil: ask the
        // field first, then canConsumeInput, and only when both say no does the
        // key pass on.
        if (input.keyPressed(key, scanCode, modifiers) || input.canConsumeInput()
                || (instance != null
                        && (instance.keyPressed(key, scanCode, modifiers)
                                || instance.canConsumeInput()))) {
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos - 1, topPos - 1, leftPos + imageWidth + 1,
                topPos + imageHeight + 1, 0xFF080A09);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight,
                0xFF232B27);
        int first = topPos + PAD + LINE + 3;
        graphics.fill(leftPos + PAD - 1, first,
                leftPos + imageWidth - PAD + 1, first + FIELD + 1, 0xFF080A09);
        if (instance != null) {
            int second = first + FIELD + 4 + LINE + 4;
            graphics.fill(leftPos + PAD - 1, second,
                    leftPos + imageWidth - PAD + 1, second + FIELD + 1, 0xFF080A09);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int second = PAD;
        if (instance != null) {
            graphics.drawString(font, FnFonts.mono(Component.translatable(
                            "screen.factorynetwork.name.instance")),
                    PAD, PAD, TerminalScreen.TEXT_DIM, false);
            second = PAD + LINE + 4 + FIELD + 4;
        }
        graphics.drawString(font, FnFonts.mono(Component.translatable(
                        isGateway() ? "screen.factorynetwork.name.title.gateway"
                                : isDisplay() ? "screen.factorynetwork.name.title.display"
                                        : "screen.factorynetwork.name.title.connector")),
                PAD, second, TerminalScreen.TEXT, false);
        graphics.drawString(font, note, PAD, second + LINE + 4 + FIELD + 3,
                noteColour, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
