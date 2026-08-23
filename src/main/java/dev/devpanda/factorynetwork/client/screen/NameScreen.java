package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.block.entity.ConnectorBlockEntity;
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
 * Das Fenster, in dem ein Block seinen Namen bekommt.
 *
 * <p>Ein Feld, zwei Knöpfe, eine Zeile Rückmeldung. <b>Jede Höhe ist eine
 * Summe</b> — die Oberfläche der Beschriftungspistole hatte ihre Werte
 * geraten, und die Knöpfe standen über dem Rand hinaus. Wer eine Fläche
 * zeichnet, rechnet sie von einer Kante zur anderen durch.
 */
public class NameScreen extends AbstractContainerScreen<NameMenu> {

    private static final int PAD = 8;
    private static final int FIELD = 20;
    private static final int BUTTON = 20;
    private static final int LINE = 11;

    private EditBox input;
    private Component note = Component.empty();
    private int noteColour = TerminalScreen.TEXT_DIM;

    public NameScreen(NameMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 192;
        // Rand, Titel, Feld, Hinweiszeile, Knöpfe, Rand.
        this.imageHeight = PAD + LINE + 4 + FIELD + 3 + LINE + 4 + BUTTON + PAD;
    }

    /** Was gerade dransteht — aus dem Block, nicht aus dem Paket. */
    private String currentName() {
        var level = minecraft == null ? null : minecraft.level;
        if (level == null) {
            return "";
        }
        var entity = level.getBlockEntity(menu.position());
        if (entity instanceof DisplayBlockEntity display) {
            return display.displayName();
        }
        if (entity instanceof ConnectorBlockEntity connector) {
            return connector.label();
        }
        return "";
    }

    /** Ob am anderen Ende eine Anzeige hängt — nur für die Überschrift. */
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
        int y = topPos + PAD + LINE + 4;

        input = new EditBox(font, left, y, width, FIELD,
                Component.translatable("screen.factorynetwork.name.field"));
        input.setMaxLength(64);
        input.setValue(currentName());
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
     * Beim Tippen dieselben Regeln wie beim Übernehmen.
     *
     * <p>Ob der Name schon vergeben ist, weiß der Client nicht — das prüft
     * der Server. Hier steht nur, was sich ohne Netzwissen sagen lässt.
     */
    private void onTyped(String text) {
        ConnectorNaming.Warning result = ConnectorNaming.check(text, null);
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
                note = Component.translatable("screen.factorynetwork.name.ready");
                noteColour = TerminalScreen.GOOD;
            }
        }
    }

    private void confirm() {
        String name = ConnectorNaming.normalize(input.getValue());
        if (!name.isEmpty() && !ConnectorNaming.isValidIdentifier(name)) {
            return;
        }
        PacketDistributor.sendToServer(new SetBlockNamePacket(menu.position(), name));
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        // Eingabetaste übernimmt — sonst muss man mit der Maus zurück.
        if (key == 257 || key == 335) {
            confirm();
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
        graphics.fill(leftPos + PAD - 1, topPos + PAD + LINE + 3,
                leftPos + imageWidth - PAD + 1, topPos + PAD + LINE + 4 + FIELD + 1,
                0xFF080A09);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, FnFonts.mono(Component.translatable(isDisplay()
                        ? "screen.factorynetwork.name.title.display"
                        : "screen.factorynetwork.name.title.connector")),
                PAD, PAD, TerminalScreen.TEXT, false);
        graphics.drawString(font, note, PAD, PAD + LINE + 4 + FIELD + 3, noteColour, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
