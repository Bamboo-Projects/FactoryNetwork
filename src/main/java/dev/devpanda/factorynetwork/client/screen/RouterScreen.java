package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.RouterBlockEntity;
import dev.devpanda.factorynetwork.client.menu.RouterMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * Der Bildschirm des Routers: sechs Seiten, fünf Knöpfe je Seite.
 *
 * <p>Dieselben Farben wie die Ringe am Block — wer die Front gesehen hat,
 * erkennt hier dieselbe Bahn wieder. Und dieselbe Reihenfolge: aus, dann eins
 * bis vier.
 */
public class RouterScreen extends AbstractContainerScreen<RouterMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FactoryNetwork.MOD_ID, "textures/gui/router.png");

    /** Abgeklemmt: dasselbe Dunkelgrau wie am Block. */
    private static final int OFF_COLOUR = 0xFF34383C;

    /** Alles durch: ein neutrales Hellgrau, kein Farbton. */
    private static final int ALL_COLOUR = 0xFFB0B4B8;

    /**
     * Die Farbe eines Knopfes.
     *
     * <p><b>Aus dem Farbstoff und nicht aus einer Liste.</b> Vorher standen
     * hier fünf Zahlen, doppelt aufgeschrieben — einmal hier, einmal im
     * Malskript. Seit der Router Farben führt statt Bahnen, sind es
     * siebzehn, und siebzehn doppelt gepflegte Zahlen laufen auseinander.
     */
    private static int colourOf(int wert) {
        if (wert == RouterBlockEntity.OFF) {
            return OFF_COLOUR;
        }
        if (wert == RouterBlockEntity.ALL) {
            return ALL_COLOUR;
        }
        var farben = dev.devpanda.factorynetwork.block.CableColour.values();
        var farbe = farben[Math.min(wert - 2, farben.length - 1)];
        return farbe.dye() == null ? ALL_COLOUR
                : 0xFF000000 | farbe.dye().getTextureDiffuseColor();
    }

    private static final int ROW_TOP = 32;
    private static final int ROW_HEIGHT = 18;
    private static final int BUTTON_LEFT = 62;

    /**
     * Ein Knopf je Seite, nicht einer je Einstellung.
     *
     * <p>Achtzehn Einstellungen mal sechs Seiten wären hundertacht Knöpfe.
     * Stattdessen zeigt jede Zeile <b>ihren</b> Wert, und ein Klick schaltet
     * weiter — dieselbe Geste wie am Block.
     */
    private static final int BUTTON_SIZE = 16;
    private static final int BUTTON_STEP = 20;

    /** Wie eine Einstellung heißt: aus, alles, oder eine Farbe. */
    private static Component labelOf(int wert) {
        if (wert == RouterBlockEntity.OFF) {
            return Component.translatable("screen.factorynetwork.router.off");
        }
        if (wert == RouterBlockEntity.ALL) {
            return Component.translatable("screen.factorynetwork.router.all");
        }
        var farben = dev.devpanda.factorynetwork.block.CableColour.values();
        var farbe = farben[Math.min(wert - 2, farben.length - 1)];
        return Component.translatable("colour.factorynetwork." + farbe.getSerializedName());
    }

    public RouterScreen(RouterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 162;
        // Ein Router nimmt nichts auf; die Beschriftung „Inventar" wäre eine
        // Überschrift ohne Inhalt.
        this.inventoryLabelY = -1000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 512, 512);

        for (Direction side : Direction.values()) {
            int y = topPos + ROW_TOP + side.ordinal() * ROW_HEIGHT;
            graphics.drawString(font, Component.translatable(
                            "side.factorynetwork." + side.getSerializedName()),
                    leftPos + 9, y + 4, Widgets.CASE_TEXT, false);

            int aktiv = menu.lane(side);
            int x = leftPos + BUTTON_LEFT;
            graphics.fill(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, 0xFFFFFFFF);
            graphics.fill(x + 1, y + 1, x + BUTTON_SIZE - 1, y + BUTTON_SIZE - 1,
                    colourOf(aktiv));

            // Daneben der Name: Ein Farbfeld allein ist bei siebzehn Farben
            // nicht mehr zu unterscheiden — Hellblau und Cyan liegen zwei
            // Pixel auseinander.
            graphics.drawString(font, labelOf(aktiv),
                    x + BUTTON_SIZE + 6, y + 4, Widgets.CASE_TEXT, false);
        }

        // Unter den Seiten, was der Router kostet: einen Tick. Ohne diese
        // Zeile wäre die Verzögerung eine Wirkung ohne sichtbare Ursache —
        // und wer sein Netz sauber trennt, wüsste nicht, wofür er zahlt.
        graphics.drawString(font, Component.translatable(
                        "screen.factorynetwork.router.latency",
                        dev.devpanda.factorynetwork.network.Latency.PER_HOP),
                leftPos + 9, topPos + ROW_TOP + Direction.values().length * ROW_HEIGHT + 4,
                Widgets.CASE_TEXT_DIM, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        // Über den Knöpfen steht, was die Bahn trägt — die Zahl, für die man
        // sonst jede Seite einzeln ansehen müsste.
        int lane = laneAt(mouseX, mouseY);
        if (lane > 0) {
            graphics.renderTooltip(font, List.of(
                            labelOf(lane),
                            Component.translatable("screen.factorynetwork.router.load",
                                    menu.formatLoad(lane))),
                    java.util.Optional.empty(), mouseX, mouseY);
        } else if (lane == 0) {
            graphics.renderTooltip(font,
                    Component.translatable("screen.factorynetwork.router.off"), mouseX, mouseY);
        }
    }

    /** Welche Einstellung unter dem Zeiger steht, oder -1. */
    private int laneAt(int mouseX, int mouseY) {
        Direction side = sideAt(mouseX, mouseY);
        return side == null ? -1 : menu.lane(side);
    }

    /** Über welcher Zeile der Zeiger steht, oder {@code null}. */
    private Direction sideAt(double mouseX, double mouseY) {
        int x = leftPos + BUTTON_LEFT;
        if (mouseX < x || mouseX >= x + BUTTON_SIZE) {
            return null;
        }
        for (Direction side : Direction.values()) {
            int y = topPos + ROW_TOP + side.ordinal() * ROW_HEIGHT;
            if (mouseY >= y && mouseY < y + BUTTON_SIZE) {
                return side;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Direction side = sideAt(mouseX, mouseY);
        if (side != null) {
            // Ein Klick schaltet weiter, statt einen von achtzehn Knöpfen zu
            // treffen — dieselbe Geste wie am Block. Rechtsklick geht
            // zurück: Wer eine Farbe verpasst, wartet sonst siebzehn Klicks.
            int naechste = button == 1
                    ? (menu.lane(side) + RouterBlockEntity.LANES) % (RouterBlockEntity.LANES + 1)
                    : (menu.lane(side) + 1) % (RouterBlockEntity.LANES + 1);
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                    RouterMenu.buttonFor(side, naechste));
            minecraft.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
