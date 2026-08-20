package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.item.ConnectorNaming;
import dev.devpanda.factorynetwork.item.LabelGunItem;
import dev.devpanda.factorynetwork.network.packet.SetLabelPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Die Namenseingabe der Label-Gun.
 *
 * <p>Bewusst schlicht und in Vanillas Bauteilen — {@link EditBox} und
 * {@link Button} sehen aus wie im Rest des Spiels und bringen Auswahl,
 * Einfügen und Tastenwiederholung mit.
 *
 * <p>Darunter stehen die zuletzt benutzten Namen zum Anklicken. Das ist der
 * eigentliche Nutzen: Wer zehn Öfen benennt, tippt den Namen einmal.
 */
public class LabelGunScreen extends Screen {

    private static final int WIDTH = 220;
    private static final int LINE = 22;

    private final ItemStack gun;
    private EditBox input;
    private String warning = "";
    private int warningColour = 0xA0A0A0;

    public LabelGunScreen(ItemStack gun) {
        super(Component.translatable("screen.factorynetwork.label_gun.title"));
        this.gun = gun;
    }

    @Override
    protected void init() {
        int left = (width - WIDTH) / 2;
        int top = height / 3;

        input = new EditBox(font, left, top, WIDTH, 20,
                Component.translatable("screen.factorynetwork.label_gun.name"));
        input.setMaxLength(64);
        input.setValue(LabelGunItem.activeLabel(gun));
        input.setResponder(this::onTyped);
        addRenderableWidget(input);
        setInitialFocus(input);

        addRenderableWidget(Button.builder(
                Component.translatable("screen.factorynetwork.label_gun.apply"),
                button -> confirm()).bounds(left, top + 28, 104, 20).build());
        addRenderableWidget(Button.builder(
                Component.translatable("gui.cancel"),
                button -> onClose()).bounds(left + WIDTH - 104, top + 28, 104, 20).build());

        // Zuletzt benutzte Namen als Knöpfe
        List<String> recent = LabelGunItem.recentLabels(gun);
        for (int i = 0; i < Math.min(recent.size(), 5); i++) {
            String label = recent.get(i);
            addRenderableWidget(Button.builder(Component.literal(label), button -> {
                input.setValue(label);
                confirm();
            }).bounds(left, top + 60 + i * LINE, WIDTH, 20).build());
        }
        onTyped(input.getValue());
    }

    /**
     * Prüft beim Tippen, was an dem Namen auffällt.
     *
     * <p>Dieselben Regeln wie der Übersetzer, aber ohne zu blockieren: Ein
     * Schlüsselwort ist als Name erlaubt, es braucht im Code nur Rückstriche.
     * Ob der Name schon vergeben ist, weiß der Client hier nicht — das
     * merkt die Gun beim Klick auf den Connector.
     */
    private void onTyped(String text) {
        ConnectorNaming.Warning result = ConnectorNaming.check(text, null);
        switch (result.kind()) {
            case EMPTY -> {
                warning = Component.translatable(
                        "screen.factorynetwork.label_gun.empty").getString();
                warningColour = 0xA0A0A0;
            }
            case NOT_AN_IDENTIFIER -> {
                warning = Component.translatable(
                        "screen.factorynetwork.label_gun.invalid_hint").getString();
                warningColour = 0xE88388;
            }
            case KEYWORD -> {
                warning = Component.translatable(
                        "screen.factorynetwork.label_gun.keyword_hint", text).getString();
                warningColour = 0xE8B888;
            }
            default -> {
                warning = Component.translatable(
                        "screen.factorynetwork.label_gun.ready").getString();
                warningColour = 0xA3D9A5;
            }
        }
    }

    private void confirm() {
        String name = ConnectorNaming.normalize(input.getValue());
        if (!name.isEmpty() && !ConnectorNaming.isValidIdentifier(name)) {
            return;
        }
        PacketDistributor.sendToServer(new SetLabelPacket(name));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = (width - WIDTH) / 2;
        int top = height / 3;

        // Eine eigene Fläche hinter den Dialog. Ohne sie steht weißer Text
        // auf verschwommenem Himmel und ist nicht zu lesen — der
        // Standardhintergrund reicht nur, wenn ein Fenster ohnehin ein Panel
        // mitbringt.
        int padding = 12;
        int panelTop = top - 42;
        int panelBottom = top + 64 + recentRows() * LINE;
        graphics.fill(left - padding, panelTop, left + WIDTH + padding, panelBottom, 0xE0101214);
        drawBorder(graphics, left - padding, panelTop,
                left + WIDTH + padding, panelBottom, 0xFF3C4147);

        // Mit Schatten, wie jede Überschrift im Spiel.
        graphics.drawCenteredString(font, title, width / 2, panelTop + 8, 0xFFFFFF);
        graphics.drawString(font,
                Component.translatable("screen.factorynetwork.label_gun.explain"),
                left, panelTop + 22, 0xA0A8B0, true);

        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawString(font, warning, left, top + 52, warningColour, true);
    }

    private int recentRows() {
        return Math.min(LabelGunItem.recentLabels(gun).size(), 5);
    }

    private static void drawBorder(GuiGraphics graphics, int x0, int y0, int x1, int y1,
                                   int colour) {
        graphics.fill(x0, y0, x1, y0 + 1, colour);
        graphics.fill(x0, y1 - 1, x1, y1, colour);
        graphics.fill(x0, y0, x0 + 1, y1, colour);
        graphics.fill(x1 - 1, y0, x1, y1, colour);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        // Eingabe bestätigt, wie in jedem Textfeld des Spiels.
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
