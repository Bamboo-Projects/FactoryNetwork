package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.client.FnFonts;
import dev.devpanda.factorynetwork.item.ConnectorNaming;
import dev.devpanda.factorynetwork.item.LabelGunItem;
import dev.devpanda.factorynetwork.network.packet.SetLabelPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Die Namenseingabe der Beschriftungspistole.
 *
 * <p>Seit Connector und Anzeige ein eigenes Fenster haben, ist die Pistole
 * keine Voraussetzung mehr, um überhaupt etwas benennen zu können. Sie kann
 * dafür etwas, das ein Fenster am Block nicht kann: <b>einen Namen einmal
 * tippen und zwanzigmal vergeben.</b> Darum geht es hier — die Liste der
 * zuletzt benutzten Namen ist der eigentliche Inhalt.
 *
 * <p><b>Die Fläche wird gerechnet, nicht gesetzt.</b> Vorher standen hier
 * geratene Abstände: die Knöpfe bei {@code top + 60 + i * 22}, der Rand bei
 * {@code top + 64 + Zeilen * 22}, die Warnung acht Pixel über dem ersten
 * Knopf. Bei einer Erklärung, die über zwei Zeilen läuft, oder bei fünf
 * gemerkten Namen ging das nicht mehr auf. Jetzt kennt jede Zeile ihre Höhe,
 * und der Rahmen ist ihre Summe.
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

    /** So viele gemerkte Namen stehen zur Auswahl. */
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

    /** Wie hoch die Fläche sein muss, damit alles hineinpasst. */
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
     * Prüft beim Tippen, was an dem Namen auffällt.
     *
     * <p>Dieselben Regeln wie der Übersetzer, aber ohne zu blockieren: Ein
     * Schlüsselwort ist als Name erlaubt, es braucht im Code nur Rückstriche.
     * Ob der Name schon vergeben ist, weiß der Client hier nicht — das
     * merkt die Pistole beim Klick auf den Connector.
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
        graphics.fill(left - PAD - 1, panelTop - 1, left + WIDTH + PAD + 1, panelBottom + 1,
                0xFF080A09);
        graphics.fill(left - PAD, panelTop, left + WIDTH + PAD, panelBottom, 0xFF232B27);

        // Text über die Fläche heben: Minecraft sammelt Beschriftungen in
        // einem eigenen Puffer und zeichnet sie zusammen — ohne diesen
        // Vorsprung landen sie hinter jeder Füllung, die danach kommt.
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

        super.render(graphics, mouseX, mouseY, partialTick);
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
